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
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
 
public class MainActivity extends Activity {
 
    // ✅ Misma key que DuckOverlayService
    private static final String GROQ_API_KEY = "PEGA_TU_KEY_DE_GROQ_AQUI";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
 
    private static final int PERM_REQUEST    = 101;
    private static final int OVERLAY_REQUEST = 102;
 
    // Colores
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
 
    // ── Alimentos con sus efectos ─────────────────────────────────────────────
    // preferencia: "love"(❤️), "like"(😊), "ok"(😐), "dislike"(🤢)
    private static final Object[][] COMIDAS = {
        // {emoji, nombre, hambre, felicidad, energía, preferencia}
        {"🍕","pizza",         40, 20, -5,  "love"},
        {"🌮","tacos",         35, 25,  5,  "love"},
        {"🍫","chocolate",     15, 30,-10,  "love"},
        {"🍦","helado",        10, 35, -5,  "love"},
        {"🍰","pastel",        25, 30, -5,  "love"},
        {"🍔","hamburguesa",   35, 15, -5,  "like"},
        {"☕","café",            5, 10, 30,  "like"},
        {"🍗","pollo",         30, 10, 10,  "like"},
        {"🥑","aguacate",      20, 10, 15,  "like"},
        {"🍎","manzana",       20,  5, 10,  "ok"},
        {"🥦","brócoli",       15, -5, 15,  "dislike"},
        {"🍋","limón",          5,-15,  5,  "dislike"},
    };
 
    // Estado de la app
    private String animal  = "duck";
    private String voz     = "femenina";
    private String nombre  = "";
 
    // Widgets de configuración
    private LinearLayout cardAnimalSel = null;
    private LinearLayout cardVozM, cardVozF;
    private LinearLayout secPermisos;
    private Button       btnActivar;
    private EditText     etNombre;
    private TextView     tvNombrePreview;
 
    // Widgets de alimentación
    private TextView   tvEmojiMascota;
    private TextView   tvBurbujaMascota;
    private ProgressBar pbHambre, pbFelicidad, pbEnergia;
    private TextView   tvPctHambre, tvPctFelicidad, tvPctEnergia;
    private TextView   tvComidaArrastrada; // vista que sigue el dedo
    private FrameLayout feedContainer;
    private int        petCenterX, petCenterY; // posición del área de drop
 
    // Arrastre activo
    private Object[] comidaActual = null;
    private TextView  dragView    = null;
    private boolean   dragging    = false;
 
    private Handler   uiHandler   = new Handler(Looper.getMainLooper());
    private Runnable  tickHambre;
 
