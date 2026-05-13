package com.quacky.duck;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.media.*;
import android.os.*;
import android.speech.*;
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
    private static final String CHANNEL_ID   = "quacky_channel";
    private static final String CLAUDE_URL   = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL = "claude-sonnet-4-20250514";
    private static final String API_KEY      = "TU_API_KEY_AQUI";

    // Pato muy pequeño: 28dp (mitad de 55)
    private static final int   DUCK_SIZE_DP = 28;

    // Velocidad muy lenta (mitad de 0.022 = 0.011)
    private static final float SPEED_BASE   = 0.011f;

    // Fuerza del giroscopio sobre el movimiento del pato
    private static final float GYRO_FORCE   = 18f;

    // ── Views ─────────────────────────────────────────────────────────────────
    private WindowManager  wm;
    private View           rootView;
    private DuckView       duckView;
    private TextView       bubbleText;
    private View           bubbleCard;

    // ── Estado ────────────────────────────────────────────────────────────────
    private WindowManager.LayoutParams params;
    private float  targetX, targetY;
    private float  currentX, currentY;
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
            this.x=x; this.y=y; this.born=born; this.isLeft=left;
        }
    }
    private final List<Footprint> footprints    = new ArrayList<>();
    private View                  footprintOverlay;
    private static final long     FOOTPRINT_LIFE = 1800;

    // ── Sonidos ───────────────────────────────────────────────────────────────
    private Handler  quackHandler  = new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private final Random rng = new Random();

    // ── Speech ────────────────────────────────────────────────────────────────
    private SpeechRecognizer speechRecognizer;

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
    private SensorManager sensorManager;
    private Sensor        gyroSensor;
    // Velocidad acumulada por giroscopio (px/frame)
    private volatile float gyroVelX = 0f;
    private volatile float gyroVelY = 0f;
    // Última vez que llegó un evento de giroscopio
    private long lastGyroTime = 0;

    // ── Touch overlay (pantalla completa, NO bloquea apps) ────────────────────
    private View                       touchOverlay;
    private WindowManager.LayoutParams touchOverlayParams;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        setupFootprintOverlay();
        setupTouchOverlay();   // capa táctil primero (abajo)
        setupOverlay();        // pato encima
        setupSpeechRecognizer();
        setupGyroscope();
        startMoveLoop();
        startRandomQuacks();

        mainHandler.postDelayed(
            () -> showBubble("¡Quack! 🐥 ¡Toca la pantalla o inclina el cel!", 5000),
            1200);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TOUCH OVERLAY — pantalla completa, SIN bloquear interacción
    //
    //  Técnica definitiva:
    //  - Tamaño = pantalla completa
    //  - FLAG_NOT_FOCUSABLE: no roba el foco del teclado
    //  - FLAG_NOT_TOUCH_MODAL: los toques FUERA de la vista pasan a las apps
    //  - Sin FLAG_NOT_TOUCHABLE: esta vista SÍ recibe toques (para mover el pato)
    //  - setOnTouchListener retorna FALSE en todos los casos →
    //    el sistema sigue enviando el toque a la app debajo también
    // ─────────────────────────────────────────────────────────────────────────
    private void setupTouchOverlay() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        touchOverlay = new View(this);
        touchOverlay.setBackgroundColor(Color.TRANSPARENT);

        touchOverlayParams = new WindowManager.LayoutParams(
            dm.widthPixels,
            dm.heightPixels,
            overlayType,
            // FLAG_NOT_FOCUSABLE: no roba foco
            // FLAG_NOT_TOUCH_MODAL: toques fuera de la vista pasan a las apps
            // SIN FLAG_NOT_TOUCHABLE: puede recibir eventos táctiles
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        touchOverlayParams.gravity = Gravity.TOP | Gravity.START;
        touchOverlayParams.x = 0;
        touchOverlayParams.y = 0;
        // Alpha = 0: completamente invisible, pero aún recibe toques
        touchOverlayParams.alpha = 0f;

        touchOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                DisplayMetrics d = getResources().getDisplayMetrics();
                int duckPx = dp2px(DUCK_SIZE_DP);
                float nx = event.getRawX() - duckPx / 2f;
                float ny = event.getRawY() - duckPx / 2f;
                nx = Math.max(0, Math.min(nx, d.widthPixels  - duckPx));
                ny = Math.max(0, Math.min(ny, d.heightPixels - duckPx));
                targetX = nx;
                targetY = ny;
                // Apagar temporalmente el giroscopio para que el toque mande
                gyroVelX = 0f;
                gyroVelY = 0f;
            }
            // IMPORTANTE: retornar false → el toque pasa también a la app debajo
            return false;
        });

        wm.addView(touchOverlay, touchOverlayParams);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GIROSCOPIO
    //  Detecta la inclinación del teléfono y desplaza el target del pato
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGyroscope() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyroSensor != null) {
                sensorManager.registerListener(this, gyroSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            } else {
                // Fallback: usar acelerómetro si no hay giroscopio
                Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accel != null) {
                    sensorManager.registerListener(this, accel,
                        SensorManager.SENSOR_DELAY_GAME);
                }
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int duckPx = dp2px(DUCK_SIZE_DP);

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // values[0] = rotación X (cabeceo, arriba-abajo)
            // values[1] = rotación Y (balanceo, izq-der)
            float rotX = event.values[0]; // inclinar arriba/abajo → mover Y
            float rotY = event.values[1]; // inclinar izq/der → mover X

            // Filtrar ruido pequeño (dead zone de 0.05 rad/s)
            if (Math.abs(rotX) < 0.05f) rotX = 0f;
            if (Math.abs(rotY) < 0.05f) rotY = 0f;

            // Acumular velocidad (el giroscopio da velocidad angular, lo
            // convertimos en desplazamiento del target)
            gyroVelX += rotY * GYRO_FORCE;   // inclinación lateral → X
            gyroVelY += rotX * GYRO_FORCE;   // inclinación adelante → Y

            // Aplicar a target con límites de pantalla
            float nx = targetX + gyroVelX;
            float ny = targetY + gyroVelY;
            nx = Math.max(0, Math.min(nx, dm.widthPixels  - duckPx));
            ny = Math.max(0, Math.min(ny, dm.heightPixels - duckPx));
            targetX = nx;
            targetY = ny;

            // Amortiguación: la velocidad se frena sola
            gyroVelX *= 0.85f;
            gyroVelY *= 0.85f;

        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Fallback para dispositivos sin giroscopio
            // values[0] = X (inclinación lateral), values[1] = Y (inclinación frontal)
            float ax = -event.values[0]; // invertir para que izquierda = izquierda
            float ay =  event.values[1];

            if (Math.abs(ax) < 0.3f) ax = 0f;
            if (Math.abs(ay) < 0.3f) ay = 0f;

            float nx = targetX + ax * 1.2f;
            float ny = targetY - ay * 1.2f;
            nx = Math.max(0, Math.min(nx, dm.widthPixels  - duckPx));
            ny = Math.max(0, Math.min(ny, dm.heightPixels - duckPx));
            targetX = nx;
            targetY = ny;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
    //  OVERLAY DEL PATO
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
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (int) currentX;
        params.y = (int) currentY;
        wm.addView(rootView, params);

        // Tap en el pato = micrófono
        duckView.setOnTouchListener(new View.OnTouchListener() {
            long touchStart;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    touchStart = System.currentTimeMillis();
                }
                if (e.getAction() == MotionEvent.ACTION_UP
                        && System.currentTimeMillis() - touchStart < 400) {
                    startListening();
                }
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
    //  LOOP DE MOVIMIENTO — ease-out muy lento y natural
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

                    // Huellas cada ~40px
                    float mx    = currentX - lastFootX;
                    float my    = currentY - lastFootY;
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
                                currentY + duckPx - dp2px(4),
                                now, nextFootLeft));
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
    //  RANDOM QUACKS
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
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 180);
            mainHandler.postDelayed(tg::release, 400);
            String[] quacks = {"¡Cuak!", "¡Quack!", "¡Cuaaak!", "🦆 Quack!", "¡Cuak cuak!"};
            showBubble(quacks[rng.nextInt(quacks.length)], 1800);
            duckView.animate().translationY(-8f).setDuration(130)
                .withEndAction(() -> duckView.animate().translationY(0).setDuration(130).start()).start();
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEECH RECOGNITION — tap al pato abre el micrófono
    // ─────────────────────────────────────────────────────────────────────────
    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {
                mainHandler.post(() -> { isListening = true; showBubble("...te escucho 👂", 0); animateBounce(duckView); });
            }
            @Override public void onResults(Bundle results) {
                List<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (m != null && !m.isEmpty()) {
                    String t = m.get(0);
                    mainHandler.post(() -> { isListening = false; showBubble("Tú: \"" + t + "\"", 2500); askClaude(t); });
                }
            }
            @Override public void onError(int error) {
                mainHandler.post(() -> {
                    isListening = false;
                    showBubble(error == SpeechRecognizer.ERROR_NO_MATCH
                        ? "No te escuché 🦆 ¡Inténtalo!" : "¡Quack! Error de mic 😬", 3000);
                });
            }
            @Override public void onEndOfSpeech()       { isListening = false; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int t, Bundle b) {}
        });
    }

    private void startListening() {
        if (isTalking || isListening) return;
        duckView.animate().scaleX(facingRight ? 1.15f : -1.15f).scaleY(1.15f).setDuration(100)
            .withEndAction(() -> duckView.animate()
                .scaleX(facingRight ? 1f : -1f).scaleY(1f).setDuration(100).start()).start();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CLAUDE API
    // ─────────────────────────────────────────────────────────────────────────
    private void askClaude(String userMessage) {
        isTalking = true;
        showBubble("💭 pensando...", 0);
        animateWiggle(duckView);
        try {
            JSONObject um = new JSONObject();
            um.put("role", "user"); um.put("content", userMessage);
            chatHistory.add(um);
        } catch (JSONException ignored) {}

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", CLAUDE_MODEL);
                body.put("max_tokens", 1000);
                body.put("system",
                    "Eres Quacky, un patito amarillo muy simpático, curioso y divertido que " +
                    "vive en la pantalla de tu dueño. Usas '¡Quack!' de vez en cuando, " +
                    "eres entusiasta, gracioso y cariñoso. Tus respuestas son CORTAS " +
                    "(máximo 2-3 oraciones) y en español. ¡Eres el mejor pato AI del mundo!");
                JSONArray msgs = new JSONArray();
                for (JSONObject m : chatHistory) msgs.put(m);
                body.put("messages", msgs);

                URL url = new URL(CLAUDE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-api-key", API_KEY);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject resp  = new JSONObject(sb.toString());
                String     reply = resp.getJSONArray("content").getJSONObject(0).getString("text").trim();
                JSONObject am = new JSONObject();
                am.put("role", "assistant"); am.put("content", reply);
                chatHistory.add(am);
                while (chatHistory.size() > 20) chatHistory.remove(0);
                mainHandler.post(() -> {
                    isTalking = false;
                    showBubble("🦆 " + reply, Math.max(4000, reply.length() * 60));
                });
            } catch (Exception e) {
                mainHandler.post(() -> { isTalking = false; showBubble("¡Quack! Sin internet 😵", 4000); });
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
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (rootView != null)         try { wm.removeView(rootView); }         catch (Exception ignored) {}
        if (footprintOverlay != null) try { wm.removeView(footprintOverlay); } catch (Exception ignored) {}
        if (touchOverlay != null)     try { wm.removeView(touchOverlay); }     catch (Exception ignored) {}
        if (speechRecognizer != null) speechRecognizer.destroy();
        mainHandler.removeCallbacks(moveRunnable);
        walkHandler.removeCallbacks(walkAnim);
        quackHandler.removeCallbacks(quackRunnable);
    }
}
