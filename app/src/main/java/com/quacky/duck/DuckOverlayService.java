package com.quacky.duck;
 
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
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
 
    // ✅ Pon tu key de Groq aquí (gsk_...)
    private static final String GROQ_API_KEY = "PEGA_TU_KEY_DE_GROQ_AQUI";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL   = "llama-3.3-70b-versatile";
 
    private static final int   DUCK_SIZE_DP = 9;
    private static final float SPEED_BASE   = 0.011f;
    private static final float GYRO_FORCE   = 18f;
 
    // Ventana del animal
    private View                       animalView;
    private WindowManager.LayoutParams animalParams;
 
    // Ventana de la burbuja (FLAG_NOT_TOUCHABLE = nunca bloquea toques)
    private LinearLayout               bubbleCard;
    private TextView                   bubbleText;
    private WindowManager.LayoutParams bubbleParams;
 
    // Huellas
    private View footprintOverlay;
 
    private WindowManager wm;
    private int overlayType;
 
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
        Footprint(float x, float y, long born, boolean left) {
            this.x = x; this.y = y; this.born = born; this.isLeft = left;
        }
    }
    private final List<Footprint> footprints     = new ArrayList<>();
    private static final long     FOOTPRINT_LIFE = 1800;
    private long    lastFootprintTime = 0;
    private boolean nextFootLeft      = true;
    private float   lastFootX = -999, lastFootY = -999;
 
    private Handler  quackHandler  = new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private final Random rng = new Random();
    private String animalTipo = "duck";
 
    private TextToSpeech tts;
    private boolean      ttsListo = false;
 
    private BroadcastReceiver voiceResultReceiver;
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
        overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
 
        SharedPreferences prefs = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        animalTipo = prefs.getString("animal", "duck");
 
        DisplayMetrics dm = getResources().getDisplayMetrics();
        currentX = dm.widthPixels / 2f - dp(DUCK_SIZE_DP) / 2f;
        currentY = dm.heightPixels * 0.72f;
        targetX  = currentX;
        targetY  = currentY;
 
        setupFootprintOverlay();
        setupBubbleWindow();
        setupAnimalWindow();
        setupTTS();
        setupSpeechRecognizer();
        setupGyroscope();
        startMoveLoop();
        startRandomSounds();
 
        mainHandler.postDelayed(() -> showBubble(saludoInicial(), 5000, false), 1200);
    }
 
    private String saludoInicial() {
        switch (animalTipo) {
            case "cat": return "¡Miau! 🐱 Tócame para hablar";
            case "dog": return "¡Guau! 🐶 Tócame para hablar";
            default:    return "¡Quack! 🐥 Tócame para hablar";
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  VENTANA DEL ANIMAL
    //  ✅ FIX: usa getIdentifier() en lugar de R.drawable.cat/dog
    //  Así compila SIEMPRE aunque las imágenes no estén aún en drawable/
    // ─────────────────────────────────────────────────────────────────────────
    private void setupAnimalWindow() {
        int animalPx = dp(DUCK_SIZE_DP);
 
        switch (animalTipo) {
            case "cat": {
                PetView pv = new PetView(this);
                // getIdentifier() busca el drawable por nombre en tiempo de ejecución,
                // NO en compilación — así nunca da error de compilación
                int resId = getResources().getIdentifier("cat", "drawable", getPackageName());
                if (resId != 0) {
                    try {
                        Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId);
                        if (bmp != null) pv.setAnimalBitmap(bmp);
                    } catch (Exception ignored) {}
                }
                animalView = pv;
                break;
            }
            case "dog": {
                PetView pv = new PetView(this);
                int resId = getResources().getIdentifier("dog", "drawable", getPackageName());
                if (resId != 0) {
                    try {
                        Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId);
                        if (bmp != null) pv.setAnimalBitmap(bmp);
                    } catch (Exception ignored) {}
                }
                animalView = pv;
                break;
            }
            default: // "duck"
                animalView = new DuckView(this);
                break;
        }
 
        animalParams = new WindowManager.LayoutParams(
            animalPx, animalPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        animalParams.gravity = Gravity.TOP | Gravity.START;
        animalParams.x = (int) currentX;
        animalParams.y = (int) currentY;
        wm.addView(animalView, animalParams);
 
        animalView.setOnTouchListener(new View.OnTouchListener() {
            long touchStart;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) touchStart = System.currentTimeMillis();
                if (e.getAction() == MotionEvent.ACTION_UP && System.currentTimeMillis() - touchStart < 400)
                    startListening();
                return true;
            }
        });
 
        setupWalkAnimation();
    }
 
    private void llamarSetWalkState(boolean moving, float phase, float dir) {
        if (animalView instanceof DuckView)
            ((DuckView) animalView).setWalkState(moving, phase, dir);
        else if (animalView instanceof PetView)
            ((PetView) animalView).setWalkState(moving, phase, dir);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  VENTANA DE LA BURBUJA — FLAG_NOT_TOUCHABLE = NUNCA bloquea toques
    // ─────────────────────────────────────────────────────────────────────────
    private void setupBubbleWindow() {
        bubbleCard = new LinearLayout(this);
        bubbleCard.setOrientation(LinearLayout.VERTICAL);
        bubbleCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14)); bg.setColor(0xEE1A1A3A); bg.setStroke(dp(1), 0xFFFFD700);
        bubbleCard.setBackground(bg);
        bubbleText = new TextView(this);
        bubbleText.setTextColor(Color.WHITE); bubbleText.setTextSize(13); bubbleText.setMaxWidth(dp(230));
        bubbleCard.addView(bubbleText);
        bubbleCard.setVisibility(View.GONE);
 
        bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = (int) currentX;
        bubbleParams.y = Math.max(0, (int) currentY - dp(60));
        wm.addView(bubbleCard, bubbleParams);
    }
 
    private void actualizarPosBurbuja() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int bx = Math.max(dp(8), Math.min((int) currentX - dp(20), dm.widthPixels - dp(240)));
        int by = Math.max(dp(8), (int) currentY - dp(65));
        bubbleParams.x = bx; bubbleParams.y = by;
        try { if (bubbleCard.getVisibility() == View.VISIBLE) wm.updateViewLayout(bubbleCard, bubbleParams); } catch (Exception ignored) {}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  TTS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                configurarVoz(getSharedPreferences("quacky_prefs", MODE_PRIVATE).getString("voz", "femenina"));
                ttsListo = true;
            }
        });
    }
 
    private void configurarVoz(String tipo) {
        if (tts == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Set<Voice> voces = tts.getVoices();
            if (voces != null) {
                for (Voice v : voces) {
                    if (v.getLocale() == null || !v.getLocale().getLanguage().equals("es")) continue;
                    String n = v.getName().toLowerCase();
                    boolean esM = n.contains("-m-") || n.contains("male") || n.contains("smb");
                    boolean esF = n.contains("-f-") || n.contains("female") || n.contains("sfb") || n.contains("esf");
                    if (tipo.equals("masculina") && esM) { tts.setVoice(v); return; }
                    if (tipo.equals("femenina")  && esF) { tts.setVoice(v); return; }
                }
            }
        }
        tts.setLanguage(new Locale("es", "MX"));
        tts.setPitch(tipo.equals("masculina") ? 0.78f : 1.25f);
        tts.setSpeechRate(tipo.equals("masculina") ? 0.93f : 1.05f);
    }
 
    private void hablar(String texto) {
        if (!ttsListo || tts == null) return;
        String limpio = texto.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]", " ").replaceAll("\\s+", " ").trim();
        if (!limpio.isEmpty()) tts.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, "q_" + System.currentTimeMillis());
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GIROSCOPIO
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGyroscope() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) return;
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroSensor != null) sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        else {
            Sensor a = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a != null) sensorManager.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME);
        }
    }
 
    @Override
    public void onSensorChanged(SensorEvent event) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int px = dp(DUCK_SIZE_DP);
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float rx = event.values[0], ry = event.values[1];
            if (Math.abs(rx) < 0.05f) rx = 0f; if (Math.abs(ry) < 0.05f) ry = 0f;
            gyroVelX += ry * GYRO_FORCE; gyroVelY += rx * GYRO_FORCE;
            targetX = Math.max(0, Math.min(targetX + gyroVelX, dm.widthPixels  - px));
            targetY = Math.max(0, Math.min(targetY + gyroVelY, dm.heightPixels - px));
            gyroVelX *= 0.85f; gyroVelY *= 0.85f;
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float ax = -event.values[0], ay = event.values[1];
            if (Math.abs(ax) < 0.3f) ax = 0f; if (Math.abs(ay) < 0.3f) ay = 0f;
            targetX = Math.max(0, Math.min(targetX + ax * 1.2f, dm.widthPixels  - px));
            targetY = Math.max(0, Math.min(targetY - ay * 1.2f, dm.heightPixels - px));
        }
    }
 
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  HUELLAS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFootprintOverlay() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        footprintOverlay = new View(this) {
            private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path  pt = new Path();
            @Override protected void onDraw(Canvas canvas) {
                long now = System.currentTimeMillis(); fp.setStyle(Paint.Style.FILL);
                synchronized (footprints) {
                    Iterator<Footprint> it = footprints.iterator();
                    while (it.hasNext()) {
                        Footprint f = it.next();
                        float age = (now - f.born) / (float) FOOTPRINT_LIFE;
                        if (age >= 1f) { it.remove(); continue; }
                        fp.setColor(Color.argb((int)(140*(1f-age)), 180, 80, 0));
                        float scale = dm.density * 3.5f;
                        canvas.save(); canvas.translate(f.x, f.y); canvas.rotate(f.isLeft ? -15f : 15f);
                        pt.reset(); pt.moveTo(0,0); pt.lineTo(-scale,scale*0.6f); pt.lineTo(-scale*0.4f,scale*1.1f);
                        pt.lineTo(scale*0.2f,scale*0.7f); pt.lineTo(scale*0.8f,scale*1.1f); pt.lineTo(scale*1.2f,scale*0.5f); pt.close();
                        canvas.drawPath(pt, fp); canvas.restore();
                    }
                }
                postInvalidateDelayed(60);
            }
        };
        footprintOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(dm.widthPixels, dm.heightPixels, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        wm.addView(footprintOverlay, p);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WALK ANIMATION
    // ─────────────────────────────────────────────────────────────────────────
    private void setupWalkAnimation() {
        walkAnim = new Runnable() {
            @Override public void run() {
                boolean moving = isMoving();
                if (moving) { walkPhase += 0.03f; if (walkPhase > 1f) walkPhase = 0f; }
                llamarSetWalkState(moving, walkPhase, facingRight ? 1f : -1f);
                walkHandler.postDelayed(this, 30);
            }
        };
        walkHandler.post(walkAnim);
    }
 
    private boolean isMoving() { return Math.abs(targetX-currentX)>2 || Math.abs(targetY-currentY)>2; }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  LOOP DE MOVIMIENTO
    // ─────────────────────────────────────────────────────────────────────────
    private void startMoveLoop() {
        moveRunnable = new Runnable() {
            @Override public void run() {
                float dx=targetX-currentX, dy=targetY-currentY, dist=(float)Math.sqrt(dx*dx+dy*dy);
                if (dist > 1.5f) {
                    float speed = Math.min(SPEED_BASE, Math.max(0.006f, dist/2000f));
                    currentX += dx*speed; currentY += dy*speed;
                    if (dx > 0 != facingRight) facingRight = dx > 0;
                    animalParams.x=(int)currentX; animalParams.y=(int)currentY;
                    try { wm.updateViewLayout(animalView, animalParams); } catch (Exception ignored) {}
                    actualizarPosBurbuja();
                    float mx=currentX-lastFootX, my=currentY-lastFootY, moved=(float)Math.sqrt(mx*mx+my*my);
                    long now=System.currentTimeMillis();
                    if (moved>dp(20) && (now-lastFootprintTime)>400) {
                        lastFootprintTime=now; lastFootX=currentX; lastFootY=currentY;
                        float offX=nextFootLeft?-dp(4):dp(4);
                        synchronized(footprints){footprints.add(new Footprint(currentX+dp(DUCK_SIZE_DP)/2f+offX,currentY+dp(DUCK_SIZE_DP)-dp(4),now,nextFootLeft));}
                        nextFootLeft=!nextFootLeft;
                    }
                }
                mainHandler.postDelayed(this, 16);
            }
        };
        mainHandler.post(moveRunnable);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SONIDOS POR ANIMAL
    // ─────────────────────────────────────────────────────────────────────────
    private void startRandomSounds() {
        quackRunnable = new Runnable() {
            @Override public void run() {
                if (!isListening && !isTalking) playAnimalSound();
                quackHandler.postDelayed(this, 15000+rng.nextInt(30000));
            }
        };
        quackHandler.postDelayed(quackRunnable, 8000+rng.nextInt(10000));
    }
 
    private void playAnimalSound() {
        switch (animalTipo) { case "cat": playSoundCat(); break; case "dog": playSoundDog(); break; default: playSoundDuck(); break; }
    }
 
    private void playSoundDuck() {
        new Thread(()->{try{int sr=44100,ms=320,n=sr*ms/1000;short[] s=new short[n];
            for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=700-350*p,a=p<0.08?p/0.08:Math.max(0,1-(p-0.08)/0.92);
            double m=a*0.65*Math.sin(2*Math.PI*f*t)+a*0.25*Math.sin(4*Math.PI*f*t)+a*0.08*Math.sin(6*Math.PI*f*t)+a*0.04*Math.sin(24*Math.PI*t)*Math.sin(2*Math.PI*f*t);
            s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}
            playRawAudio(s,sr,ms);}catch(Exception ignored){}}).start();
        mainHandler.post(()->{String[] f={"¡Cuak!","¡Quack!","¡Cuaaak!","🦆 Quack!","¡Cuak cuak!"};showBubble(f[rng.nextInt(f.length)],1800,false);animarSalto();});
    }
 
    private void playSoundCat() {
        new Thread(()->{try{int sr=44100,ms=500,n=sr*ms/1000;short[] s=new short[n];
            for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=600+200*Math.sin(p*Math.PI);
            double a=p<0.1?p/0.1:p>0.8?(1-p)/0.2:1.0;
            double m=a*(0.7*Math.sin(2*Math.PI*f*t)+0.2*Math.sin(4*Math.PI*f*t)+0.05*Math.sin(6*Math.PI*f*t));
            s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}
            playRawAudio(s,sr,ms);}catch(Exception ignored){}}).start();
        mainHandler.post(()->{String[] f={"¡Miau!","¡Miiiau!","😺 Miau~","¡Miau miau!","Purrr..."};showBubble(f[rng.nextInt(f.length)],1800,false);animarSalto();});
    }
 
    private void playSoundDog() {
        new Thread(()->{try{int sr=44100,ms=300,n=sr*ms/1000;short[] s=new short[n];
            for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=350+100*Math.sin(p*Math.PI*3);
            double a=p<0.05?p/0.05:p>0.7?(1-p)/0.3:1.0,noise=(rng.nextDouble()-0.5)*0.15;
            double m=a*(0.6*Math.sin(2*Math.PI*f*t)+0.25*Math.sin(4*Math.PI*f*t)+noise);
            s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}
            playRawAudio(s,sr,ms);}catch(Exception ignored){}}).start();
        mainHandler.post(()->{String[] f={"¡Guau!","¡Woof!","🐶 Guau!","¡Guau guau!","¡Arf arf!"};showBubble(f[rng.nextInt(f.length)],1800,false);animarSalto();});
    }
 
    private void playRawAudio(short[] samples, int sampleRate, int durMs) throws Exception {
        AudioTrack track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();
        track.write(samples,0,samples.length); track.play();
        Thread.sleep(durMs+80); track.stop(); track.release();
    }
 
    private void animarSalto() {
        if (animalView!=null) animalView.animate().translationY(-8f).setDuration(130).withEndAction(()->animalView.animate().translationY(0).setDuration(130).start()).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SPEECH RECEIVER
    // ─────────────────────────────────────────────────────────────────────────
    private void setupSpeechRecognizer() {
        voiceResultReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String texto = intent.getStringExtra("voice_text");
                if (texto!=null && !texto.isEmpty()) {
                    mainHandler.post(()->{isListening=false;if(!handleVoiceCommand(texto)){showBubble("Tú: \""+texto+"\"",2500,false);askGroq(texto);}});
                } else {
                    mainHandler.post(()->{isListening=false;showBubble("No te escuché 🐾 ¡Inténtalo!",3000,false);});
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.quacky.duck.VOICE_RESULT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(voiceResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(voiceResultReceiver, filter);
    }
 
    private void startListening() {
        if (isTalking||isListening) return;
        if (tts!=null&&ttsListo) tts.stop();
        isListening=true; showBubble("...te escucho 👂",0,false);
        if (animalView!=null) animalView.animate().translationY(-14f).setDuration(180).withEndAction(()->animalView.animate().translationY(0).setDuration(180).start()).start();
        Intent intent = new Intent(DuckOverlayService.this, VoiceActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  COMANDOS DE VOZ
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleVoiceCommand(String texto) {
        String lower=texto.toLowerCase().trim();
        for (String patron:new String[]{"marcale a ","llama a ","llámale a ","llamar a ","marcar a ","hablar con ","marca a "}) {
            if (lower.contains(patron)) {
                String nombre=texto.substring(lower.indexOf(patron)+patron.length()).trim().replace(" por favor","").replace(" porfavor","").trim();
                buscarYLlamar(nombre); return true;
            }
        }
        return false;
    }
 
    private void buscarYLlamar(String nombre) {
        showBubble("🔍 Buscando a "+nombre+"...",0,true);
        new Thread(()->{
            String tel=buscarTelefono(nombre);
            mainHandler.post(()->{
                if (tel!=null) {
                    showBubble("📞 Llamando a "+nombre+"...",3000,true);
                    try{Intent l=new Intent(Intent.ACTION_CALL);l.setData(Uri.parse("tel:"+tel));l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(l);}
                    catch(SecurityException e){showBubble("⚠️ Necesito permiso de Teléfono en Ajustes",5000,true);}
                } else {showBubble("No encontré a "+nombre+" en contactos",3500,true);}
            });
        }).start();
    }
 
    private String buscarTelefono(String nombre) {
        try{android.database.Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);
            if(c==null)return null;String buscar=nombre.toLowerCase().trim(),mejor=null;int mejorP=0;
            while(c.moveToNext()){String nc=c.getString(0),tel=c.getString(1);if(nc==null||tel==null)continue;
                String nl=nc.toLowerCase().trim();if(nl.equals(buscar)){c.close();return tel.replaceAll("[^+0-9]","");}
                int p=nl.contains(buscar)?2:buscar.contains(nl)?1:0;if(p>mejorP){mejorP=p;mejor=tel.replaceAll("[^+0-9]","");}}
            c.close();return mejor;}catch(Exception e){return null;}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GROQ API
    // ─────────────────────────────────────────────────────────────────────────
    private void askGroq(String userMessage) {
        isTalking=true; showBubble("💭 pensando...",0,false);
        if(animalView!=null) animalView.animate().rotation(10f).setDuration(100).withEndAction(()->animalView.animate().rotation(-10f).setDuration(100).withEndAction(()->animalView.animate().rotation(0f).setDuration(100).start()).start()).start();
        try{JSONObject um=new JSONObject();um.put("role","user");um.put("content",userMessage);chatHistory.add(um);}catch(JSONException ignored){}
        new Thread(()->{
            try{
                JSONArray msgs=new JSONArray();
                JSONObject sys=new JSONObject();sys.put("role","system");
                String nombre=animalTipo.equals("cat")?"Michi, un gatito gris IA":animalTipo.equals("dog")?"Guau, un perrito golden IA":"Quacky, un patito amarillo IA";
                String sonido=animalTipo.equals("cat")?"¡Miau!":animalTipo.equals("dog")?"¡Guau!":"¡Quack!";
                sys.put("content","Eres "+nombre+" que vive flotando en la pantalla. Puedes llamar a contactos con 'llama a [nombre]'. Eres simpático y divertido. Dices '"+sonido+"' de vez en cuando. Respuestas CORTAS (máximo 2-3 oraciones) en español mexicano.");
                msgs.put(sys); for(JSONObject m:chatHistory)msgs.put(m);
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
        }).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  BUBBLE
    // ─────────────────────────────────────────────────────────────────────────
    private void showBubble(String text, int durationMs, boolean hablarEnVoz) {
        mainHandler.post(()->{
            bubbleText.setText(text); actualizarPosBurbuja();
            bubbleCard.setVisibility(View.VISIBLE); bubbleCard.setAlpha(0f);
            bubbleCard.animate().alpha(1f).setDuration(200).start();
            try{wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}
            if(hablarEnVoz) hablar(text.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim());
            if(bubbleHider!=null)bubbleHandler.removeCallbacks(bubbleHider);
            if(durationMs>0){bubbleHider=()->bubbleCard.animate().alpha(0f).setDuration(300).withEndAction(()->bubbleCard.setVisibility(View.GONE)).start();bubbleHandler.postDelayed(bubbleHider,durationMs);}
        });
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  NOTIFICATION
    // ─────────────────────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Quacky IA",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);}
    }
    private Notification buildNotification() {
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("🐾 Quacky activo").setContentText("Tócame · habla conmigo 🎤").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true).build();
    }
 
    private int dp(int val){return Math.round(val*getResources().getDisplayMetrics().density);}
    @Override public IBinder onBind(Intent i){return null;}
 
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(sensorManager!=null)       sensorManager.unregisterListener(this);
        if(animalView!=null)          try{wm.removeView(animalView);}catch(Exception ignored){}
        if(bubbleCard!=null)          try{wm.removeView(bubbleCard);}catch(Exception ignored){}
        if(footprintOverlay!=null)    try{wm.removeView(footprintOverlay);}catch(Exception ignored){}
        if(voiceResultReceiver!=null) unregisterReceiver(voiceResultReceiver);
        if(tts!=null){tts.stop();tts.shutdown();}
        mainHandler.removeCallbacks(moveRunnable);
        walkHandler.removeCallbacks(walkAnim);
        quackHandler.removeCallbacks(quackRunnable);
    }
}
 
