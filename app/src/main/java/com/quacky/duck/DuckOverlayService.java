package com.quacky.duck;
 
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
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
 
    // ── Constantes ────────────────────────────────────────────────────────────
    private static final String CHANNEL_ID  = "quacky_channel";
 
    // ✅ GROQ — IA gratis con Llama 3 (muy buena)
    // Pon aquí tu key de console.groq.com (empieza con gsk_...)
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL   = "llama-3.3-70b-versatile";
 
    // Pato pequeño: 18dp
    private static final int   DUCK_SIZE_DP = 18;
    private static final float SPEED_BASE   = 0.011f;
    private static final float GYRO_FORCE   = 18f;
 
    // ── Views ─────────────────────────────────────────────────────────────────
    private WindowManager  wm;
    private View           rootView;
    private DuckView       duckView;
    private TextView       bubbleText;
    private View           bubbleCard;
 
    // ── Estado ────────────────────────────────────────────────────────────────
    private WindowManager.LayoutParams params;
    private float   targetX, targetY;
    private float   currentX, currentY;
    private boolean isTalking   = false;
    private boolean isListening = false;
    private boolean facingRight = true;
    private Handler mainHandler;
    private Runnable moveRunnable;
 
    // ── Walk animation ────────────────────────────────────────────────────────
    private Handler  walkHandler = new Handler(Looper.getMainLooper());
    private float    walkPhase   = 0f;
    private Runnable walkAnim;
 
    // ── Huellas ───────────────────────────────────────────────────────────────
    private static class Footprint {
        float x, y; long born; boolean isLeft;
        Footprint(float x, float y, long born, boolean left) {
            this.x = x; this.y = y; this.born = born; this.isLeft = left;
        }
    }
    private final List<Footprint> footprints     = new ArrayList<>();
    private View                  footprintOverlay;
    private static final long     FOOTPRINT_LIFE = 1800;
 
    // ── Sonidos ───────────────────────────────────────────────────────────────
    private Handler  quackHandler  = new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private final Random rng = new Random();
 
    // ── Voice Result Receiver ─────────────────────────────────────────────────
    private BroadcastReceiver voiceResultReceiver;
 
    // ── Historial conversación ────────────────────────────────────────────────
    private final List<JSONObject> chatHistory = new ArrayList<>();
 
    // ── Huella timing ─────────────────────────────────────────────────────────
    private long    lastFootprintTime = 0;
    private boolean nextFootLeft      = true;
    private float   lastFootX = -999, lastFootY = -999;
 
    // ── Bubble ────────────────────────────────────────────────────────────────
    private Handler  bubbleHandler = new Handler(Looper.getMainLooper());
    private Runnable bubbleHider;
 
    // ── Giroscopio ───────────────────────────────────────────────────────────
    private SensorManager  sensorManager;
    private Sensor         gyroSensor;
    private volatile float gyroVelX = 0f;
    private volatile float gyroVelY = 0f;
 
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
 
        setupFootprintOverlay();
        setupOverlay();
        setupSpeechRecognizer();
        setupGyroscope();
        startMoveLoop();
        startRandomQuacks();
 
        mainHandler.postDelayed(
            () -> showBubble("¡Quack! 🐥 ¡Tócame para hablar, inclina el cel!", 5000),
            1200);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GIROSCOPIO
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGyroscope() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) return;
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null)
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
    }
 
    @Override
    public void onSensorChanged(SensorEvent event) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int duckPx = dp2px(DUCK_SIZE_DP);
 
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float rotX = event.values[0];
            float rotY = event.values[1];
            if (Math.abs(rotX) < 0.05f) rotX = 0f;
            if (Math.abs(rotY) < 0.05f) rotY = 0f;
            gyroVelX += rotY * GYRO_FORCE;
            gyroVelY += rotX * GYRO_FORCE;
            targetX = Math.max(0, Math.min(targetX + gyroVelX, dm.widthPixels  - duckPx));
            targetY = Math.max(0, Math.min(targetY + gyroVelY, dm.heightPixels - duckPx));
            gyroVelX *= 0.85f;
            gyroVelY *= 0.85f;
 
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float ax = -event.values[0];
            float ay =  event.values[1];
            if (Math.abs(ax) < 0.3f) ax = 0f;
            if (Math.abs(ay) < 0.3f) ay = 0f;
            targetX = Math.max(0, Math.min(targetX + ax * 1.2f, dm.widthPixels  - duckPx));
            targetY = Math.max(0, Math.min(targetY - ay * 1.2f, dm.heightPixels - duckPx));
        }
    }
 
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  FOOTPRINT OVERLAY
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFootprintOverlay() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
 
        footprintOverlay = new View(this) {
            private final Paint fp       = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path  footPath = new Path();
            @Override protected void onDraw(Canvas canvas) {
                long now = System.currentTimeMillis();
                fp.setStyle(Paint.Style.FILL);
                synchronized (footprints) {
                    Iterator<Footprint> it = footprints.iterator();
                    while (it.hasNext()) {
                        Footprint f = it.next();
                        float age = (now - f.born) / (float) FOOTPRINT_LIFE;
                        if (age >= 1f) { it.remove(); continue; }
                        int alpha = (int)(140 * (1f - age));
                        fp.setColor(Color.argb(alpha, 180, 80, 0));
                        float scale = dm.density * 3.5f;
                        canvas.save();
                        canvas.translate(f.x, f.y);
                        canvas.rotate(f.isLeft ? -15f : 15f);
                        footPath.reset();
                        footPath.moveTo(0, 0);
                        footPath.lineTo(-scale, scale * 0.6f);
                        footPath.lineTo(-scale * 0.4f, scale * 1.1f);
                        footPath.lineTo(scale * 0.2f, scale * 0.7f);
                        footPath.lineTo(scale * 0.8f, scale * 1.1f);
                        footPath.lineTo(scale * 1.2f, scale * 0.5f);
                        footPath.close();
                        canvas.drawPath(footPath, fp);
                        canvas.restore();
                    }
                }
                postInvalidateDelayed(60);
            }
        };
        footprintOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
 
        WindowManager.LayoutParams fpParams = new WindowManager.LayoutParams(
            dm.widthPixels, dm.heightPixels, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        fpParams.gravity = Gravity.TOP | Gravity.START;
        wm.addView(footprintOverlay, fpParams);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  OVERLAY DEL PATO — solo el pato recibe toques, las apps funcionan normal
    // ─────────────────────────────────────────────────────────────────────────
    private void setupOverlay() {
        LayoutInflater inflater = LayoutInflater.from(this);
        rootView   = inflater.inflate(R.layout.overlay_duck, null);
        duckView   = rootView.findViewById(R.id.duck_image);
        bubbleCard = rootView.findViewById(R.id.bubble_card);
        bubbleText = rootView.findViewById(R.id.bubble_text);
 
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int duckPx = dp2px(DUCK_SIZE_DP);
 
        currentX = dm.widthPixels / 2f - duckPx / 2f;
        currentY = dm.heightPixels * 0.72f;
        targetX  = currentX;
        targetY  = currentY;
 
        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
 
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (int) currentX;
        params.y = (int) currentY;
        wm.addView(rootView, params);
 
        duckView.setOnTouchListener(new View.OnTouchListener() {
            long touchStart;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN)
                    touchStart = System.currentTimeMillis();
                if (e.getAction() == MotionEvent.ACTION_UP
                        && System.currentTimeMillis() - touchStart < 400)
                    startListening();
                return true;
            }
        });
 
        bubbleCard.setVisibility(View.GONE);
        setupWalkAnimation();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  WALK ANIMATION
    // ─────────────────────────────────────────────────────────────────────────
    private void setupWalkAnimation() {
        walkAnim = new Runnable() {
            @Override public void run() {
                boolean moving = isMoving();
                if (moving) { walkPhase += 0.03f; if (walkPhase > 1f) walkPhase = 0f; }
                duckView.setWalkState(moving, walkPhase, facingRight ? 1f : -1f);
                walkHandler.postDelayed(this, 30);
            }
        };
        walkHandler.post(walkAnim);
    }
 
    private boolean isMoving() {
        return Math.abs(targetX - currentX) > 2 || Math.abs(targetY - currentY) > 2;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  LOOP DE MOVIMIENTO
    // ─────────────────────────────────────────────────────────────────────────
    private void startMoveLoop() {
        moveRunnable = new Runnable() {
            @Override public void run() {
                float dx   = targetX - currentX;
                float dy   = targetY - currentY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 1.5f) {
                    float speed = Math.min(SPEED_BASE, Math.max(0.006f, dist / 2000f));
                    currentX += dx * speed;
                    currentY += dy * speed;
                    if (dx > 0 != facingRight) facingRight = dx > 0;
                    params.x = (int) currentX;
                    params.y = (int) currentY;
                    try { wm.updateViewLayout(rootView, params); } catch (Exception ignored) {}
 
                    float mx = currentX - lastFootX, my = currentY - lastFootY;
                    float moved = (float) Math.sqrt(mx * mx + my * my);
                    long  now   = System.currentTimeMillis();
                    if (moved > dp2px(20) && (now - lastFootprintTime) > 400) {
                        lastFootprintTime = now;
                        lastFootX = currentX; lastFootY = currentY;
                        int   duckPx = dp2px(DUCK_SIZE_DP);
                        float offX   = nextFootLeft ? -dp2px(4) : dp2px(4);
                        synchronized (footprints) {
                            footprints.add(new Footprint(
                                currentX + duckPx / 2f + offX,
                                currentY + duckPx - dp2px(4), now, nextFootLeft));
                        }
                        nextFootLeft = !nextFootLeft;
                    }
                }
                mainHandler.postDelayed(this, 16);
            }
        };
        mainHandler.post(moveRunnable);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  QUACKS ALEATORIOS — sonido de pato sintetizado
    // ─────────────────────────────────────────────────────────────────────────
    private void startRandomQuacks() {
        quackRunnable = new Runnable() {
            @Override public void run() {
                if (!isListening && !isTalking) playQuack();
                quackHandler.postDelayed(this, 15000 + rng.nextInt(30000));
            }
        };
        quackHandler.postDelayed(quackRunnable, 8000 + rng.nextInt(10000));
    }
 
    private void playQuack() {
        new Thread(() -> {
            try {
                int     sampleRate = 44100;
                int     duracionMs = 320;
                int     numSamples = sampleRate * duracionMs / 1000;
                short[] muestras   = new short[numSamples];
 
                for (int i = 0; i < numSamples; i++) {
                    double t        = (double) i / sampleRate;
                    double progreso = (double) i / numSamples;
                    double freq     = 700.0 - 350.0 * progreso;
                    double amp;
                    if (progreso < 0.08) amp = progreso / 0.08;
                    else                 amp = Math.max(0, 1.0 - (progreso - 0.08) / 0.92);
 
                    double muestra  = amp * 0.65 * Math.sin(2 * Math.PI * freq * t);
                    muestra        += amp * 0.25 * Math.sin(2 * Math.PI * freq * 2.0 * t);
                    muestra        += amp * 0.08 * Math.sin(2 * Math.PI * freq * 3.0 * t);
                    muestra        += amp * 0.04 * Math.sin(2 * Math.PI * 12 * t)
                                          * Math.sin(2 * Math.PI * freq * t);
 
                    muestras[i] = (short) Math.max(Short.MIN_VALUE,
                                         Math.min(Short.MAX_VALUE, muestra * Short.MAX_VALUE));
                }
 
                AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
 
                track.write(muestras, 0, numSamples);
                track.play();
                Thread.sleep(duracionMs + 80);
                track.stop();
                track.release();
            } catch (Exception ignored) {}
        }).start();
 
        mainHandler.post(() -> {
            String[] frases = {"¡Cuak!", "¡Quack!", "¡Cuaaak!", "🦆 Quack!", "¡Cuak cuak!"};
            showBubble(frases[rng.nextInt(frases.length)], 1800);
            duckView.animate().translationY(-8f).setDuration(130)
                .withEndAction(() -> duckView.animate().translationY(0).setDuration(130).start()).start();
        });
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SPEECH RECEIVER
    // ─────────────────────────────────────────────────────────────────────────
    private void setupSpeechRecognizer() {
        voiceResultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String texto = intent.getStringExtra("voice_text");
                if (texto != null && !texto.isEmpty()) {
                    mainHandler.post(() -> {
                        isListening = false;
                        if (!handleVoiceCommand(texto)) {
                            showBubble("Tú: \"" + texto + "\"", 2500);
                            askGroq(texto);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        isListening = false;
                        showBubble("No te escuché 🦆 ¡Inténtalo!", 3000);
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.quacky.duck.VOICE_RESULT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(voiceResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(voiceResultReceiver, filter);
        }
    }
 
    private void startListening() {
        if (isTalking || isListening) return;
        isListening = true;
        showBubble("...te escucho 👂", 0);
        animateBounce(duckView);
        Intent intent = new Intent(DuckOverlayService.this, VoiceActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  COMANDOS DE VOZ — llamadas a contactos
    // ─────────────────────────────────────────────────────────────────────────
    private boolean handleVoiceCommand(String texto) {
        String lower = texto.toLowerCase().trim();
        String[] patrones = {
            "marcale a ", "llama a ", "llámale a ", "llamar a ",
            "marcar a ", "hablar con ", "marca a "
        };
        for (String patron : patrones) {
            if (lower.contains(patron)) {
                int idx = lower.indexOf(patron) + patron.length();
                String nombre = texto.substring(idx).trim()
                    .replace(" por favor", "").replace(" porfavor", "").trim();
                buscarYLlamar(nombre);
                return true;
            }
        }
        return false;
    }
 
    private void buscarYLlamar(String nombre) {
        showBubble("🔍 Buscando a " + nombre + "...", 0);
        new Thread(() -> {
            String telefono = buscarTelefonoContacto(nombre);
            mainHandler.post(() -> {
                if (telefono != null) {
                    showBubble("📞 Llamando a " + nombre + "... ¡Quack!", 3000);
                    try {
                        Intent llamada = new Intent(Intent.ACTION_CALL);
                        llamada.setData(Uri.parse("tel:" + telefono));
                        llamada.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(llamada);
                    } catch (SecurityException e) {
                        showBubble("⚠️ Ve a Ajustes > Apps > Quacky > Permisos y activa Teléfono", 5000);
                    } catch (Exception e) {
                        showBubble("¡Quack! No pude hacer la llamada 😬", 3000);
                    }
                } else {
                    showBubble("🦆 No encontré \"" + nombre + "\" en tus contactos", 3500);
                }
            });
        }).start();
    }
 
    private String buscarTelefonoContacto(String nombre) {
        try {
            String[] columnas = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            };
            android.database.Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                columnas, null, null, null);
            if (cursor == null) return null;
 
            String buscar = nombre.toLowerCase().trim();
            String mejorTelefono = null;
            int mejorPuntaje = 0;
 
            while (cursor.moveToNext()) {
                String nombreC  = cursor.getString(0);
                String telefono = cursor.getString(1);
                if (nombreC == null || telefono == null) continue;
                String cLower = nombreC.toLowerCase().trim();
                if (cLower.equals(buscar)) {
                    cursor.close();
                    return telefono.replaceAll("[^+0-9]", "");
                }
                int puntaje = cLower.contains(buscar) ? 2 : buscar.contains(cLower) ? 1 : 0;
                if (puntaje > mejorPuntaje) {
                    mejorPuntaje  = puntaje;
                    mejorTelefono = telefono.replaceAll("[^+0-9]", "");
                }
            }
            cursor.close();
            return mejorTelefono;
        } catch (Exception e) { return null; }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  GROQ API — Llama 3, gratis, muy rápido
    //
    //  Formato de petición (igual que OpenAI):
    //  POST https://api.groq.com/openai/v1/chat/completions
    //  Header: Authorization: Bearer gsk_...
    //  Body:   { "model": "...", "messages": [...], "max_tokens": 1000 }
    //
    //  Formato de respuesta:
    //  { "choices": [ { "message": { "content": "..." } } ] }
    // ─────────────────────────────────────────────────────────────────────────
    private void askGroq(String userMessage) {
        isTalking = true;
        showBubble("💭 pensando...", 0);
        animateWiggle(duckView);
 
        try {
            JSONObject um = new JSONObject();
            um.put("role", "user");
            um.put("content", userMessage);
            chatHistory.add(um);
        } catch (JSONException ignored) {}
 
        new Thread(() -> {
            try {
                // Armar la lista de mensajes con el sistema al inicio
                JSONArray messages = new JSONArray();
 
                // Mensaje de sistema (personalidad del pato)
                JSONObject sistema = new JSONObject();
                sistema.put("role", "system");
                sistema.put("content",
                    "Eres Quacky, un patito amarillo IA que vive flotando en la pantalla del " +
                    "teléfono de tu dueño como asistente personal. " +
                    "Puedes hacer llamadas a contactos cuando te dicen 'llama a [nombre]'. " +
                    "Eres muy simpático, curioso, divertido y cariñoso. " +
                    "Usas '¡Quack!' de vez en cuando. " +
                    "Tus respuestas son CORTAS (máximo 2-3 oraciones) y siempre en español mexicano. " +
                    "Si te piden algo que no puedes, díselo con humor. " +
                    "¡Eres el mejor pato asistente IA del mundo!");
                messages.put(sistema);
 
                // Historial de conversación
                for (JSONObject m : chatHistory) messages.put(m);
 
                // Armar el cuerpo de la petición
                JSONObject body = new JSONObject();
                body.put("model", GROQ_MODEL);
                body.put("max_tokens", 1000);
                body.put("messages", messages);
 
                // Conectar con Groq
                URL url = new URL(GROQ_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
 
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
 
                // Leer respuesta
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
 
                // Groq responde con: choices[0].message.content
                JSONObject resp  = new JSONObject(sb.toString());
                String     reply = resp.getJSONArray("choices")
                                       .getJSONObject(0)
                                       .getJSONObject("message")
                                       .getString("content")
                                       .trim();
 
                // Guardar en historial
                JSONObject am = new JSONObject();
                am.put("role", "assistant");
                am.put("content", reply);
                chatHistory.add(am);
                while (chatHistory.size() > 20) chatHistory.remove(0);
 
                mainHandler.post(() -> {
                    isTalking = false;
                    showBubble("🦆 " + reply, Math.max(4000, reply.length() * 60));
                });
 
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isTalking = false;
                    // Mostrar el error real para poder diagnosticarlo
                    showBubble("¡Quack! Error: " + e.getMessage(), 5000);
                });
            }
        }).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  BUBBLE
    // ─────────────────────────────────────────────────────────────────────────
    private void showBubble(String text, int durationMs) {
        mainHandler.post(() -> {
            bubbleText.setText(text);
            bubbleCard.setVisibility(View.VISIBLE);
            bubbleCard.setAlpha(0f);
            bubbleCard.animate().alpha(1f).setDuration(200).start();
            if (bubbleHider != null) bubbleHandler.removeCallbacks(bubbleHider);
            if (durationMs > 0) {
                bubbleHider = () -> bubbleCard.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> bubbleCard.setVisibility(View.GONE)).start();
                bubbleHandler.postDelayed(bubbleHider, durationMs);
            }
        });
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ANIMATIONS
    // ─────────────────────────────────────────────────────────────────────────
    private void animateBounce(View v) {
        v.animate().translationY(-14f).setDuration(180)
            .withEndAction(() -> v.animate().translationY(0).setDuration(180).start()).start();
    }
    private void animateWiggle(View v) {
        v.animate().rotation(10f).setDuration(100)
            .withEndAction(() -> v.animate().rotation(-10f).setDuration(100)
                .withEndAction(() -> v.animate().rotation(0f).setDuration(100).start()).start()).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  NOTIFICATION
    // ─────────────────────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Quacky Pato IA", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Quacky flotando sobre tus apps");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
 
    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐥 Quacky está vivo")
            .setContentText("Tócame · habla conmigo 🎤")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi).setOngoing(true).build();
    }
 
    private int dp2px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
 
    @Override public IBinder onBind(Intent i) { return null; }
 
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null)       sensorManager.unregisterListener(this);
        if (rootView != null)            try { wm.removeView(rootView); }         catch (Exception ignored) {}
        if (footprintOverlay != null)    try { wm.removeView(footprintOverlay); } catch (Exception ignored) {}
        if (voiceResultReceiver != null) unregisterReceiver(voiceResultReceiver);
        mainHandler.removeCallbacks(moveRunnable);
        walkHandler.removeCallbacks(walkAnim);
        quackHandler.removeCallbacks(quackRunnable);
    }
}
 
