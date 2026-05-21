package com.quacky.duck;
 
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.*;
import android.widget.*;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
 
public class DuckOverlayService extends Service implements SensorEventListener {
 
    private static final String CHANNEL_ID = "quacky_channel";
 
    // ✅ Pon tu key de Groq aquí
    private static final String GROQ_API_KEY = "PEGA_TU_KEY_DE_GROQ_AQUI";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL   = "llama-3.3-70b-versatile";
 
    private static final int   DUCK_SIZE_DP = 60;
    private static final float SPEED_BASE   = 0.011f;
    private static final float GYRO_FORCE   = 18f;
 
    // Ventanas
    private View                       animalView;
    private WindowManager.LayoutParams animalParams;
    private LinearLayout               bubbleCard;
    private TextView                   bubbleText;
    private WindowManager.LayoutParams bubbleParams;
    private View                       footprintOverlay;
    private WindowManager              wm;
    private int                        overlayType;
 
    // Posición
    private float   currentX, currentY, targetX, targetY;
    private boolean isTalking   = false;
    private boolean isListening = false;
    private boolean facingRight = true;
    private Handler mainHandler;
    private Runnable moveRunnable;
 
    // Animación
    private Handler  walkHandler = new Handler(Looper.getMainLooper());
    private float    walkPhase   = 0f;
    private Runnable walkAnim;
 
    // Huellas
    private static class Footprint {
        float x, y; long born; boolean isLeft;
        Footprint(float x,float y,long born,boolean l){this.x=x;this.y=y;this.born=born;this.isLeft=l;}
    }
    private final List<Footprint> footprints=new ArrayList<>();
    private static final long FOOTPRINT_LIFE=1800;
    private long lastFootprintTime=0;boolean nextFootLeft=true;float lastFootX=-999,lastFootY=-999;
 
    // Sonidos
    private Handler  quackHandler=new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private final Random rng=new Random();
    private String animalTipo="duck";
    private String nombreMascota="";
 
    // TTS
    private TextToSpeech tts;
    private boolean      ttsListo=false;
 
    // Receptores de broadcast
    private BroadcastReceiver voiceResultReceiver;  // resultado de comando
    private BroadcastReceiver wakeWordReceiver;     // nombre detectado
    private BroadcastReceiver idleReceiver;         // no hubo detección → relanzar loop
 
    // ── Wake word loop ─────────────────────────────────────────────────────
    private boolean wakeLoopActivo = true;
    private Runnable wakeLoopRunnable;
    private static final int WAKE_PAUSA_MS = 1500; // pausa entre ciclos de escucha
 
    // Historial de chat
    private final List<JSONObject> chatHistory=new ArrayList<>();
    private Handler  bubbleHandler=new Handler(Looper.getMainLooper());
    private Runnable bubbleHider;
 
