package com.quacky.duck;
 
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.speech.*;
import android.view.*;
import java.util.List;
 
/**
 * Dos modos:
 *  "wake"    → escucha silenciosa con SpeechRecognizer directo.
 *              Si escucha el nombre → manda broadcast VOICE_WAKEWORD → termina.
 *              Si no escucha nada  → manda broadcast VOICE_IDLE     → termina.
 *
 *  "command" → diálogo de Google para reconocer comandos completos.
 *              Manda broadcast VOICE_RESULT con el texto.
 */
public class VoiceActivity extends Activity {
 
    private static final int REQ_SPEECH = 1;
 
    private SpeechRecognizer wakeRecognizer;
    private final Handler    handler = new Handler(Looper.getMainLooper());
    private String           nombreMascota = "";
    private boolean          terminando    = false;
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
 
        // Pantalla completamente invisible + no captura toques
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        );
 
        SharedPreferences prefs = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        nombreMascota = prefs.getString("nombre_mascota", "").toLowerCase().trim();
 
        String modo = getIntent().getStringExtra("modo");
        if ("wake".equals(modo)) {
            escucharWakeWord();
        } else {
            escucharComando();
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  MODO WAKE — escucha silenciosa para detectar el nombre de la mascota
    // ─────────────────────────────────────────────────────────────────────────
    private void escucharWakeWord() {
        if (nombreMascota.isEmpty()) {
            // Sin nombre configurado → solo enviar IDLE
            enviarBroadcast("com.quacky.duck.VOICE_IDLE", null);
            finish();
            return;
        }
 
        wakeRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        wakeRecognizer.setRecognitionListener(new RecognitionListener() {
 
            @Override
            public void onResults(Bundle results) {
                if (terminando) return;
                List<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                boolean oyo = false;
                if (matches != null) {
                    for (String s : matches) {
                        if (s.toLowerCase().trim().contains(nombreMascota)) {
                            oyo = true;
                            break;
                        }
                    }
                }
                if (oyo) {
                    enviarBroadcast("com.quacky.duck.VOICE_WAKEWORD", null);
                } else {
                    enviarBroadcast("com.quacky.duck.VOICE_IDLE", null);
                }
                terminar();
            }
 
            @Override
            public void onError(int error) {
                if (terminando) return;
                // Tiempo de espera agotado u otro error → reiniciar loop
                enviarBroadcast("com.quacky.duck.VOICE_IDLE", null);
                terminar();
            }
 
            // Métodos vacíos requeridos por la interfaz
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,        "es-MX");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,      5);
        // Silencio breve para no esperar demasiado
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,   1500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
 
        try {
            wakeRecognizer.startListening(intent);
        } catch (Exception e) {
            enviarBroadcast("com.quacky.duck.VOICE_IDLE", null);
            terminar();
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  MODO COMANDO — diálogo de Google para reconocimiento completo
    // ─────────────────────────────────────────────────────────────────────────
    private void escucharComando() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué necesitas? 🐾");
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            enviarResultado("");
            finish();
        }
    }
 
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH) {
            String texto = "";
            if (resultCode == RESULT_OK && data != null) {
                List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) texto = results.get(0);
            }
            enviarResultado(texto);
            finish();
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void enviarResultado(String texto) {
        Intent r = new Intent("com.quacky.duck.VOICE_RESULT");
        r.setPackage(getPackageName());
        r.putExtra("voice_text", texto);
        sendBroadcast(r);
    }
 
    private void enviarBroadcast(String accion, String extra) {
        Intent r = new Intent(accion);
        r.setPackage(getPackageName());
        if (extra != null) r.putExtra("texto", extra);
        sendBroadcast(r);
    }
 
    private void terminar() {
        terminando = true;
        handler.post(this::finish);
    }
 
    @Override
    protected void onDestroy() {
        super.onDestroy();
        terminando = true;
        handler.removeCallbacksAndMessages(null);
        if (wakeRecognizer != null) {
            try { wakeRecognizer.cancel();  } catch (Exception ignored) {}
            try { wakeRecognizer.destroy(); } catch (Exception ignored) {}
            wakeRecognizer = null;
        }
    }
}
 
