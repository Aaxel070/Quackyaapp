package com.quacky.duck;
 
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import java.util.List;
 
/**
 * Activity permanente e invisible.
 * Se lanza UNA SOLA VEZ y nunca termina mientras el servicio esté activo.
 *
 * Flujo interno:
 *   1. Escucha silenciosamente con SpeechRecognizer (wake word loop)
 *   2. Si escucha el nombre → avisa al servicio → activa diálogo de comando
 *   3. Tras el comando → vuelve a wake word
 *   4. Si el servicio se destruye → termina
 *
 * Recibe intents con onNewIntent para cambiar de modo sin relanzarse.
 */
public class VoiceActivity extends Activity {
 
    private static final int REQ_COMANDO = 1;
 
    private SpeechRecognizer recognizer;
    private final Handler    handler    = new Handler(Looper.getMainLooper());
    private String           nombre     = "";
    private boolean          destruyendo = false;
    private boolean          enComando  = false;
 
    // Receptor para cuando el servicio quiere que escuchemos un comando
    // (el usuario tocó el animal directamente)
    private BroadcastReceiver activarComandoReceiver;
    // Receptor para cuando el servicio se destruye
    private BroadcastReceiver stopReceiver;
 
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
 
        // Pantalla 100% invisible y que no captura toques
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE   |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        );
 
        cargarNombre();
        registrarReceptores();
 
        // Si viene con modo="command" es porque el usuario tocó el animal
        String modo = getIntent() != null ? getIntent().getStringExtra("modo") : null;
        if ("command".equals(modo)) {
            escucharComando();
        } else {
            iniciarWakeLoop();
        }
    }
 
    // onNewIntent: recibe actualizaciones SIN relanzar la actividad
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String modo = intent != null ? intent.getStringExtra("modo") : null;
        if ("command".equals(modo) && !enComando) {
            pararRecognizer();
            escucharComando();
        } else if ("reload_name".equals(modo)) {
            cargarNombre();
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WAKE WORD LOOP — escucha silenciosa y continua
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarWakeLoop() {
        if (destruyendo || enComando) return;
 
        pararRecognizer();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
 
            @Override
            public void onResults(Bundle results) {
                if (destruyendo || enComando) return;
                List<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                boolean detectado = false;
                if (matches != null && !nombre.isEmpty()) {
                    for (String s : matches) {
                        if (s.toLowerCase().trim().contains(nombre)) {
                            detectado = true;
                            break;
                        }
                    }
                }
                if (detectado) {
                    // ¡Nombre escuchado! Avisar al servicio y escuchar comando
                    enviarBroadcast("com.quacky.duck.VOICE_WAKEWORD");
                    handler.postDelayed(() -> escucharComando(), 1600);
                } else {
                    // No fue el nombre → reiniciar escucha después de pausa corta
                    handler.postDelayed(() -> iniciarWakeLoop(), 600);
                }
            }
 
            @Override
            public void onError(int error) {
                if (destruyendo || enComando) return;
                // Timeout, sin audio, o error → simplemente reiniciar
                int delay = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                             error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 800 : 1500;
                handler.postDelayed(() -> iniciarWakeLoop(), delay);
            }
 
            // Métodos vacíos requeridos
            @Override public void onReadyForSpeech(Bundle p)    {}
            @Override public void onBeginningOfSpeech()         {}
            @Override public void onRmsChanged(float v)         {}
            @Override public void onBufferReceived(byte[] b)    {}
            @Override public void onEndOfSpeech()               {}
            @Override public void onPartialResults(Bundle b)    {}
            @Override public void onEvent(int t, Bundle b)      {}
        });
 
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        // Pausa de silencio razonablemente corta para no esperar demasiado
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,   2000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
 
        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            handler.postDelayed(() -> iniciarWakeLoop(), 2000);
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  MODO COMANDO — diálogo de Google para reconocimiento completo
    // ─────────────────────────────────────────────────────────────────────────
    private void escucharComando() {
        if (destruyendo) return;
        enComando = true;
        pararRecognizer();
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué necesitas? 🐾");
            startActivityForResult(i, REQ_COMANDO);
        } catch (Exception e) {
            enviarResultado("");
            volverAWake();
        }
    }
 
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_COMANDO) {
            String texto = "";
            if (res == RESULT_OK && data != null) {
                List<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) texto = r.get(0);
            }
            enviarResultado(texto);
            volverAWake();
        }
    }
 
    private void volverAWake() {
        enComando = false;
        // Pequeña pausa antes de volver a escuchar el nombre
        handler.postDelayed(() -> iniciarWakeLoop(), 2500);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Receptores de broadcast
    // ─────────────────────────────────────────────────────────────────────────
    private void registrarReceptores() {
        // El servicio manda este broadcast cuando el usuario TOCA el animal
        activarComandoReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                if (!enComando) { pararRecognizer(); escucharComando(); }
            }
        };
        registrar(activarComandoReceiver, "com.quacky.duck.ACTIVAR_COMANDO");
 
        // El servicio manda este broadcast al destruirse
        stopReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) { terminar(); }
        };
        registrar(stopReceiver, "com.quacky.duck.STOP_VOICE");
    }
 
    private void registrar(BroadcastReceiver r, String accion) {
        IntentFilter f = new IntentFilter(accion);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarNombre() {
        nombre = getSharedPreferences("quacky_prefs", MODE_PRIVATE)
            .getString("nombre_mascota", "").toLowerCase().trim();
    }
 
    private void pararRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }
 
    private void enviarBroadcast(String accion) {
        Intent r = new Intent(accion);
        r.setPackage(getPackageName());
        sendBroadcast(r);
    }
 
    private void enviarResultado(String texto) {
        Intent r = new Intent("com.quacky.duck.VOICE_RESULT");
        r.setPackage(getPackageName());
        r.putExtra("voice_text", texto);
        sendBroadcast(r);
    }
 
    private void terminar() {
        destruyendo = true;
        handler.removeCallbacksAndMessages(null);
        pararRecognizer();
        try { unregisterReceiver(activarComandoReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(stopReceiver); }          catch (Exception ignored) {}
        finish();
    }
 
    @Override
    protected void onDestroy() {
        super.onDestroy();
        destruyendo = true;
        handler.removeCallbacksAndMessages(null);
        pararRecognizer();
        try { unregisterReceiver(activarComandoReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(stopReceiver); }          catch (Exception ignored) {}
    }
}
 