    // Giroscopio
    private SensorManager  sensorManager;
    private Sensor         gyroSensor;
    private volatile float gyroVelX=0f,gyroVelY=0f;
 
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler=new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1,buildNotification());
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        overlayType=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O
            ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            :WindowManager.LayoutParams.TYPE_PHONE;
 
        SharedPreferences prefs=getSharedPreferences("quacky_prefs",MODE_PRIVATE);
        animalTipo    =prefs.getString("animal","duck");
        nombreMascota =prefs.getString("nombre_mascota","").toLowerCase().trim();
 
        DisplayMetrics dm=getResources().getDisplayMetrics();
        currentX=dm.widthPixels/2f-dp(DUCK_SIZE_DP)/2f;
        currentY=dm.heightPixels*0.72f;
        targetX=currentX;targetY=currentY;
 
        setupFootprintOverlay();
        setupBubbleWindow();
        setupAnimalWindow();
        setupTTS();
        setupReceivers();   // ← Registra TODOS los receptores
        setupGyroscope();
        startMoveLoop();
        startRandomSounds();
 
        // Saludo inicial
        mainHandler.postDelayed(()->showBubble(saludoInicial(),5000,false),1200);
 
        // Iniciar loop de wake word después de 4 segundos
        mainHandler.postDelayed(this::iniciarWakeLoop,4000);
    }
 
    private String saludoInicial(){
        String n=nombreMascota.isEmpty()?"Quacky":capitalize(nombreMascota);
        switch(animalTipo){case"cat":return"¡Miau! Soy "+n+" 🐱 Tócame o dí mi nombre";case"dog":return"¡Guau! Soy "+n+" 🐶 Tócame o dí mi nombre";default:return"¡Quack! Soy "+n+" 🐥 Tócame o dí mi nombre";}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WAKE WORD LOOP
    //  El servicio lanza VoiceActivity en modo "wake" repetidamente.
    //  VoiceActivity escucha y manda:
    //    - VOICE_WAKEWORD si escuchó el nombre → activar
    //    - VOICE_IDLE     si no escuchó nada   → relanzar loop
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarWakeLoop() {
        if (!wakeLoopActivo || isListening || isTalking) return;
        if (nombreMascota.isEmpty()) return; // Sin nombre → no hay wake word
 
        // Lanzar VoiceActivity en modo silencioso de detección de nombre
        try {
            Intent i = new Intent(DuckOverlayService.this, VoiceActivity.class);
            i.putExtra("modo", "wake");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception e) {
            // Si falla, reintentar en 3 segundos
            mainHandler.postDelayed(this::iniciarWakeLoop, 3000);
        }
    }
 
    private void programarSiguienteWake() {
        if (!wakeLoopActivo || isListening || isTalking) return;
        mainHandler.postDelayed(this::iniciarWakeLoop, WAKE_PAUSA_MS);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  RECEPTORES DE BROADCAST
    // ─────────────────────────────────────────────────────────────────────────
    private void setupReceivers() {
        // ── 1. VOICE_WAKEWORD: el animal escuchó su nombre ──────────────────
        wakeWordReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                mainHandler.post(() -> {
                    // No estamos en medio de un comando activo
                    if (isTalking || isListening) return;
                    String n = nombreMascota.isEmpty() ? "Quacky" : capitalize(nombreMascota);
                    String saludo;
                    switch(animalTipo) {
                        case "cat": saludo="¡Miau! ¿Qué necesitas? 🐱"; break;
                        case "dog": saludo="¡Guau! ¡Aquí estoy! 🐶";    break;
                        default:    saludo="¡Quack! ¿Qué necesitas? 🐥"; break;
                    }
                    showBubble(saludo, 0, true);
                    animarSalto();
                    // Lanzar modo COMANDO después del saludo
                    mainHandler.postDelayed(() -> startListeningCommand(), 1800);
                });
            }
        };
        registrarReceiver(wakeWordReceiver, "com.quacky.duck.VOICE_WAKEWORD");
 
        // ── 2. VOICE_IDLE: no se detectó nombre → reiniciar wake loop ───────
        idleReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                mainHandler.post(() -> programarSiguienteWake());
            }
        };
        registrarReceiver(idleReceiver, "com.quacky.duck.VOICE_IDLE");
 
        // ── 3. VOICE_RESULT: resultado del modo COMANDO ──────────────────────
        voiceResultReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String texto = i.getStringExtra("voice_text");
                mainHandler.post(() -> {
                    isListening = false;
                    if (texto != null && !texto.isEmpty()) {
                        if (!handleVoiceCommand(texto)) {
                            showBubble("Tú: \""+texto+"\"", 2500, false);
                            askGroq(texto);
                        }
                    } else {
                        showBubble("No te escuché 🐾", 2000, false);
                        // Reiniciar wake loop después de no entender
                        mainHandler.postDelayed(() -> programarSiguienteWake(), 2000);
                    }
                });
            }
        };
        registrarReceiver(voiceResultReceiver, "com.quacky.duck.VOICE_RESULT");
    }
 
    private void registrarReceiver(BroadcastReceiver r, String accion) {
        IntentFilter f = new IntentFilter(accion);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }
 
    // ── Activar escucha por toque (igual que antes) ───────────────────────────
    private void startListening() { startListeningCommand(); }
 
    private void startListeningCommand() {
        if (isTalking || isListening) return;
        isListening = true;
        wakeLoopActivo = false; // Pausar loop mientras estamos en modo comando
        showBubble("...te escucho 👂", 0, false);
        animarSalto();
        Intent i = new Intent(DuckOverlayService.this, VoiceActivity.class);
        i.putExtra("modo", "command");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch (Exception e) {
            isListening = false; wakeLoopActivo = true;
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  VENTANA DEL ANIMAL
    // ─────────────────────────────────────────────────────────────────────────
    private void setupAnimalWindow(){
        int px=dp(DUCK_SIZE_DP);
        switch(animalTipo){
            case"cat":case"dog":{PetView pv=new PetView(this);int resId=getResources().getIdentifier(animalTipo,"drawable",getPackageName());if(resId!=0)try{Bitmap b=BitmapFactory.decodeResource(getResources(),resId);if(b!=null)pv.setAnimalBitmap(b);}catch(Exception ignored){}animalView=pv;break;}
            default:animalView=new DuckView(this);}
        animalParams=new WindowManager.LayoutParams(px,px,overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        animalParams.gravity=Gravity.TOP|Gravity.START;animalParams.x=(int)currentX;animalParams.y=(int)currentY;
        wm.addView(animalView,animalParams);
        animalView.setOnTouchListener(new View.OnTouchListener(){long ts;
            @Override public boolean onTouch(View v,MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN)ts=System.currentTimeMillis();
                if(e.getAction()==MotionEvent.ACTION_UP&&System.currentTimeMillis()-ts<400){wakeLoopActivo=true;startListeningCommand();}
                return true;}});
        setupWalkAnimation();}
 
    private void setWalkState(boolean m,float p,float d){
        if(animalView instanceof DuckView)  ((DuckView) animalView).setWalkState(m,p,d);
        else if(animalView instanceof PetView)((PetView)animalView).setWalkState(m,p,d);}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  BURBUJA
    // ─────────────────────────────────────────────────────────────────────────
    private void setupBubbleWindow(){
        bubbleCard=new LinearLayout(this);bubbleCard.setOrientation(LinearLayout.VERTICAL);bubbleCard.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(14));bg.setColor(0xEE1A1A3A);bg.setStroke(dp(1),0xFFFFD700);bubbleCard.setBackground(bg);
        bubbleText=new TextView(this);bubbleText.setTextColor(Color.WHITE);bubbleText.setTextSize(13);bubbleText.setMaxWidth(dp(240));bubbleCard.addView(bubbleText);
        bubbleCard.setVisibility(View.GONE);
        bubbleParams=new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.START;bubbleParams.x=(int)currentX;bubbleParams.y=Math.max(0,(int)currentY-dp(85));
        wm.addView(bubbleCard,bubbleParams);}
 
    private void actualizarPosBurbuja(){
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int bx=Math.max(dp(8),Math.min((int)currentX-dp(10),dm.widthPixels-dp(250)));int by=Math.max(dp(8),(int)currentY-dp(90));
        bubbleParams.x=bx;bubbleParams.y=by;try{if(bubbleCard.getVisibility()==View.VISIBLE)wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  TTS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupTTS(){tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS){configurarVoz(getSharedPreferences("quacky_prefs",MODE_PRIVATE).getString("voz","femenina"));ttsListo=true;}});}
    private void configurarVoz(String tipo){if(tts==null)return;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){Set<Voice>vs=tts.getVoices();if(vs!=null)for(Voice v:vs){if(v.getLocale()==null||!v.getLocale().getLanguage().equals("es"))continue;String n=v.getName().toLowerCase();if(tipo.equals("masculina")&&(n.contains("-m-")||n.contains("male")||n.contains("smb"))){tts.setVoice(v);return;}if(tipo.equals("femenina")&&(n.contains("-f-")||n.contains("female")||n.contains("sfb")||n.contains("esf"))){tts.setVoice(v);return;}}}tts.setLanguage(new Locale("es","MX"));tts.setPitch(tipo.equals("masculina")?0.78f:1.25f);tts.setSpeechRate(tipo.equals("masculina")?0.93f:1.05f);}
    private void hablar(String texto){if(!ttsListo||tts==null)return;String l=texto.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim();if(!l.isEmpty())tts.speak(l,TextToSpeech.QUEUE_FLUSH,null,"q_"+System.currentTimeMillis());}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GIROSCOPIO
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGyroscope(){sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);if(sensorManager==null)return;gyroSensor=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);if(gyroSensor!=null)sensorManager.registerListener(this,gyroSensor,SensorManager.SENSOR_DELAY_GAME);else{Sensor a=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(a!=null)sensorManager.registerListener(this,a,SensorManager.SENSOR_DELAY_GAME);}}
    @Override public void onSensorChanged(SensorEvent e){DisplayMetrics dm=getResources().getDisplayMetrics();int px=dp(DUCK_SIZE_DP);if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE){float rx=e.values[0],ry=e.values[1];if(Math.abs(rx)<0.05f)rx=0f;if(Math.abs(ry)<0.05f)ry=0f;gyroVelX+=ry*GYRO_FORCE;gyroVelY+=rx*GYRO_FORCE;targetX=Math.max(0,Math.min(targetX+gyroVelX,dm.widthPixels-px));targetY=Math.max(0,Math.min(targetY+gyroVelY,dm.heightPixels-px));gyroVelX*=0.85f;gyroVelY*=0.85f;}else if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){float ax=-e.values[0],ay=e.values[1];if(Math.abs(ax)<0.3f)ax=0f;if(Math.abs(ay)<0.3f)ay=0f;targetX=Math.max(0,Math.min(targetX+ax*1.2f,dm.widthPixels-px));targetY=Math.max(0,Math.min(targetY-ay*1.2f,dm.heightPixels-px));}}
    @Override public void onAccuracyChanged(Sensor s,int a){}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  HUELLAS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFootprintOverlay(){DisplayMetrics dm=getResources().getDisplayMetrics();footprintOverlay=new View(this){private final Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path pt=new Path();@Override protected void onDraw(Canvas c){long now=System.currentTimeMillis();fp.setStyle(Paint.Style.FILL);synchronized(footprints){Iterator<Footprint>it=footprints.iterator();while(it.hasNext()){Footprint f=it.next();float age=(now-f.born)/(float)FOOTPRINT_LIFE;if(age>=1f){it.remove();continue;}fp.setColor(Color.argb((int)(140*(1f-age)),180,80,0));float s=dm.density*3.5f;c.save();c.translate(f.x,f.y);c.rotate(f.isLeft?-15f:15f);pt.reset();pt.moveTo(0,0);pt.lineTo(-s,s*.6f);pt.lineTo(-s*.4f,s*1.1f);pt.lineTo(s*.2f,s*.7f);pt.lineTo(s*.8f,s*1.1f);pt.lineTo(s*1.2f,s*.5f);pt.close();c.drawPath(pt,fp);c.restore();}}postInvalidateDelayed(60);}};footprintOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE,null);WindowManager.LayoutParams p=new WindowManager.LayoutParams(dm.widthPixels,dm.heightPixels,overlayType,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;wm.addView(footprintOverlay,p);}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WALK + MOVE
    // ─────────────────────────────────────────────────────────────────────────
    private void setupWalkAnimation(){walkAnim=new Runnable(){@Override public void run(){boolean m=isMoving();if(m){walkPhase+=0.05f;if(walkPhase>1f)walkPhase=0f;}setWalkState(m,walkPhase,facingRight?1f:-1f);walkHandler.postDelayed(this,25);}};walkHandler.post(walkAnim);}
    private boolean isMoving(){return Math.abs(targetX-currentX)>2||Math.abs(targetY-currentY)>2;}
 
    private void startMoveLoop(){moveRunnable=new Runnable(){@Override public void run(){float dx=targetX-currentX,dy=targetY-currentY,dist=(float)Math.sqrt(dx*dx+dy*dy);if(dist>1.5f){float speed=Math.min(SPEED_BASE,Math.max(0.006f,dist/2000f));currentX+=dx*speed;currentY+=dy*speed;if(dx>0!=facingRight)facingRight=dx>0;animalParams.x=(int)currentX;animalParams.y=(int)currentY;try{wm.updateViewLayout(animalView,animalParams);}catch(Exception ignored){}actualizarPosBurbuja();float mx=currentX-lastFootX,my=currentY-lastFootY,moved=(float)Math.sqrt(mx*mx+my*my);long now=System.currentTimeMillis();if(moved>dp(20)&&(now-lastFootprintTime)>400){lastFootprintTime=now;lastFootX=currentX;lastFootY=currentY;float ox=nextFootLeft?-dp(4):dp(4);synchronized(footprints){footprints.add(new Footprint(currentX+dp(DUCK_SIZE_DP)/2f+ox,currentY+dp(DUCK_SIZE_DP)-dp(4),now,nextFootLeft));}nextFootLeft=!nextFootLeft;}}mainHandler.postDelayed(this,16);}};mainHandler.post(moveRunnable);}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SONIDOS
    // ─────────────────────────────────────────────────────────────────────────
    private void startRandomSounds(){quackRunnable=new Runnable(){@Override public void run(){if(!isListening&&!isTalking)playAnimalSound();quackHandler.postDelayed(this,15000+rng.nextInt(30000));}};quackHandler.postDelayed(quackRunnable,8000+rng.nextInt(10000));}
    private void playAnimalSound(){switch(animalTipo){case"cat":playSoundCat();break;case"dog":playSoundDog();break;default:playSoundDuck();}}
    private void playSoundDuck(){new Thread(()->{try{int sr=44100,ms=320,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=700-350*p,a=p<0.08?p/0.08:Math.max(0,1-(p-0.08)/0.92);double m=a*0.65*Math.sin(2*Math.PI*f*t)+a*0.25*Math.sin(4*Math.PI*f*t);s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();mainHandler.post(()->{showBubble(new String[]{"¡Cuak!","¡Quack!","¡Cuaaak!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playSoundCat(){new Thread(()->{try{int sr=44100,ms=500,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=600+200*Math.sin(p*Math.PI),a=p<0.1?p/0.1:p>0.8?(1-p)/0.2:1.0;double m=a*(0.7*Math.sin(2*Math.PI*f*t)+0.2*Math.sin(4*Math.PI*f*t));s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();mainHandler.post(()->{showBubble(new String[]{"¡Miau!","Purrr...","¡Miiiau!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playSoundDog(){new Thread(()->{try{int sr=44100,ms=300,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=350+100*Math.sin(p*Math.PI*3),a=p<0.05?p/0.05:p>0.7?(1-p)/0.3:1.0,noise=(rng.nextDouble()-0.5)*0.15;double m=a*(0.6*Math.sin(2*Math.PI*f*t)+0.25*Math.sin(4*Math.PI*f*t)+noise);s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();mainHandler.post(()->{showBubble(new String[]{"¡Guau!","¡Woof!","¡Arf!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playRaw(short[]s,int sr,int d)throws Exception{AudioTrack t=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(s.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();t.write(s,0,s.length);t.play();Thread.sleep(d+80);t.stop();t.release();}
    private void animarSalto(){if(animalView!=null)animalView.animate().translationY(-10f).setDuration(130).withEndAction(()->animalView.animate().translationY(0).setDuration(130).start()).start();}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  COMANDOS DE VOZ
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleVoiceCommand(String texto){
        String lower=texto.toLowerCase().trim();
        if(lower.contains("alarma")||lower.contains("despiértame")||lower.contains("despertador")){parsearYPonerAlarma(texto);return true;}
        if(lower.contains("calendario")||lower.contains("recuérdame")||lower.contains("recordame")||lower.contains("cumpleaños de")||lower.contains("agrega")){parsearYAgregarCalendario(texto);return true;}
        if((lower.contains("whatsapp")||lower.contains("wsp")||lower.contains("wasap"))&&(lower.contains("llama")||lower.contains("videollamada"))){llamarPorWhatsApp(extraerNombreContacto(lower,texto));return true;}
        if(lower.contains("whatsapp")||lower.contains("wsp")||lower.contains("wasap")){abrirWhatsAppChat(extraerNombreContacto(lower,texto),extraerMensaje(lower,texto));return true;}
        if(lower.contains("pon la canción")||lower.contains("reproduce")||lower.contains("quiero escuchar")||lower.contains("ponme")||lower.contains("pon música de")){pedirYReproducirCancion(texto);return true;}
        if(lower.contains("pon música")||lower.contains("abre spotify")||lower.contains("pon spotify")||lower.contains("abre tidal")){abrirAppMusica(detectarAppMusica(lower),null);return true;}
        for(String p:new String[]{"marcale a ","llama a ","llámale a ","llamar a ","marcar a ","marca a "}){if(lower.contains(p)&&!lower.contains("whatsapp")&&!lower.contains("wsp")){buscarYLlamar(texto.substring(lower.indexOf(p)+p.length()).trim().replace(" por favor","").trim());return true;}}
        if(lower.startsWith("abre ")||lower.startsWith("abrir ")||lower.contains("abre la ")){abrirAppDinamica(lower.replace("abre la app de","").replace("abre la ","").replace("abrir la ","").replace("abre ","").replace("abrir ","").trim());return true;}
        return false;
    }
 
    private String extraerNombreContacto(String lower,String texto){String[]ps={"whatsapp a ","wsp a ","wasap a ","manda whatsapp a ","mándale whatsapp a ","llama a ","llámale a ","videollamada a ","mensaje a "};for(String p:ps)if(lower.contains(p)){int idx=lower.indexOf(p)+p.length();String n=texto.substring(idx).trim();for(String c:new String[]{" por whatsapp"," un mensaje"," diciéndole"," diciendo"," que le"," con el mensaje"," que diga"}){int pos=n.toLowerCase().indexOf(c);if(pos>0)n=n.substring(0,pos);}return n.replace(" por favor","").trim();}return"";}
    private String extraerMensaje(String lower,String texto){for(String m:new String[]{"diciéndole ","que diga ","con el mensaje ","diciendo ","el mensaje "}){if(lower.contains(m)){int idx=lower.indexOf(m)+m.length();return texto.substring(idx).trim().replace(" por favor","").trim();}}return"";}
 
    private void abrirWhatsAppChat(String nombre,String mensaje){
        if(nombre.isEmpty()){showBubble("¿A quién le mando mensaje por WhatsApp?",3000,true);return;}
        showBubble("🔍 Buscando a "+nombre+"...",0,true);
        new Thread(()->{String tel=buscarTelefono(nombre);mainHandler.post(()->{
            if(tel!=null){String num=tel.replaceAll("[^0-9]","");if(!num.startsWith("52")&&num.length()==10)num="52"+num;
                String url="https://wa.me/"+num+(mensaje.isEmpty()?"":"?text="+Uri.encode(mensaje));
                showBubble("💬 Abriendo chat de "+nombre+" en WhatsApp",4000,true);
                boolean ok=false;
                try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse(url));wa.setPackage("com.whatsapp");wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);ok=true;}catch(Exception ignored){}
                if(!ok)try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse(url));wa.setPackage("com.whatsapp.w4b");wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);ok=true;}catch(Exception ignored){}
                if(!ok)try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse(url));wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);}catch(Exception ignored){}
            }else showBubble("No encontré a "+nombre+" en tus contactos",3500,true);});
        }).start();}
 
    private void llamarPorWhatsApp(String nombre){if(nombre.isEmpty()){showBubble("¿A quién llamo por WhatsApp?",3000,true);return;}showBubble("📱 Buscando a "+nombre+"...",0,true);new Thread(()->{String tel=buscarTelefono(nombre);mainHandler.post(()->{if(tel!=null){String num=tel.replaceAll("[^0-9]","");if(!num.startsWith("52")&&num.length()==10)num="52"+num;showBubble("📱 Llamando por WhatsApp a "+nombre+"...",4000,true);boolean ok=false;try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse("whatsapp://call?number=+"+num));wa.setPackage("com.whatsapp");wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);ok=true;}catch(Exception ignored){}if(!ok){try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+num));wa.setPackage("com.whatsapp");wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);}catch(Exception ignored){}showBubble("Abrí el chat de "+nombre+" → presiona el ícono de llamada 📞",5000,true);}}else showBubble("No encontré a "+nombre,3500,true);});}).start();}
 
    private void buscarYLlamar(String nombre){showBubble("📞 Buscando a "+nombre+"...",0,true);new Thread(()->{String tel=buscarTelefono(nombre);mainHandler.post(()->{if(tel!=null){showBubble("📞 Marcando a "+nombre+"...",3000,true);try{Intent l=new Intent(Intent.ACTION_CALL);l.setData(Uri.parse("tel:"+tel));l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(l);}catch(SecurityException e){showBubble("Necesito permiso de Teléfono en Ajustes",5000,true);};}else showBubble("No encontré a "+nombre,3500,true);});}).start();}
 
    private String buscarTelefono(String nombre){try{android.database.Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);if(c==null)return null;String buscar=nombre.toLowerCase().trim(),mejor=null;int mejorP=0;while(c.moveToNext()){String nc=c.getString(0),tel=c.getString(1);if(nc==null||tel==null)continue;String nl=nc.toLowerCase().trim();if(nl.equals(buscar)){c.close();return tel.replaceAll("[^+0-9]","");}int p=nl.contains(buscar)?2:buscar.contains(nl)?1:0;if(p>mejorP){mejorP=p;mejor=tel.replaceAll("[^+0-9]","");}}c.close();return mejor;}catch(Exception e){return null;}}
 
    private void pedirYReproducirCancion(String texto){String cancion=texto.toLowerCase().replace("pon la canción","").replace("reproduce","").replace("ponme","").replace("quiero escuchar","").replace("pon música de","").replace("en spotify","").replace("en tidal","").replace("en qobuz","").trim();String app=detectarAppMusica(texto.toLowerCase());String guardada=getSharedPreferences("quacky_prefs",MODE_PRIVATE).getString("app_musica","");if(!app.equals("spotify")||texto.toLowerCase().contains("spotify")){getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("app_musica",app).apply();abrirAppMusica(app,cancion);}else if(!guardada.isEmpty())abrirAppMusica(guardada,cancion);else showBubble("🎵 ¿En qué app? Spotify, Tidal, Qobuz...",5000,true);}
    private String detectarAppMusica(String l){if(l.contains("tidal"))return"tidal";if(l.contains("qobuz"))return"qobuz";if(l.contains("youtube music")||l.contains("yt music"))return"youtube_music";if(l.contains("deezer"))return"deezer";if(l.contains("amazon music"))return"amazon_music";if(l.contains("soundcloud"))return"soundcloud";return"spotify";}
    private void abrirAppMusica(String app,String cancion){String paquete,nombre;switch(app){case"tidal":paquete="com.aspiro.tidal";nombre="Tidal";break;case"qobuz":paquete="com.qobuz.music";nombre="Qobuz";break;case"youtube_music":paquete="com.google.android.apps.youtube.music";nombre="YouTube Music";break;case"deezer":paquete="deezer.android.app";nombre="Deezer";break;case"amazon_music":paquete="com.amazon.mp3";nombre="Amazon Music";break;default:paquete="com.spotify.music";nombre="Spotify";}showBubble("🎵 "+(cancion!=null&&!cancion.isEmpty()?"Buscando \""+cancion+"\" en "+nombre:"Abriendo "+nombre)+"...",3000,true);try{Intent i=null;if(cancion!=null&&!cancion.isEmpty()){if(app.equals("spotify"))i=new Intent(Intent.ACTION_VIEW,Uri.parse("spotify:search:"+Uri.encode(cancion)));else if(app.equals("youtube_music"))i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/search?q="+Uri.encode(cancion)));}if(i==null)i=getPackageManager().getLaunchIntentForPackage(paquete);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}else{Intent store=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+paquete));store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(store);showBubble(nombre+" no instalado, abriendo Play Store",3000,true);}}catch(Exception e){showBubble("No pude abrir "+nombre,3000,false);}}
 
    private void abrirAppDinamica(String nombre){showBubble("📱 Buscando "+nombre+"...",0,false);Map<String,String>conocidas=new HashMap<>();conocidas.put("spotify","com.spotify.music");conocidas.put("whatsapp","com.whatsapp");conocidas.put("instagram","com.instagram.android");conocidas.put("facebook","com.facebook.katana");conocidas.put("twitter","com.twitter.android");conocidas.put("x","com.twitter.android");conocidas.put("tiktok","com.zhiliaoapp.musically");conocidas.put("youtube","com.google.android.youtube");conocidas.put("maps","com.google.android.apps.maps");conocidas.put("gmail","com.google.android.gm");conocidas.put("chrome","com.android.chrome");conocidas.put("netflix","com.netflix.mediaclient");conocidas.put("uber","com.ubercab");conocidas.put("tidal","com.aspiro.tidal");conocidas.put("telegram","org.telegram.messenger");conocidas.put("snapchat","com.snapchat.android");conocidas.put("zoom","us.zoom.videomeetings");
        String paquete=conocidas.get(nombre.toLowerCase().trim());if(paquete!=null){try{Intent i=getPackageManager().getLaunchIntentForPackage(paquete);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);showBubble("📱 Abriendo "+nombre+"...",2000,true);startActivity(i);return;}}catch(Exception ignored){}}
        try{Intent main=new Intent(Intent.ACTION_MAIN);main.addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo>apps=getPackageManager().queryIntentActivities(main,0);String buscar=nombre.toLowerCase().trim();for(ResolveInfo app:apps){String label=app.loadLabel(getPackageManager()).toString().toLowerCase().trim();if(label.contains(buscar)||buscar.contains(label)){Intent i=new Intent(Intent.ACTION_MAIN);i.setComponent(new android.content.ComponentName(app.activityInfo.packageName,app.activityInfo.name));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);showBubble("📱 Abriendo "+app.loadLabel(getPackageManager())+"...",2000,true);startActivity(i);return;}}}catch(Exception ignored){}
        showBubble("No encontré la app \""+nombre+"\" instalada",3000,true);}
 
    private void parsearYPonerAlarma(String texto){showBubble("⏰ Configurando alarma...",0,true);askGroqJSON("Extrae la hora de: \""+texto+"\"\nResponde SOLO con JSON: {\"hora\":número,\"minutos\":número,\"etiqueta\":\"texto\"}\nSi dice 'de la tarde' suma 12. 'y media'=minutos:30.",json->{try{int hora=json.getInt("hora"),min=json.optInt("minutos",0);String eta=json.optString("etiqueta","Quacky");Intent a=new Intent(AlarmClock.ACTION_SET_ALARM);a.putExtra(AlarmClock.EXTRA_HOUR,hora);a.putExtra(AlarmClock.EXTRA_MINUTES,min);a.putExtra(AlarmClock.EXTRA_MESSAGE,eta);a.putExtra(AlarmClock.EXTRA_SKIP_UI,false);a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(a);showBubble("⏰ Alarma a las "+hora+":"+String.format("%02d",min),4000,true);}catch(Exception e){showBubble("No entendí la hora, ¿repites?",3000,true);}});}
    private void parsearYAgregarCalendario(String texto){showBubble("📅 Agregando al calendario...",0,true);askGroqJSON("Extrae el evento de: \""+texto+"\"\nResponde SOLO con JSON: {\"titulo\":\"texto\",\"dia\":número,\"mes\":número,\"hora\":número,\"minutos\":número}\nSi no hay hora pon hora:9. Mes en número.",json->{try{String tit=json.optString("titulo","Evento");int d=json.optInt("dia",1),m=json.optInt("mes",1),h=json.optInt("hora",9),min=json.optInt("minutos",0);Calendar c=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,d);c.set(Calendar.MONTH,m-1);c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,min);c.set(Calendar.SECOND,0);Intent ci=new Intent(Intent.ACTION_INSERT);ci.setData(CalendarContract.Events.CONTENT_URI);ci.putExtra(CalendarContract.Events.TITLE,tit);ci.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,c.getTimeInMillis());ci.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,c.getTimeInMillis()+3600000);ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(ci);showBubble("📅 Agregué: "+tit+" el "+d+"/"+m,4000,true);}catch(Exception e){showBubble("No pude agregar al calendario",3000,true);}});}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GROQ API
    // ─────────────────────────────────────────────────────────────────────────
    private void askGroq(String userMessage){
        isTalking=true;showBubble("💭 pensando...",0,false);
        if(animalView!=null)animalView.animate().rotation(10f).setDuration(100).withEndAction(()->animalView.animate().rotation(-10f).setDuration(100).withEndAction(()->animalView.animate().rotation(0f).setDuration(100).start()).start()).start();
        try{JSONObject um=new JSONObject();um.put("role","user");um.put("content",userMessage);chatHistory.add(um);}catch(JSONException ignored){}
        String n2=capitalize(nombreMascota.isEmpty()?"Quacky":nombreMascota);String emoji=animalTipo.equals("cat")?"🐱":animalTipo.equals("dog")?"🐶":"🐥";String sonido=animalTipo.equals("cat")?"¡Miau!":animalTipo.equals("dog")?"¡Guau!":"¡Quack!";
        new Thread(()->{try{JSONArray msgs=new JSONArray();JSONObject sys=new JSONObject();sys.put("role","system");sys.put("content","Eres "+n2+" "+emoji+", asistente IA avanzado. Puedes: llamar, WhatsApp, alarmas, calendario, abrir apps, música. Dices '"+sonido+"' de vez en cuando. Respuestas CORTAS en español mexicano.");msgs.put(sys);for(JSONObject m:chatHistory)msgs.put(m);JSONObject body=new JSONObject();body.put("model",GROQ_MODEL);body.put("max_tokens",300);body.put("messages",msgs);URL url=new URL(GROQ_URL);HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);conn.setDoOutput(true);conn.setConnectTimeout(15000);conn.setReadTimeout(30000);try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}String reply=new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();JSONObject am=new JSONObject();am.put("role","assistant");am.put("content",reply);chatHistory.add(am);while(chatHistory.size()>20)chatHistory.remove(0);int dur=Math.max(4000,reply.length()*60);mainHandler.post(()->{isTalking=false;showBubble(emoji+" "+reply,dur,true);// Reactivar wake loop después de responder
                mainHandler.postDelayed(()->{wakeLoopActivo=true;programarSiguienteWake();},dur+1500);});}catch(Exception e){mainHandler.post(()->{isTalking=false;showBubble("Error: "+e.getMessage(),5000,false);wakeLoopActivo=true;programarSiguienteWake();});}}).start();}
 
    interface GroqJSONCallback{void onResult(JSONObject j);}
    private void askGroqJSON(String prompt,GroqJSONCallback cb){new Thread(()->{try{JSONArray msgs=new JSONArray();JSONObject um=new JSONObject();um.put("role","user");um.put("content",prompt);msgs.put(um);JSONObject body=new JSONObject();body.put("model",GROQ_MODEL);body.put("max_tokens",200);body.put("messages",msgs);URL url=new URL(GROQ_URL);HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);conn.setDoOutput(true);conn.setConnectTimeout(15000);conn.setReadTimeout(20000);try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}String raw=new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim().replaceAll("```json","").replaceAll("```","").trim();JSONObject json=new JSONObject(raw);mainHandler.post(()->cb.onResult(json));}catch(Exception e){mainHandler.post(()->showBubble("No pude procesar, ¿repites?",3000,true));}}).start();}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  BUBBLE
    // ─────────────────────────────────────────────────────────────────────────
    private void showBubble(String text,int durationMs,boolean hablarEnVoz){mainHandler.post(()->{bubbleText.setText(text);actualizarPosBurbuja();bubbleCard.setVisibility(View.VISIBLE);bubbleCard.setAlpha(0f);bubbleCard.animate().alpha(1f).setDuration(200).start();try{wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}if(hablarEnVoz)hablar(text.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim());if(bubbleHider!=null)bubbleHandler.removeCallbacks(bubbleHider);if(durationMs>0){bubbleHider=()->bubbleCard.animate().alpha(0f).setDuration(300).withEndAction(()->bubbleCard.setVisibility(View.GONE)).start();bubbleHandler.postDelayed(bubbleHider,durationMs);}});}
 
    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Quacky IA",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);}}
    private Notification buildNotification(){PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);return new NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("🐾 Quacky activo").setContentText("Di \""+capitalize(nombreMascota.isEmpty()?"Quacky":nombreMascota)+"\" para activar 🎤").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true).build();}
 
    private String capitalize(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public IBinder onBind(Intent i){return null;}
 
    @Override public void onDestroy(){super.onDestroy();
        wakeLoopActivo=false;
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        if(animalView!=null)try{wm.removeView(animalView);}catch(Exception ignored){}
        if(bubbleCard!=null)try{wm.removeView(bubbleCard);}catch(Exception ignored){}
        if(footprintOverlay!=null)try{wm.removeView(footprintOverlay);}catch(Exception ignored){}
        if(voiceResultReceiver!=null)try{unregisterReceiver(voiceResultReceiver);}catch(Exception ignored){}
        if(wakeWordReceiver!=null)try{unregisterReceiver(wakeWordReceiver);}catch(Exception ignored){}
        if(idleReceiver!=null)try{unregisterReceiver(idleReceiver);}catch(Exception ignored){}
        if(tts!=null){tts.stop();tts.shutdown();}
        mainHandler.removeCallbacks(moveRunnable);walkHandler.removeCallbacks(walkAnim);quackHandler.removeCallbacks(quackRunnable);}
}
