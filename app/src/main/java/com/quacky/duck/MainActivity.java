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
 
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
 
    private static final int PERM_REQUEST    = 101;
    private static final int OVERLAY_REQUEST = 102;
 
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
 
    // comida: {emoji, nombre, hambre, felicidad, energía, preferencia}
    private static final Object[][] COMIDAS = {
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
 
    private String animal  = "duck";
    private String voz     = "femenina";
    private String nombre  = "";
 
    // Config page
    private LinearLayout cardAnimalSel, cardVozM, cardVozF, secPermisos;
    private Button       btnActivar;
    private EditText     etNombre;
    private TextView     tvNombrePreview;
 
    // Feeding page
    private ProgressBar pbHambre, pbFelicidad, pbEnergia;
    private TextView    tvPctH, tvPctF, tvPctE;
    private TextView    tvEmojiMascota, tvBurbujaMascota;
    private TextView    dragView;       // vista que sigue el dedo
    private FrameLayout feedContainer;  // contenedor del drag
    private float       dragStartY;     // Y donde empezó el arrastre
 
    private Handler  uiHandler = new Handler(Looper.getMainLooper());
    private Runnable tickStats;
 
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
 
    @Override protected void onResume() {
        super.onResume();
        if (secPermisos != null) actualizarPermisos();
        iniciarTickStats();
    }
    @Override protected void onPause() {
        super.onPause();
        if (tickStats != null) uiHandler.removeCallbacks(tickStats);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ESTRUCTURA PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────────
    private void construirUI() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(C_FONDO);
 
        // Tab bar
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(0xFF08081A);
        final TextView tC = textoTab("⚙️  Configurar", true);
        final TextView tF = textoTab("🍖  Alimentar", false);
        tabs.addView(tC, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View div = new View(this); div.setBackgroundColor(0xFF2A2A4A);
        tabs.addView(div, new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
        tabs.addView(tF, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
 
        ViewPager pager = new ViewPager(this);
        pager.setOffscreenPageLimit(2); // mantener ambas páginas en memoria
        pager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return 2; }
            @Override public boolean isViewFromObject(View v, Object o) { return v == o; }
            @Override public Object instantiateItem(ViewGroup c, int pos) {
                View p = pos == 0 ? paginaConfig() : paginaComida();
                c.addView(p); return p;
            }
            @Override public void destroyItem(ViewGroup c, int pos, Object obj) {
                c.removeView((View) obj);
            }
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int pos) {
                tC.setTextColor(pos == 0 ? C_ORO : C_GRIS);
                tF.setTextColor(pos == 1 ? C_ORO : C_GRIS);
                for (int i = 0; i < pts.length; i++) pts[i].setTextColor(i == pos ? C_ORO : C_GRIS);
                if (pos == 1) actualizarStats(); // refrescar al entrar a la página de comida
            }
        });
 
        LinearLayout puntos = new LinearLayout(this);
        puntos.setGravity(Gravity.CENTER); puntos.setPadding(0, dp(2), 0, dp(4));
        for (int i = 0; i < 2; i++) {
            pts[i] = new TextView(this); pts[i].setText("●"); pts[i].setTextSize(9);
            pts[i].setPadding(dp(5), 0, dp(5), 0); pts[i].setTextColor(i == 0 ? C_ORO : C_GRIS);
            puntos.addView(pts[i]);
        }
 
        raiz.addView(tabs, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        raiz.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        raiz.addView(puntos, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(raiz);
    }
 
    private TextView textoTab(String txt, boolean activo) {
        TextView tv = new TextView(this); tv.setText(txt);
        tv.setGravity(Gravity.CENTER); tv.setTextSize(13);
        tv.setPadding(0, dp(10), 0, dp(10));
        tv.setTextColor(activo ? C_ORO : C_GRIS);
        return tv;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  PÁGINA DE COMIDA
    //  ⚠️ Sin ScrollView — todo en un LinearLayout para que el drag funcione
    // ─────────────────────────────────────────────────────────────────────────
    private FrameLayout paginaComida() {
        // FrameLayout raíz para poder superponer el dragView encima de todo
        feedContainer = new FrameLayout(this);
        feedContainer.setBackgroundColor(C_FONDO);
 
        // Contenido principal: LinearLayout vertical SIN ScrollView
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(12), dp(8), dp(12), dp(4));
 
        // ─── 1. Stats compactos (top) ─────────────────────────────────────
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bgStats = new GradientDrawable();
        bgStats.setCornerRadius(dp(14)); bgStats.setColor(C_CARD); bgStats.setStroke(dp(1), 0xFF2A2A5A);
        statsRow.setBackground(bgStats);
 
        pbHambre    = miniBar(); tvPctH = miniPct();
        pbFelicidad = miniBar(); tvPctF = miniPct();
        pbEnergia   = miniBar(); tvPctE = miniPct();
 
        statsRow.addView(miniStatCol("😋 Hambre",    pbHambre,    tvPctH));
        statsRow.addView(miniStatCol("😊 Felicidad", pbFelicidad, tvPctF));
        statsRow.addView(miniStatCol("⚡ Energía",   pbEnergia,   tvPctE));
        main.addView(statsRow, mrgn(0, 0, 0, 8));
 
        // ─── 2. Zona de la mascota (zona de drop, flexible) ───────────────
        LinearLayout petZone = new LinearLayout(this);
        petZone.setOrientation(LinearLayout.VERTICAL);
        petZone.setGravity(Gravity.CENTER);
        petZone.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bgPet = new GradientDrawable();
        bgPet.setCornerRadius(dp(20)); bgPet.setColor(0xFF0D0D20); bgPet.setStroke(dp(2), 0xFF3A3A7A);
        petZone.setBackground(bgPet);
 
        tvEmojiMascota = new TextView(this);
        tvEmojiMascota.setText(emojiAnimal()); tvEmojiMascota.setTextSize(64);
        tvEmojiMascota.setGravity(Gravity.CENTER); petZone.addView(tvEmojiMascota, mrgn(0, 0, 0, 6));
 
        tvBurbujaMascota = new TextView(this);
        tvBurbujaMascota.setText("¡Arrastra comida HACIA ARRIBA hasta aquí! ⬆️");
        tvBurbujaMascota.setTextColor(C_BLANCO); tvBurbujaMascota.setTextSize(12);
        tvBurbujaMascota.setGravity(Gravity.CENTER);
        GradientDrawable bgBurb = new GradientDrawable();
        bgBurb.setCornerRadius(dp(12)); bgBurb.setColor(0xEE1A1A3A); bgBurb.setStroke(dp(1), C_ORO);
        tvBurbujaMascota.setBackground(bgBurb); tvBurbujaMascota.setPadding(dp(10), dp(6), dp(10), dp(6));
        petZone.addView(tvBurbujaMascota);
 
        main.addView(petZone, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
 
        // ─── 3. Instrucción de arrastre ───────────────────────────────────
        TextView instruccion = new TextView(this);
        instruccion.setText("⬆️  Mantén presionada una comida y arrástrala hacia la mascota");
        instruccion.setTextColor(0xFF666699); instruccion.setTextSize(11);
        instruccion.setGravity(Gravity.CENTER); instruccion.setPadding(0, dp(4), 0, dp(4));
        main.addView(instruccion);
 
        // ─── 4. Grid de comidas (bottom, NO scrollable) ───────────────────
        // Usa un GridLayout de 4 columnas con altura fija
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setBackgroundColor(C_FONDO);
 
        for (int i = 0; i < COMIDAS.length; i++) {
            View item = crearItemComida(COMIDAS[i]);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                GridLayout.spec(i / 4), GridLayout.spec(i % 4, 1f));
            glp.width  = 0;
            glp.height = dp(68);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(item, glp);
        }
        main.addView(grid, mrgn(0, 4, 0, 0));
 
        // Leyenda
        LinearLayout leyenda = new LinearLayout(this);
        leyenda.setGravity(Gravity.CENTER);
        for (String l : new String[]{"❤️ Fav","😊 Gusta","😐 Normal","🤢 Asco"}) {
            TextView lt = new TextView(this); lt.setText(l); lt.setTextColor(C_GRIS);
            lt.setTextSize(9); lt.setPadding(dp(3), dp(2), dp(3), dp(2));
            leyenda.addView(lt);
        }
        main.addView(leyenda);
 
        // Agregar el LinearLayout al FrameLayout
        feedContainer.addView(main,
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
 
        // ─── 5. Vista flotante que sigue el dedo durante el drag ─────────
        dragView = new TextView(this);
        dragView.setTextSize(40); dragView.setGravity(Gravity.CENTER);
        dragView.setVisibility(View.GONE);
        dragView.setElevation(dp(20));
        feedContainer.addView(dragView, new FrameLayout.LayoutParams(dp(60), dp(60)));
 
        actualizarStats();
        mostrarAntojo();
        return feedContainer;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Item de comida con drag SIMPLE:
    //  - Mantén presionado y arrastra hacia arriba
    //  - Si subiste >= 80dp cuando sueltas → alimentar
    //  - No necesita coordenadas absolutas de la mascota
    // ─────────────────────────────────────────────────────────────────────────
    private View crearItemComida(Object[] comida) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
 
        String pref = (String) comida[5];
        int bgColor   = pref.equals("love") ? 0xFF2A1520 : pref.equals("like") ? 0xFF152015 : pref.equals("dislike") ? 0xFF2A2010 : C_CARD;
        int bordColor = pref.equals("love") ? Color.parseColor("#FF4444") : pref.equals("like") ? Color.parseColor("#44AA44") : pref.equals("dislike") ? Color.parseColor("#887722") : Color.parseColor("#333366");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10)); bg.setColor(bgColor); bg.setStroke(dp(1), bordColor);
        card.setBackground(bg);
        card.setPadding(dp(2), dp(4), dp(2), dp(2));
 
        TextView emoji = new TextView(this);
        emoji.setText((String) comida[0]); emoji.setTextSize(24); emoji.setGravity(Gravity.CENTER);
        card.addView(emoji);
 
        String prefEmoji = pref.equals("love") ? "❤️" : pref.equals("like") ? "😊" : pref.equals("dislike") ? "🤢" : "😐";
        TextView pe = new TextView(this); pe.setText(prefEmoji); pe.setTextSize(9); pe.setGravity(Gravity.CENTER);
        card.addView(pe);
 
        card.setOnTouchListener(new DragListener(comida));
        return card;
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  DragListener — detección simple de arrastre hacia arriba
    // ─────────────────────────────────────────────────────────────────────────
    private class DragListener implements View.OnTouchListener {
        private final Object[] comida;
        private float startRawY;
 
        DragListener(Object[] c) { this.comida = c; }
 
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startRawY = e.getRawY();
                    // Mostrar vista de drag encima del dedo
                    if (dragView != null) {
                        dragView.setText((String) comida[0]);
                        dragView.setVisibility(View.VISIBLE);
                        moverDragView(e);
                    }
                    v.setAlpha(0.4f);
                    // Crecer la mascota indicando que es el destino
                    if (tvEmojiMascota != null)
                        tvEmojiMascota.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
                    return true;
 
                case MotionEvent.ACTION_MOVE:
                    moverDragView(e);
                    // Si ya subió suficiente: resaltar más la mascota
                    float subida = startRawY - e.getRawY();
                    if (tvEmojiMascota != null) {
                        float sc = subida > dp(60) ? 1.5f : 1.2f;
                        tvEmojiMascota.setScaleX(sc); tvEmojiMascota.setScaleY(sc);
                    }
                    if (tvBurbujaMascota != null) {
                        tvBurbujaMascota.setText(subida > dp(60) ? "¡Suelta aquí! 🎉" : "⬆️ Sube más...");
                    }
                    return true;
 
                case MotionEvent.ACTION_UP:
                    // Ocultar drag view y restaurar
                    if (dragView != null) dragView.setVisibility(View.GONE);
                    v.setAlpha(1f);
                    if (tvEmojiMascota != null)
                        tvEmojiMascota.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    if (tvBurbujaMascota != null)
                        tvBurbujaMascota.setText("¡Arrastra comida HACIA ARRIBA hasta aquí! ⬆️");
 
                    // ✅ DETECTAR SI SE SUBIÓ LO SUFICIENTE
                    float distanciaSubida = startRawY - e.getRawY();
                    if (distanciaSubida >= dp(80)) { // al menos 80dp hacia arriba
                        alimentarConComida(comida);
                    }
                    return true;
 
                case MotionEvent.ACTION_CANCEL:
                    if (dragView != null) dragView.setVisibility(View.GONE);
                    v.setAlpha(1f);
                    if (tvEmojiMascota != null)
                        tvEmojiMascota.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    if (tvBurbujaMascota != null)
                        tvBurbujaMascota.setText("¡Arrastra comida HACIA ARRIBA hasta aquí! ⬆️");
                    return true;
            }
            return false;
        }
 
        private void moverDragView(MotionEvent e) {
            if (dragView == null || feedContainer == null) return;
            int[] loc = new int[2];
            feedContainer.getLocationOnScreen(loc);
            dragView.setX(e.getRawX() - loc[0] - dp(30));
            dragView.setY(e.getRawY() - loc[1] - dp(30));
        }
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  ALIMENTAR CON COMIDA
    // ─────────────────────────────────────────────────────────────────────────
    private void alimentarConComida(Object[] comida) {
        String emoji  = (String) comida[0];
        String nomC   = (String) comida[1];
        int    hBonus = (int)    comida[2];
        int    fBonus = (int)    comida[3];
        int    eBonus = (int)    comida[4];
        String pref   = (String) comida[5];
 
        // Guardar nueva comida
        SharedPreferences prefs = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        int felNow = Math.max(0, Math.min(100, prefs.getInt("felicidad", 70) + fBonus));
        int eneNow = Math.max(0, Math.min(100, prefs.getInt("energia", 80) + eBonus));
        prefs.edit().putLong("ultima_comida", System.currentTimeMillis())
            .putInt("felicidad", felNow).putInt("energia", eneNow).apply();
 
        // Animar emoji mascota
        if (tvEmojiMascota != null) {
            tvEmojiMascota.animate().scaleX(1.5f).scaleY(1.5f).setDuration(200)
                .withEndAction(() -> tvEmojiMascota.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
        }
 
        // Actualizar stats
        actualizarStats();
 
        // Sonido
        reproducirSonido();
 
        // Mostrar reacción inmediata
        String[] reacs;
        switch (pref) {
            case "love":    reacs = new String[]{"¡¡SÍ!! " + emoji + " ¡¡Lo amo!! ❤️","¡¡MI FAVORITO!! " + emoji + " ❤️❤️"}; break;
            case "like":    reacs = new String[]{"¡Qué rico! " + emoji + " 😊","¡Mmm! " + emoji + " ¡Me gusta! 😊"}; break;
            case "dislike": reacs = new String[]{"Ugh... " + emoji + " 🤢 Bueno...","No me gusta " + emoji + " pero gracias 🤢"}; break;
            default:        reacs = new String[]{"Gracias " + emoji + " 😐","Está bien " + emoji + " 😐"}; break;
        }
        mostrarBurbuja(reacs[new Random().nextInt(reacs.length)]);
 
        // Avisar al servicio
        Intent alim = new Intent("com.quacky.duck.ALIMENTAR");
        alim.setPackage(getPackageName());
        alim.putExtra("comida_emoji", emoji); alim.putExtra("comida_nombre", nomC);
        alim.putExtra("hambre_bonus", hBonus); alim.putExtra("felicidad_bonus", fBonus);
        alim.putExtra("energia_bonus", eBonus); alim.putExtra("preferencia", pref);
        sendBroadcast(alim);
 
        // Respuesta de IA desde Groq
        generarRespuestaIA(emoji, nomC, pref);
 
        // Toast de éxito
        Toast.makeText(this, emojiAnimal() + " ¡Gracias! " + emoji, Toast.LENGTH_SHORT).show();
 
        // Nuevo antojo
        uiHandler.postDelayed(this::mostrarAntojo, 8000);
    }
 
    private void generarRespuestaIA(String emoji, String nomC, String pref) {
        String nomM = nombre.isEmpty() ? "Quacky" : capitalize(nombre);
        String tipo = animal.equals("cat") ? "gato" : animal.equals("dog") ? "perro" : "pato";
        String sent;
        switch (pref) { case "love": sent="¡es tu FAVORITA!"; break; case "like": sent="te gusta bastante"; break; case "dislike": sent="no te gusta nada pero te la comiste"; break; default: sent="es normal para ti"; }
        String prompt = "Eres " + nomM + ", un " + tipo + " virtual. Acabas de comer " + emoji + " " + nomC + " que " + sent + ". UNA oración corta, divertida, en español mexicano.";
        new Thread(() -> {
            try {
                JSONArray msgs = new JSONArray();
                JSONObject um = new JSONObject(); um.put("role","user"); um.put("content",prompt); msgs.put(um);
                JSONObject body = new JSONObject(); body.put("model","llama-3.3-70b-versatile"); body.put("max_tokens",60); body.put("messages",msgs);
                URL url = new URL(GROQ_URL); HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Authorization","Bearer "+GROQ_API_KEY);
                c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(10000);
                try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                StringBuilder sb = new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}
                String reply = new JSONObject(sb.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                uiHandler.post(() -> mostrarBurbuja(reply));
            } catch (Exception ignored) {}
        }).start();
    }
 
    private void mostrarAntojo() {
        if (tvBurbujaMascota == null) return;
        int h = nivelHambre();
        String[] msgs = h >= 80
            ? new String[]{"¡Tengo MUCHA hambre! ¡Arrástrare algo! 😭","¡Casi me desmayo de hambre! 😵","¡Por favor dame de comer! ¡Cualquier cosa! 🙏"}
            : h >= 50
            ? new String[]{"¡Quiero pizza! 🍕","¡Antojo de chocolate! 🍫","¡Me provoca algo rico! 🌮","¡Tengo hambrín! 😋"}
            : new String[]{"¡Estoy muy bien alimentada! 😊","¡Qué feliz estoy! ❤️","¡Gracias por cuidarme! 🌟"};
        mostrarBurbuja(msgs[new Random().nextInt(msgs.length)]);
    }
 
    private void mostrarBurbuja(String txt) {
        if (tvBurbujaMascota == null) return;
        tvBurbujaMascota.setAlpha(0f); tvBurbujaMascota.setText(txt);
        tvBurbujaMascota.animate().alpha(1f).setDuration(300).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  STATS
    // ─────────────────────────────────────────────────────────────────────────
    private int nivelHambre() {
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        long ult = p.getLong("ultima_comida", System.currentTimeMillis());
        return (int) Math.min(100, (System.currentTimeMillis() - ult) / 60000L * 100 / 30);
    }
 
    private void actualizarStats() {
        if (pbHambre == null) return;
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        int h = nivelHambre();
        int f = p.getInt("felicidad", 70);
        int e = p.getInt("energia",   80);
        setBar(pbHambre,    tvPctH, h, h >= 80 ? C_ROJO : h >= 50 ? C_NARANJA : C_VERDE);
        setBar(pbFelicidad, tvPctF, f, f >= 60 ? C_ROSA : f >= 30 ? C_NARANJA : C_ROJO);
        setBar(pbEnergia,   tvPctE, e, C_AZUL);
    }
 
    private void setBar(ProgressBar pb, TextView tv, int val, int color) {
        if (pb == null) return;
        pb.setProgressDrawable(barDraw(color)); pb.setProgress(val);
        if (tv != null) tv.setText(val + "%");
    }
 
    private void iniciarTickStats() {
        tickStats = new Runnable() { @Override public void run() { actualizarStats(); uiHandler.postDelayed(this, 15000); } };
        uiHandler.post(tickStats);
    }
 
    private void reproducirSonido() {
        new Thread(() -> {
            try {
                int sr = 44100, ms = 400, n = sr * ms / 1000; short[] s = new short[n];
                double f = animal.equals("cat") ? 620 : animal.equals("dog") ? 350 : 720;
                for (int i = 0; i < n; i++) { double p = (double)i/n, a = p<0.1?p/0.1:p>0.75?(1-p)/0.25:1.0; s[i]=(short)(a*0.7*Math.sin(2*Math.PI*f*(double)i/sr)*Short.MAX_VALUE); }
                AudioTrack at = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(s.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();
                at.write(s,0,s.length); at.play(); Thread.sleep(ms+80); at.stop(); at.release();
            } catch (Exception ignored) {}
        }).start();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout miniStatCol(String label, ProgressBar pb, TextView pct) {
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0); col.setLayoutParams(lp);
        TextView lbl = new TextView(this); lbl.setText(label); lbl.setTextColor(C_GRIS); lbl.setTextSize(10); lbl.setGravity(Gravity.CENTER);
        col.addView(lbl);
        LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)); lpb.setMargins(0, dp(2), 0, dp(2));
        col.addView(pb, lpb);
        pct.setGravity(Gravity.CENTER); pct.setTextSize(10); pct.setTextColor(C_GRIS);
        col.addView(pct);
        return col;
    }
    private ProgressBar miniBar() {
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100); pb.setProgress(50); pb.setProgressDrawable(barDraw(C_VERDE)); return pb;
    }
    private TextView miniPct() { TextView tv = new TextView(this); tv.setTextSize(10); tv.setTextColor(C_GRIS); return tv; }
    private Drawable barDraw(int color) {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{rectRed(0xFF222244, dp(4)), rectRed(color, dp(4))});
        ld.setId(0, android.R.id.background); ld.setId(1, android.R.id.progress); return ld;
    }
    private GradientDrawable rectRed(int c, int r) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.RECTANGLE); g.setCornerRadius(r); g.setColor(c); return g; }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  PÁGINA DE CONFIGURACIÓN
    // ─────────────────────────────────────────────────────────────────────────
    private ScrollView paginaConfig() {
        ScrollView sc = new ScrollView(this); sc.setBackgroundColor(C_FONDO); sc.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(20),dp(20),dp(20));
        root.addView(encabezado()); root.addView(divisor(), mrgn(0,0,0,16));
        root.addView(etiqueta("✏️  Nombre"), mrgn(0,0,0,8)); root.addView(seccionNombre(), mrgn(0,0,0,18));
        root.addView(etiqueta("🐾  Animal"), mrgn(0,0,0,8)); root.addView(gridAnimales(), mrgn(0,0,0,18));
        root.addView(etiqueta("🔊  Voz"),    mrgn(0,0,0,8)); root.addView(seccionVoz(), mrgn(0,0,0,18));
        root.addView(etiqueta("🔑  Permisos"),mrgn(0,0,0,8)); secPermisos = seccionPermisos(); root.addView(secPermisos, mrgn(0,0,0,18));
        btnActivar = botonActivar(); root.addView(btnActivar, mrgn(0,0,0,8));
        TextView nota = new TextView(this); nota.setText("Desliza ▶ para alimentar 🍖"); nota.setTextColor(C_GRIS); nota.setTextSize(11); nota.setGravity(Gravity.CENTER);
        root.addView(nota); sc.addView(root); return sc;
    }
 
    private LinearLayout encabezado() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        FrameLayout ci = new FrameLayout(this); GradientDrawable fc = new GradientDrawable(); fc.setShape(GradientDrawable.OVAL); fc.setColor(0xFF2A2A4A); fc.setStroke(dp(3),C_ORO); ci.setBackground(fc);
        TextView eg = new TextView(this); eg.setText(emojiAnimal()); eg.setTextSize(40); eg.setGravity(Gravity.CENTER); FrameLayout.LayoutParams pe = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT); pe.gravity=Gravity.CENTER; ci.addView(eg,pe);
        int sz=dp(76); LinearLayout.LayoutParams pc=new LinearLayout.LayoutParams(sz,sz); pc.gravity=Gravity.CENTER; pc.setMargins(0,0,0,dp(8)); c.addView(ci,pc);
        TextView t=new TextView(this); t.setText(nombre.isEmpty()?"Quacky":capitalize(nombre)); t.setTextSize(26); t.setTypeface(null,Typeface.BOLD); t.setTextColor(C_ORO); t.setGravity(Gravity.CENTER); c.addView(t,mrgn(0,0,0,14)); return c;
    }
    private LinearLayout seccionNombre(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(14));bg.setColor(C_CARD);bg.setStroke(dp(2),C_MORADO);c.setBackground(bg);c.setPadding(dp(14),dp(12),dp(14),dp(12));TextView d=new TextView(this);d.setText("Este nombre activa a tu mascota con tu voz.");d.setTextColor(C_GRIS);d.setTextSize(12);c.addView(d,mrgn(0,0,0,8));LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);etNombre=new EditText(this);etNombre.setHint("Ej: Panchito, Michi...");etNombre.setHintTextColor(0xFF555577);etNombre.setText(nombre);etNombre.setTextColor(C_BLANCO);etNombre.setTextSize(15);etNombre.setTypeface(null,Typeface.BOLD);etNombre.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);etNombre.setSingleLine(true);GradientDrawable eb=new GradientDrawable();eb.setCornerRadius(dp(10));eb.setColor(0xFF1E1E40);eb.setStroke(dp(1),0xFF3A3A6A);etNombre.setBackground(eb);etNombre.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);ep.setMargins(0,0,dp(8),0);f.addView(etNombre,ep);Button bs=new Button(this);bs.setText("💾");bs.setTextSize(18);bs.setAllCaps(false);bs.setTextColor(C_BLANCO);GradientDrawable bb=new GradientDrawable();bb.setCornerRadius(dp(10));bb.setColor(C_MORADO);bs.setBackground(bb);bs.setOnClickListener(v->guardarNombre());f.addView(bs,new LinearLayout.LayoutParams(dp(50),dp(44)));c.addView(f,mrgn(0,0,0,6));tvNombrePreview=new TextView(this);actualizarPreviewNombre();tvNombrePreview.setTextSize(12);tvNombrePreview.setGravity(Gravity.CENTER);c.addView(tvNombrePreview);return c;}
    private void guardarNombre(){nombre=etNombre.getText().toString().trim().toLowerCase();getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("nombre_mascota",nombre).apply();actualizarPreviewNombre();InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(etNombre.getWindowToken(),0);Toast.makeText(this,"✅ "+capitalize(nombre.isEmpty()?"Quacky":nombre),Toast.LENGTH_SHORT).show();}
    private void actualizarPreviewNombre(){if(tvNombrePreview==null)return;if(nombre.isEmpty()){tvNombrePreview.setText("⚠️ Sin nombre configurado");tvNombrePreview.setTextColor(C_NARANJA);}else{tvNombrePreview.setText("✅ Di \""+capitalize(nombre)+"\" para activar");tvNombrePreview.setTextColor(C_VERDE);}}
    private LinearLayout gridAnimales(){LinearLayout g=new LinearLayout(this);g.setOrientation(LinearLayout.VERTICAL);for(int i=0;i<ANIMALES.length;i+=2){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.addView(cardAnimal(ANIMALES[i]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));f.addView(new View(this),new LinearLayout.LayoutParams(dp(8),0));if(i+1<ANIMALES.length)f.addView(cardAnimal(ANIMALES[i+1]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));else f.addView(new View(this),new LinearLayout.LayoutParams(0,0,1f));LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(8));g.addView(f,pf);}return g;}
    private LinearLayout cardAnimal(String[]a){String id=a[0],emoji=a[1],nom=a[2];boolean disp=a[3].equals("si"),sel=id.equals(animal)&&disp;LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(10),dp(12),dp(10),dp(12));aplicarFondoCard(c,sel,disp);TextView te=new TextView(this);te.setText(emoji);te.setTextSize(28);te.setGravity(Gravity.CENTER);te.setAlpha(disp?1f:0.4f);c.addView(te,mrgn(0,0,0,4));TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(12);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(disp?C_BLANCO:C_GRIS);c.addView(tn,mrgn(0,0,0,2));TextView tb=new TextView(this);tb.setText(sel?"✓ Sel.":disp?"Disponible":"Próximo");tb.setTextColor(sel?C_ORO:disp?C_VERDE:C_GRIS);tb.setTextSize(9);tb.setGravity(Gravity.CENTER);c.addView(tb);if(sel)cardAnimalSel=c;c.setOnClickListener(v->{if(!disp){Toast.makeText(this,"¡"+nom+" pronto!",Toast.LENGTH_SHORT).show();return;}seleccionarAnimal(id,c);});return c;}
    private void aplicarFondoCard(LinearLayout c,boolean sel,boolean disp){GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));if(!disp){f.setColor(0xFF141428);f.setStroke(dp(1),0xFF222240);}else if(sel){f.setColor(C_SEL);f.setStroke(dp(2),C_ORO);}else{f.setColor(C_CARD);f.setStroke(dp(1),0xFF2A2A4A);}c.setBackground(f);}
    private void seleccionarAnimal(String id,LinearLayout cn){if(cardAnimalSel!=null&&cardAnimalSel!=cn)aplicarFondoCard(cardAnimalSel,false,true);aplicarFondoCard(cn,true,true);Animation r=new ScaleAnimation(0.93f,1f,0.93f,1f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);r.setDuration(150);cn.startAnimation(r);cardAnimalSel=cn;animal=id;getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("animal",id).apply();}
    private LinearLayout seccionVoz(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);cardVozM=cardVoz("masculina","👨","Masculina",C_AZUL);f.addView(cardVozM,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));f.addView(new View(this),new LinearLayout.LayoutParams(dp(10),0));cardVozF=cardVoz("femenina","👩","Femenina",C_ROSA);f.addView(cardVozF,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));actualizarVoz(voz,false);return f;}
    private LinearLayout cardVoz(String tipo,String emoji,String nom,int ac){boolean sel=tipo.equals(voz);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(12),dp(14),dp(12),dp(14));GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));f.setColor(sel?0xFF1E2A3A:C_CARD);f.setStroke(dp(sel?2:1),sel?ac:0xFF2A2A4A);c.setBackground(f);TextView te=new TextView(this);te.setText(emoji);te.setTextSize(28);te.setGravity(Gravity.CENTER);c.addView(te,mrgn(0,0,0,6));TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(13);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(sel?ac:C_BLANCO);c.addView(tn,mrgn(0,0,0,4));TextView tb=new TextView(this);tb.setText(sel?"✓ Activa":"Elegir");tb.setTextColor(sel?ac:C_GRIS);tb.setTextSize(10);tb.setGravity(Gravity.CENTER);c.addView(tb);c.setOnClickListener(v->actualizarVoz(tipo,true));return c;}
    private void actualizarVoz(String tipo,boolean g){voz=tipo;if(g)getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("voz",tipo).apply();actualizarEstiloVoz(cardVozM,"masculina",C_AZUL);actualizarEstiloVoz(cardVozF,"femenina",C_ROSA);}
    private void actualizarEstiloVoz(LinearLayout c,String tipo,int ac){if(c==null)return;boolean sel=tipo.equals(voz);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));f.setColor(sel?0xFF1E2A3A:C_CARD);f.setStroke(dp(sel?2:1),sel?ac:0xFF2A2A4A);c.setBackground(f);if(c.getChildCount()>=2)((TextView)c.getChildAt(1)).setTextColor(sel?ac:C_BLANCO);if(c.getChildCount()>=3){TextView b=(TextView)c.getChildAt(2);b.setText(sel?"✓ Activa":"Elegir");b.setTextColor(sel?ac:C_GRIS);}}
    private LinearLayout seccionPermisos(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(14));f.setColor(C_CARD);f.setStroke(dp(1),0xFF2A2A4A);c.setBackground(f);c.setPadding(dp(14),dp(14),dp(14),dp(14));String[][]ps={{"🖥️","Mostrar sobre apps","Flota sobre pantalla"},{"🎤","Micrófono","Para escucharte"},{"👥","Contactos","Para llamar"},{"📞","Teléfono","Para marcar"}};for(String[]p:ps)c.addView(filaPermiso(p[0],p[1],p[2]));Button b=new Button(this);b.setText("Conceder permisos");b.setTextColor(C_ORO);b.setTextSize(12);b.setBackgroundColor(Color.TRANSPARENT);b.setAllCaps(false);b.setOnClickListener(v->solicitarPermisos());LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pb.setMargins(0,dp(6),0,0);c.addView(b,pb);return c;}
    private LinearLayout filaPermiso(String ic,String nom,String desc){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(8));f.setLayoutParams(pf);TextView ti=new TextView(this);ti.setText(ic);ti.setTextSize(18);ti.setGravity(Gravity.CENTER);f.addView(ti,new LinearLayout.LayoutParams(dp(28),dp(28)));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams pt=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);pt.setMargins(dp(8),0,0,0);TextView tn=new TextView(this);tn.setText(nom);tn.setTextSize(12);tn.setTypeface(null,Typeface.BOLD);tn.setTextColor(C_BLANCO);t.addView(tn);TextView td=new TextView(this);td.setText(desc);td.setTextSize(10);td.setTextColor(C_GRIS);t.addView(td);f.addView(t,pt);TextView e=new TextView(this);e.setTag("e_"+nom);boolean ok=verificarPermiso(nom);e.setText(ok?"✓":"✗");e.setTextColor(ok?C_VERDE:C_ROJO);e.setTextSize(14);e.setTypeface(null,Typeface.BOLD);f.addView(e,new LinearLayout.LayoutParams(dp(22),dp(22)));return f;}
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
    private TextView etiqueta(String t){TextView tv=new TextView(this);tv.setText(t);tv.setTextSize(13);tv.setTypeface(null,Typeface.BOLD);tv.setTextColor(C_BLANCO);return tv;}
    private LinearLayout.LayoutParams mrgn(int i,int a,int d,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(i),dp(a),dp(d),dp(b));return p;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
 
