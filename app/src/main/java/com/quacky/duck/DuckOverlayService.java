package com.quacky.duck;
 
import android.app.*;
import android.content.*;
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
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL   = "llama-3.3-70b-versatile";
 
    // Tamaño grande del animal
    private static final int   DUCK_SIZE_DP = 60;
    private static final float SPEED_BASE   = 0.011f;
    private static final float GYRO_FORCE   = 18f;
 
    private View                       animalView;
    private WindowManager.LayoutParams animalParams;
    private LinearLayout               bubbleCard;
    private TextView                   bubbleText;
    private WindowManager.LayoutParams bubbleParams;
    private View                       footprintOverlay;
    private WindowManager              wm;
    private int                        overlayType;
 
    private float   currentX, currentY, targetX, targetY;
    private boolean isTalking   = false;
    private boolean isListening = false;
    private boolean facingRight = true;
    private Handler mainHandler;
    private Runnable moveRunnable;
 
    private Handler  walkHandler = new Handler(Looper.getMainLooper());
    private float    walkPhase   = 0f;
    private Runnable walkAnim;
 
    private static class Footprint {
        float x, y; long born; boolean isLeft;
        Footprint(float x, float y, long born, boolean left) { this.x=x;this.y=y;this.born=born;this.isLeft=left; }
    }
    private final List<Footprint> footprints     = new ArrayList<>();
    private static final long     FOOTPRINT_LIFE = 1800;
    private long    lastFootprintTime = 0;
    private boolean nextFootLeft      = true;
    private float   lastFootX = -999, lastFootY = -999;
 
    private Handler  quackHandler = new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private final Random rng = new Random();
    private String animalTipo = "duck";
 
    private TextToSpeech tts;
    private boolean      ttsListo = false;
 
    private BroadcastReceiver voiceResultReceiver;
    private BroadcastReceiver powerButtonReceiver;
    private long lastScreenOff = 0; // Para detectar doble pulsación de power
 
    private final List<JSONObject> chatHistory = new ArrayList<>();
 
    private Handler  bubbleHandler = new Handler(Looper.getMainLooper());
    private Runnable bubbleHider;
 
    private SensorManager  sensorManager;
    private Sensor         gyroSensor;
    private volatile float gyroVelX = 0f;
    private volatile float gyroVelY = 0f;
 
    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
 
        SharedPreferences prefs = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        animalTipo = prefs.getString("animal", "duck");
 
        DisplayMetrics dm = getResources().getDisplayMetrics();
        currentX = dm.widthPixels / 2f - dp(DUCK_SIZE_DP) / 2f;
        currentY = dm.heightPixels * 0.72f;
        targetX = currentX; targetY = currentY;
 
        setupFootprintOverlay();
        setupBubbleWindow();
        setupAnimalWindow();
        setupTTS();
        setupSpeechRecognizer();
        setupPowerButtonReceiver();
        setupGyroscope();
        startMoveLoop();
        startRandomSounds();
 
        mainHandler.postDelayed(() -> showBubble(saludoInicial(), 5000, false), 1200);
    }
 
    private String saludoInicial() {
        switch (animalTipo) {
            case "cat": return "¡Miau! 🐱 Tócame o 2x botón power";
            case "dog": return "¡Guau! 🐶 Tócame o 2x botón power";
            default:    return "¡Quack! 🐥 Tócame o 2x botón power";
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  DOBLE BOTÓN DE POWER — detección correcta:
    //  SCREEN_OFF → SCREEN_ON en menos de 700ms = doble pulsación
    // ─────────────────────────────────────────────────────────────────────────
    private void setupPowerButtonReceiver() {
        powerButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                long ahora = System.currentTimeMillis();
 
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    // Primer press: pantalla apagada → guardar timestamp
                    lastScreenOff = ahora;
 
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    // Segundo press: pantalla encendida
                    // Si pasaron menos de 700ms desde que se apagó → doble press
                    if (lastScreenOff > 0 && (ahora - lastScreenOff) < 700) {
                        lastScreenOff = 0;
                        mainHandler.postDelayed(() -> {
                            // Pequeña pausa para que el sistema se estabilice
                            showBubble("👂 ¡Te escucho!", 0, false);
                            startListening();
                        }, 300);
                    } else {
                        lastScreenOff = 0;
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(powerButtonReceiver, filter);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  VENTANA DEL ANIMAL
    // ─────────────────────────────────────────────────────────────────────────
    private void setupAnimalWindow() {
        int px = dp(DUCK_SIZE_DP);
        switch (animalTipo) {
            case "cat": case "dog": {
                PetView pv = new PetView(this);
                int resId = getResources().getIdentifier(animalTipo, "drawable", getPackageName());
                if (resId != 0) {
                    try { Bitmap b = BitmapFactory.decodeResource(getResources(), resId); if (b != null) pv.setAnimalBitmap(b); } catch (Exception ignored) {}
                }
                animalView = pv;
                break;
            }
            default: animalView = new DuckView(this); break;
        }
        animalParams = new WindowManager.LayoutParams(px, px, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        animalParams.gravity = Gravity.TOP | Gravity.START;
        animalParams.x = (int)currentX; animalParams.y = (int)currentY;
        wm.addView(animalView, animalParams);
        animalView.setOnTouchListener(new View.OnTouchListener() {
            long ts;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction()==MotionEvent.ACTION_DOWN) ts=System.currentTimeMillis();
                if (e.getAction()==MotionEvent.ACTION_UP && System.currentTimeMillis()-ts<400) startListening();
                return true;
            }
        });
        setupWalkAnimation();
    }
 
    private void llamarSetWalkState(boolean m, float p, float d) {
        if (animalView instanceof DuckView)   ((DuckView)  animalView).setWalkState(m,p,d);
        else if (animalView instanceof PetView)((PetView)  animalView).setWalkState(m,p,d);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  BURBUJA
    // ─────────────────────────────────────────────────────────────────────────
    private void setupBubbleWindow() {
        bubbleCard = new LinearLayout(this);
        bubbleCard.setOrientation(LinearLayout.VERTICAL);
        bubbleCard.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14)); bg.setColor(0xEE1A1A3A); bg.setStroke(dp(1),0xFFFFD700);
        bubbleCard.setBackground(bg);
        bubbleText = new TextView(this);
        bubbleText.setTextColor(Color.WHITE); bubbleText.setTextSize(13); bubbleText.setMaxWidth(dp(240));
        bubbleCard.addView(bubbleText);
        bubbleCard.setVisibility(View.GONE);
        bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = (int)currentX; bubbleParams.y = Math.max(0,(int)currentY-dp(80));
        wm.addView(bubbleCard, bubbleParams);
    }
 
    private void actualizarPosBurbuja() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int bx = Math.max(dp(8), Math.min((int)currentX-dp(10), dm.widthPixels-dp(250)));
        int by = Math.max(dp(8),(int)currentY-dp(85));
        bubbleParams.x=bx; bubbleParams.y=by;
        try{if(bubbleCard.getVisibility()==View.VISIBLE) wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  TTS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status==TextToSpeech.SUCCESS) {
                configurarVoz(getSharedPreferences("quacky_prefs",MODE_PRIVATE).getString("voz","femenina"));
                ttsListo=true;
            }
        });
    }
    private void configurarVoz(String tipo) {
        if(tts==null) return;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
            Set<Voice> vs=tts.getVoices();
            if(vs!=null) for(Voice v:vs){
                if(v.getLocale()==null||!v.getLocale().getLanguage().equals("es")) continue;
                String n=v.getName().toLowerCase();
                if(tipo.equals("masculina")&&(n.contains("-m-")||n.contains("male")||n.contains("smb"))){tts.setVoice(v);return;}
                if(tipo.equals("femenina") &&(n.contains("-f-")||n.contains("female")||n.contains("sfb")||n.contains("esf"))){tts.setVoice(v);return;}
            }
        }
        tts.setLanguage(new Locale("es","MX"));
        tts.setPitch(tipo.equals("masculina")?0.78f:1.25f);
        tts.setSpeechRate(tipo.equals("masculina")?0.93f:1.05f);
    }
    private void hablar(String texto) {
        if(!ttsListo||tts==null) return;
        String l=texto.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim();
        if(!l.isEmpty()) tts.speak(l,TextToSpeech.QUEUE_FLUSH,null,"q_"+System.currentTimeMillis());
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GIROSCOPIO
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGyroscope() {
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager==null) return;
        gyroSensor=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(gyroSensor!=null) sensorManager.registerListener(this,gyroSensor,SensorManager.SENSOR_DELAY_GAME);
        else{Sensor a=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(a!=null)sensorManager.registerListener(this,a,SensorManager.SENSOR_DELAY_GAME);}
    }
    @Override public void onSensorChanged(SensorEvent e) {
        DisplayMetrics dm=getResources().getDisplayMetrics(); int px=dp(DUCK_SIZE_DP);
        if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE){
            float rx=e.values[0],ry=e.values[1];
            if(Math.abs(rx)<0.05f)rx=0f;if(Math.abs(ry)<0.05f)ry=0f;
            gyroVelX+=ry*GYRO_FORCE;gyroVelY+=rx*GYRO_FORCE;
            targetX=Math.max(0,Math.min(targetX+gyroVelX,dm.widthPixels-px));
            targetY=Math.max(0,Math.min(targetY+gyroVelY,dm.heightPixels-px));
            gyroVelX*=0.85f;gyroVelY*=0.85f;
        } else if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            float ax=-e.values[0],ay=e.values[1];
            if(Math.abs(ax)<0.3f)ax=0f;if(Math.abs(ay)<0.3f)ay=0f;
            targetX=Math.max(0,Math.min(targetX+ax*1.2f,dm.widthPixels-px));
            targetY=Math.max(0,Math.min(targetY-ay*1.2f,dm.heightPixels-px));
        }
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  HUELLAS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFootprintOverlay() {
        DisplayMetrics dm=getResources().getDisplayMetrics();
        footprintOverlay=new View(this){
            private final Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path pt=new Path();
            @Override protected void onDraw(Canvas canvas){
                long now=System.currentTimeMillis();fp.setStyle(Paint.Style.FILL);
                synchronized(footprints){Iterator<Footprint> it=footprints.iterator();
                    while(it.hasNext()){Footprint f=it.next();float age=(now-f.born)/(float)FOOTPRINT_LIFE;
                        if(age>=1f){it.remove();continue;}fp.setColor(Color.argb((int)(140*(1f-age)),180,80,0));
                        float s=dm.density*3.5f;canvas.save();canvas.translate(f.x,f.y);canvas.rotate(f.isLeft?-15f:15f);
                        pt.reset();pt.moveTo(0,0);pt.lineTo(-s,s*0.6f);pt.lineTo(-s*0.4f,s*1.1f);pt.lineTo(s*0.2f,s*0.7f);pt.lineTo(s*0.8f,s*1.1f);pt.lineTo(s*1.2f,s*0.5f);pt.close();
                        canvas.drawPath(pt,fp);canvas.restore();}}
                postInvalidateDelayed(60);}};
        footprintOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dm.widthPixels,dm.heightPixels,overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;wm.addView(footprintOverlay,p);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WALK ANIMATION
    // ─────────────────────────────────────────────────────────────────────────
    private void setupWalkAnimation(){
        walkAnim=new Runnable(){@Override public void run(){
            boolean m=isMoving();if(m){walkPhase+=0.04f;if(walkPhase>1f)walkPhase=0f;}
            llamarSetWalkState(m,walkPhase,facingRight?1f:-1f);walkHandler.postDelayed(this,30);}};
        walkHandler.post(walkAnim);}
    private boolean isMoving(){return Math.abs(targetX-currentX)>2||Math.abs(targetY-currentY)>2;}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  LOOP DE MOVIMIENTO
    // ─────────────────────────────────────────────────────────────────────────
    private void startMoveLoop(){
        moveRunnable=new Runnable(){@Override public void run(){
            float dx=targetX-currentX,dy=targetY-currentY,dist=(float)Math.sqrt(dx*dx+dy*dy);
            if(dist>1.5f){
                float speed=Math.min(SPEED_BASE,Math.max(0.006f,dist/2000f));
                currentX+=dx*speed;currentY+=dy*speed;
                if(dx>0!=facingRight)facingRight=dx>0;
                animalParams.x=(int)currentX;animalParams.y=(int)currentY;
                try{wm.updateViewLayout(animalView,animalParams);}catch(Exception ignored){}
                actualizarPosBurbuja();
                float mx=currentX-lastFootX,my=currentY-lastFootY,moved=(float)Math.sqrt(mx*mx+my*my);
                long now=System.currentTimeMillis();
                if(moved>dp(20)&&(now-lastFootprintTime)>400){
                    lastFootprintTime=now;lastFootX=currentX;lastFootY=currentY;
                    float ox=nextFootLeft?-dp(4):dp(4);
                    synchronized(footprints){footprints.add(new Footprint(currentX+dp(DUCK_SIZE_DP)/2f+ox,currentY+dp(DUCK_SIZE_DP)-dp(4),now,nextFootLeft));}
                    nextFootLeft=!nextFootLeft;}}
            mainHandler.postDelayed(this,16);}};
        mainHandler.post(moveRunnable);}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SONIDOS
    // ─────────────────────────────────────────────────────────────────────────
    private void startRandomSounds(){
        quackRunnable=new Runnable(){@Override public void run(){
            if(!isListening&&!isTalking)playAnimalSound();
            quackHandler.postDelayed(this,15000+rng.nextInt(30000));}};
        quackHandler.postDelayed(quackRunnable,8000+rng.nextInt(10000));}
 
    private void playAnimalSound(){switch(animalTipo){case"cat":playSoundCat();break;case"dog":playSoundDog();break;default:playSoundDuck();}}
    private void playSoundDuck(){
        new Thread(()->{try{int sr=44100,ms=320,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=700-350*p,a=p<0.08?p/0.08:Math.max(0,1-(p-0.08)/0.92);double m=a*0.65*Math.sin(2*Math.PI*f*t)+a*0.25*Math.sin(4*Math.PI*f*t);s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();
        mainHandler.post(()->{showBubble(new String[]{"¡Cuak!","¡Quack!","¡Cuaaak!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playSoundCat(){
        new Thread(()->{try{int sr=44100,ms=500,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=600+200*Math.sin(p*Math.PI),a=p<0.1?p/0.1:p>0.8?(1-p)/0.2:1.0;double m=a*(0.7*Math.sin(2*Math.PI*f*t)+0.2*Math.sin(4*Math.PI*f*t));s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();
        mainHandler.post(()->{showBubble(new String[]{"¡Miau!","Purrr...","¡Miiiau!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playSoundDog(){
        new Thread(()->{try{int sr=44100,ms=300,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=350+100*Math.sin(p*Math.PI*3),a=p<0.05?p/0.05:p>0.7?(1-p)/0.3:1.0,noise=(rng.nextDouble()-0.5)*0.15;double m=a*(0.6*Math.sin(2*Math.PI*f*t)+0.25*Math.sin(4*Math.PI*f*t)+noise);s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();
        mainHandler.post(()->{showBubble(new String[]{"¡Guau!","¡Woof!","¡Arf arf!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playRaw(short[]s,int sr,int d)throws Exception{
        AudioTrack t=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(s.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();
        t.write(s,0,s.length);t.play();Thread.sleep(d+80);t.stop();t.release();}
    private void animarSalto(){if(animalView!=null)animalView.animate().translationY(-10f).setDuration(130).withEndAction(()->animalView.animate().translationY(0).setDuration(130).start()).start();}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SPEECH RECEIVER
    // ─────────────────────────────────────────────────────────────────────────
    private void setupSpeechRecognizer(){
        voiceResultReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            String texto=i.getStringExtra("voice_text");
            if(texto!=null&&!texto.isEmpty()){mainHandler.post(()->{isListening=false;if(!handleVoiceCommand(texto)){showBubble("Tú: \""+texto+"\"",2500,false);askGroq(texto);}});}
            else mainHandler.post(()->{isListening=false;showBubble("No te escuché 🐾 ¡Inténtalo!",3000,false);});}};
        IntentFilter f=new IntentFilter("com.quacky.duck.VOICE_RESULT");
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) registerReceiver(voiceResultReceiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(voiceResultReceiver,f);}
 
    private void startListening(){
        if(isTalking||isListening)return;
        if(tts!=null&&ttsListo)tts.stop();
        isListening=true;showBubble("...te escucho 👂",0,false);
        if(animalView!=null)animalView.animate().translationY(-14f).setDuration(180).withEndAction(()->animalView.animate().translationY(0).setDuration(180).start()).start();
        Intent intent=new Intent(DuckOverlayService.this,VoiceActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  COMANDOS DE VOZ — control total del dispositivo
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleVoiceCommand(String texto) {
        String lower = texto.toLowerCase().trim();
 
        // ── 1. ALARMA ────────────────────────────────────────────────────────
        if (lower.contains("alarma") || lower.contains("despiértame") || lower.contains("despertador")) {
            parsearYPonerAlarma(texto);
            return true;
        }
 
        // ── 2. CALENDARIO / RECORDATORIO ─────────────────────────────────────
        if (lower.contains("calendario") || lower.contains("recuérdame") || lower.contains("recordame")
            || lower.contains("agrega al") || lower.contains("es cumpleaños") || lower.contains("cumpleaños de")
            || lower.contains("reunión") || lower.contains("cita el")) {
            parsearYAgregarCalendario(texto);
            return true;
        }
 
        // ── 3. MÚSICA CON CANCIÓN ─────────────────────────────────────────────
        if (lower.contains("pon la canción") || lower.contains("reproduce") || lower.contains("pon la música de")
            || lower.contains("quiero escuchar") || lower.contains("ponme") || lower.contains("pon música de")
            || lower.contains("pon a ") && (lower.contains("canción")||lower.contains("música")||lower.contains("banda"))) {
            pedirYReproducirCancion(texto);
            return true;
        }
 
        // ── 4. ABRIR APP DE MÚSICA SIN CANCIÓN ───────────────────────────────
        if (lower.contains("pon música") || lower.contains("abre spotify") || lower.contains("abre tidal")
            || lower.contains("abre qobuz") || lower.contains("pon spotify") || lower.contains("pon tidal")) {
            String app = detectarAppMusica(lower);
            guardarAppMusicaPreferida(app);
            abrirAppMusica(app, null);
            return true;
        }
 
        // ── 5. WHATSAPP LLAMADA ───────────────────────────────────────────────
        if ((lower.contains("whatsapp")||lower.contains("wsp")||lower.contains("wasap"))
            && (lower.contains("llama")||lower.contains("llamar")||lower.contains("llámale")||lower.contains("llámale")||lower.contains("videollamada"))) {
            String nombre = extraerNombreWsp(lower, texto);
            llamarPorWhatsApp(nombre);
            return true;
        }
 
        // ── 6. WHATSAPP MENSAJE ───────────────────────────────────────────────
        if (lower.contains("whatsapp")||lower.contains("wsp")||lower.contains("wasap")) {
            // Detectar si trae texto del mensaje
            String nombre = extraerNombreWsp(lower, texto);
            String mensaje = extraerMensajeWsp(lower, texto);
            abrirWhatsAppChat(nombre, mensaje);
            return true;
        }
 
        // ── 7. LLAMADA NORMAL ─────────────────────────────────────────────────
        for (String p : new String[]{"marcale a ","llama a ","llámale a ","llamar a ","marcar a ","marca a "}) {
            if (lower.contains(p)) {
                String nombre = texto.substring(lower.indexOf(p)+p.length()).trim().replace(" por favor","").trim();
                buscarYLlamar(nombre);
                return true;
            }
        }
 
        // ── 8. ABRIR APPS ─────────────────────────────────────────────────────
        if (lower.startsWith("abre ")||lower.startsWith("abrir ")||lower.startsWith("abre la ")) {
            abrirAppPorNombre(lower.replace("abre la app de","").replace("abre la ","").replace("abrir la ","").replace("abre ","").replace("abrir ","").trim());
            return true;
        }
 
        return false;
    }
 
    // ── Alarma ────────────────────────────────────────────────────────────────
    private void parsearYPonerAlarma(String texto) {
        showBubble("⏰ Configurando alarma...", 0, true);
        askGroqJSON(
            "Extrae la hora de esta solicitud de alarma: \"" + texto + "\"\n" +
            "Responde SOLO con JSON: {\"hora\":número, \"minutos\":número, \"etiqueta\":\"texto\"}\n" +
            "Si dice '3 y media' = hora:3, minutos:30. 'de la tarde' suma 12. Sin texto extra.",
            jsonResp -> {
                try {
                    int hora = jsonResp.getInt("hora");
                    int min  = jsonResp.optInt("minutos", 0);
                    String etiqueta = jsonResp.optString("etiqueta", "Quacky");
                    Intent alarm = new Intent(AlarmClock.ACTION_SET_ALARM);
                    alarm.putExtra(AlarmClock.EXTRA_HOUR, hora);
                    alarm.putExtra(AlarmClock.EXTRA_MINUTES, min);
                    alarm.putExtra(AlarmClock.EXTRA_MESSAGE, etiqueta);
                    alarm.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    alarm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(alarm);
                    showBubble("⏰ Alarma puesta a las " + hora + ":" + String.format("%02d", min), 4000, true);
                } catch (Exception e) {
                    showBubble("No entendí la hora, ¿puedes repetirla?", 3000, true);
                }
            }
        );
    }
 
    // ── Calendario ────────────────────────────────────────────────────────────
    private void parsearYAgregarCalendario(String texto) {
        showBubble("📅 Agregando al calendario...", 0, true);
        askGroqJSON(
            "Extrae el evento de esta solicitud: \"" + texto + "\"\n" +
            "Responde SOLO con JSON: {\"titulo\":\"texto\", \"dia\":número, \"mes\":número, \"hora\":número, \"minutos\":número}\n" +
            "Si no hay hora pon hora:9, minutos:0. Mes en número (enero=1). Sin texto extra.",
            jsonResp -> {
                try {
                    String titulo = jsonResp.optString("titulo", "Evento Quacky");
                    int dia  = jsonResp.optInt("dia", 1);
                    int mes  = jsonResp.optInt("mes", 1);
                    int hora = jsonResp.optInt("hora", 9);
                    int min  = jsonResp.optInt("minutos", 0);
 
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.DAY_OF_MONTH, dia);
                    cal.set(Calendar.MONTH, mes - 1);
                    cal.set(Calendar.HOUR_OF_DAY, hora);
                    cal.set(Calendar.MINUTE, min);
                    cal.set(Calendar.SECOND, 0);
 
                    Intent calIntent = new Intent(Intent.ACTION_INSERT);
                    calIntent.setData(CalendarContract.Events.CONTENT_URI);
                    calIntent.putExtra(CalendarContract.Events.TITLE, titulo);
                    calIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.getTimeInMillis());
                    calIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.getTimeInMillis() + 3600000);
                    calIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(calIntent);
                    showBubble("📅 Agregué: " + titulo + " el " + dia + "/" + mes, 4000, true);
                } catch (Exception e) {
                    showBubble("No pude agregar al calendario, ¿puedes dar más detalles?", 3000, true);
                }
            }
        );
    }
 
    // ── Música con canción ────────────────────────────────────────────────────
    private void pedirYReproducirCancion(String texto) {
        // Extraer nombre de canción
        String cancion = texto.toLowerCase()
            .replace("pon la canción","").replace("reproduce","").replace("ponme","")
            .replace("quiero escuchar","").replace("pon música de","").replace("pon a ","")
            .replace("en spotify","").replace("en tidal","").replace("en qobuz","")
            .replace("en youtube music","").replace("en deezer","").trim();
 
        // Ver si mencionó app específica
        String app = detectarAppMusica(texto.toLowerCase());
        String appGuardada = getSharedPreferences("quacky_prefs",MODE_PRIVATE).getString("app_musica","");
 
        if (!app.equals("spotify") || texto.toLowerCase().contains("spotify")) {
            // Mencionó app específica → usarla y guardarla
            guardarAppMusicaPreferida(app);
            abrirAppMusica(app, cancion);
        } else if (!appGuardada.isEmpty()) {
            // Usar la guardada
            abrirAppMusica(appGuardada, cancion);
        } else {
            // Preguntar cuál app prefiere
            showBubble("🎵 ¿En qué app quieres escuchar? Dime: Spotify, Tidal, Qobuz...", 5000, true);
            // La próxima respuesta de voz indicará la app
        }
    }
 
    private String detectarAppMusica(String lower) {
        if(lower.contains("tidal"))         return "tidal";
        if(lower.contains("qobuz"))         return "qobuz";
        if(lower.contains("youtube music")||lower.contains("yt music")) return "youtube_music";
        if(lower.contains("deezer"))        return "deezer";
        if(lower.contains("amazon music"))  return "amazon_music";
        if(lower.contains("soundcloud"))    return "soundcloud";
        if(lower.contains("apple music"))   return "apple_music";
        return "spotify";
    }
 
    private void guardarAppMusicaPreferida(String app) {
        getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("app_musica",app).apply();
    }
 
    private void abrirAppMusica(String app, String cancion) {
        String paquete, nombre;
        switch(app) {
            case "tidal":        paquete="com.aspiro.tidal";                      nombre="Tidal";         break;
            case "qobuz":        paquete="com.qobuz.music";                       nombre="Qobuz";         break;
            case "youtube_music":paquete="com.google.android.apps.youtube.music"; nombre="YouTube Music"; break;
            case "deezer":       paquete="deezer.android.app";                    nombre="Deezer";        break;
            case "amazon_music": paquete="com.amazon.mp3";                        nombre="Amazon Music";  break;
            case "soundcloud":   paquete="com.soundcloud.android";                nombre="SoundCloud";    break;
            default:             paquete="com.spotify.music";                     nombre="Spotify";       break;
        }
 
        showBubble("🎵 " + (cancion!=null&&!cancion.isEmpty() ? "Buscando \""+cancion+"\" en "+nombre : "Abriendo "+nombre) + "...", 3000, true);
 
        try {
            Intent i;
            if (cancion != null && !cancion.isEmpty()) {
                // Buscar canción específica
                switch(app) {
                    case "spotify":
                        i = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(cancion)));
                        i.setPackage("com.spotify.music");
                        break;
                    case "youtube_music":
                        i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(cancion)));
                        break;
                    case "tidal":
                        i = new Intent(Intent.ACTION_VIEW, Uri.parse("tidal://search?query=" + Uri.encode(cancion)));
                        i.setPackage("com.aspiro.tidal");
                        break;
                    default:
                        // Lanzar app y que el usuario busque
                        i = getPackageManager().getLaunchIntentForPackage(paquete);
                        break;
                }
            } else {
                i = getPackageManager().getLaunchIntentForPackage(paquete);
            }
 
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } else {
                // App no instalada → Play Store
                Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + paquete));
                store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(store);
                showBubble(nombre + " no está instalado, abriendo Play Store", 3000, true);
            }
        } catch (Exception e) {
            showBubble("No pude abrir " + nombre, 3000, false);
        }
    }
 
    // ── WhatsApp ──────────────────────────────────────────────────────────────
    private String extraerNombreWsp(String lower, String texto) {
        String[] patrones = {"whatsapp a ","wsp a ","wasap a ","manda whatsapp a ","mándale whatsapp a ",
                             "manda un whatsapp a ","mensaje por whatsapp a ","llama a ","llámale a ",
                             "llama por whatsapp a ","videollamada a "};
        for (String p : patrones) {
            if (lower.contains(p)) {
                int idx = lower.indexOf(p) + p.length();
                String nombre = texto.substring(idx).trim();
                // Cortar en "por whatsapp", "un mensaje", etc.
                for (String corte : new String[]{" por whatsapp"," un mensaje"," diciéndole"," que "," con el mensaje"}) {
                    int pos = nombre.toLowerCase().indexOf(corte);
                    if (pos > 0) nombre = nombre.substring(0, pos);
                }
                return nombre.replace(" por favor","").trim();
            }
        }
        return "";
    }
 
    private String extraerMensajeWsp(String lower, String texto) {
        String[] marcadores = {"diciéndole ","que diga ","con el mensaje ","diciendo ","el mensaje ","mensaje: "};
        for (String m : marcadores) {
            if (lower.contains(m)) {
                int idx = lower.indexOf(m) + m.length();
                return texto.substring(idx).trim().replace(" por favor","").trim();
            }
        }
        return "";
    }
 
    private void abrirWhatsAppChat(String nombre, String mensaje) {
        if (nombre.isEmpty()) {
            showBubble("¿A quién le quieres escribir por WhatsApp?", 3000, true);
            return;
        }
        showBubble("🔍 Buscando a " + nombre + " en WhatsApp...", 0, true);
        new Thread(() -> {
            String tel = buscarTelefono(nombre);
            mainHandler.post(() -> {
                if (tel != null) {
                    String num = tel.replaceAll("[^0-9]","");
                    if (!num.startsWith("52") && num.length()==10) num = "52" + num;
                    String url = "https://api.whatsapp.com/send?phone=" + num;
                    if (!mensaje.isEmpty()) url += "&text=" + Uri.encode(mensaje);
                    showBubble("💬 Abriendo WhatsApp con " + nombre + (mensaje.isEmpty() ? "" : " (ya copié el mensaje)"), 4000, true);
                    try {
                        Intent wa = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        wa.setPackage("com.whatsapp");
                        wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(wa);
                    } catch (Exception e) {
                        try {
                            Intent wa = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(wa);
                        } catch (Exception e2) {
                            showBubble("WhatsApp no está instalado", 3000, true);
                        }
                    }
                } else {
                    showBubble("No encontré a " + nombre + " en tus contactos", 3500, true);
                }
            });
        }).start();
    }
 
    private void llamarPorWhatsApp(String nombre) {
        if (nombre.isEmpty()) { showBubble("¿A quién le quieres llamar por WhatsApp?",3000,true); return; }
        showBubble("🔍 Buscando a "+nombre+" para llamar por WhatsApp...",0,true);
        new Thread(()->{
            String tel=buscarTelefono(nombre);
            mainHandler.post(()->{
                if(tel!=null){
                    String num=tel.replaceAll("[^0-9]","");
                    if(!num.startsWith("52")&&num.length()==10) num="52"+num;
                    showBubble("📱 Iniciando llamada de WhatsApp con "+nombre+"...",4000,true);
                    // Intentar deep link de llamada WhatsApp
                    boolean abierto=false;
                    try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse("whatsapp://call?number="+num));wa.setPackage("com.whatsapp");wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);abierto=true;}catch(Exception ignored){}
                    if(!abierto){
                        // Fallback: abrir chat (el usuario puede llamar desde ahí)
                        try{Intent wa=new Intent(Intent.ACTION_VIEW,Uri.parse("https://api.whatsapp.com/send?phone="+num));wa.setPackage("com.whatsapp");wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(wa);}catch(Exception ignored){}
                        showBubble("Abrí el chat de "+nombre+". Presiona el teléfono para llamar 📞",4000,true);
                    }
                } else showBubble("No encontré a "+nombre+" en tus contactos",3500,true);
            });
        }).start();
    }
 
    // ── Llamada normal ────────────────────────────────────────────────────────
    private void buscarYLlamar(String nombre){
        showBubble("📞 Llamando a "+nombre+"...",0,true);
        new Thread(()->{String tel=buscarTelefono(nombre);mainHandler.post(()->{
            if(tel!=null){showBubble("📞 Marcando a "+nombre+"...",3000,true);
                try{Intent l=new Intent(Intent.ACTION_CALL);l.setData(Uri.parse("tel:"+tel));l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(l);}
                catch(SecurityException e){showBubble("Necesito permiso de Teléfono en Ajustes",5000,true);}
            }else showBubble("No encontré a "+nombre+" en tus contactos",3500,true);});}).start();}
 
    // ── Abrir apps generales ──────────────────────────────────────────────────
    private void abrirAppPorNombre(String nombre){
        Map<String,String> apps=new HashMap<>();
        apps.put("spotify","com.spotify.music");apps.put("whatsapp","com.whatsapp");
        apps.put("instagram","com.instagram.android");apps.put("facebook","com.facebook.katana");
        apps.put("twitter","com.twitter.android");apps.put("x","com.twitter.android");
        apps.put("tiktok","com.zhiliaoapp.musically");apps.put("youtube","com.google.android.youtube");
        apps.put("maps","com.google.android.apps.maps");apps.put("google maps","com.google.android.apps.maps");
        apps.put("gmail","com.google.android.gm");apps.put("chrome","com.android.chrome");
        apps.put("netflix","com.netflix.mediaclient");apps.put("uber","com.ubercab");
        apps.put("rappi","com.grability.rappi");apps.put("tidal","com.aspiro.tidal");
        apps.put("deezer","deezer.android.app");apps.put("amazon music","com.amazon.mp3");
        apps.put("camera","android.media.action.STILL_IMAGE_CAMERA");
        apps.put("camara","android.media.action.STILL_IMAGE_CAMERA");
        String paquete=apps.get(nombre.toLowerCase().trim());
        if(paquete!=null){
            showBubble("📱 Abriendo "+nombre+"...",2000,true);
            try{Intent i=getPackageManager().getLaunchIntentForPackage(paquete);
                if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}
                else showBubble(nombre+" no está instalada",3000,true);}
            catch(Exception ignored){}
        }else{askGroq("El usuario quiere abrir: "+nombre+". Si puedes ayudarlo dile cómo, si no puedes díselo.");}
    }
 
    // ── Buscar teléfono ───────────────────────────────────────────────────────
    private String buscarTelefono(String nombre){
        try{android.database.Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);
            if(c==null)return null;String buscar=nombre.toLowerCase().trim(),mejor=null;int mejorP=0;
            while(c.moveToNext()){String nc=c.getString(0),tel=c.getString(1);if(nc==null||tel==null)continue;
                String nl=nc.toLowerCase().trim();if(nl.equals(buscar)){c.close();return tel.replaceAll("[^+0-9]","");}
                int p=nl.contains(buscar)?2:buscar.contains(nl)?1:0;if(p>mejorP){mejorP=p;mejor=tel.replaceAll("[^+0-9]","");}}
            c.close();return mejor;}catch(Exception e){return null;}}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GROQ API — respuesta conversacional
    // ─────────────────────────────────────────────────────────────────────────
    private void askGroq(String userMessage){
        isTalking=true;showBubble("💭 pensando...",0,false);
        if(animalView!=null)animalView.animate().rotation(10f).setDuration(100).withEndAction(()->animalView.animate().rotation(-10f).setDuration(100).withEndAction(()->animalView.animate().rotation(0f).setDuration(100).start()).start()).start();
        try{JSONObject um=new JSONObject();um.put("role","user");um.put("content",userMessage);chatHistory.add(um);}catch(JSONException ignored){}
        new Thread(()->{
            try{
                JSONArray msgs=new JSONArray();JSONObject sys=new JSONObject();sys.put("role","system");
                String n=animalTipo.equals("cat")?"Michi, un gatito gris IA":animalTipo.equals("dog")?"Guau, un perrito golden IA":"Quacky, un patito amarillo IA";
                String s2=animalTipo.equals("cat")?"¡Miau!":animalTipo.equals("dog")?"¡Guau!":"¡Quack!";
                sys.put("content","Eres "+n+" asistente IA avanzado en la pantalla del teléfono. Puedes: llamar contactos, WhatsApp, poner alarmas, calendario, abrir Spotify/Tidal/Qobuz/música, abrir apps. Dices '"+s2+"' de vez en cuando. Respuestas CORTAS (2-3 oraciones) en español mexicano.");
                msgs.put(sys);for(JSONObject m:chatHistory)msgs.put(m);
                JSONObject body=new JSONObject();body.put("model",GROQ_MODEL);body.put("max_tokens",300);body.put("messages",msgs);
                URL url=new URL(GROQ_URL);HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);
                conn.setDoOutput(true);conn.setConnectTimeout(15000);conn.setReadTimeout(30000);
                try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                StringBuilder sb=new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}
                String reply=new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                JSONObject am=new JSONObject();am.put("role","assistant");am.put("content",reply);chatHistory.add(am);
                while(chatHistory.size()>20)chatHistory.remove(0);
                mainHandler.post(()->{isTalking=false;showBubble("🐾 "+reply,Math.max(4000,reply.length()*60),true);});
            }catch(Exception e){mainHandler.post(()->{isTalking=false;showBubble("Error: "+e.getMessage(),5000,false);});}
        }).start();}
 
    // Groq para parsear JSON estructurado (alarmas, calendario)
    private interface GroqJSONCallback { void onResult(JSONObject json); }
    private void askGroqJSON(String prompt, GroqJSONCallback callback) {
        new Thread(()->{
            try{
                JSONArray msgs=new JSONArray();
                JSONObject um=new JSONObject();um.put("role","user");um.put("content",prompt);msgs.put(um);
                JSONObject body=new JSONObject();body.put("model",GROQ_MODEL);body.put("max_tokens",200);body.put("messages",msgs);
                URL url=new URL(GROQ_URL);HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);
                conn.setDoOutput(true);conn.setConnectTimeout(15000);conn.setReadTimeout(20000);
                try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                StringBuilder sb=new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}
                String raw=new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                // Limpiar ```json ``` si los hay
                raw=raw.replaceAll("```json","").replaceAll("```","").trim();
                JSONObject json=new JSONObject(raw);
                mainHandler.post(()->callback.onResult(json));
            }catch(Exception e){mainHandler.post(()->showBubble("No pude procesar eso, ¿puedes repetirlo?",3000,true));}
        }).start();}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  BUBBLE
    // ─────────────────────────────────────────────────────────────────────────
    private void showBubble(String text,int durationMs,boolean hablarEnVoz){
        mainHandler.post(()->{
            bubbleText.setText(text);actualizarPosBurbuja();
            bubbleCard.setVisibility(View.VISIBLE);bubbleCard.setAlpha(0f);
            bubbleCard.animate().alpha(1f).setDuration(200).start();
            try{wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}
            if(hablarEnVoz)hablar(text.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim());
            if(bubbleHider!=null)bubbleHandler.removeCallbacks(bubbleHider);
            if(durationMs>0){bubbleHider=()->bubbleCard.animate().alpha(0f).setDuration(300).withEndAction(()->bubbleCard.setVisibility(View.GONE)).start();bubbleHandler.postDelayed(bubbleHider,durationMs);}});}
 
    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Quacky IA",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);}}
    private Notification buildNotification(){
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("🐾 Quacky — 2x Power = escuchar").setContentText("Tócame · doble power 🎤").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true).build();}
 
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public IBinder onBind(Intent i){return null;}
 
    @Override public void onDestroy(){
        super.onDestroy();
        if(sensorManager!=null)        sensorManager.unregisterListener(this);
        if(animalView!=null)           try{wm.removeView(animalView);}catch(Exception ignored){}
        if(bubbleCard!=null)           try{wm.removeView(bubbleCard);}catch(Exception ignored){}
        if(footprintOverlay!=null)     try{wm.removeView(footprintOverlay);}catch(Exception ignored){}
        if(voiceResultReceiver!=null)  unregisterReceiver(voiceResultReceiver);
        if(powerButtonReceiver!=null)  unregisterReceiver(powerButtonReceiver);
        if(tts!=null){tts.stop();tts.shutdown();}
        mainHandler.removeCallbacks(moveRunnable);
        walkHandler.removeCallbacks(walkAnim);
        quackHandler.removeCallbacks(quackRunnable);}
}
 
