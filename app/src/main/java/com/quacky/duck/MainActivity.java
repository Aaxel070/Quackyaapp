package com.quacky.duck;
 
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.animation.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
 
public class MainActivity extends Activity {
 
    private static final int PERM_REQUEST    = 101;
    private static final int OVERLAY_REQUEST = 102;
 
    // Colores del tema
    private static final int C_FONDO   = 0xFF0F0F23;
    private static final int C_CARD    = 0xFF1A1A3A;
    private static final int C_SEL     = 0xFF2A2A5A;
    private static final int C_ORO     = 0xFFFFD700;
    private static final int C_BLANCO  = 0xFFFFFFFF;
    private static final int C_GRIS    = 0xFF888888;
    private static final int C_VERDE   = 0xFF4CAF50;
    private static final int C_ROJO    = 0xFFF44336;
    private static final int C_NARANJA = 0xFFFF9800;
    private static final int C_AZUL    = 0xFF5C9BD4;
    private static final int C_ROSA    = 0xFFE57BB2;
    private static final int C_MORADO  = 0xFF9B59B6;
 
    private static final String[][] ANIMALES = {
        {"duck","🦆","Pato","si"},{"cat","🐱","Gato","si"},{"dog","🐶","Perro","si"},
        {"frog","🐸","Rana","no"},{"hamster","🐹","Hámster","no"},{"fox","🦊","Zorro","no"},
    };
 
    private String       animal     = "duck";
    private String       voz        = "femenina";
    private String       nombre     = "";
    private LinearLayout cardAnimal = null;
    private LinearLayout cardVozM, cardVozF;
    private LinearLayout secPermisos;
    private Button       btnActivar;
    private EditText     etNombre;
    private TextView     tvNombrePreview;
 
    // Página de comida
    private TextView     tvNivelHambre;
    private ProgressBar  pbHambre;
    private TextView     tvEstadoHambre;
    private TextView     tvEmojiAnimal;
    private TextView     tvUltimaComida;
    private Button       btnComer;
    private Handler      uiHandler = new Handler(Looper.getMainLooper());
    private Runnable     actualizarHambreRunnable;
 
    // Puntos indicadores de página
    private TextView[] puntoPager = new TextView[2];
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(C_FONDO);
 
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        animal = p.getString("animal", "duck");
        voz    = p.getString("voz", "femenina");
        nombre = p.getString("nombre_mascota", "");
 
