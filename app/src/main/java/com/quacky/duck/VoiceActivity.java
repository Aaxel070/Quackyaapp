package com.quacky.duck;
 
import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.*;
import android.speech.RecognizerIntent;
import java.util.List;
 
/**
 * Pantalla invisible para escuchar comandos de voz.
 * Silencia el sonido de activación del micrófono para pasar desapercibida.
 */
public class VoiceActivity extends Activity {
 
    private static final int REQ = 1;
    private AudioManager audioManager;
    private int volumenOriginal = -1;
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        silenciarSonidoActivacion();
 
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué necesitas? 🐾");
            startActivityForResult(i, REQ);
        } catch (Exception e) {
            restaurarVolumen();
            enviar("");
            finish();
        }
    }
 
    /**
     * Silencia temporalmente el canal de notificaciones del sistema.
     * Esto evita el "ding" o sonido de activación del micrófono.
     */
    private void silenciarSonidoActivacion() {
        if (audioManager == null) return;
        try {
            // Guardar volumen actual del canal de sistema (donde suena el "ding")
            volumenOriginal = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
            // Bajar a 0 silenciosamente (sin hacer sonido al bajar)
            audioManager.setStreamVolume(
                AudioManager.STREAM_SYSTEM,
                0,
                0  // Sin flags = sin sonido al cambiar
            );
        } catch (Exception ignored) {}
    }
 
    /**
     * Restaura el volumen original después de que el micrófono se activó.
     * Esperamos un momento para que el "ding" de activación ya haya pasado.
     */
    private void restaurarVolumen() {
        if (audioManager == null || volumenOriginal < 0) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_SYSTEM,
                    volumenOriginal,
                    0  // Sin flags = sin sonido al restaurar
                );
            } catch (Exception ignored) {}
        }, 600); // Esperar 600ms — ya pasó el momento del "ding"
    }
 
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ) {
            restaurarVolumen();
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
 
    @Override
    protected void onDestroy() {
        super.onDestroy();
        restaurarVolumen();
    }
}
 
