package com.quacky.duck;
 
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import java.util.List;
 
// Pantalla invisible que usa el reconocimiento de voz del sistema (Google).
// Es mucho más confiable que el SpeechRecognizer directo.
public class VoiceActivity extends Activity {
 
    private static final int PETICION_VOZ = 1;
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Lanzar el reconocedor de voz del sistema (Google)
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX"); // Español México
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Quacky 🦆");
            startActivityForResult(intent, PETICION_VOZ);
        } catch (Exception e) {
            // Si no hay reconocedor instalado, avisar al pato
            enviarResultado("");
            finish();
        }
    }
 
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PETICION_VOZ) {
            String texto = "";
            if (resultCode == RESULT_OK && data != null) {
                List<String> resultados = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
                if (resultados != null && !resultados.isEmpty()) {
                    texto = resultados.get(0);
                }
            }
            enviarResultado(texto);
        }
        finish();
    }
 
    private void enviarResultado(String texto) {
        Intent resultado = new Intent("com.quacky.duck.VOICE_RESULT");
        resultado.setPackage(getPackageName());
        resultado.putExtra("voice_text", texto);
        sendBroadcast(resultado);
    }
}
 