    // Puntos pager
    private TextView[] pts = new TextView[2];
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(C_FONDO);
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        animal = p.getString("animal","duck");
        voz    = p.getString("voz","femenina");
        nombre = p.getString("nombre_mascota","");
        construirUI();
    }
 
    @Override protected void onResume()  { super.onResume(); if(secPermisos!=null)actualizarPermisos(); iniciarTick(); }
    @Override protected void onPause()   { super.onPause(); uiHandler.removeCallbacks(tickHambre); }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ESTRUCTURA PRINCIPAL con ViewPager
    // ─────────────────────────────────────────────────────────────────────────
    private void construirUI() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(C_FONDO);
 
        // ── Tab bar ──────────────────────────────────────────────────────────
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(0xFF08081A);
 
        final TextView tConfig = new TextView(this);
        tConfig.setText("⚙️  Configurar");
        tConfig.setGravity(Gravity.CENTER); tConfig.setTextSize(13);
        tConfig.setTextColor(C_ORO); tConfig.setPadding(0,dp(10),0,dp(10));
 
        View sep = new View(this); sep.setBackgroundColor(0xFF2A2A4A);
 
        final TextView tComida = new TextView(this);
        tComida.setText("🍖  Alimentar");
        tComida.setGravity(Gravity.CENTER); tComida.setTextSize(13);
        tComida.setTextColor(C_GRIS); tComida.setPadding(0,dp(10),0,dp(10));
 
        tabs.addView(tConfig, new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        tabs.addView(sep, new LinearLayout.LayoutParams(dp(1),ViewGroup.LayoutParams.MATCH_PARENT));
        tabs.addView(tComida, new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
 
        // ── ViewPager ────────────────────────────────────────────────────────
        ViewPager pager = new ViewPager(this);
        pager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return 2; }
            @Override public boolean isViewFromObject(View v,Object o) { return v==o; }
            @Override public Object instantiateItem(ViewGroup c,int pos) {
                View p = pos==0 ? paginaConfig() : paginaComida();
                c.addView(p); return p;
            }
            @Override public void destroyItem(ViewGroup c,int pos,Object obj) { c.removeView((View)obj); }
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override public void onPageSelected(int pos){
                tConfig.setTextColor(pos==0?C_ORO:C_GRIS);
                tComida.setTextColor(pos==1?C_ORO:C_GRIS);
                for(int i=0;i<pts.length;i++) pts[i].setTextColor(i==pos?C_ORO:C_GRIS);
            }
        });
 
        // Puntos indicadores
        LinearLayout puntos = new LinearLayout(this);
        puntos.setGravity(Gravity.CENTER); puntos.setPadding(0,dp(2),0,dp(4));
        for(int i=0;i<2;i++){pts[i]=new TextView(this);pts[i].setText("●");pts[i].setTextSize(9);pts[i].setPadding(dp(5),0,dp(5),0);pts[i].setTextColor(i==0?C_ORO:C_GRIS);puntos.addView(pts[i]);}
 
        raiz.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        raiz.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        raiz.addView(puntos, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(raiz);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  PÁGINA 2 — ALIMENTACIÓN INTERACTIVA
    // ─────────────────────────────────────────────────────────────────────────
    private FrameLayout paginaComida() {
        feedContainer = new FrameLayout(this);
        feedContainer.setBackgroundColor(C_FONDO);
 
        // ScrollView con stats + mascota
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_FONDO);
 
        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(dp(16),dp(12),dp(16),dp(8));
 
        // Título
        TextView titulo = new TextView(this);
        titulo.setText("🍖 Alimenta a "+capitalize(nombre.isEmpty()?"tu mascota":nombre));
        titulo.setTextSize(18); titulo.setTypeface(null,Typeface.BOLD);
        titulo.setTextColor(C_ORO); titulo.setGravity(Gravity.CENTER);
        contenido.addView(titulo,margen(0,0,0,12));
 
        // ── Estadísticas ──────────────────────────────────────────────────────
        LinearLayout cardStats = new LinearLayout(this);
        cardStats.setOrientation(LinearLayout.VERTICAL);
        cardStats.setPadding(dp(14),dp(12),dp(14),dp(12));
        GradientDrawable gsb = new GradientDrawable();
        gsb.setCornerRadius(dp(16)); gsb.setColor(C_CARD); gsb.setStroke(dp(1),0xFF2A2A5A);
        cardStats.setBackground(gsb);
 
        pbHambre     = crearBarraStat(); tvPctHambre     = new TextView(this);
        pbFelicidad  = crearBarraStat(); tvPctFelicidad  = new TextView(this);
        pbEnergia    = crearBarraStat(); tvPctEnergia    = new TextView(this);
 
        cardStats.addView(filaStat("😋 Hambre",    pbHambre,    tvPctHambre));
        cardStats.addView(filaStat("😊 Felicidad", pbFelicidad, tvPctFelicidad));
        cardStats.addView(filaStat("⚡ Energía",   pbEnergia,   tvPctEnergia));
        contenido.addView(cardStats,margen(0,0,0,12));
 
        // ── Mascota + burbuja ─────────────────────────────────────────────────
        LinearLayout petArea = new LinearLayout(this);
        petArea.setOrientation(LinearLayout.VERTICAL);
        petArea.setGravity(Gravity.CENTER);
        petArea.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable gpb = new GradientDrawable();
        gpb.setCornerRadius(dp(16)); gpb.setColor(0xFF0D0D20); gpb.setStroke(dp(2),0xFF3A3A7A);
        petArea.setBackground(gpb);
 
        tvEmojiMascota = new TextView(this);
        tvEmojiMascota.setText(emojiAnimal()); tvEmojiMascota.setTextSize(60);
        tvEmojiMascota.setGravity(Gravity.CENTER); petArea.addView(tvEmojiMascota,margen(0,0,0,6));
 
        tvBurbujaMascota = new TextView(this);
        tvBurbujaMascota.setText("¡Arrastra comida hasta aquí para darme de comer! 🍽️");
        tvBurbujaMascota.setTextColor(C_BLANCO); tvBurbujaMascota.setTextSize(13);
        tvBurbujaMascota.setGravity(Gravity.CENTER);
        GradientDrawable gbb = new GradientDrawable();
        gbb.setCornerRadius(dp(12)); gbb.setColor(0xEE1A1A3A); gbb.setStroke(dp(1),C_ORO);
        tvBurbujaMascota.setBackground(gbb); tvBurbujaMascota.setPadding(dp(10),dp(6),dp(10),dp(6));
        petArea.addView(tvBurbujaMascota);
 
        TextView hint = new TextView(this);
        hint.setText("↑ suelta aquí ↑"); hint.setTextColor(0xFF555588);
        hint.setTextSize(11); hint.setGravity(Gravity.CENTER);
        petArea.addView(hint,margen(0,4,0,0));
 
        contenido.addView(petArea,margen(0,0,0,10));
        scroll.addView(contenido);
 
        // ── Grid de comidas ───────────────────────────────────────────────────
        LinearLayout gridWrapper = new LinearLayout(this);
        gridWrapper.setOrientation(LinearLayout.VERTICAL);
        gridWrapper.setPadding(dp(10),dp(4),dp(10),dp(10));
 
        TextView labelComida = new TextView(this);
        labelComida.setText("🍽️ Arrastra una comida a tu mascota:");
        labelComida.setTextColor(C_GRIS); labelComida.setTextSize(12);
        labelComida.setPadding(dp(4),dp(4),dp(4),dp(8));
        gridWrapper.addView(labelComida);
 
        // Grid 4 columnas
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4); grid.setRowCount(3);
        for(int i=0;i<COMIDAS.length;i++) {
            View fc = crearItemComida(COMIDAS[i]);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                GridLayout.spec(i/4,1f), GridLayout.spec(i%4,1f));
            glp.width  = 0; glp.height = dp(72);
            glp.setMargins(dp(4),dp(4),dp(4),dp(4));
            grid.addView(fc,glp);
        }
        gridWrapper.addView(grid);
 
        // Leyenda de preferencias
        LinearLayout leyenda = new LinearLayout(this);
        leyenda.setOrientation(LinearLayout.HORIZONTAL);
        leyenda.setGravity(Gravity.CENTER);
        leyenda.setPadding(0,dp(8),0,dp(4));
        String[] leyTextos = {"❤️ Favorita","😊 Le gusta","😐 Normal","🤢 No le gusta"};
        for(String l:leyTextos){TextView lt=new TextView(this);lt.setText(l);lt.setTextColor(C_GRIS);lt.setTextSize(10);lt.setPadding(dp(4),0,dp(4),0);leyenda.addView(lt);}
        gridWrapper.addView(leyenda);
 
        // ── Vista flotante para el drag (sigue el dedo) ───────────────────────
        dragView = new TextView(this);
        dragView.setTextSize(36); dragView.setGravity(Gravity.CENTER);
        dragView.setVisibility(View.GONE);
        dragView.setElevation(dp(16));
 
        // Montar todo en el FrameLayout
        FrameLayout.LayoutParams fpScroll = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        FrameLayout.LayoutParams fpGrid = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
 
        feedContainer.addView(scroll, fpScroll);
        feedContainer.addView(gridWrapper, fpGrid);
        feedContainer.addView(dragView, new FrameLayout.LayoutParams(dp(60),dp(60)));
 
        // Calcular posición del área de drop (después de layout)
        petArea.post(() -> {
            int[] loc = new int[2];
            petArea.getLocationInWindow(loc);
            petCenterX = loc[0] + petArea.getWidth()/2;
            petCenterY = loc[1] + petArea.getHeight()/2;
        });
 
        actualizarStats();
        generarAntojo();
        return feedContainer;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Crear item de comida draggable
    // ─────────────────────────────────────────────────────────────────────────
    private View crearItemComida(Object[] comida) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
 
        String pref = (String) comida[5];
        int color;
        switch(pref) {
            case "love":    color = 0xFF2A1A1A; break;
            case "like":    color = 0xFF1A2A1A; break;
            case "dislike": color = 0xFF2A2A10; break;
            default:        color = C_CARD;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(color);
        String borderColor;
        switch(pref) {
            case "love":    borderColor = "FF4444"; break;
            case "like":    borderColor = "44AA44"; break;
            case "dislike": borderColor = "887722"; break;
            default:        borderColor = "333366";
        }
        bg.setStroke(dp(1), Color.parseColor("#"+borderColor));
        card.setBackground(bg);
        card.setPadding(dp(4),dp(4),dp(4),dp(4));
 
        // Emoji grande
        TextView tvEmoji = new TextView(this);
        tvEmoji.setText((String)comida[0]);
        tvEmoji.setTextSize(26); tvEmoji.setGravity(Gravity.CENTER);
        card.addView(tvEmoji);
 
        // Preferencia pequeña
        String prefEmoji = pref.equals("love")?"❤️":pref.equals("like")?"😊":pref.equals("dislike")?"🤢":"😐";
        TextView tvPref = new TextView(this);
        tvPref.setText(prefEmoji); tvPref.setTextSize(10); tvPref.setGravity(Gravity.CENTER);
        card.addView(tvPref);
 
        // Touch listener para arrastrar
        card.setOnTouchListener(new FoodTouchListener(comida));
        return card;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Touch listener para arrastrar comida hacia la mascota
    // ─────────────────────────────────────────────────────────────────────────
    private class FoodTouchListener implements View.OnTouchListener {
        private final Object[] comida;
        FoodTouchListener(Object[] c) { this.comida = c; }
 
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int[] containerLoc = new int[2];
            feedContainer.getLocationInWindow(containerLoc);
 
            float fx = event.getRawX() - containerLoc[0] - dp(30);
            float fy = event.getRawY() - containerLoc[1] - dp(30);
 
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    comidaActual = comida;
                    dragging = true;
                    dragView.setText((String) comida[0]);
                    dragView.setVisibility(View.VISIBLE);
                    dragView.setX(fx); dragView.setY(fy);
                    dragView.setScaleX(1.3f); dragView.setScaleY(1.3f);
                    v.setAlpha(0.5f);
                    break;
 
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) break;
                    dragView.setX(fx); dragView.setY(fy);
                    // Resaltar mascota si está cerca de la zona de drop
                    boolean cercaDrop = esCercaDelPet(event.getRawX(), event.getRawY());
                    tvEmojiMascota.setScaleX(cercaDrop ? 1.3f : 1f);
                    tvEmojiMascota.setScaleY(cercaDrop ? 1.3f : 1f);
                    break;
 
                case MotionEvent.ACTION_UP:
                    dragView.setVisibility(View.GONE);
                    v.setAlpha(1f);
                    tvEmojiMascota.setScaleX(1f); tvEmojiMascota.setScaleY(1f);
                    dragging = false;
                    if (esCercaDelPet(event.getRawX(), event.getRawY())) {
                        alimentarConComida(comidaActual);
                    }
                    comidaActual = null;
                    break;
 
                case MotionEvent.ACTION_CANCEL:
                    dragView.setVisibility(View.GONE);
                    v.setAlpha(1f);
                    tvEmojiMascota.setScaleX(1f); tvEmojiMascota.setScaleY(1f);
                    dragging = false;
                    comidaActual = null;
                    break;
            }
            return true;
        }
    }
 
    private boolean esCercaDelPet(float rawX, float rawY) {
        if (petCenterX == 0 && petCenterY == 0) return false;
        float dx = rawX - petCenterX;
        float dy = rawY - petCenterY;
        return Math.sqrt(dx*dx + dy*dy) < dp(120); // radio de 120dp
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ALIMENTAR CON COMIDA ESPECÍFICA
    // ─────────────────────────────────────────────────────────────────────────
    private void alimentarConComida(Object[] comida) {
        if (comida == null) return;
 
        String emoji  = (String) comida[0];
        String nombre2 = (String) comida[1];
        int hamBonus  = (int)    comida[2];
        int felBonus  = (int)    comida[3];
        int eneBonus  = (int)    comida[4];
        String pref   = (String) comida[5];
 
        // Animación de recibir comida
        tvEmojiMascota.animate().scaleX(1.4f).scaleY(1.4f).setDuration(200)
            .withEndAction(() -> tvEmojiMascota.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
 
        // Actualizar stats
        SharedPreferences prefs = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        long ultimaComida = System.currentTimeMillis();
        int hambreActual = Math.max(0, Math.min(100, nivelHambreActual() + hamBonus));
        int felActual    = Math.max(0, Math.min(100, prefs.getInt("felicidad", 70) + felBonus));
        int eneActual    = Math.max(0, Math.min(100, prefs.getInt("energia", 80) + eneBonus));
 
        prefs.edit()
            .putLong("ultima_comida", ultimaComida)
            .putInt("felicidad", felActual)
            .putInt("energia", eneActual)
            .apply();
 
        // Animar stats
        animarStatBar(pbHambre,    tvPctHambre,    hambreActual,   colorHambre(hambreActual));
        animarStatBar(pbFelicidad, tvPctFelicidad, felActual,      colorFelicidad(felActual));
        animarStatBar(pbEnergia,   tvPctEnergia,   eneActual,      C_AZUL);
 
        // Reproducir sonido del animal
        reproducirSonido();
 
        // Mostrar reacción inmediata mientras espera la IA
        String reacInmediata;
        switch(pref) {
            case "love":    reacInmediata = "¡¡¡SÍ!!! ¡¡" + emoji + " " + nombre2 + "!! ❤️❤️❤️"; break;
            case "like":    reacInmediata = "¡Mmm! " + emoji + " " + nombre2 + " 😊"; break;
            case "dislike": reacInmediata = emoji + " " + nombre2 + "... bueno... 🤢"; break;
            default:        reacInmediata = emoji + " " + nombre2 + "... está bien 😐"; break;
        }
        mostrarBurbuja(reacInmediata);
 
        // Avisar al servicio (para animación en pantalla y refresco)
        Intent alimentar = new Intent("com.quacky.duck.ALIMENTAR");
        alimentar.setPackage(getPackageName());
        alimentar.putExtra("comida_emoji",   emoji);
        alimentar.putExtra("comida_nombre",  nombre2);
        alimentar.putExtra("hambre_bonus",   hamBonus);
        alimentar.putExtra("felicidad_bonus",felBonus);
        alimentar.putExtra("energia_bonus",  eneBonus);
        alimentar.putExtra("preferencia",    pref);
        sendBroadcast(alimentar);
 
        // Llamar a Groq para respuesta personalizada de la mascota
        generarRespuestaIA(emoji, nombre2, pref, felBonus);
 
        // Nuevo antojo después de comer
        uiHandler.postDelayed(this::generarAntojo, 8000);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Groq — respuesta de la mascota a la comida
    // ─────────────────────────────────────────────────────────────────────────
    private void generarRespuestaIA(String emoji, String nombreComida, String pref, int felBonus) {
        String nombreMascota = this.nombre.isEmpty() ? "Quacky" : capitalize(this.nombre);
        String tipoAnimal;
        switch(animal){case"cat":tipoAnimal="gato";break;case"dog":tipoAnimal="perro";break;default:tipoAnimal="pato";}
 
        String sentimiento;
        switch(pref){
            case"love":    sentimiento="¡es tu comida FAVORITA! Amas absolutamente esta comida."; break;
            case"like":    sentimiento="te gusta bastante esta comida."; break;
            case"dislike": sentimiento="no te gusta esta comida para nada, te da asco pero te la comiste."; break;
            default:       sentimiento="es una comida normal para ti, ni bien ni mal."; break;
        }
 
        String prompt = "Eres "+nombreMascota+", un "+tipoAnimal+" virtual. Acabas de recibir "+emoji+" "+nombreComida+". Para ti "+sentimiento+" "
            +"Reacciona con UNA sola oración muy expresiva y divertida en español mexicano. "
            +"Puedes incluir un emoji al final. Sin comillas.";
 
        new Thread(() -> {
            try {
                JSONArray msgs = new JSONArray();
                JSONObject um  = new JSONObject(); um.put("role","user"); um.put("content",prompt); msgs.put(um);
                JSONObject body = new JSONObject(); body.put("model","llama-3.3-70b-versatile"); body.put("max_tokens",80); body.put("messages",msgs);
                URL url = new URL(GROQ_URL); HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST"); conn.setRequestProperty("Content-Type","application/json"); conn.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);
                conn.setDoOutput(true); conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                try(OutputStream os=conn.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                StringBuilder sb = new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}
                String reply = new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                uiHandler.post(() -> mostrarBurbuja(reply));
            } catch (Exception e) {
                // Fallback local si falla Groq
                uiHandler.post(() -> {
                    String[] fallbacks;
                    if(pref.equals("love")) fallbacks=new String[]{"¡ESTO ES LO MEJOR QUE HE COMIDO! ❤️","¡Me encantan! ¡Dame más! ❤️","¡Soy la mascota más feliz del mundo! ❤️"};
                    else if(pref.equals("dislike")) fallbacks=new String[]{"Guácala... pero gracias... 🤢","¡Próxima vez algo mejor, porfa! 🤢","Eso estuvo... interesante... 🫤"};
                    else fallbacks=new String[]{"¡Gracias por alimentarme! 😊","¡Qué rico estuvo! 😋","¡Siempre es bueno comer! 😊"};
                    mostrarBurbuja(fallbacks[new Random().nextInt(fallbacks.length)]);
                });
            }
        }).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ANTOJOS — la mascota pide comida específica
    // ─────────────────────────────────────────────────────────────────────────
    private void generarAntojo() {
        if (tvBurbujaMascota == null) return;
        Random r = new Random();
        int hambre = nivelHambreActual();
 
        String antojo;
        if (hambre >= 80) {
            // Hambre alta → pide su favorita
            String[] comFav = {"🍕 pizza","🌮 tacos","🍫 chocolate","🍦 helado","🍰 pastel"};
            antojo = "¡Tengo MUCHA hambre! ¡Quiero "+comFav[r.nextInt(comFav.length)]+"! 😭";
        } else if (hambre >= 50) {
            String[] peticiones = {"¡Tengo antojo de algo dulce! 🍫🍦","¡Me provoca una pizza! 🍕","¡Quiero algo rico! 🌮","¡Tengo hambrín! ¿Me das algo? 😋"};
            antojo = peticiones[r.nextInt(peticiones.length)];
        } else {
            String[] contentos = {"¡Estoy muy bien alimentada! 😊","¡Gracias por cuidarme! ❤️","¡Me siento con mucha energía! ⚡","¡Hoy estoy muy feliz! 🎉"};
            antojo = contentos[r.nextInt(contentos.length)];
        }
        mostrarBurbuja(antojo);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  STATS — leer y mostrar
    // ─────────────────────────────────────────────────────────────────────────
    private int nivelHambreActual() {
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        long ultimaComida = p.getLong("ultima_comida", System.currentTimeMillis());
        long min = (System.currentTimeMillis() - ultimaComida) / 60000L;
        return (int) Math.min(100, min * 100 / 30);
    }
 
    private void actualizarStats() {
        if (pbHambre == null) return;
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        int hambre     = nivelHambreActual();
        int felicidad  = p.getInt("felicidad", 70);
        int energia    = p.getInt("energia", 80);
 
        pbHambre.setProgress(hambre);     pbHambre.setProgressDrawable(barDrawable(colorHambre(hambre)));
        pbFelicidad.setProgress(felicidad); pbFelicidad.setProgressDrawable(barDrawable(colorFelicidad(felicidad)));
        pbEnergia.setProgress(energia);   pbEnergia.setProgressDrawable(barDrawable(C_AZUL));
 
        setStatText(tvPctHambre,     hambre + "%");
        setStatText(tvPctFelicidad,  felicidad + "%");
        setStatText(tvPctEnergia,    energia + "%");
    }
 
    private void iniciarTick() {
        tickHambre = new Runnable() {
            @Override public void run() {
                actualizarStats();
                uiHandler.postDelayed(this, 15000);
            }
        };
        uiHandler.post(tickHambre);
    }
 
    private void animarStatBar(ProgressBar pb, TextView tv, int valor, int color) {
        if (pb == null) return;
        pb.setProgressDrawable(barDrawable(color));
        ValueAnimator anim = ValueAnimator.ofInt(pb.getProgress(), valor);
        anim.setDuration(600); anim.addUpdateListener(a -> { pb.setProgress((int)a.getAnimatedValue()); if(tv!=null)tv.setText(a.getAnimatedValue()+"%"); });
        anim.start();
    }
 
    private int colorHambre(int nivel) {
        return nivel >= 80 ? C_ROJO : nivel >= 50 ? C_NARANJA : C_VERDE;
    }
    private int colorFelicidad(int nivel) {
        return nivel >= 60 ? C_ROSA : nivel >= 30 ? C_NARANJA : C_ROJO;
    }
 
    private void mostrarBurbuja(String texto) {
        if (tvBurbujaMascota == null) return;
        tvBurbujaMascota.setAlpha(0f);
        tvBurbujaMascota.setText(texto);
        tvBurbujaMascota.animate().alpha(1f).setDuration(300).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  UI Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout filaStat(String label, ProgressBar pb, TextView tvPct) {
        LinearLayout f = new LinearLayout(this); f.setOrientation(LinearLayout.HORIZONTAL); f.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(6)); f.setLayoutParams(lp);
        TextView lbl = new TextView(this); lbl.setText(label); lbl.setTextSize(12); lbl.setTextColor(C_GRIS);
        LinearLayout.LayoutParams llbl = new LinearLayout.LayoutParams(dp(85),ViewGroup.LayoutParams.WRAP_CONTENT);
        f.addView(lbl,llbl);
        LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(0,dp(14),1f); lpb.setMargins(dp(4),0,dp(6),0);
        f.addView(pb,lpb);
        tvPct.setTextSize(11); tvPct.setTextColor(C_GRIS); tvPct.setMinWidth(dp(30));
        f.addView(tvPct,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return f;
    }
 
    private ProgressBar crearBarraStat() {
        ProgressBar pb = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100); pb.setProgress(50);
        pb.setProgressDrawable(barDrawable(C_VERDE));
        return pb;
    }
 
    private Drawable barDrawable(int color) {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{rectRedondeado(0xFF222244,dp(6)),rectRedondeado(color,dp(6))});
        ld.setId(0,android.R.id.background); ld.setId(1,android.R.id.progress); return ld;
    }
    private GradientDrawable rectRedondeado(int color,int radio){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.RECTANGLE);g.setCornerRadius(radio);g.setColor(color);return g;}
 
    private void setStatText(TextView tv, String text) { if(tv!=null) tv.setText(text); }
 
    private void reproducirSonido() {
        new Thread(() -> {
            try {
                int sr=44100,ms=400,n=sr*ms/1000; short[]s=new short[n];
                for(int i=0;i<n;i++){double t=(double)i/sr,p=(double)i/n;
                    double f=animal.equals("cat")?620:animal.equals("dog")?350:720;
                    double a=p<0.1?p/0.1:p>0.75?(1-p)/0.25:1.0;
                    s[i]=(short)(a*0.7*Math.sin(2*Math.PI*f*t)*Short.MAX_VALUE);}
                AudioTrack at=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(s.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();
                at.write(s,0,s.length);at.play();Thread.sleep(ms+80);at.stop();at.release();
            } catch(Exception ignored){}
        }).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  PÁGINA 1 — CONFIGURACIÓN
    // ─────────────────────────────────────────────────────────────────────────
    private ScrollView paginaConfig() {
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(C_FONDO);sc.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(20));
        root.addView(encabezado());root.addView(divisor(),margen(0,0,0,18));
        root.addView(etiqueta("✏️  Nombre"),margen(0,0,0,10));root.addView(seccionNombre(),margen(0,0,0,20));
        root.addView(etiqueta("🐾  Animal"),margen(0,0,0,10));root.addView(gridAnimales(),margen(0,0,0,20));
        root.addView(etiqueta("🔊  Voz"),margen(0,0,0,10));root.addView(seccionVoz(),margen(0,0,0,20));
        root.addView(etiqueta("🔑  Permisos"),margen(0,0,0,10));secPermisos=seccionPermisos();root.addView(secPermisos,margen(0,0,0,20));
        btnActivar=botonActivar();root.addView(btnActivar,margen(0,0,0,10));
        TextView n=new TextView(this);n.setText("Desliza a la derecha para alimentar 🍖");n.setTextColor(C_GRIS);n.setTextSize(12);n.setGravity(Gravity.CENTER);root.addView(n);
        sc.addView(root);return sc;
    }
 
    private LinearLayout encabezado(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);FrameLayout ci=new FrameLayout(this);GradientDrawable fc=new GradientDrawable();fc.setShape(GradientDrawable.OVAL);fc.setColor(0xFF2A2A4A);fc.setStroke(dp(3),C_ORO);ci.setBackground(fc);TextView eg=new TextView(this);eg.setText(emojiAnimal());eg.setTextSize(44);eg.setGravity(Gravity.CENTER);FrameLayout.LayoutParams pe=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);pe.gravity=Gravity.CENTER;ci.addView(eg,pe);int sz=dp(80);LinearLayout.LayoutParams pc=new LinearLayout.LayoutParams(sz,sz);pc.gravity=Gravity.CENTER;pc.setMargins(0,0,0,dp(10));c.addView(ci,pc);TextView t=new TextView(this);t.setText(nombre.isEmpty()?"Quacky":capitalize(nombre));t.setTextSize(28);t.setTypeface(null,Typeface.BOLD);t.setTextColor(C_ORO);t.setGravity(Gravity.CENTER);c.addView(t,margen(0,0,0,16));return c;}
    private LinearLayout seccionNombre(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(14));bg.setColor(C_CARD);bg.setStroke(dp(2),C_MORADO);c.setBackground(bg);c.setPadding(dp(14),dp(14),dp(14),dp(14));TextView d=new TextView(this);d.setText("El nombre que pongas aquí lo usará la mascota para presentarse y para activarse con tu voz.");d.setTextColor(C_GRIS);d.setTextSize(12);c.addView(d,margen(0,0,0,10));LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);etNombre=new EditText(this);etNombre.setHint("Ej: Panchito, Michi...");etNombre.setHintTextColor(0xFF555577);etNombre.setText(nombre);etNombre.setTextColor(C_BLANCO);etNombre.setTextSize(15);etNombre.setTypeface(null,Typeface.BOLD);etNombre.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);etNombre.setSingleLine(true);GradientDrawable eb=new GradientDrawable();eb.setCornerRadius(dp(10));eb.setColor(0xFF1E1E40);eb.setStroke(dp(1),0xFF3A3A6A);etNombre.setBackground(eb);etNombre.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);ep.setMargins(0,0,dp(10),0);f.addView(etNombre,ep);Button bs=new Button(this);bs.setText("💾");bs.setTextSize(18);bs.setAllCaps(false);bs.setTextColor(C_BLANCO);GradientDrawable bb=new GradientDrawable();bb.setCornerRadius(dp(10));bb.setColor(C_MORADO);bs.setBackground(bb);bs.setOnClickListener(v->guardarNombre());f.addView(bs,new LinearLayout.LayoutParams(dp(52),dp(46)));c.addView(f,margen(0,0,0,8));tvNombrePreview=new TextView(this);actualizarPreviewNombre();tvNombrePreview.setTextSize(12);tvNombrePreview.setGravity(Gravity.CENTER);c.addView(tvNombrePreview);return c;}
    private void guardarNombre(){nombre=etNombre.getText().toString().trim().toLowerCase();getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("nombre_mascota",nombre).apply();actualizarPreviewNombre();InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(etNombre.getWindowToken(),0);Toast.makeText(this,"✅ "+capitalize(nombre.isEmpty()?"Quacky":nombre),Toast.LENGTH_SHORT).show();}
    private void actualizarPreviewNombre(){if(tvNombrePreview==null)return;if(nombre.isEmpty()){tvNombrePreview.setText("⚠️ Sin nombre");tvNombrePreview.setTextColor(C_NARANJA);}else{tvNombrePreview.setText("✅ Di \""+capitalize(nombre)+"\" para activar");tvNombrePreview.setTextColor(C_VERDE);}}
    private LinearLayout gridAnimales(){LinearLayout g=new LinearLayout(this);g.setOrientation(LinearLayout.VERTICAL);for(int i=0;i<ANIMALES.length;i+=2){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.addView(cardAnimal(ANIMALES[i]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));f.addView(new View(this),new LinearLayout.LayoutParams(dp(10),0));if(i+1<ANIMALES.length)f.addView(cardAnimal(ANIMALES[i+1]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));else f.addView(new View(this),new LinearLayout.LayoutParams(0,0,1f));LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(8));g.addView(f,pf);}return g;}
    private LinearLayout cardAnimal(String[]a){String id=a[0],emoji=a[1],nom=a[2];boolean disp=a[3].equals("si"),sel=id.equals(animal)&&disp;LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(10),dp(12),dp(10),dp(12));aplicarFondoCard(c,sel,disp);TextView te=new TextView(this);te.setText(emoji);te.setTextSize(30);te.setGravity(Gravity.CENTER);te.setAlpha(disp?1f:0.4f);c.addView(te,margen(0,0,0,4));TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(12);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(disp?C_BLANCO:C_GRIS);c.addView(tn,margen(0,0,0,2));TextView tb=new TextView(this);tb.setText(sel?"✓ Selec.":disp?"Disponible":"Próximo");tb.setTextColor(sel?C_ORO:disp?C_VERDE:C_GRIS);tb.setTextSize(9);tb.setGravity(Gravity.CENTER);c.addView(tb);if(sel)cardAnimalSel=c;c.setOnClickListener(v->{if(!disp){Toast.makeText(this,"¡"+nom+" pronto!",Toast.LENGTH_SHORT).show();return;}seleccionarAnimal(id,c);});return c;}
    private void aplicarFondoCard(LinearLayout c,boolean sel,boolean disp){GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));if(!disp){f.setColor(0xFF141428);f.setStroke(dp(1),0xFF222240);}else if(sel){f.setColor(C_SEL);f.setStroke(dp(2),C_ORO);}else{f.setColor(C_CARD);f.setStroke(dp(1),0xFF2A2A4A);}c.setBackground(f);}
    private void seleccionarAnimal(String id,LinearLayout cn){if(cardAnimalSel!=null&&cardAnimalSel!=cn){aplicarFondoCard(cardAnimalSel,false,true);}aplicarFondoCard(cn,true,true);Animation r=new ScaleAnimation(0.92f,1f,0.92f,1f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);r.setDuration(150);cn.startAnimation(r);cardAnimalSel=cn;animal=id;getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("animal",id).apply();}
    private LinearLayout seccionVoz(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);cardVozM=cardVoz("masculina","👨","Masculina",C_AZUL);f.addView(cardVozM,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));f.addView(new View(this),new LinearLayout.LayoutParams(dp(10),0));cardVozF=cardVoz("femenina","👩","Femenina",C_ROSA);f.addView(cardVozF,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));actualizarVoz(voz,false);return f;}
    private LinearLayout cardVoz(String tipo,String emoji,String nom,int ac){boolean sel=tipo.equals(voz);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(12),dp(14),dp(12),dp(14));GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));f.setColor(sel?0xFF1E2A3A:C_CARD);f.setStroke(dp(sel?2:1),sel?ac:0xFF2A2A4A);c.setBackground(f);TextView te=new TextView(this);te.setText(emoji);te.setTextSize(28);te.setGravity(Gravity.CENTER);c.addView(te,margen(0,0,0,6));TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(13);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(sel?ac:C_BLANCO);c.addView(tn,margen(0,0,0,4));TextView tb=new TextView(this);tb.setText(sel?"✓ Activa":"Elegir");tb.setTextColor(sel?ac:C_GRIS);tb.setTextSize(10);tb.setGravity(Gravity.CENTER);c.addView(tb);c.setOnClickListener(v->actualizarVoz(tipo,true));return c;}
    private void actualizarVoz(String tipo,boolean guardar){voz=tipo;if(guardar)getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("voz",tipo).apply();actualizarEstiloVoz(cardVozM,"masculina",C_AZUL);actualizarEstiloVoz(cardVozF,"femenina",C_ROSA);}
    private void actualizarEstiloVoz(LinearLayout c,String tipo,int ac){if(c==null)return;boolean sel=tipo.equals(voz);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));f.setColor(sel?0xFF1E2A3A:C_CARD);f.setStroke(dp(sel?2:1),sel?ac:0xFF2A2A4A);c.setBackground(f);if(c.getChildCount()>=2)((TextView)c.getChildAt(1)).setTextColor(sel?ac:C_BLANCO);if(c.getChildCount()>=3){TextView b=(TextView)c.getChildAt(2);b.setText(sel?"✓ Activa":"Elegir");b.setTextColor(sel?ac:C_GRIS);}}
    private LinearLayout seccionPermisos(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));f.setColor(C_CARD);f.setStroke(dp(1),0xFF2A2A4A);c.setBackground(f);c.setPadding(dp(14),dp(14),dp(14),dp(14));String[][]ps={{"🖥️","Mostrar sobre apps","Flota sobre tu pantalla"},{"🎤","Micrófono","Para escucharte"},{"👥","Contactos","Para llamar"},{"📞","Teléfono","Para marcar"}};for(String[]p:ps)c.addView(filaPermiso(p[0],p[1],p[2]));Button b=new Button(this);b.setText("Conceder permisos");b.setTextColor(C_ORO);b.setTextSize(12);b.setBackgroundColor(Color.TRANSPARENT);b.setAllCaps(false);b.setOnClickListener(v->solicitarPermisos());LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pb.setMargins(0,dp(6),0,0);c.addView(b,pb);return c;}
    private LinearLayout filaPermiso(String ic,String nom,String desc){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(8));f.setLayoutParams(pf);TextView ti=new TextView(this);ti.setText(ic);ti.setTextSize(18);ti.setGravity(Gravity.CENTER);f.addView(ti,new LinearLayout.LayoutParams(dp(30),dp(30)));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams pt=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);pt.setMargins(dp(8),0,0,0);TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(12);tn.setTypeface(null,Typeface.BOLD);tn.setTextColor(C_BLANCO);t.addView(tn);TextView td=new TextView(this);td.setText(desc);td.setTextSize(10);td.setTextColor(C_GRIS);t.addView(td);f.addView(t,pt);TextView e=new TextView(this);e.setTag("e_"+nom);boolean ok=verificarPermiso(nom);e.setText(ok?"✓":"✗");e.setTextColor(ok?C_VERDE:C_ROJO);e.setTextSize(15);e.setTypeface(null,Typeface.BOLD);f.addView(e,new LinearLayout.LayoutParams(dp(24),dp(24)));return f;}
    private boolean verificarPermiso(String nom){switch(nom){case"Mostrar sobre apps":return Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(this);case"Micrófono":return ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;case"Contactos":return ContextCompat.checkSelfPermission(this,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;case"Teléfono":return ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;default:return false;}}
    private void actualizarPermisos(){if(secPermisos!=null)buscarActualizar(secPermisos);}
    private void buscarActualizar(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View h=g.getChildAt(i);if(h instanceof TextView){Object t=h.getTag();if(t instanceof String&&((String)t).startsWith("e_")){String n=((String)t).replace("e_","");boolean ok=verificarPermiso(n);((TextView)h).setText(ok?"✓":"✗");((TextView)h).setTextColor(ok?C_VERDE:C_ROJO);}}else if(h instanceof ViewGroup)buscarActualizar((ViewGroup)h);}}
    private Button botonActivar(){Button b=new Button(this);b.setText("🚀  Activar "+emojiAnimal());b.setTextSize(16);b.setTypeface(null,Typeface.BOLD);b.setTextColor(C_FONDO);b.setAllCaps(false);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(50));f.setColor(C_ORO);b.setBackground(f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));b.setLayoutParams(p);b.setOnClickListener(v->activar());return b;}
    private void activar(){if(etNombre!=null&&!etNombre.getText().toString().trim().isEmpty())guardarNombre();boolean ok=verificarPermiso("Mostrar sobre apps")&&verificarPermiso("Micrófono")&&verificarPermiso("Contactos")&&verificarPermiso("Teléfono");if(!ok){solicitarPermisos();return;}stopService(new Intent(this,DuckOverlayService.class));new Handler().postDelayed(()->{Intent s=new Intent(this,DuckOverlayService.class);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)startForegroundService(s);else startService(s);moveTaskToBack(true);},600);}
    private void solicitarPermisos(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("🖥️ Permiso de pantalla").setMessage("Busca 'Quacky' y activa el interruptor.").setPositiveButton("Ajustes",(d,w)->startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY_REQUEST)).setNegativeButton("Cancelar",null).show();return;}String[]perms={Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE};boolean falta=false;for(String p:perms)if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED){falta=true;break;}if(falta)new AlertDialog.Builder(this).setTitle("🎤 Permisos").setMessage("Necesito micrófono, contactos y teléfono.").setPositiveButton("Conceder",(d,w)->ActivityCompat.requestPermissions(this,perms,PERM_REQUEST)).setNegativeButton("Ahora no",null).show();}
    @Override public void onRequestPermissionsResult(int req,String[]perms,int[]res){super.onRequestPermissionsResult(req,perms,res);if(req==PERM_REQUEST){actualizarPermisos();if(verificarPermiso("Mostrar sobre apps")&&verificarPermiso("Micrófono")&&verificarPermiso("Contactos")&&verificarPermiso("Teléfono"))activar();}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==OVERLAY_REQUEST){actualizarPermisos();if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&Settings.canDrawOverlays(this))solicitarPermisos();}}
 
    private String emojiAnimal(){for(String[]a:ANIMALES)if(a[0].equals(animal))return a[1];return"🦆";}
    private String capitalize(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private View divisor(){View d=new View(this);d.setBackgroundColor(0xFF2A2A4A);return d;}
    private TextView etiqueta(String t){TextView tv=new TextView(this);tv.setText(t);tv.setTextSize(14);tv.setTypeface(null,Typeface.BOLD);tv.setTextColor(C_BLANCO);return tv;}
    private LinearLayout.LayoutParams margen(int i,int a,int d,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(i),dp(a),dp(d),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
 
