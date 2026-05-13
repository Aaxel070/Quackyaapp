package com.quacky.duck;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.List;

public class VoiceActivity extends Activity {

    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Pantalla completamente transparente — el usuario no ve nada
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setContentView(new android.view.View(this));
        iniciarMicrofono();
    }

    private void iniciarMicrofono() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                String textoEscuchado = (matches != null && !matches.isEmpty())
                    ? matches.get(0) : "";
                enviarResultado(textoEscuchado, null);
                terminar();
            }

            @Override
            public void onError(int error) {
                enviarResultado(null, "error");
                terminar();
            }

            @Override public void onReadyForSpeech(Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int t, Bundle b) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void enviarResultado(String texto, String error) {
        Intent resultado = new Intent("com.quacky.duck.VOICE_RESULT");
        resultado.setPackage(getPackageName());
        if (texto != null) resultado.putExtra("voice_text", texto);
        if (error != null) resultado.putExtra("voice_error", error);
        sendBroadcast(resultado);
    }

    private void terminar() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
