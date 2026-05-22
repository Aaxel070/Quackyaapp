package com.quacky.duck;
 
import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.*;
import android.speech.RecognizerIntent;
import java.util.List;
 
/**
 * Solo se usa para el diálogo de COMANDO (Google Speech).
 * El wake word (nombre) lo maneja DuckOverlayService directamente.
 */
public class VoiceActivity extends Activity {
 
    private static final int REQ = 1;
    private AudioManager audioMgr;
    private int volOriginal = -1;
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioMgr = (AudioManager) getSystemService(AUDIO_SERVICE);
        silenciar();
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué necesitas? 🐾");
            startActivityForResult(i, REQ);
        } catch (Exception e) {
            restaurar(); enviar(""); finish();
        }
    }
 
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ) {
            restaurar();
            String texto = "";
            if (res == RESULT_OK && data != null) {
                List<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) texto = r.get(0);
            }
            enviar(texto);
            finish();
        }
    }
 
    private void enviar(String texto) {
        Intent r = new Intent("com.quacky.duck.VOICE_RESULT");
        r.setPackage(getPackageName());
        r.putExtra("voice_text", texto);
        sendBroadcast(r);
    }
 
    private void silenciar() {
        if (audioMgr == null) return;
        try { volOriginal = audioMgr.getStreamVolume(AudioManager.STREAM_SYSTEM); audioMgr.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0); } catch (Exception ignored) {}
    }
    private void restaurar() {
        if (audioMgr == null || volOriginal < 0) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> { try { audioMgr.setStreamVolume(AudioManager.STREAM_SYSTEM, volOriginal, 0); } catch (Exception ignored) {} }, 600);
    }
}
 
