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
import android.speech.*;
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
 
    private static final String CHANNEL_ID       = "quacky_channel";
    private static final String ACTION_ESCUCHAR  = "com.quacky.duck.ACTIVAR_ESCUCHA";
    private static final String ACTION_ALIMENTAR = "com.quacky.duck.ALIMENTAR";
 
    // ✅ Pon tu key de Groq aquí
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL   = "llama-3.3-70b-versatile";
 
    private static final int   DUCK_SIZE_DP = 60;
    private static final float SPEED_BASE   = 0.011f;
    private static final float GYRO_FORCE   = 18f;
 
    // ── Wake word — vista invisible 1×1 px con SpeechRecognizer propio ────────
    // La vista tiene su propio lifecycle, más estable que service directo
    // - SpeechRecognizer sabemos que funciona (el toque ya lo usa)
    // - No necesita internet propio (usa el reconocedor del sistema)
    // - No tiene problemas de archivo/permisos de grabación
    // - Más confiable en background como foreground service
    private SpeechRecognizer wakeRecognizer;
    private boolean          wakeActivo   = false;
    private boolean          enComando    = false;
    private String           nombreMascota = "";
 
    // ── Muerte ────────────────────────────────────────────────────────────────────
    private boolean animalMuerto = false;
 
    // ── Hambre (Tamagotchi) ───────────────────────────────────────────────────
    private int      hambreNivel   = 0;
    private int      felicidad     = 70;
    private int      energia       = 80;
    private boolean  estaComiendo  = false;
    private Handler  hambreHandler = new Handler(Looper.getMainLooper());
    private Runnable hambreTick;
    private Handler  quejaHandler  = new Handler(Looper.getMainLooper());
    private Runnable quejaRunnable;
 
    // Ventanas overlay
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
    private Handler  mainHandler;
    private Runnable moveRunnable;
 
    // Animación
    private Handler  walkHandler = new Handler(Looper.getMainLooper());
    private float    walkPhase   = 0f;
    private Runnable walkAnim;
 
    // Huellas
    private static class Footprint {
        float x,y; long born; boolean isLeft;
        Footprint(float x,float y,long born,boolean l){this.x=x;this.y=y;this.born=born;this.isLeft=l;}
    }
    private final List<Footprint> footprints = new ArrayList<>();
    private static final long FOOTPRINT_LIFE = 1800;
    private long lastFootprintTime=0; boolean nextFootLeft=true; float lastFootX=-999,lastFootY=-999;
 
    // Sonidos
    private Handler  quackHandler=new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private final Random rng=new Random();
    private String animalTipo="duck";
 
    // TTS
    private TextToSpeech tts;
    private boolean      ttsListo=false;
 
    // Receptores
    private BroadcastReceiver voiceResultReceiver;
    private BroadcastReceiver escucharReceiver;
    private BroadcastReceiver alimentarReceiver;
 
    private final List<JSONObject> chatHistory=new ArrayList<>();
    private Handler  bubbleHandler=new Handler(Looper.getMainLooper());
    private Runnable bubbleHider;
 
    // Giroscopio
    private SensorManager  sensorManager;
    private Sensor         gyroSensor;
    private volatile float gyroVelX=0f,gyroVelY=0f;
 
    @Override
    public void onCreate(){
        super.onCreate();
        mainHandler=new Handler(Looper.getMainLooper());
 
        SharedPreferences prefs=getSharedPreferences("quacky_prefs",MODE_PRIVATE);
        animalTipo    =prefs.getString("animal","duck");
        nombreMascota =prefs.getString("nombre_mascota","").toLowerCase().trim();
 
        long ultimaComida=prefs.getLong("ultima_comida",System.currentTimeMillis());
        long minSin=(System.currentTimeMillis()-ultimaComida)/60000L;
        hambreNivel=(int)Math.min(100,minSin*100/30);
        felicidad=prefs.getInt("felicidad",70);
        energia  =prefs.getInt("energia",  80);
 
        createNotificationChannel();
        startForeground(1,buildNotification());
 
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        overlayType=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O
            ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            :WindowManager.LayoutParams.TYPE_PHONE;
 
        DisplayMetrics dm=getResources().getDisplayMetrics();
        currentX=dm.widthPixels/2f-dp(DUCK_SIZE_DP)/2f;
        currentY=dm.heightPixels*0.72f;
        targetX=currentX;targetY=currentY;
 
        setupFootprintOverlay();
        setupBubbleWindow();
        setupAnimalWindow();
        setupTTS();
        setupReceivers();
        setupGyroscope();
        startMoveLoop();
        startRandomSounds();
        iniciarSistemaHambre();
 
        mainHandler.postDelayed(()->showBubble(saludoInicial(),5000,false),1200);
 
        // ✅ Iniciar wake word con SpeechRecognizer
        // Esperamos 5 segundos para que todo esté listo
        mainHandler.postDelayed(this::iniciarWakeWord,5000);
    }
 
    private String saludoInicial(){
        String n=nombreMascota.isEmpty()?"Quacky":capitalize(nombreMascota);
        if(hambreNivel>=80)return"¡Tengo hambre! 😋 Abre la app para darme de comer";
        switch(animalTipo){case"cat":return"¡Miau! Soy "+n+" 🐱 Di mi nombre para hablar";case"dog":return"¡Guau! Soy "+n+" 🐶 Di mi nombre para hablar";default:return"¡Quack! Soy "+n+" 🐥 Di mi nombre para hablar";}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WAKE WORD — SpeechRecognizer corriendo en el main thread del Service
    //
    //  Flujo:
    //  iniciarWakeWord() → cicloEscucha() → startListening()
    //  Si oye el nombre → reaccionarAlNombre() → (espera 8s) → cicloEscucha()
    //  Si timeout/error → (espera 500ms) → cicloEscucha()
    // ─────────────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────
    //  VISTA INVISIBLE 1×1 px — maneja SpeechRecognizer en su propio ciclo
    //  Se posiciona sobre el pato y "escucha" continuamente.
    //  Cuando detecta el nombre llama a reaccionarAlNombre() —
    //  exactamente como cuando el usuario toca el pato con el dedo.
    // ─────────────────────────────────────────────────────────────────────────
    private View              wakeView;
    private WindowManager.LayoutParams wakeViewParams;
    private SpeechRecognizer  wakeSR;          // recognizer propio de la vista
    private int               wakeErrCount = 0;// errores consecutivos
    private final Handler     wakeRetryH   = new Handler(Looper.getMainLooper());
 
    private void iniciarWakeWord() {
        if (nombreMascota.isEmpty()) {
            showBubble("Configura un nombre en la app para activacion por voz", 5000, false);
            return;
        }
        wakeActivo = true;
 
        // Crear vista 1×1 completamente transparente
        wakeView = new View(DuckOverlayService.this);
        wakeViewParams = new WindowManager.LayoutParams(
            1, 1, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT);
        wakeViewParams.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        wakeViewParams.x = (int) currentX;
        wakeViewParams.y = (int) currentY;
        wm.addView(wakeView, wakeViewParams);
 
        // Iniciar ciclo de escucha con delay inicial
        wakeRetryH.postDelayed(this::cicloEscucha, 2000);
    }
 
    /** Un ciclo de escucha: destruir anterior → crear nuevo → startListening */
    private void cicloEscucha() {
        if (!wakeActivo || enComando || isTalking || estaComiendo || animalMuerto) {
            wakeRetryH.postDelayed(this::cicloEscucha, 1500);
            return;
        }
 
        // Si hubo demasiados errores seguidos → pausa larga y reset
        if (wakeErrCount >= 6) {
            wakeErrCount = 0;
            wakeRetryH.postDelayed(this::cicloEscucha, 8000);
            return;
        }
 
        destruirWakeSR();
 
        if (!SpeechRecognizer.isRecognitionAvailable(DuckOverlayService.this)) {
            wakeRetryH.postDelayed(this::cicloEscucha, 10000);
            return;
        }
 
        wakeSR = SpeechRecognizer.createSpeechRecognizer(DuckOverlayService.this);
        wakeSR.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onResults(android.os.Bundle results) {
                if (!wakeActivo || enComando) { wakeRetryH.post(()->cicloEscucha()); return; }
                wakeErrCount = 0;
                java.util.List<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && detectarNombre(matches)) {
                    reaccionarAlNombre();
                    // Pausar 8s tras detección (el pato está atendiendo el comando)
                    wakeRetryH.postDelayed(()->cicloEscucha(), 8000);
                } else {
                    wakeRetryH.postDelayed(()->cicloEscucha(), 300);
                }
            }
            @Override
            public void onPartialResults(android.os.Bundle partial) {
                if (!wakeActivo || enComando) return;
                java.util.List<String> p =
                    partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (p != null && detectarNombre(p)) {
                    destruirWakeSR();
                    reaccionarAlNombre();
                    wakeRetryH.postDelayed(()->cicloEscucha(), 8000);
                }
            }
            @Override
            public void onError(int error) {
                if (!wakeActivo) return;
                // NO_MATCH / SPEECH_TIMEOUT = normal, reinicio rápido
                boolean normal = (error == SpeechRecognizer.ERROR_NO_MATCH
                               || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
                if (!normal) wakeErrCount++;
                int delay = normal ? 300 : (wakeErrCount < 3 ? 1500 : 4000);
                wakeRetryH.postDelayed(()->cicloEscucha(), delay);
            }
            @Override public void onReadyForSpeech(android.os.Bundle p) { wakeErrCount = 0; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int t, android.os.Bundle b) {}
        });
 
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
 
        try {
            wakeSR.startListening(intent);
        } catch (Exception e) {
            wakeErrCount++;
            wakeRetryH.postDelayed(()->cicloEscucha(), 3000);
        }
    }
 
    private void destruirWakeSR() {
        if (wakeSR != null) {
            try { wakeSR.cancel();  } catch (Exception ignored) {}
            try { wakeSR.destroy(); } catch (Exception ignored) {}
            wakeSR = null;
        }
    }
 
    private void destruirWakeRecognizer() { destruirWakeSR(); }
 
    private boolean detectarNombre(List<String> candidatos){
        if(nombreMascota.isEmpty()||candidatos==null) return false;
        String n=nombreMascota.toLowerCase().trim();
        for(String candidato:candidatos){
            String c=candidato.toLowerCase().trim();
            if(c.contains(n)) return true;
            for(String palabra:c.split("\\s+")){
                if(palabra.isEmpty()) continue;
                if(palabra.equals(n)) return true;
                if(n.length()>=4 && levenshtein(palabra,n)<=2) return true;
                if(n.length()>=3 && palabra.length()>=3 && palabra.substring(0,3).equals(n.substring(0,3))) return true;
            }
        }
        return false;
    }
 
    private int levenshtein(String a,String b){
        int la=a.length(),lb=b.length();
        int[][]dp=new int[la+1][lb+1];
        for(int i=0;i<=la;i++)dp[i][0]=i;
        for(int j=0;j<=lb;j++)dp[0][j]=j;
        for(int i=1;i<=la;i++)for(int j=1;j<=lb;j++)
            dp[i][j]=a.charAt(i-1)==b.charAt(j-1)?dp[i-1][j-1]:1+Math.min(dp[i-1][j-1],Math.min(dp[i-1][j],dp[i][j-1]));
        return dp[la][lb];
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Reacción cuando se detecta el nombre
    // ─────────────────────────────────────────────────────────────────────────
    private void reaccionarAlNombre(){
        if(enComando) return;
        enComando=true; isListening=true;
        String saludo;
        if(hambreNivel>=80)saludo="¡Hola! Pero... 😋 ¿me das de comer primero? Abre la app 🍖";
        else switch(animalTipo){case"cat":saludo="¡Miau! ¿Qué necesitas? 🐱";break;case"dog":saludo="¡Guau! ¡Aquí estoy! 🐶";break;default:saludo="¡Quack! ¿Qué necesitas? 🐥";}
        showBubble(saludo,0,true);
        animarSalto();
        mainHandler.postDelayed(this::lanzarDialogoComando,1800);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ACTIVAR ESCUCHA (desde toque o botón notificación)
    // ─────────────────────────────────────────────────────────────────────────
    private void startListening(){
        if(isTalking||isListening) return;
        isListening=true; enComando=true;
        // Pausar el wake word mientras estamos en comando
        destruirWakeRecognizer();
        if(tts!=null&&ttsListo)tts.stop();
        showBubble("...te escucho 👂",0,false);
        animarSalto();
        lanzarDialogoComando();
    }
 
    private void lanzarDialogoComando(){
        try{
            Intent i=new Intent(DuckOverlayService.this,VoiceActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }catch(Exception e){isListening=false;enComando=false;reanudarWake();}
    }
 
    private void reanudarWake(){
        enComando=false;
        mainHandler.postDelayed(()->cicloEscucha(),2500);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SISTEMA DE HAMBRE — Tamagotchi
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarSistemaHambre(){
        hambreTick=new Runnable(){@Override public void run(){
            if(!estaComiendo&&hambreNivel<100){
                hambreNivel=Math.min(100,hambreNivel+1);
                actualizarHambreEnAnimal();
                guardarStats();
                if(hambreNivel>=80&&quejaRunnable==null)activarQuejas();
                if(hambreNivel>=100)mainHandler.post(()->activarMuerte());
            }
            hambreHandler.postDelayed(this,5_000L);
        }};
        hambreHandler.postDelayed(hambreTick,5_000L);
        actualizarHambreEnAnimal();
        if(hambreNivel>=80)activarQuejas();
    }
 
    private void activarMuerte() {
        if (animalMuerto) return;
        animalMuerto = true;
        wakeActivo = false;
        destruirWakeRecognizer();
        // Detener movimiento — el animal se queda donde está
        targetX = currentX; targetY = currentY;
        // Detener sonidos
        quackHandler.removeCallbacks(quackRunnable);
        // Detener quejas
        if (quejaRunnable != null) { quejaHandler.removeCallbacks(quejaRunnable); quejaRunnable = null; }
        // Iniciar animación de muerte en el DuckView
        if (animalView instanceof DuckView) ((DuckView) animalView).iniciarMuerte();
        // Registrar callback para expandir la vista cuando termine la fase 1
        if (animalView instanceof DuckView) {
            ((DuckView) animalView).setOnDeathCompleteListener(() -> {
                // Fase 1 terminó → expandir vista para mostrar escena completa
                mainHandler.post(() -> expandirVistaParaMuerte());
            });
        }
        // Mensaje dramático
        mainHandler.postDelayed(() -> showBubble("💀 Me morí de hambre... ¡Aliméntame en la app!", 0, true), 3000);
        // Resucitar si le dan de comer (se procesa en procesarAlimentacion)
    }
 
    private void resucitar() {
        if (!animalMuerto) return;
        animalMuerto = false;
        resetearTamanioVista();
        // El DuckView no tiene reset de muerte en tiempo de ejecución,
        // así que reiniciamos la vista recreándola
        mainHandler.post(() -> {
            try {
                // Quitar el animal muerto y crear uno nuevo
                if (animalView != null) { wm.removeView(animalView); animalView = null; }
                setupAnimalWindow();
                showBubble("😊 ¡Reviviste! ¡Gracias por la comida! 🍖", 5000, true);
            } catch (Exception e) {
                showBubble("😊 ¡Gracias por la comida!", 4000, true);
            }
        });
        // Reanudar wake word
        mainHandler.postDelayed(this::cicloEscucha, 3000);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Vista de muerte — expandir para mostrar la escena completa (calavera+moscas)
    // ─────────────────────────────────────────────────────────────────────────
    private void expandirVistaParaMuerte() {
        if (!animalMuerto || animalView == null) return;
        // La escena completa es más alta que el pato normal:
        // incluye calavera arriba + pato + moscas orbitando
        int nuevoAncho = dp(135);
        int nuevoAlto  = dp(160);
        // Centrar horizontalmente y mover arriba para que el pato quede en su sitio
        int nuevoX = (int)(currentX - (nuevoAncho - dp(DUCK_SIZE_DP)) / 2f);
        int nuevoY = (int)(currentY - dp(100));
        animalParams.width  = nuevoAncho;
        animalParams.height = nuevoAlto;
        animalParams.x = nuevoX;
        animalParams.y = Math.max(0, nuevoY);
        try { wm.updateViewLayout(animalView, animalParams); } catch (Exception ignored) {}
    }
 
    private void resetearTamanioVista() {
        if (animalView == null) return;
        animalParams.width  = dp(DUCK_SIZE_DP);
        animalParams.height = dp(DUCK_SIZE_DP);
        animalParams.x = (int) currentX;
        animalParams.y = (int) currentY;
        try { wm.updateViewLayout(animalView, animalParams); } catch (Exception ignored) {}
    }
 
        private void guardarStats(){
        getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit()
            .putInt("hambre_nivel",hambreNivel).putInt("felicidad",felicidad).putInt("energia",energia).apply();
    }
 
    private void actualizarHambreEnAnimal(){
        if(animalView instanceof DuckView)   ((DuckView) animalView).setHungerLevel(hambreNivel);
        else if(animalView instanceof PetView)((PetView) animalView).setHungerLevel(hambreNivel);
    }
 
    private void activarQuejas(){
        if(quejaRunnable!=null) return;
        quejaRunnable=new Runnable(){@Override public void run(){
            if(hambreNivel<80){quejaRunnable=null;return;}
            if(!isListening&&!isTalking&&!estaComiendo){
                String[]msgs=hambreNivel>=100
                    ?new String[]{"😵 ¡Me muero de hambre! ¡Abre la app!","🆘 ¡Necesito comida ya!"}
                    :new String[]{"😋 Tengo hambre... abre la app para darme de comer","🍖 ¿Me darías algo de comer?","🥣 Mi tazón está vacío..."};
                showBubble(msgs[rng.nextInt(msgs.length)],4000,true);
                animarSalto();
            }
            quejaHandler.postDelayed(this,hambreNivel>=100?12_000:25_000);
        }};
        quejaHandler.postDelayed(quejaRunnable,3000);
    }
 
    private void procesarAlimentacion(Intent datos){
        // Si estaba muerto → resucitar primero
        if (animalMuerto) { resucitar(); }
        if(estaComiendo) return;
        estaComiendo=true;
 
        String emoji   =(String)datos.getStringExtra("comida_emoji");if(emoji==null)emoji="🍖";
        String nomC    =(String)datos.getStringExtra("comida_nombre");if(nomC==null)nomC="comida";
        int    hBonus  =datos.getIntExtra("hambre_bonus",20);
        int    fBonus  =datos.getIntExtra("felicidad_bonus",10);
        int    eBonus  =datos.getIntExtra("energia_bonus",5);
        String pref    =(String)datos.getStringExtra("preferencia");if(pref==null)pref="ok";
 
        quejaHandler.removeCallbacks(quejaRunnable); quejaRunnable=null;
        hambreNivel=Math.max(0,Math.min(100,hambreNivel-hBonus));
        felicidad  =Math.max(0,Math.min(100,felicidad+fBonus));
        energia    =Math.max(0,Math.min(100,energia+eBonus));
 
        getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit()
            .putLong("ultima_comida",System.currentTimeMillis())
            .putInt("hambre_nivel",hambreNivel).putInt("felicidad",felicidad).putInt("energia",energia).apply();
 
        if(animalView instanceof DuckView)   ((DuckView) animalView).setEatingState(true);
        else if(animalView instanceof PetView)((PetView) animalView).setEatingState(true);
        actualizarHambreEnAnimal();
 
        String[]msgs={pref.equals("love")?"¡¡SÍ!! ¡"+emoji+"! ¡Es mi favorito! ❤️":pref.equals("dislike")?"Ugh... "+emoji+"... gracias 🤢":"¡Nom nom! "+emoji+" ¡Gracias! 😊"};
        showBubble(msgs[0],0,true);
 
        mainHandler.postDelayed(()->{
            estaComiendo=false;
            if(animalView instanceof DuckView)   ((DuckView) animalView).setEatingState(false);
            else if(animalView instanceof PetView)((PetView) animalView).setEatingState(false);
            showBubble("😊 ¡Estuvo delicioso! ¡Gracias!",4000,false);
        },7000);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  RECEPTORES
    // ─────────────────────────────────────────────────────────────────────────
    private void setupReceivers(){
        escucharReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){mainHandler.post(()->startListening());}};
        registrar(escucharReceiver,ACTION_ESCUCHAR);
 
        voiceResultReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            String texto=i.getStringExtra("voice_text");
            mainHandler.post(()->{
                isListening=false;
                if(texto!=null&&!texto.isEmpty()){if(!handleVoiceCommand(texto)){showBubble("Tú: \""+texto+"\"",2500,false);askGroq(texto);}}
                else showBubble("No te escuché 🐾",2000,false);
                // Reanudar wake word después del comando
                reanudarWake();
            });}};
        registrar(voiceResultReceiver,"com.quacky.duck.VOICE_RESULT");
 
        alimentarReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){mainHandler.post(()->procesarAlimentacion(i));}};
        registrar(alimentarReceiver,ACTION_ALIMENTAR);
    }
 
    private void registrar(BroadcastReceiver r,String a){IntentFilter f=new IntentFilter(a);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(r,f);}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  NOTIFICACIÓN
    // ─────────────────────────────────────────────────────────────────────────
    private Notification buildNotification(){
        PendingIntent piApp=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        Intent ei=new Intent(ACTION_ESCUCHAR);ei.setPackage(getPackageName());
        PendingIntent piE=PendingIntent.getBroadcast(this,1,ei,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String n=nombreMascota.isEmpty()?"Quacky":capitalize(nombreMascota);
        String titulo=hambreNivel>=80?"🍖 ¡"+n+" tiene hambre!":"🐾 "+n+" escuchando...";
        String texto =hambreNivel>=80?"Abre la app para dar de comer 🥣":"Di \""+n+"\" o tócame para hablar 🎤";
        return new NotificationCompat.Builder(this,CHANNEL_ID)
            .setContentTitle(titulo).setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(piApp)
            .addAction(android.R.drawable.ic_btn_speak_now,"🎤 Hablar",piE)
            .setOngoing(true).build();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  VENTANA DEL ANIMAL
    // ─────────────────────────────────────────────────────────────────────────
    private void setupAnimalWindow(){int px=dp(DUCK_SIZE_DP);switch(animalTipo){case"cat":case"dog":{PetView pv=new PetView(this);int resId=getResources().getIdentifier(animalTipo,"drawable",getPackageName());if(resId!=0)try{Bitmap b=BitmapFactory.decodeResource(getResources(),resId);if(b!=null)pv.setAnimalBitmap(b);}catch(Exception ignored){}animalView=pv;break;}default:animalView=new DuckView(this);}animalParams=new WindowManager.LayoutParams(px,px,overlayType,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);animalParams.gravity=Gravity.TOP|Gravity.START;animalParams.x=(int)currentX;animalParams.y=(int)currentY;wm.addView(animalView,animalParams);animalView.setOnTouchListener(new View.OnTouchListener(){long ts;@Override public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN)ts=System.currentTimeMillis();if(e.getAction()==MotionEvent.ACTION_UP&&System.currentTimeMillis()-ts<400)startListening();return true;}});setupWalkAnimation();actualizarHambreEnAnimal();}
    private void setWalkState(boolean m,float p,float d){if(estaComiendo)return;if(animalView instanceof DuckView)((DuckView)animalView).setWalkState(m,p,d);else if(animalView instanceof PetView)((PetView)animalView).setWalkState(m,p,d);}
 
    private void setupBubbleWindow(){bubbleCard=new LinearLayout(this);bubbleCard.setOrientation(LinearLayout.VERTICAL);bubbleCard.setPadding(dp(12),dp(8),dp(12),dp(8));GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(14));bg.setColor(0xEE1A1A3A);bg.setStroke(dp(1),0xFFFFD700);bubbleCard.setBackground(bg);bubbleText=new TextView(this);bubbleText.setTextColor(Color.WHITE);bubbleText.setTextSize(13);bubbleText.setMaxWidth(dp(240));bubbleCard.addView(bubbleText);bubbleCard.setVisibility(View.GONE);bubbleParams=new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,overlayType,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);bubbleParams.gravity=Gravity.TOP|Gravity.START;bubbleParams.x=(int)currentX;bubbleParams.y=Math.max(0,(int)currentY-dp(85));wm.addView(bubbleCard,bubbleParams);}
    private void actualizarPosBurbuja(){DisplayMetrics dm=getResources().getDisplayMetrics();int bx=Math.max(dp(8),Math.min((int)currentX-dp(10),dm.widthPixels-dp(250)));int by=Math.max(dp(8),(int)currentY-dp(90));bubbleParams.x=bx;bubbleParams.y=by;try{if(bubbleCard.getVisibility()==View.VISIBLE)wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}}
 
    private void setupTTS(){tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS){configurarVoz(getSharedPreferences("quacky_prefs",MODE_PRIVATE).getString("voz","femenina"));ttsListo=true;}});}
    private void configurarVoz(String tipo){if(tts==null)return;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){Set<Voice>vs=tts.getVoices();if(vs!=null)for(Voice v:vs){if(v.getLocale()==null||!v.getLocale().getLanguage().equals("es"))continue;String n=v.getName().toLowerCase();if(tipo.equals("masculina")&&(n.contains("-m-")||n.contains("male")||n.contains("smb"))){tts.setVoice(v);return;}if(tipo.equals("femenina")&&(n.contains("-f-")||n.contains("female")||n.contains("sfb")||n.contains("esf"))){tts.setVoice(v);return;}}}tts.setLanguage(new Locale("es","MX"));tts.setPitch(tipo.equals("masculina")?0.78f:1.25f);tts.setSpeechRate(tipo.equals("masculina")?0.93f:1.05f);}
    private void hablar(String texto){if(!ttsListo||tts==null)return;String l=texto.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim();if(!l.isEmpty())tts.speak(l,TextToSpeech.QUEUE_FLUSH,null,"q_"+System.currentTimeMillis());}
 
    private void setupGyroscope(){sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);if(sensorManager==null)return;gyroSensor=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);if(gyroSensor!=null)sensorManager.registerListener(this,gyroSensor,SensorManager.SENSOR_DELAY_GAME);else{Sensor a=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(a!=null)sensorManager.registerListener(this,a,SensorManager.SENSOR_DELAY_GAME);}}
    @Override public void onSensorChanged(SensorEvent e){if(estaComiendo||animalMuerto)return;DisplayMetrics dm=getResources().getDisplayMetrics();int px=dp(DUCK_SIZE_DP);if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE){float rx=e.values[0],ry=e.values[1];if(Math.abs(rx)<0.05f)rx=0f;if(Math.abs(ry)<0.05f)ry=0f;gyroVelX+=ry*GYRO_FORCE;gyroVelY+=rx*GYRO_FORCE;targetX=Math.max(0,Math.min(targetX+gyroVelX,dm.widthPixels-px));targetY=Math.max(0,Math.min(targetY+gyroVelY,dm.heightPixels-px));gyroVelX*=0.85f;gyroVelY*=0.85f;float dx=targetX-currentX,dy=targetY-currentY;if(animalView instanceof DuckView)((DuckView)animalView).setMovementDirection(dx,dy);}else if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){float ax=-e.values[0],ay=e.values[1];if(Math.abs(ax)<0.3f)ax=0f;if(Math.abs(ay)<0.3f)ay=0f;targetX=Math.max(0,Math.min(targetX+ax*1.2f,dm.widthPixels-px));targetY=Math.max(0,Math.min(targetY-ay*1.2f,dm.heightPixels-px));float dx=targetX-currentX,dy=targetY-currentY;if(animalView instanceof DuckView)((DuckView)animalView).setMovementDirection(dx,dy);}}
    @Override public void onAccuracyChanged(Sensor s,int a){}
 
    private void setupFootprintOverlay(){DisplayMetrics dm=getResources().getDisplayMetrics();footprintOverlay=new View(this){private final Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path pt=new Path();@Override protected void onDraw(Canvas c){long now=System.currentTimeMillis();fp.setStyle(Paint.Style.FILL);synchronized(footprints){Iterator<Footprint>it=footprints.iterator();while(it.hasNext()){Footprint f=it.next();float age=(now-f.born)/(float)FOOTPRINT_LIFE;if(age>=1f){it.remove();continue;}fp.setColor(Color.argb((int)(140*(1f-age)),180,80,0));float s=dm.density*3.5f;c.save();c.translate(f.x,f.y);c.rotate(f.isLeft?-15f:15f);pt.reset();pt.moveTo(0,0);pt.lineTo(-s,s*.6f);pt.lineTo(-s*.4f,s*1.1f);pt.lineTo(s*.2f,s*.7f);pt.lineTo(s*.8f,s*1.1f);pt.lineTo(s*1.2f,s*.5f);pt.close();c.drawPath(pt,fp);c.restore();}}postInvalidateDelayed(60);}};footprintOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE,null);WindowManager.LayoutParams p=new WindowManager.LayoutParams(dm.widthPixels,dm.heightPixels,overlayType,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;wm.addView(footprintOverlay,p);}
 
    private void setupWalkAnimation(){walkAnim=new Runnable(){@Override public void run(){boolean m=isMoving()&&!estaComiendo;if(m){walkPhase+=0.05f;if(walkPhase>1f)walkPhase=0f;}setWalkState(m,walkPhase,facingRight?1f:-1f);walkHandler.postDelayed(this,25);}};walkHandler.post(walkAnim);}
    private boolean isMoving(){return Math.abs(targetX-currentX)>2||Math.abs(targetY-currentY)>2;}
    private void startMoveLoop(){moveRunnable=new Runnable(){@Override public void run(){if(!estaComiendo&&!animalMuerto){float dx=targetX-currentX,dy=targetY-currentY,dist=(float)Math.sqrt(dx*dx+dy*dy);if(dist>1.5f){float speed=Math.min(SPEED_BASE,Math.max(0.006f,dist/2000f));currentX+=dx*speed;currentY+=dy*speed;if(dx>0!=facingRight)facingRight=dx>0;if(animalView instanceof DuckView)((DuckView)animalView).setMovementDirection(dx,dy);animalParams.x=(int)currentX;animalParams.y=(int)currentY;try{wm.updateViewLayout(animalView,animalParams);}catch(Exception ignored){}if(wakeView!=null&&wakeViewParams!=null){wakeViewParams.x=(int)currentX;wakeViewParams.y=(int)currentY;try{wm.updateViewLayout(wakeView,wakeViewParams);}catch(Exception ignored){}}actualizarPosBurbuja();float mx=currentX-lastFootX,my=currentY-lastFootY,moved=(float)Math.sqrt(mx*mx+my*my);long now=System.currentTimeMillis();if(moved>dp(20)&&(now-lastFootprintTime)>400){lastFootprintTime=now;lastFootX=currentX;lastFootY=currentY;float ox=nextFootLeft?-dp(4):dp(4);synchronized(footprints){footprints.add(new Footprint(currentX+dp(DUCK_SIZE_DP)/2f+ox,currentY+dp(DUCK_SIZE_DP)-dp(4),now,nextFootLeft));}nextFootLeft=!nextFootLeft;}}}mainHandler.postDelayed(this,16);}};mainHandler.post(moveRunnable);}
 
    private void startRandomSounds(){quackRunnable=new Runnable(){@Override public void run(){if(!isListening&&!isTalking&&!estaComiendo)playAnimalSound();quackHandler.postDelayed(this,15000+rng.nextInt(30000));}};quackHandler.postDelayed(quackRunnable,8000+rng.nextInt(10000));}
    private void playAnimalSound(){switch(animalTipo){case"cat":playSoundCat();break;case"dog":playSoundDog();break;default:playSoundDuck();}}
    private void playSoundDuck(){new Thread(()->{try{int sr=44100,ms=320,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=700-350*p,a=p<0.08?p/0.08:Math.max(0,1-(p-0.08)/0.92);double m=a*0.65*Math.sin(2*Math.PI*f*t)+a*0.25*Math.sin(4*Math.PI*f*t);s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();mainHandler.post(()->{showBubble(new String[]{"¡Cuak!","¡Quack!","¡Cuaaak!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playSoundCat(){new Thread(()->{try{int sr=44100,ms=500,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=600+200*Math.sin(p*Math.PI),a=p<0.1?p/0.1:p>0.8?(1-p)/0.2:1.0;double m=a*(0.7*Math.sin(2*Math.PI*f*t)+0.2*Math.sin(4*Math.PI*f*t));s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();mainHandler.post(()->{showBubble(new String[]{"¡Miau!","Purrr...","¡Miiiau!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playSoundDog(){new Thread(()->{try{int sr=44100,ms=300,n=sr*ms/1000;short[]s=new short[n];for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n,f=350+100*Math.sin(p*Math.PI*3),a=p<0.05?p/0.05:p>0.7?(1-p)/0.3:1.0,noise=(rng.nextDouble()-0.5)*0.15;double m=a*(0.6*Math.sin(2*Math.PI*f*t)+0.25*Math.sin(4*Math.PI*f*t)+noise);s[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,m*Short.MAX_VALUE));}playRaw(s,sr,ms);}catch(Exception ignored){}}).start();mainHandler.post(()->{showBubble(new String[]{"¡Guau!","¡Woof!","¡Arf!"}[rng.nextInt(3)],1800,false);animarSalto();});}
    private void playRaw(short[]s,int sr,int d)throws Exception{AudioTrack t=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(s.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();t.write(s,0,s.length);t.play();Thread.sleep(d+80);t.stop();t.release();}
    private void animarSalto(){if(animalView!=null)animalView.animate().translationY(-10f).setDuration(130).withEndAction(()->animalView.animate().translationY(0).setDuration(130).start()).start();}
 
    // ═══════════════════════════════════════════════════════════════════════
    //  SISTEMA DE COMANDOS DE VOZ
    // ═══════════════════════════════════════════════════════════════════════
    private boolean handleVoiceCommand(String texto) {
        String lo = texto.toLowerCase().trim();
 
        // VIDEO (antes que música — "reproduce" aplica para ambos)
        if (lo.contains("netflix")||lo.contains("prime video")||lo.contains("amazon prime")
            ||lo.contains("disney")||lo.contains("hbo")||lo.contains("vix")
            ||lo.contains("paramount")||lo.contains("crunchyroll")||lo.contains("apple tv")
            ||(lo.contains("youtube")&&!lo.contains("music")&&!lo.contains("música"))
            ||(lo.contains("película")||lo.contains("serie")||lo.contains("documental"))) {
            abrirVideo(texto); return true;
        }
 
        // MÚSICA
        if (lo.contains("reproduce")||lo.contains("pon la canción")||lo.contains("quiero escuchar")
            ||lo.contains("ponme")||lo.contains("pon música")||lo.contains("pon musica")
            ||lo.contains("abre spotify")||lo.contains("abre tidal")||lo.contains("escucho")
            ||lo.contains("abre youtube music")||lo.contains("abre apple music")) {
            abrirMusica(texto); return true;
        }
 
        // ALARMA
        if (lo.contains("alarma")||lo.contains("despiértame")||lo.contains("despertador")
            ||lo.contains("pon alarma")||lo.contains("ponme alarma")) {
            parsearYPonerAlarma(texto); return true;
        }
 
        // CALENDARIO
        if (lo.contains("recuérdame")||lo.contains("recordame")||lo.contains("cumpleaños de")
            ||lo.contains("agenda")||lo.contains("anota")
            ||(lo.contains("agrega")&&!lo.contains("contacto"))) {
            parsearYAgregarCalendario(texto); return true;
        }
 
        // LLAMADA WHATSAPP
        if ((lo.contains("whatsapp")||lo.contains("wsp")||lo.contains("wasap"))
            &&(lo.contains("llama")||lo.contains("videollamada"))) {
            whatsappLlamar(contacto(lo,texto)); return true;
        }
 
        // MENSAJE WHATSAPP
        if (lo.contains("whatsapp")||lo.contains("wsp")||lo.contains("wasap")
            ||lo.contains("mándale")||lo.contains("mandale")) {
            whatsappMensaje(contacto(lo,texto), mensaje(lo,texto)); return true;
        }
 
        // SMS
        if ((lo.contains("manda")||lo.contains("envía")||lo.contains("envia"))
            &&(lo.contains("mensaje")||lo.contains("sms")||lo.contains("texto"))
            &&!lo.contains("whatsapp")) {
            enviarSMS(contacto(lo,texto), mensaje(lo,texto)); return true;
        }
 
        // LLAMADA NORMAL
        for (String p : new String[]{"llama a ","llámale a ","marcale a ","marca a ","llamar a "}) {
            if (lo.contains(p)&&!lo.contains("whatsapp")&&!lo.contains("wsp")) {
                llamar(limpiar(texto.substring(lo.indexOf(p)+p.length()))); return true;
            }
        }
 
        // ABRIR APP
        if (lo.startsWith("abre ")||lo.startsWith("abrir ")||lo.contains("abre la ")) {
            String app = lo.replace("abre la app de","").replace("abre la ","")
                .replace("abrir la ","").replace("abre ","").replace("abrir ","").trim();
            abrirApp(app); return true;
        }
 
        return false;
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  MÚSICA
    // ───────────────────────────────────────────────────────────────────────
    private void abrirMusica(String texto) {
        String lo = texto.toLowerCase();
        // 1. Detectar app
        String pkg, appNombre, uriBase;
        if      (lo.contains("tidal"))   { pkg="com.aspiro.tidal"; appNombre="Tidal"; uriBase=""; }
        else if (lo.contains("youtube music")||lo.contains("yt music")) { pkg="com.google.android.apps.youtube.music"; appNombre="YouTube Music"; uriBase=""; }
        else if (lo.contains("apple music")) { pkg="com.apple.android.music"; appNombre="Apple Music"; uriBase=""; }
        else if (lo.contains("deezer")) { pkg="deezer.android.app"; appNombre="Deezer"; uriBase=""; }
        else if (lo.contains("amazon music")) { pkg="com.amazon.mp3"; appNombre="Amazon Music"; uriBase=""; }
        else { pkg="com.spotify.music"; appNombre="Spotify"; uriBase="spotify:"; }
 
        // 2. Extraer canción
        String cancion = lo.replace("reproduce","").replace("pon la canción","")
            .replace("quiero escuchar","").replace("ponme","").replace("pon música de","")
            .replace("pon musica de","").replace("pon música","").replace("pon musica","")
            .replace("en spotify","").replace("spotify","").replace("en tidal","").replace("tidal","")
            .replace("en youtube music","").replace("youtube music","")
            .replace("en apple music","").replace("apple music","")
            .replace("en deezer","").replace("deezer","")
            .replace("en amazon music","").replace("amazon music","")
            .replace("música","").replace("musica","").replace("escucho","")
            .replace("por favor","").replaceAll("\\s+"," ").trim();
        if (cancion.length() < 2) cancion = null;
 
        showBubble(cancion!=null ? "🎵 Buscando \""+cancion+"\" en "+appNombre+"..." : "🎵 Abriendo "+appNombre+"...", 3000, true);
 
        // 3. URI scheme del app (más directo que package launch)
        if (pkg.equals("com.spotify.music")) {
            String sUri = cancion != null
                ? "spotify:search:" + Uri.encode(cancion)
                : "spotify:";
            if (lanzarUri(sUri)) return;
        }
        if (pkg.equals("com.google.android.apps.youtube.music") && cancion != null) {
            if (lanzarUri("https://music.youtube.com/search?q=" + Uri.encode(cancion))) return;
        }
 
        // 4. Abrir app directo por paquete (necesita QUERY_ALL_PACKAGES en manifest)
        if (lanzarPaquete(pkg)) return;
 
        // 5. Fallback: URL web (se abre en browser o en la app si tiene App Links)
        String url = pkg.equals("com.spotify.music")
            ? (cancion!=null ? "https://open.spotify.com/search/"+Uri.encode(cancion) : "https://open.spotify.com/")
            : "https://play.google.com/store/apps/details?id="+pkg;
        lanzarUrl(url);
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  VIDEO
    // ───────────────────────────────────────────────────────────────────────
    private void abrirVideo(String texto) {
        String lo = texto.toLowerCase();
 
        String pkg, appNombre;
        if      (lo.contains("netflix"))            { pkg="com.netflix.mediaclient";              appNombre="Netflix"; }
        else if (lo.contains("prime")||lo.contains("amazon")) { pkg="com.amazon.avod.thirdpartyclient"; appNombre="Prime Video"; }
        else if (lo.contains("disney"))             { pkg="com.disney.disneyplus";                appNombre="Disney+"; }
        else if (lo.contains("hbo")||lo.contains("max ")) { pkg="com.hbo.hbonow";                appNombre="Max/HBO"; }
        else if (lo.contains("vix"))                { pkg="tv.vixx.android";                      appNombre="Vix"; }
        else if (lo.contains("paramount"))          { pkg="com.cbs.app";                          appNombre="Paramount+"; }
        else if (lo.contains("crunchyroll"))        { pkg="com.crunchyroll.crunchyroid";          appNombre="Crunchyroll"; }
        else if (lo.contains("apple tv"))           { pkg="com.apple.atve.androidtv.appletv";    appNombre="Apple TV+"; }
        else                                        { pkg="com.google.android.youtube";           appNombre="YouTube"; }
 
        // Extraer título buscado
        String titulo = lo.replace("reproduce","").replace("pon la película","")
            .replace("quiero ver","").replace("ponme","").replace("pon","")
            .replace("la película","").replace("la serie","").replace("el documental","")
            .replace("en netflix","").replace("netflix","")
            .replace("en prime video","").replace("prime video","").replace("amazon prime","")
            .replace("en disney","").replace("disney plus","").replace("disney+","").replace("disney","")
            .replace("en hbo","").replace("hbo max","").replace("en max","")
            .replace("en vix","").replace("vix","")
            .replace("en youtube","").replace("youtube","")
            .replace("crunchyroll","").replace("paramount","")
            .replace("por favor","").replaceAll("\\s+"," ").trim();
        if (titulo.length() < 2) titulo = null;
 
        showBubble(titulo!=null ? "🎬 Buscando \""+titulo+"\" en "+appNombre+"..." : "📺 Abriendo "+appNombre+"...", 3000, true);
 
        // YouTube: URI scheme nativo para buscar
        if (pkg.equals("com.google.android.youtube")) {
            String q = titulo != null ? titulo : "";
            if (!q.isEmpty()) {
                if (lanzarUri("vnd.youtube://results?search_query=" + Uri.encode(q))) return;
                if (lanzarUri("youtube://results?search_query=" + Uri.encode(q))) return;
                if (lanzarUrl("https://www.youtube.com/results?search_query=" + Uri.encode(q))) return;
            } else {
                if (lanzarUri("vnd.youtube:")) return;
            }
        }
        // Netflix search deep link
        if (pkg.equals("com.netflix.mediaclient") && titulo != null) {
            if (lanzarUri("nflx://www.netflix.com/search?q=" + Uri.encode(titulo))) return;
        }
        // Disney+ search
        if (pkg.equals("com.disney.disneyplus") && titulo != null) {
            if (lanzarUri("disneyplus://search?q=" + Uri.encode(titulo))) return;
        }
 
        // Resto: abrir por paquete (necesita QUERY_ALL_PACKAGES)
        if (lanzarPaquete(pkg)) return;
 
        // Fallback: búsqueda web o Play Store
        String url = appNombre.equals("YouTube") && titulo!=null
            ? "https://www.youtube.com/results?search_query="+Uri.encode(titulo)
            : "https://play.google.com/store/apps/details?id="+pkg;
        if (!lanzarUrl(url)) {
            showBubble(appNombre+" no está instalado — te abro Play Store 📲", 4000, true);
        }
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  WHATSAPP — usa URI scheme "whatsapp://" que no necesita permiso extra
    // ───────────────────────────────────────────────────────────────────────
    private void whatsappMensaje(String nombre, String msg) {
        if (nombre.isEmpty()) { showBubble("¿A quién le mando el WhatsApp? 💬",3000,true); return; }
        showBubble("🔍 Buscando a "+nombre+"...", 0, true);
        new Thread(()->{
            String tel = buscarTelefono(nombre);
            mainHandler.post(()->{
                if (tel!=null) {
                    String num = tel.replaceAll("[^0-9]","");
                    if (!num.startsWith("52")&&num.length()==10) num="52"+num;
                    // whatsapp:// URI scheme → abre WhatsApp directamente SIN setPackage
                    String uri = "whatsapp://send?phone="+num+(msg.isEmpty()?"":"&text="+Uri.encode(msg));
                    showBubble(msg.isEmpty()
                        ? "💬 Abriendo chat de "+nombre+" en WhatsApp..."
                        : "💬 Enviando a "+nombre+": \""+msg+"\"", 5000, true);
                    if (!lanzarUri(uri)) {
                        // Fallback: wa.me URL
                        lanzarUrl("https://wa.me/"+num+(msg.isEmpty()?"":"?text="+Uri.encode(msg)));
                    }
                } else {
                    showBubble("No encontré a "+nombre+" en tus contactos 😔", 3500, true);
                }
            });
        }).start();
    }
 
    private void whatsappLlamar(String nombre) {
        if (nombre.isEmpty()) { showBubble("¿A quién llamo por WhatsApp? 📱",3000,true); return; }
        showBubble("🔍 Buscando a "+nombre+"...", 0, true);
        new Thread(()->{
            String tel = buscarTelefono(nombre);
            mainHandler.post(()->{
                if (tel!=null) {
                    String num = tel.replaceAll("[^0-9]","");
                    if (!num.startsWith("52")&&num.length()==10) num="52"+num;
                    showBubble("📱 Llamando a "+nombre+" por WhatsApp...", 4000, true);
                    // Intentar videollamada, si falla → abrir chat
                    if (!lanzarUri("whatsapp://call?phone="+num))
                        lanzarUrl("https://wa.me/"+num);
                } else {
                    showBubble("No encontré a "+nombre+" 😔", 3500, true);
                }
            });
        }).start();
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  SMS Y LLAMADA NORMAL
    // ───────────────────────────────────────────────────────────────────────
    private void enviarSMS(String nombre, String msg) {
        if (nombre.isEmpty()) { showBubble("¿A quién le mando el mensaje? ✉️",3000,true); return; }
        showBubble("🔍 Buscando a "+nombre+"...", 0, true);
        new Thread(()->{
            String tel = buscarTelefono(nombre);
            mainHandler.post(()->{
                if (tel!=null) {
                    showBubble("✉️ Abriendo mensaje para "+nombre+"...", 3000, true);
                    try {
                        Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"+tel));
                        if (!msg.isEmpty()) sms.putExtra("sms_body", msg);
                        sms.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(sms);
                    } catch (Exception e) {
                        showBubble("No pude abrir la app de mensajes 😔", 3000, true);
                    }
                } else { showBubble("No encontré a "+nombre+" 😔", 3500, true); }
            });
        }).start();
    }
 
    private void llamar(String nombre) {
        if (nombre.isEmpty()) { showBubble("¿A quién quieres llamar? 📞",3000,true); return; }
        showBubble("📞 Buscando a "+nombre+"...", 0, true);
        new Thread(()->{
            String tel = buscarTelefono(nombre);
            mainHandler.post(()->{
                if (tel!=null) {
                    showBubble("📞 Llamando a "+nombre+"...", 4000, true);
                    // Intentar ACTION_CALL, si falla → ACTION_DIAL (abre marcador)
                    try {
                        Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"+tel));
                        call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(call);
                    } catch (Exception e) {
                        try {
                            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+tel));
                            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(dial);
                        } catch (Exception e2) {
                            showBubble("No pude marcar — revisa permisos 📱", 3000, true);
                        }
                    }
                } else { showBubble("No encontré a "+nombre+" 😔", 3500, true); }
            });
        }).start();
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  ABRIR APP POR NOMBRE
    // ───────────────────────────────────────────────────────────────────────
    private void abrirApp(String nombre) {
        if (nombre.isEmpty()) return;
        Map<String,String> mapa = new HashMap<>();
        mapa.put("spotify","com.spotify.music"); mapa.put("whatsapp","com.whatsapp");
        mapa.put("instagram","com.instagram.android"); mapa.put("facebook","com.facebook.katana");
        mapa.put("twitter","com.twitter.android"); mapa.put("x","com.twitter.android");
        mapa.put("tiktok","com.zhiliaoapp.musically"); mapa.put("youtube","com.google.android.youtube");
        mapa.put("maps","com.google.android.apps.maps"); mapa.put("gmail","com.google.android.gm");
        mapa.put("chrome","com.android.chrome"); mapa.put("netflix","com.netflix.mediaclient");
        mapa.put("uber","com.ubercab"); mapa.put("tidal","com.aspiro.tidal");
        mapa.put("telegram","org.telegram.messenger"); mapa.put("snapchat","com.snapchat.android");
        mapa.put("zoom","us.zoom.videomeetings"); mapa.put("disney","com.disney.disneyplus");
        mapa.put("prime video","com.amazon.avod.thirdpartyclient");
        mapa.put("youtube music","com.google.android.apps.youtube.music");
        mapa.put("deezer","deezer.android.app"); mapa.put("amazon music","com.amazon.mp3");
 
        String buscar = nombre.toLowerCase().trim();
        String pkg = mapa.get(buscar);
 
        if (pkg != null && lanzarPaquete(pkg)) return;
 
        // Búsqueda dinámica entre apps instaladas
        try {
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo app : getPackageManager().queryIntentActivities(main,0)) {
                String label = app.loadLabel(getPackageManager()).toString().toLowerCase();
                if (label.contains(buscar)||buscar.contains(label)) {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.setComponent(new android.content.ComponentName(
                        app.activityInfo.packageName, app.activityInfo.name));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    showBubble("📱 Abriendo "+app.loadLabel(getPackageManager())+"...", 2000, true);
                    startActivity(i); return;
                }
            }
        } catch (Exception ignored) {}
        showBubble("No encontré \""+nombre+"\" 📱", 3000, true);
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  HELPERS — intents simplificados
    // ───────────────────────────────────────────────────────────────────────
    /** Lanza app por paquete. Devuelve true si tuvo éxito. */
    private boolean lanzarPaquete(String paquete) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(paquete);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; }
        } catch (Exception ignored) {}
        return false;
    }
 
    /** Lanza URI scheme (spotify:, whatsapp://, vnd.youtube://, etc). Devuelve true si tuvo éxito. */
    private boolean lanzarUri(String uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i); return true;
        } catch (Exception ignored) {}
        return false;
    }
 
    /** Lanza URL https. Devuelve true si tuvo éxito. */
    private boolean lanzarUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i); return true;
        } catch (Exception ignored) {}
        return false;
    }
 
    // ───────────────────────────────────────────────────────────────────────
    //  HELPERS — extracción de texto
    // ───────────────────────────────────────────────────────────────────────
    private String contacto(String lo, String texto) {
        String[] prefs = {"whatsapp a ","wsp a ","wasap a ","mándale un whatsapp a ",
            "manda whatsapp a ","mándale whatsapp a ","manda mensaje a ","envía mensaje a ",
            "manda un mensaje a ","envíale a ","llama a ","llámale a ","mensaje a "};
        for (String p : prefs) {
            if (lo.contains(p)) {
                int idx = lo.indexOf(p)+p.length();
                String n = texto.substring(idx).trim();
                for (String c : new String[]{" diciéndole"," diciendo"," que le"," con el mensaje",
                    " que diga"," un mensaje"," por whatsapp"," para decirle"}) {
                    int pos = n.toLowerCase().indexOf(c);
                    if (pos > 0) { n = n.substring(0,pos); break; }
                }
                return limpiar(n);
            }
        }
        return "";
    }
 
    private String mensaje(String lo, String texto) {
        for (String m : new String[]{"diciéndole ","que le diga ","con el mensaje ","diciendo ","que diga "}) {
            if (lo.contains(m)) return limpiar(texto.substring(lo.indexOf(m)+m.length()));
        }
        return "";
    }
 
    private String limpiar(String s) {
        return s.replace(" por favor","").replace(" porfa","").replace(" gracias","").trim();
    }
 
    private String buscarTelefono(String nombre) {
        try {
            android.database.Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                             ContactsContract.CommonDataKinds.Phone.NUMBER}, null,null,null);
            if (c==null) return null;
            String buscar=nombre.toLowerCase().trim(), mejor=null; int mejorP=0;
            while (c.moveToNext()) {
                String nc=c.getString(0), tel=c.getString(1);
                if (nc==null||tel==null) continue;
                String nl=nc.toLowerCase().trim();
                if (nl.equals(buscar)) { c.close(); return tel.replaceAll("[^+0-9]",""); }
                int p = nl.contains(buscar)?3 : buscar.contains(nl)?2 : nl.startsWith(buscar.split(" ")[0])?1:0;
                if (p>mejorP) { mejorP=p; mejor=tel.replaceAll("[^+0-9]",""); }
            }
            c.close(); return mejor;
        } catch (Exception e) { return null; }
    }
 
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ALARMA
    // ─────────────────────────────────────────────────────────────────────────
    private void parsearYPonerAlarma(String texto) {
        showBubble("Configurando alarma... ⏰", 0, true);
        String p = "Del texto extrae hora y minutos para alarma. Texto: " + texto
            + " Responde SOLO con JSON: {hora:NUMERO,minutos:NUMERO,etiqueta:TEXTO}"
            + " Reglas: tarde o noche suma 12 si hora menor de 12."
            + " y media = 30 minutos. al cuarto = 15 minutos.";
        askGroqJSON(p, json -> {
            try {
                int hora = json.getInt("hora");
                int min  = json.optInt("minutos", 0);
                String et = json.optString("etiqueta", "Quacky IA");
                Intent a = new Intent(AlarmClock.ACTION_SET_ALARM);
                a.putExtra(AlarmClock.EXTRA_HOUR,    hora);
                a.putExtra(AlarmClock.EXTRA_MINUTES, min);
                a.putExtra(AlarmClock.EXTRA_MESSAGE, et);
                a.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(a);
                showBubble("Alarma puesta a las " + hora + ":" + String.format("%02d", min) + " ⏰", 5000, true);
            } catch (Exception e) {
                showBubble("No entendi la hora, di: a las 7 de la manana ⏰", 4000, true);
            }
        });
    }
 
    private void parsearYAgregarCalendario(String texto) {
        showBubble("Agregando al calendario... 📅", 0, true);
        int anioActual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String p = "Del texto extrae datos para evento de calendario. Texto: " + texto
            + " Responde SOLO con JSON: {titulo:TEXTO,dia:NUMERO,mes:NUMERO,anio:NUMERO,hora:NUMERO,minutos:NUMERO}"
            + " Si no hay hora usa 9. Si no hay anio usa " + anioActual + "."
            + " Mes en numero (enero=1).";
        askGroqJSON(p, json -> {
            try {
                String tit = json.optString("titulo", "Evento");
                int d   = json.optInt("dia",    1);
                int mes = json.optInt("mes",     1);
                int ani = json.optInt("anio",    anioActual);
                int h   = json.optInt("hora",    9);
                int min = json.optInt("minutos", 0);
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(ani, mes - 1, d, h, min, 0);
                Intent ci = new Intent(Intent.ACTION_INSERT);
                ci.setData(CalendarContract.Events.CONTENT_URI);
                ci.putExtra(CalendarContract.Events.TITLE, tit);
                ci.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, c.getTimeInMillis());
                ci.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,   c.getTimeInMillis() + 3600000);
                ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(ci);
                showBubble("Guarde: " + tit + " el " + d + "/" + mes + " 📅", 5000, true);
            } catch (Exception e) {
                showBubble("No pude agregar, intenta con mas detalle 📅", 4000, true);
            }
        });
    }
 
    private void askGroq(String userMsg){isTalking=true;showBubble("💭 pensando...",0,false);if(animalView!=null)animalView.animate().rotation(10f).setDuration(100).withEndAction(()->animalView.animate().rotation(-10f).setDuration(100).withEndAction(()->animalView.animate().rotation(0f).setDuration(100).start()).start()).start();try{JSONObject um=new JSONObject();um.put("role","user");um.put("content",userMsg);chatHistory.add(um);}catch(JSONException ignored){}String n2=capitalize(nombreMascota.isEmpty()?"Quacky":nombreMascota);String emoji=animalTipo.equals("cat")?"🐱":animalTipo.equals("dog")?"🐶":"🐥";new Thread(()->{try{JSONArray msgs=new JSONArray();JSONObject sys=new JSONObject();sys.put("role","system");sys.put("content","Eres "+n2+" "+emoji+" mascota virtual. Hambre:"+hambreNivel+"/100 Felicidad:"+felicidad+"/100 Energía:"+energia+"/100. Puedes: llamar,WhatsApp,alarmas,calendario,apps,música. Respuestas CORTAS español mexicano.");msgs.put(sys);for(JSONObject mm:chatHistory)msgs.put(mm);JSONObject body=new JSONObject();body.put("model",GROQ_MODEL);body.put("max_tokens",300);body.put("messages",msgs);URL url=new URL(GROQ_URL);HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);conn.setDoOutput(true);conn.setConnectTimeout(15000);conn.setReadTimeout(30000);try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}String reply=new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();JSONObject am=new JSONObject();am.put("role","assistant");am.put("content",reply);chatHistory.add(am);while(chatHistory.size()>20)chatHistory.remove(0);mainHandler.post(()->{isTalking=false;showBubble(emoji+" "+reply,Math.max(4000,reply.length()*60),true);});}catch(Exception e){mainHandler.post(()->{isTalking=false;showBubble("Error de IA",4000,false);});}}).start();}
    interface GroqJSONCallback{void onResult(JSONObject j);}
    private void askGroqJSON(String prompt,GroqJSONCallback cb){new Thread(()->{try{JSONArray msgs=new JSONArray();JSONObject um=new JSONObject();um.put("role","user");um.put("content",prompt);msgs.put(um);JSONObject body=new JSONObject();body.put("model",GROQ_MODEL);body.put("max_tokens",200);body.put("messages",msgs);URL url=new URL(GROQ_URL);HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);conn.setDoOutput(true);conn.setConnectTimeout(15000);conn.setReadTimeout(20000);try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}String raw=new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim().replaceAll("```json","").replaceAll("```","").trim();JSONObject json=new JSONObject(raw);mainHandler.post(()->cb.onResult(json));}catch(Exception e){mainHandler.post(()->showBubble("No pude procesar",3000,true));}}).start();}
 
    private void showBubble(String text,int durationMs,boolean hablarEnVoz){mainHandler.post(()->{bubbleText.setText(text);actualizarPosBurbuja();bubbleCard.setVisibility(View.VISIBLE);bubbleCard.setAlpha(0f);bubbleCard.animate().alpha(1f).setDuration(200).start();try{wm.updateViewLayout(bubbleCard,bubbleParams);}catch(Exception ignored){}if(hablarEnVoz)hablar(text.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?áéíóúüñÁÉÍÓÚÜÑ¿¡\\-]"," ").replaceAll("\\s+"," ").trim());if(bubbleHider!=null)bubbleHandler.removeCallbacks(bubbleHider);if(durationMs>0){bubbleHider=()->bubbleCard.animate().alpha(0f).setDuration(300).withEndAction(()->bubbleCard.setVisibility(View.GONE)).start();bubbleHandler.postDelayed(bubbleHider,durationMs);}});}
    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Quacky IA",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);}}
    private String capitalize(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public IBinder onBind(Intent i){return null;}
 
    @Override
    public void onDestroy(){
        super.onDestroy();
        wakeActivo=false;
        destruirWakeSR();
        wakeRetryH.removeCallbacksAndMessages(null);
        if(wakeView!=null)try{wm.removeView(wakeView);}catch(Exception ignored){}
        hambreHandler.removeCallbacks(hambreTick);
        if(quejaRunnable!=null)quejaHandler.removeCallbacks(quejaRunnable);
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        if(animalView!=null)try{wm.removeView(animalView);}catch(Exception ignored){}
        if(bubbleCard!=null)try{wm.removeView(bubbleCard);}catch(Exception ignored){}
        if(footprintOverlay!=null)try{wm.removeView(footprintOverlay);}catch(Exception ignored){}
        if(voiceResultReceiver!=null)try{unregisterReceiver(voiceResultReceiver);}catch(Exception ignored){}
        if(escucharReceiver!=null)try{unregisterReceiver(escucharReceiver);}catch(Exception ignored){}
        if(alimentarReceiver!=null)try{unregisterReceiver(alimentarReceiver);}catch(Exception ignored){}
        if(tts!=null){tts.stop();tts.shutdown();}
        mainHandler.removeCallbacks(moveRunnable);
        walkHandler.removeCallbacks(walkAnim);
        quackHandler.removeCallbacks(quackRunnable);
    }
}
 