        construirUI();
    }
 
    @Override
    protected void onResume() {
        super.onResume();
        if (secPermisos != null) actualizarPermisos();
        iniciarActualizacionHambre();
    }
 
    @Override
    protected void onPause() {
        super.onPause();
        if (actualizarHambreRunnable != null) uiHandler.removeCallbacks(actualizarHambreRunnable);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  UI PRINCIPAL con ViewPager (deslizar izquierda/derecha)
    // ─────────────────────────────────────────────────────────────────────────
    private void construirUI() {
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(C_FONDO);
 
        // ── ViewPager ──────────────────────────────────────────────────────
        ViewPager pager = new ViewPager(this);
        pager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return 2; }
            @Override public boolean isViewFromObject(View v, Object o) { return v == o; }
            @Override public Object instantiateItem(ViewGroup container, int position) {
                View page = position == 0 ? paginaConfig() : paginaComida();
                container.addView(page);
                return page;
            }
            @Override public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
            @Override public CharSequence getPageTitle(int pos) {
                return pos == 0 ? "⚙️ Config" : "🍖 Alimentar";
            }
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int pos) { actualizarPuntos(pos); }
        });
 
        // ── Puntos indicadores de página ───────────────────────────────────
        LinearLayout puntos = new LinearLayout(this);
        puntos.setOrientation(LinearLayout.HORIZONTAL);
        puntos.setGravity(Gravity.CENTER);
        for (int i = 0; i < 2; i++) {
            puntoPager[i] = new TextView(this);
            puntoPager[i].setText("●");
            puntoPager[i].setTextSize(10);
            puntoPager[i].setPadding(dp(6), 0, dp(6), 0);
            puntoPager[i].setTextColor(i == 0 ? C_ORO : C_GRIS);
            puntos.addView(puntoPager[i]);
        }
 
        // ── Labels de página ───────────────────────────────────────────────
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        labels.setGravity(Gravity.CENTER);
        labels.setBackgroundColor(0xFF0A0A1A);
        labels.setPadding(0, dp(4), 0, dp(4));
 
        TextView lConfig = new TextView(this);
        lConfig.setText("⚙️  Configurar");
        lConfig.setTextColor(C_ORO); lConfig.setTextSize(13);
        lConfig.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labels.addView(lConfig, lp);
 
        View sep = new View(this); sep.setBackgroundColor(0xFF2A2A4A);
        labels.addView(sep, new LinearLayout.LayoutParams(dp(1), dp(20)));
 
        TextView lComida = new TextView(this);
        lComida.setText("🍖  Alimentar");
        lComida.setTextColor(C_GRIS); lComida.setTextSize(13);
        lComida.setGravity(Gravity.CENTER);
        labels.addView(lComida, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
 
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int pos) {
                lConfig.setTextColor(pos == 0 ? C_ORO : C_GRIS);
                lComida.setTextColor(pos == 1 ? C_ORO : C_GRIS);
            }
        });
 
        // ── Layout completo ────────────────────────────────────────────────
        LinearLayout todo = new LinearLayout(this);
        todo.setOrientation(LinearLayout.VERTICAL);
        todo.setBackgroundColor(C_FONDO);
 
        todo.addView(labels, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
 
        // Hint de desliz (solo primera vez)
        TextView hint = new TextView(this);
        hint.setText("← desliza para alimentar →");
        hint.setTextColor(0xFF555577); hint.setTextSize(11); hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(2), 0, dp(2));
        todo.addView(hint);
 
        todo.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
 
        LinearLayout.LayoutParams lpPuntos = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        lpPuntos.setMargins(0, dp(4), 0, dp(8));
        todo.addView(puntos, lpPuntos);
 
        raiz.addView(todo);
        setContentView(raiz);
    }
 
    private void actualizarPuntos(int pagActual) {
        for (int i = 0; i < puntoPager.length; i++)
            puntoPager[i].setTextColor(i == pagActual ? C_ORO : C_GRIS);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  PÁGINA 1 — Configuración (código existente)
    // ─────────────────────────────────────────────────────────────────────────
    private ScrollView paginaConfig() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_FONDO); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
 
        root.addView(encabezado());
        root.addView(divisor(), margen(0,0,0,20));
        root.addView(etiqueta("✏️  Nombre de tu mascota"), margen(0,0,0,12));
        root.addView(seccionNombre(), margen(0,0,0,24));
        root.addView(etiqueta("🐾  Elige tu compañero"), margen(0,0,0,12));
        root.addView(gridAnimales(), margen(0,0,0,24));
        root.addView(etiqueta("🔊  Voz del asistente"), margen(0,0,0,12));
        root.addView(seccionVoz(), margen(0,0,0,24));
        root.addView(etiqueta("🔑  Permisos necesarios"), margen(0,0,0,12));
        secPermisos = seccionPermisos();
        root.addView(secPermisos, margen(0,0,0,24));
        btnActivar = botonActivar();
        root.addView(btnActivar, margen(0,0,0,12));
 
        TextView nota = new TextView(this);
        nota.setText("Desliza a la derecha ▶ para dar de comer a tu mascota 🍖");
        nota.setTextColor(C_GRIS); nota.setTextSize(12); nota.setGravity(Gravity.CENTER);
        root.addView(nota);
 
        scroll.addView(root); return scroll;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  PÁGINA 2 — Alimentar a tu mascota 🍖
    // ─────────────────────────────────────────────────────────────────────────
    private ScrollView paginaComida() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_FONDO); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
 
        // ── Título ────────────────────────────────────────────────────────
        TextView titulo = new TextView(this);
        titulo.setText("🍖 Alimenta a tu mascota");
        titulo.setTextSize(22); titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(C_ORO); titulo.setGravity(Gravity.CENTER);
        root.addView(titulo, margen(0,0,0,8));
 
        TextView subtitulo = new TextView(this);
        String n = nombre.isEmpty() ? "tu mascota" : capitalize(nombre);
        subtitulo.setText("Mantén a " + n + " bien alimentada 😋");
        subtitulo.setTextSize(13); subtitulo.setTextColor(C_GRIS); subtitulo.setGravity(Gravity.CENTER);
        root.addView(subtitulo, margen(0,0,0,24));
 
        // ── Tarjeta de hambre ─────────────────────────────────────────────
        LinearLayout cardHambre = new LinearLayout(this);
        cardHambre.setOrientation(LinearLayout.VERTICAL);
        cardHambre.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setCornerRadius(dp(20)); bgCard.setColor(C_CARD); bgCard.setStroke(dp(1), 0xFF2A2A5A);
        cardHambre.setBackground(bgCard);
 
        // Emoji grande del animal
        tvEmojiAnimal = new TextView(this);
        tvEmojiAnimal.setText(emojiAnimal());
        tvEmojiAnimal.setTextSize(56); tvEmojiAnimal.setGravity(Gravity.CENTER);
        cardHambre.addView(tvEmojiAnimal, margen(0,0,0,12));
 
        // Nivel de hambre texto
        tvNivelHambre = new TextView(this);
        tvNivelHambre.setTextSize(16); tvNivelHambre.setTypeface(null, Typeface.BOLD);
        tvNivelHambre.setGravity(Gravity.CENTER);
        cardHambre.addView(tvNivelHambre, margen(0,0,0,8));
 
        // Barra de hambre
        pbHambre = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbHambre.setMax(100);
        pbHambre.setProgressDrawable(crearDrawableBarraHambre());
        cardHambre.addView(pbHambre, margen(0,0,0,10));
 
        // Estado de hambre
        tvEstadoHambre = new TextView(this);
        tvEstadoHambre.setTextSize(14); tvEstadoHambre.setGravity(Gravity.CENTER);
        cardHambre.addView(tvEstadoHambre, margen(0,0,0,6));
 
        // Última comida
        tvUltimaComida = new TextView(this);
        tvUltimaComida.setTextSize(12); tvUltimaComida.setTextColor(C_GRIS); tvUltimaComida.setGravity(Gravity.CENTER);
        cardHambre.addView(tvUltimaComida);
 
        root.addView(cardHambre, margen(0,0,0,24));
 
        // ── Botón DAR DE COMER ────────────────────────────────────────────
        btnComer = new Button(this);
        btnComer.setText("🥣  ¡Dar de comer!");
        btnComer.setTextSize(18); btnComer.setTypeface(null, Typeface.BOLD);
        btnComer.setTextColor(C_FONDO); btnComer.setAllCaps(false);
        GradientDrawable bgBtn = new GradientDrawable();
        bgBtn.setCornerRadius(dp(50)); bgBtn.setColor(C_ORO);
        btnComer.setBackground(bgBtn);
        btnComer.setOnClickListener(v -> darDeCorner());
        root.addView(btnComer, margen(0,0,0,16));
 
        // ── Info de la comida ─────────────────────────────────────────────
        LinearLayout cardInfo = new LinearLayout(this);
        cardInfo.setOrientation(LinearLayout.VERTICAL);
        cardInfo.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bgInfo = new GradientDrawable();
        bgInfo.setCornerRadius(dp(14)); bgInfo.setColor(0xFF0F0F2A); bgInfo.setStroke(dp(1), 0xFF222244);
        cardInfo.setBackground(bgInfo);
 
        String[] tips = {
            "💡 La mascota tiene hambre cada 30 minutos",
            "🔔 Te avisará cuando tenga mucha hambre",
            "🎵 Escucha su reacción al comer 😋"
        };
        for (String tip : tips) {
            TextView tv = new TextView(this);
            tv.setText(tip); tv.setTextColor(C_GRIS); tv.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(4));
            cardInfo.addView(tv, lp);
        }
        root.addView(cardInfo);
 
        scroll.addView(root);
        actualizarDisplayHambre(); // primer dibujo
        return scroll;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  SISTEMA DE COMIDA
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarActualizacionHambre() {
        actualizarHambreRunnable = new Runnable() {
            @Override public void run() {
                actualizarDisplayHambre();
                uiHandler.postDelayed(this, 10000); // actualizar cada 10s
            }
        };
        uiHandler.post(actualizarHambreRunnable);
    }
 
    private void actualizarDisplayHambre() {
        if (tvNivelHambre == null) return;
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        long ultimaComida = p.getLong("ultima_comida", System.currentTimeMillis());
        long minSin = (System.currentTimeMillis() - ultimaComida) / 60000L;
        int hambre = (int) Math.min(100, minSin * 100 / 30);
 
        // Barra de hambre
        pbHambre.setProgress(hambre);
        pbHambre.setProgressDrawable(crearDrawableBarraHambre(hambre));
 
        // Texto nivel
        tvNivelHambre.setText("Hambre: " + hambre + "%");
        if (hambre >= 80) tvNivelHambre.setTextColor(C_ROJO);
        else if (hambre >= 50) tvNivelHambre.setTextColor(C_NARANJA);
        else tvNivelHambre.setTextColor(C_VERDE);
 
        // Estado
        String estado; int colorEstado;
        if (hambre >= 100) { estado = "😵 ¡Se está muriendo de hambre! ¡Dale de comer!"; colorEstado = C_ROJO; }
        else if (hambre >= 80) { estado = "😋 ¡Tiene mucha hambre! ¡Aliméntala ya!"; colorEstado = C_ROJO; }
        else if (hambre >= 50) { estado = "🤤 Empieza a tener un poco de hambre"; colorEstado = C_NARANJA; }
        else if (hambre >= 20) { estado = "😊 Está bien, pero pronto tendrá hambre"; colorEstado = C_VERDE; }
        else { estado = "😊 ¡Bien alimentada y feliz!"; colorEstado = C_VERDE; }
        tvEstadoHambre.setText(estado); tvEstadoHambre.setTextColor(colorEstado);
 
        // Tiempo desde última comida
        if (minSin < 1) tvUltimaComida.setText("Comió hace menos de 1 minuto");
        else if (minSin == 1) tvUltimaComida.setText("Comió hace 1 minuto");
        else if (minSin < 60) tvUltimaComida.setText("Comió hace " + minSin + " minutos");
        else tvUltimaComida.setText("Comió hace " + (minSin/60) + " hora" + (minSin/60>1?"s":""));
 
        // Emoji del animal (actualizar si cambió)
        if (tvEmojiAnimal != null) tvEmojiAnimal.setText(emojiAnimal());
 
        // Botón: deshabilitar si está en animación
        if (btnComer != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(50));
            bg.setColor(hambre > 0 ? C_ORO : 0xFF4CAF50);
            btnComer.setBackground(bg);
            btnComer.setText(hambre >= 80 ? "🥣  ¡Dale de comer ahora!" : "🥣  Dar de comer");
        }
    }
 
    private void darDeCorner() {
        // Animación del botón
        Animation anim = new ScaleAnimation(1f,0.9f,1f,0.9f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);
        anim.setDuration(100); anim.setRepeatMode(Animation.REVERSE); anim.setRepeatCount(1);
        btnComer.startAnimation(anim);
 
        // Guardar tiempo de comida
        long ahora = System.currentTimeMillis();
        getSharedPreferences("quacky_prefs", MODE_PRIVATE).edit()
            .putLong("ultima_comida", ahora).apply();
 
        // Avisar al servicio
        Intent alimento = new Intent("com.quacky.duck.ALIMENTAR");
        alimento.setPackage(getPackageName());
        sendBroadcast(alimento);
 
        // Reproducir sonido del animal
        reproducirSonidoComiendo();
 
        // Animación visual del botón y feedback
        btnComer.setText("😋  ¡Ñom ñom ñom!");
        uiHandler.postDelayed(() -> {
            actualizarDisplayHambre();
            if (btnComer != null) btnComer.setText("🥣  Dar de comer");
            Toast.makeText(this, "¡" + emojiAnimal() + " Gracias por la comida! ¡Estaba deliciosa!", Toast.LENGTH_SHORT).show();
        }, 3000);
 
        // Actualizar display inmediatamente
        if (tvNivelHambre != null) {
            pbHambre.setProgress(0);
            tvNivelHambre.setText("Hambre: 0%");
            tvNivelHambre.setTextColor(C_VERDE);
            tvEstadoHambre.setText("😊 ¡Nom nom! ¡Qué rico está!");
            tvEstadoHambre.setTextColor(C_VERDE);
            tvUltimaComida.setText("Comió hace menos de 1 minuto");
        }
    }
 
    /** Reproduce el sonido del animal comiendo usando AudioTrack */
    private void reproducirSonidoComiendo() {
        String animalActual = getSharedPreferences("quacky_prefs", MODE_PRIVATE).getString("animal", "duck");
        new Thread(() -> {
            try {
                int sr = 44100;
                int ms = 600;
                int n  = sr * ms / 1000;
                short[] s = new short[n];
 
                for (int i = 0; i < n; i++) {
                    double t = (double) i / sr;
                    double p = (double) i / n;
                    double m = 0;
 
                    switch (animalActual) {
                        case "cat":
                            // Miau feliz (tono ascendente)
                            double f_cat = 550 + 200 * Math.sin(p * Math.PI);
                            double a_cat = p < 0.1 ? p/0.1 : p > 0.8 ? (1-p)/0.2 : 1.0;
                            m = a_cat * (0.7*Math.sin(2*Math.PI*f_cat*t) + 0.2*Math.sin(4*Math.PI*f_cat*t));
                            break;
                        case "dog":
                            // Ladrido feliz corto
                            double f_dog = 300 + 150 * Math.sin(p * Math.PI * 4);
                            double a_dog = p < 0.05 ? p/0.05 : p > 0.6 ? (1-p)/0.4 : 1.0;
                            double noise = (new java.util.Random().nextDouble()-0.5)*0.2;
                            m = a_dog * (0.6*Math.sin(2*Math.PI*f_dog*t) + 0.2*Math.sin(4*Math.PI*f_dog*t) + noise);
                            break;
                        default:
                            // Cuak feliz del pato
                            double f_duck = 750 - 300 * p;
                            double a_duck = p < 0.08 ? p/0.08 : Math.max(0, 1-(p-0.08)/0.92);
                            m = a_duck * (0.65*Math.sin(2*Math.PI*f_duck*t) + 0.25*Math.sin(4*Math.PI*f_duck*t));
                            break;
                    }
                    s[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, m * Short.MAX_VALUE));
                }
 
                AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(s.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC).build();
                track.write(s, 0, s.length);
                track.play();
                Thread.sleep(ms + 100);
                track.stop(); track.release();
 
                // Segundo "nom" después de 400ms
                Thread.sleep(200);
                // Sonido más corto de masticar
                for (int i = 0; i < n/2; i++) {
                    double t = (double) i / sr;
                    double p = (double) i / (n/2);
                    double f = animalActual.equals("cat") ? 520 : animalActual.equals("dog") ? 280 : 680;
                    double a = p < 0.1 ? p/0.1 : p > 0.7 ? (1-p)/0.3 : 1.0;
                    s[i] = (short)(a * 0.5 * Math.sin(2*Math.PI*f*t) * Short.MAX_VALUE);
                }
                AudioTrack track2 = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                track2.write(s, 0, n/2);
                track2.play(); Thread.sleep(ms/2+80); track2.stop(); track2.release();
 
            } catch (Exception ignored) {}
        }).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Barra de hambre coloreada
    // ─────────────────────────────────────────────────────────────────────────
    private Drawable crearDrawableBarraHambre() {
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        long ultimaComida = p.getLong("ultima_comida", System.currentTimeMillis());
        int hambre = (int) Math.min(100, ((System.currentTimeMillis()-ultimaComida)/60000L)*100/30);
        return crearDrawableBarraHambre(hambre);
    }
    private Drawable crearDrawableBarraHambre(int hambre) {
        int colorBarra = hambre >= 80 ? C_ROJO : hambre >= 50 ? C_NARANJA : C_VERDE;
        LayerDrawable ld = new LayerDrawable(new Drawable[]{
            crearRectRedondeado(0xFF222244, dp(4)),
            crearRectRedondeado(colorBarra, dp(4))
        });
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        return ld;
    }
    private GradientDrawable crearRectRedondeado(int color, int radio) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radio); gd.setColor(color);
        return gd;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  UI de configuración (código existente simplificado)
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout encabezado(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);
        FrameLayout circ=new FrameLayout(this);GradientDrawable fc=new GradientDrawable();fc.setShape(GradientDrawable.OVAL);fc.setColor(0xFF2A2A4A);fc.setStroke(dp(3),C_ORO);circ.setBackground(fc);
        TextView eg=new TextView(this);eg.setText(emojiAnimal());eg.setTextSize(48);eg.setGravity(Gravity.CENTER);FrameLayout.LayoutParams pe=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);pe.gravity=Gravity.CENTER;circ.addView(eg,pe);
        int sz=dp(90);LinearLayout.LayoutParams pc=new LinearLayout.LayoutParams(sz,sz);pc.gravity=Gravity.CENTER;pc.setMargins(0,0,0,dp(12));c.addView(circ,pc);
        TextView t=new TextView(this);t.setText(nombre.isEmpty()?"Quacky":capitalize(nombre));t.setTextSize(32);t.setTypeface(null,Typeface.BOLD);t.setTextColor(C_ORO);t.setGravity(Gravity.CENTER);c.addView(t,margen(0,0,0,20));
        return c;
    }
 
    private LinearLayout seccionNombre(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(16));bg.setColor(C_CARD);bg.setStroke(dp(2),C_MORADO);c.setBackground(bg);c.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView d=new TextView(this);d.setText("Pon el nombre de tu mascota. Al decirlo en voz alta se activará automáticamente.");d.setTextColor(C_GRIS);d.setTextSize(13);c.addView(d,margen(0,0,0,12));
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);
        etNombre=new EditText(this);etNombre.setHint("Ej: Panchito, Michi, Rocky...");etNombre.setHintTextColor(0xFF555577);etNombre.setText(nombre);etNombre.setTextColor(C_BLANCO);etNombre.setTextSize(16);etNombre.setTypeface(null,Typeface.BOLD);etNombre.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);etNombre.setSingleLine(true);GradientDrawable eb=new GradientDrawable();eb.setCornerRadius(dp(10));eb.setColor(0xFF1E1E40);eb.setStroke(dp(1),0xFF3A3A6A);etNombre.setBackground(eb);etNombre.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);ep.setMargins(0,0,dp(10),0);f.addView(etNombre,ep);
        Button bg2=new Button(this);bg2.setText("💾 Guardar");bg2.setTextSize(13);bg2.setAllCaps(false);bg2.setTextColor(0xFF0F0F23);GradientDrawable bb=new GradientDrawable();bb.setCornerRadius(dp(10));bb.setColor(C_MORADO);bg2.setBackground(bb);bg2.setOnClickListener(v->guardarNombre());f.addView(bg2,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));c.addView(f,margen(0,0,0,10));
        tvNombrePreview=new TextView(this);actualizarPreviewNombre();tvNombrePreview.setTextSize(13);tvNombrePreview.setGravity(Gravity.CENTER);c.addView(tvNombrePreview);
        return c;
    }
 
    private void guardarNombre(){nombre=etNombre.getText().toString().trim().toLowerCase();getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("nombre_mascota",nombre).apply();actualizarPreviewNombre();InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(etNombre.getWindowToken(),0);Toast.makeText(this,"✅ Nombre: "+capitalize(nombre.isEmpty()?"Quacky":nombre),Toast.LENGTH_SHORT).show();}
    private void actualizarPreviewNombre(){if(tvNombrePreview==null)return;if(nombre.isEmpty()){tvNombrePreview.setText("⚠️ Sin nombre — usa el botón 🎤 para activar");tvNombrePreview.setTextColor(C_NARANJA);}else{tvNombrePreview.setText("✅ Di \""+capitalize(nombre)+"\" para activar");tvNombrePreview.setTextColor(C_VERDE);}}
 
    private LinearLayout gridAnimales(){
        LinearLayout g=new LinearLayout(this);g.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<ANIMALES.length;i+=2){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.addView(cardAnimal(ANIMALES[i]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));f.addView(new View(this),new LinearLayout.LayoutParams(dp(12),0));if(i+1<ANIMALES.length)f.addView(cardAnimal(ANIMALES[i+1]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));else f.addView(new View(this),new LinearLayout.LayoutParams(0,0,1f));LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(10));g.addView(f,pf);}
        return g;
    }
 
    private LinearLayout cardAnimal(String[]a){
        String id=a[0],emoji=a[1],nom=a[2];boolean disp=a[3].equals("si"),sel=id.equals(animal)&&disp;
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(12),dp(14),dp(12),dp(14));aplicarFondoCard(c,sel,disp);
        TextView te=new TextView(this);te.setText(emoji);te.setTextSize(34);te.setGravity(Gravity.CENTER);te.setAlpha(disp?1f:0.4f);c.addView(te,margen(0,0,0,4));
        TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(13);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(disp?C_BLANCO:C_GRIS);c.addView(tn,margen(0,0,0,4));
        TextView tb=new TextView(this);tb.setText(sel?"✓ Seleccionado":disp?"Disponible":"Próximamente");tb.setTextColor(sel?C_ORO:disp?C_VERDE:C_GRIS);tb.setTextSize(10);tb.setGravity(Gravity.CENTER);c.addView(tb);
        if(sel)cardAnimal=c;
        c.setOnClickListener(v->{if(!disp){Toast.makeText(this,"¡"+nom+" pronto!",Toast.LENGTH_SHORT).show();return;}seleccionarAnimal(id,c);});
        return c;
    }
 
    private void aplicarFondoCard(LinearLayout c,boolean sel,boolean disp){GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));if(!disp){f.setColor(0xFF141428);f.setStroke(dp(1),0xFF222240);}else if(sel){f.setColor(C_SEL);f.setStroke(dp(2),C_ORO);}else{f.setColor(C_CARD);f.setStroke(dp(1),0xFF2A2A4A);}c.setBackground(f);}
    private void seleccionarAnimal(String id,LinearLayout cn){if(cardAnimal!=null&&cardAnimal!=cn){aplicarFondoCard(cardAnimal,false,true);if(cardAnimal.getChildCount()>=3)((TextView)cardAnimal.getChildAt(2)).setText("Disponible");}aplicarFondoCard(cn,true,true);if(cn.getChildCount()>=3)((TextView)cn.getChildAt(2)).setText("✓ Seleccionado");Animation r=new ScaleAnimation(0.93f,1f,0.93f,1f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);r.setDuration(150);cn.startAnimation(r);cardAnimal=cn;animal=id;getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("animal",id).apply();}
 
    private LinearLayout seccionVoz(){
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);
        cardVozM=cardVoz("masculina","👨","Masculina","Voz grave",C_AZUL);f.addView(cardVozM,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));f.addView(new View(this),new LinearLayout.LayoutParams(dp(12),0));
        cardVozF=cardVoz("femenina","👩","Femenina","Voz aguda",C_ROSA);f.addView(cardVozF,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        actualizarVoz(voz,false);return f;
    }
    private LinearLayout cardVoz(String tipo,String emoji,String nom,String desc,int ac){boolean sel=tipo.equals(voz);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(12),dp(16),dp(12),dp(16));GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));f.setColor(sel?0xFF1E2A3A:C_CARD);f.setStroke(dp(sel?2:1),sel?ac:0xFF2A2A4A);c.setBackground(f);TextView te=new TextView(this);te.setText(emoji);te.setTextSize(30);te.setGravity(Gravity.CENTER);c.addView(te,margen(0,0,0,6));TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(14);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(sel?ac:C_BLANCO);c.addView(tn,margen(0,0,0,2));TextView td=new TextView(this);td.setText(desc);td.setTextSize(11);td.setGravity(Gravity.CENTER);td.setTextColor(C_GRIS);c.addView(td,margen(0,0,0,4));TextView tb=new TextView(this);tb.setText(sel?"✓ Activa":"Elegir");tb.setTextColor(sel?ac:C_GRIS);tb.setTextSize(10);tb.setGravity(Gravity.CENTER);c.addView(tb);c.setOnClickListener(v->actualizarVoz(tipo,true));return c;}
    private void actualizarVoz(String tipo,boolean guardar){voz=tipo;if(guardar)getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("voz",tipo).apply();actualizarEstiloVoz(cardVozM,"masculina",C_AZUL);actualizarEstiloVoz(cardVozF,"femenina",C_ROSA);}
    private void actualizarEstiloVoz(LinearLayout c,String tipo,int ac){if(c==null)return;boolean sel=tipo.equals(voz);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));f.setColor(sel?0xFF1E2A3A:C_CARD);f.setStroke(dp(sel?2:1),sel?ac:0xFF2A2A4A);c.setBackground(f);if(c.getChildCount()>=2)((TextView)c.getChildAt(1)).setTextColor(sel?ac:C_BLANCO);if(c.getChildCount()>=4){TextView b=(TextView)c.getChildAt(3);b.setText(sel?"✓ Activa":"Elegir");b.setTextColor(sel?ac:C_GRIS);}}
 
    private LinearLayout seccionPermisos(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));f.setColor(C_CARD);f.setStroke(dp(1),0xFF2A2A4A);c.setBackground(f);c.setPadding(dp(16),dp(16),dp(16),dp(16));
        String[][]ps={{"🖥️","Mostrar sobre apps","Flota sobre tu pantalla"},{"🎤","Micrófono","Para escucharte"},{"👥","Contactos","Para llamar por voz"},{"📞","Teléfono","Para marcar por ti"}};
        for(String[]p:ps)c.addView(filaPermiso(p[0],p[1],p[2]));
        Button b=new Button(this);b.setText("Conceder permisos pendientes");b.setTextColor(C_ORO);b.setTextSize(13);b.setBackgroundColor(Color.TRANSPARENT);b.setAllCaps(false);b.setOnClickListener(v->solicitarPermisos());LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pb.setMargins(0,dp(8),0,0);c.addView(b,pb);return c;
    }
 
    private LinearLayout filaPermiso(String ic,String nom,String desc){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(10));f.setLayoutParams(pf);TextView ti=new TextView(this);ti.setText(ic);ti.setTextSize(20);ti.setGravity(Gravity.CENTER);f.addView(ti,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams pt=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);pt.setMargins(dp(10),0,0,0);TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(13);tn.setTypeface(null,Typeface.BOLD);tn.setTextColor(C_BLANCO);t.addView(tn);TextView td=new TextView(this);td.setText(desc);td.setTextSize(11);td.setTextColor(C_GRIS);t.addView(td);f.addView(t,pt);TextView e=new TextView(this);e.setTag("e_"+nom);boolean ok=verificarPermiso(nom);e.setText(ok?"✓":"✗");e.setTextColor(ok?C_VERDE:C_ROJO);e.setTextSize(16);e.setTypeface(null,Typeface.BOLD);f.addView(e,new LinearLayout.LayoutParams(dp(28),dp(28)));return f;}
    private boolean verificarPermiso(String nom){switch(nom){case"Mostrar sobre apps":return Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(this);case"Micrófono":return ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;case"Contactos":return ContextCompat.checkSelfPermission(this,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;case"Teléfono":return ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;default:return false;}}
    private void actualizarPermisos(){if(secPermisos!=null)buscarActualizar(secPermisos);}
    private void buscarActualizar(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View h=g.getChildAt(i);if(h instanceof TextView){Object t=h.getTag();if(t instanceof String&&((String)t).startsWith("e_")){String n=((String)t).replace("e_","");boolean ok=verificarPermiso(n);((TextView)h).setText(ok?"✓":"✗");((TextView)h).setTextColor(ok?C_VERDE:C_ROJO);}}else if(h instanceof ViewGroup)buscarActualizar((ViewGroup)h);}}
 
    private Button botonActivar(){Button b=new Button(this);b.setText("🚀  Activar "+emojiAnimal());b.setTextSize(16);b.setTypeface(null,Typeface.BOLD);b.setTextColor(C_FONDO);b.setAllCaps(false);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(50));f.setColor(C_ORO);b.setBackground(f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));b.setLayoutParams(p);b.setOnClickListener(v->activar());return b;}
    private void activar(){if(etNombre!=null&&!etNombre.getText().toString().trim().isEmpty())guardarNombre();boolean ok=verificarPermiso("Mostrar sobre apps")&&verificarPermiso("Micrófono")&&verificarPermiso("Contactos")&&verificarPermiso("Teléfono");if(!ok){solicitarPermisos();return;}stopService(new Intent(this,DuckOverlayService.class));new Handler().postDelayed(()->{Intent s=new Intent(this,DuckOverlayService.class);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)startForegroundService(s);else startService(s);moveTaskToBack(true);},600);}
    private void solicitarPermisos(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("🖥️ Permiso de pantalla").setMessage("Busca 'Quacky' y activa el interruptor.").setPositiveButton("Ajustes",(d,w)->startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY_REQUEST)).setNegativeButton("Cancelar",null).show();return;}String[]perms={Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE};boolean falta=false;for(String p:perms)if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED){falta=true;break;}if(falta)new AlertDialog.Builder(this).setTitle("🎤 Permisos").setMessage("Necesito micrófono, contactos y teléfono.").setPositiveButton("Conceder",(d,w)->ActivityCompat.requestPermissions(this,perms,PERM_REQUEST)).setNegativeButton("Ahora no",null).show();}
    @Override public void onRequestPermissionsResult(int req,String[]perms,int[]res){super.onRequestPermissionsResult(req,perms,res);if(req==PERM_REQUEST){actualizarPermisos();if(verificarPermiso("Mostrar sobre apps")&&verificarPermiso("Micrófono")&&verificarPermiso("Contactos")&&verificarPermiso("Teléfono"))activar();}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==OVERLAY_REQUEST){actualizarPermisos();if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&Settings.canDrawOverlays(this))solicitarPermisos();}}
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private String emojiAnimal(){for(String[]a:ANIMALES)if(a[0].equals(animal))return a[1];return"🦆";}
    private String capitalize(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private View divisor(){View d=new View(this);d.setBackgroundColor(0xFF2A2A4A);return d;}
    private TextView etiqueta(String t){TextView tv=new TextView(this);tv.setText(t);tv.setTextSize(14);tv.setTypeface(null,Typeface.BOLD);tv.setTextColor(C_BLANCO);return tv;}
    private LinearLayout.LayoutParams margen(int i,int a,int d,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(i),dp(a),dp(d),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
 
