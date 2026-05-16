package com.quacky.duck;
 
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
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
 
public class MainActivity extends Activity {
 
    private static final int PERM_REQUEST    = 101;
    private static final int OVERLAY_REQUEST = 102;
 
    private static final int COLOR_FONDO       = 0xFF0F0F23;
    private static final int COLOR_CARD        = 0xFF1A1A3A;
    private static final int COLOR_CARD_SELECT = 0xFF2A2A5A;
    private static final int COLOR_ORO         = 0xFFFFD700;
    private static final int COLOR_BLANCO      = 0xFFFFFFFF;
    private static final int COLOR_GRIS        = 0xFF888888;
    private static final int COLOR_VERDE       = 0xFF4CAF50;
    private static final int COLOR_ROJO        = 0xFFF44336;
    private static final int COLOR_CARD_PROX   = 0xFF141428;
    private static final int COLOR_AZUL        = 0xFF5C9BD4;
    private static final int COLOR_ROSA        = 0xFFE57BB2;
    private static final int COLOR_MORADO      = 0xFF9B59B6;
 
    private static final String[][] ANIMALES = {
        {"duck","🦆","Pato","si"},{"cat","🐱","Gato","si"},{"dog","🐶","Perro","si"},
        {"frog","🐸","Rana","no"},{"hamster","🐹","Hámster","no"},{"fox","🦊","Zorro","no"},
    };
 
    private String       animalSeleccionado = "duck";
    private String       vozSeleccionada    = "femenina";
    private String       nombreMascota      = "";
    private LinearLayout cardAnimalActiva   = null;
    private LinearLayout cardVozMasculina;
    private LinearLayout cardVozFemenina;
    private LinearLayout seccionPermisos;
    private Button       btnActivar;
    private EditText     etNombre;
    private TextView     tvNombrePreview;
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(COLOR_FONDO);
        SharedPreferences p = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        animalSeleccionado = p.getString("animal", "duck");
        vozSeleccionada    = p.getString("voz", "femenina");
        nombreMascota      = p.getString("nombre_mascota", "");
        construirUI();
    }
 
    @Override protected void onResume() { super.onResume(); if(seccionPermisos!=null)actualizarEstadoPermisos(); }
 
    private void construirUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_FONDO);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(48),dp(20),dp(32));
 
        root.addView(crearEncabezado());
        root.addView(crearDivisor(), margen(0,0,0,24));
 
        // ── Nombre de tu mascota ──────────────────────────────────────────
        root.addView(crearEtiquetaSeccion("✏️  Ponle nombre a tu mascota"), margen(0,0,0,14));
        root.addView(crearSeccionNombre(), margen(0,0,0,28));
 
        root.addView(crearEtiquetaSeccion("🐾  Elige tu compañero"), margen(0,0,0,14));
        root.addView(crearGridAnimales(), margen(0,0,0,28));
        root.addView(crearEtiquetaSeccion("🔊  Voz del asistente"), margen(0,0,0,14));
        root.addView(crearSeccionVoz(), margen(0,0,0,28));
        root.addView(crearEtiquetaSeccion("🔑  Permisos necesarios"), margen(0,0,0,14));
        seccionPermisos = crearSeccionPermisos();
        root.addView(seccionPermisos, margen(0,0,0,28));
        btnActivar = crearBotonActivar();
        root.addView(btnActivar, margen(0,0,0,14));
 
        TextView nota = new TextView(this);
        nota.setText("Di el nombre de tu mascota para activarla 🎙️  •  O tócala directamente");
        nota.setTextColor(COLOR_GRIS); nota.setTextSize(12); nota.setGravity(Gravity.CENTER);
        root.addView(nota);
 
        scroll.addView(root);
        setContentView(scroll);
    }
 
    // ── ENCABEZADO ─────────────────────────────────────────────────────────────
    private LinearLayout crearEncabezado() {
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER);
        FrameLayout circulo = new FrameLayout(this);
        GradientDrawable fc = new GradientDrawable(); fc.setShape(GradientDrawable.OVAL); fc.setColor(0xFF2A2A4A); fc.setStroke(dp(3),COLOR_ORO);
        circulo.setBackground(fc);
        TextView eg = new TextView(this); eg.setText(emojiAnimal()); eg.setTextSize(52); eg.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pe = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT); pe.gravity=Gravity.CENTER;
        circulo.addView(eg,pe);
        int sz=dp(100); LinearLayout.LayoutParams pc=new LinearLayout.LayoutParams(sz,sz); pc.gravity=Gravity.CENTER; pc.setMargins(0,0,0,dp(16));
        col.addView(circulo,pc);
        TextView titulo = new TextView(this); titulo.setText(nombreMascota.isEmpty()?"Quacky":capitalize(nombreMascota));
        titulo.setTextSize(38); titulo.setTypeface(null,Typeface.BOLD); titulo.setTextColor(COLOR_ORO); titulo.setGravity(Gravity.CENTER);
        col.addView(titulo, margen(0,0,0,4));
        TextView sub = new TextView(this); sub.setText("Tu asistente IA de bolsillo");
        sub.setTextSize(14); sub.setTextColor(COLOR_GRIS); sub.setGravity(Gravity.CENTER);
        col.addView(sub, margen(0,0,0,24));
        return col;
    }
 
    // ── SECCIÓN NOMBRE ─────────────────────────────────────────────────────────
    private LinearLayout crearSeccionNombre() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(16)); bg.setColor(COLOR_CARD); bg.setStroke(dp(2),COLOR_MORADO);
        card.setBackground(bg); card.setPadding(dp(16),dp(16),dp(16),dp(16));
 
        // Descripción
        TextView desc = new TextView(this);
        desc.setText("Ponle un nombre a tu mascota. Cuando lo digas en voz alta, ¡se activará automáticamente para escucharte!");
        desc.setTextColor(COLOR_GRIS); desc.setTextSize(13);
        card.addView(desc, margen(0,0,0,14));
 
        // Campo de texto
        LinearLayout fila = new LinearLayout(this); fila.setOrientation(LinearLayout.HORIZONTAL); fila.setGravity(Gravity.CENTER_VERTICAL);
 
        etNombre = new EditText(this);
        etNombre.setHint("Ej: Quacky, Michi, Rocky...");
        etNombre.setHintTextColor(0xFF555577);
        etNombre.setText(nombreMascota);
        etNombre.setTextColor(COLOR_BLANCO);
        etNombre.setTextSize(16);
        etNombre.setTypeface(null, Typeface.BOLD);
        etNombre.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        etNombre.setSingleLine(true);
        GradientDrawable etBg = new GradientDrawable(); etBg.setCornerRadius(dp(10)); etBg.setColor(0xFF1E1E40); etBg.setStroke(dp(1),0xFF3A3A6A);
        etNombre.setBackground(etBg);
        etNombre.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        etParams.setMargins(0,0,dp(10),0);
        fila.addView(etNombre, etParams);
 
        Button btnGuardar = new Button(this);
        btnGuardar.setText("💾 Guardar");
        btnGuardar.setTextSize(13); btnGuardar.setAllCaps(false); btnGuardar.setTextColor(0xFF0F0F23);
        GradientDrawable btnBg = new GradientDrawable(); btnBg.setCornerRadius(dp(10)); btnBg.setColor(COLOR_MORADO);
        btnGuardar.setBackground(btnBg);
        btnGuardar.setOnClickListener(v -> guardarNombre());
        fila.addView(btnGuardar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(fila, margen(0,0,0,10));
 
        // Preview del nombre actual
        tvNombrePreview = new TextView(this);
        actualizarPreviewNombre();
        tvNombrePreview.setTextSize(13); tvNombrePreview.setGravity(Gravity.CENTER);
        card.addView(tvNombrePreview);
 
        return card;
    }
 
    private void guardarNombre() {
        String nuevoNombre = etNombre.getText().toString().trim().toLowerCase();
        nombreMascota = nuevoNombre;
        getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("nombre_mascota",nuevoNombre).apply();
        actualizarPreviewNombre();
        // Ocultar teclado
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(imm!=null)imm.hideSoftInputFromWindow(etNombre.getWindowToken(),0);
        Toast.makeText(this, "✅ Nombre guardado: "+capitalize(nuevoNombre.isEmpty()?"Quacky":nuevoNombre), Toast.LENGTH_SHORT).show();
    }
 
    private void actualizarPreviewNombre() {
        if(tvNombrePreview==null)return;
        if(nombreMascota.isEmpty()){
            tvNombrePreview.setText("⚠️ Sin nombre — di 'Quacky' para activar");
            tvNombrePreview.setTextColor(COLOR_NARANJA());
        }else{
            tvNombrePreview.setText("✅ Di \""+capitalize(nombreMascota)+"\" para activar tu mascota");
            tvNombrePreview.setTextColor(COLOR_VERDE);
        }
    }
 
    private int COLOR_NARANJA(){return 0xFFFF9800;}
 
    // ── GRID DE ANIMALES ────────────────────────────────────────────────────────
    private LinearLayout crearGridAnimales(){
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<ANIMALES.length;i+=2){
            LinearLayout fila=new LinearLayout(this);fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.addView(crearCardAnimal(ANIMALES[i]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            fila.addView(new View(this),new LinearLayout.LayoutParams(dp(12),0));
            if(i+1<ANIMALES.length)fila.addView(crearCardAnimal(ANIMALES[i+1]),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            else fila.addView(new View(this),new LinearLayout.LayoutParams(0,0,1f));
            LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(12));
            grid.addView(fila,pf);}return grid;}
 
    private LinearLayout crearCardAnimal(String[]a){
        String id=a[0],emoji=a[1],nombre=a[2];boolean disp=a[3].equals("si"),sel=id.equals(animalSeleccionado)&&disp;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(12),dp(16),dp(12),dp(16));
        aplicarFondoCard(card,sel,disp);
        TextView te=new TextView(this);te.setText(emoji);te.setTextSize(36);te.setGravity(Gravity.CENTER);te.setAlpha(disp?1f:0.4f);card.addView(te,margen(0,0,0,6));
        TextView tn=new TextView(this);tn.setText(nombre);tn.setTextSize(14);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(disp?COLOR_BLANCO:COLOR_GRIS);card.addView(tn,margen(0,0,0,4));
        TextView tb=new TextView(this);tb.setText(sel?"✓ Seleccionado":disp?"Disponible":"Próximamente");tb.setTextColor(sel?COLOR_ORO:disp?COLOR_VERDE:COLOR_GRIS);tb.setTextSize(10);tb.setGravity(Gravity.CENTER);card.addView(tb);
        if(sel)cardAnimalActiva=card;
        card.setOnClickListener(v->{if(!disp){Toast.makeText(this,"¡"+nombre+" llegará pronto!",Toast.LENGTH_SHORT).show();return;}seleccionarAnimal(id,card);});
        return card;}
 
    private void aplicarFondoCard(LinearLayout c,boolean sel,boolean disp){
        GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));
        if(!disp){f.setColor(COLOR_CARD_PROX);f.setStroke(dp(1),0xFF222240);}
        else if(sel){f.setColor(COLOR_CARD_SELECT);f.setStroke(dp(2),COLOR_ORO);}
        else{f.setColor(COLOR_CARD);f.setStroke(dp(1),0xFF2A2A4A);}c.setBackground(f);}
 
    private void seleccionarAnimal(String id,LinearLayout cn){
        if(cardAnimalActiva!=null&&cardAnimalActiva!=cn){aplicarFondoCard(cardAnimalActiva,false,true);if(cardAnimalActiva.getChildCount()>=3)actualizarBadge((TextView)cardAnimalActiva.getChildAt(2),false,true);}
        aplicarFondoCard(cn,true,true);if(cn.getChildCount()>=3)actualizarBadge((TextView)cn.getChildAt(2),true,true);
        Animation r=new ScaleAnimation(0.93f,1f,0.93f,1f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);r.setDuration(150);cn.startAnimation(r);
        cardAnimalActiva=cn;animalSeleccionado=id;getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("animal",id).apply();}
    private void actualizarBadge(TextView b,boolean s,boolean d){b.setText(s?"✓ Seleccionado":d?"Disponible":"Próximamente");b.setTextColor(s?COLOR_ORO:d?COLOR_VERDE:COLOR_GRIS);}
 
    // ── SECCIÓN VOZ ─────────────────────────────────────────────────────────────
    private LinearLayout crearSeccionVoz(){
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);
        cardVozMasculina=crearCardVoz("masculina","👨","Masculina","Voz grave",COLOR_AZUL);
        f.addView(cardVozMasculina,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        f.addView(new View(this),new LinearLayout.LayoutParams(dp(12),0));
        cardVozFemenina=crearCardVoz("femenina","👩","Femenina","Voz aguda",COLOR_ROSA);
        f.addView(cardVozFemenina,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        actualizarSeleccionVoz(vozSeleccionada,false);return f;}
 
    private LinearLayout crearCardVoz(String tipo,String emoji,String nombre,String desc,int acento){
        boolean sel=tipo.equals(vozSeleccionada);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(12),dp(18),dp(12),dp(18));
        GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));f.setColor(sel?0xFF1E2A3A:COLOR_CARD);f.setStroke(dp(sel?2:1),sel?acento:0xFF2A2A4A);card.setBackground(f);
        TextView te=new TextView(this);te.setText(emoji);te.setTextSize(32);te.setGravity(Gravity.CENTER);card.addView(te,margen(0,0,0,6));
        TextView tn=new TextView(this);tn.setText(nombre);tn.setTextSize(15);tn.setTypeface(null,Typeface.BOLD);tn.setGravity(Gravity.CENTER);tn.setTextColor(sel?acento:COLOR_BLANCO);card.addView(tn,margen(0,0,0,2));
        TextView td=new TextView(this);td.setText(desc);td.setTextSize(11);td.setGravity(Gravity.CENTER);td.setTextColor(COLOR_GRIS);card.addView(td,margen(0,0,0,6));
        TextView tb=new TextView(this);tb.setText(sel?"✓ Activa":"Tocar para elegir");tb.setTextColor(sel?acento:COLOR_GRIS);tb.setTextSize(10);tb.setGravity(Gravity.CENTER);card.addView(tb);
        card.setOnClickListener(v->actualizarSeleccionVoz(tipo,true));return card;}
 
    private void actualizarSeleccionVoz(String tipo,boolean guardar){
        vozSeleccionada=tipo;if(guardar)getSharedPreferences("quacky_prefs",MODE_PRIVATE).edit().putString("voz",tipo).apply();
        actualizarEstiloVoz(cardVozMasculina,"masculina",COLOR_AZUL);actualizarEstiloVoz(cardVozFemenina,"femenina",COLOR_ROSA);}
    private void actualizarEstiloVoz(LinearLayout c,String tipo,int acento){
        if(c==null)return;boolean sel=tipo.equals(vozSeleccionada);
        GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));f.setColor(sel?0xFF1E2A3A:COLOR_CARD);f.setStroke(dp(sel?2:1),sel?acento:0xFF2A2A4A);c.setBackground(f);
        if(c.getChildCount()>=2)((TextView)c.getChildAt(1)).setTextColor(sel?acento:COLOR_BLANCO);
        if(c.getChildCount()>=4){TextView b=(TextView)c.getChildAt(3);b.setText(sel?"✓ Activa":"Tocar para elegir");b.setTextColor(sel?acento:COLOR_GRIS);}
        if(sel){Animation r=new ScaleAnimation(0.93f,1f,0.93f,1f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);r.setDuration(150);c.startAnimation(r);}}
 
    // ── PERMISOS ─────────────────────────────────────────────────────────────────
    private LinearLayout crearSeccionPermisos(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(16));f.setColor(COLOR_CARD);f.setStroke(dp(1),0xFF2A2A4A);c.setBackground(f);c.setPadding(dp(16),dp(16),dp(16),dp(16));
        String[][]ps={{"🖥️","Mostrar sobre apps","Flota sobre tu pantalla"},{"🎤","Micrófono","Para escucharte"},{"👥","Contactos","Para llamar por voz"},{"📞","Teléfono","Para marcar por ti"}};
        for(String[]p:ps)c.addView(crearFilaPermiso(p[0],p[1],p[2]));
        Button b=new Button(this);b.setText("Conceder permisos pendientes");b.setTextColor(COLOR_ORO);b.setTextSize(13);b.setBackgroundColor(Color.TRANSPARENT);b.setAllCaps(false);b.setOnClickListener(v->solicitarPermisos());
        LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pb.setMargins(0,dp(8),0,0);c.addView(b,pb);return c;}
 
    private LinearLayout crearFilaPermiso(String ic,String nombre,String desc){
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.HORIZONTAL);f.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);pf.setMargins(0,0,0,dp(12));f.setLayoutParams(pf);
        TextView ti=new TextView(this);ti.setText(ic);ti.setTextSize(22);ti.setGravity(Gravity.CENTER);f.addView(ti,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams pt=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);pt.setMargins(dp(10),0,0,0);
        TextView tn=new TextView(this);tn.setText(nombre);tn.setTextSize(14);tn.setTypeface(null,Typeface.BOLD);tn.setTextColor(COLOR_BLANCO);t.addView(tn);
        TextView td=new TextView(this);td.setText(desc);td.setTextSize(11);td.setTextColor(COLOR_GRIS);t.addView(td);f.addView(t,pt);
        TextView e=new TextView(this);e.setTag("estado_"+nombre);boolean ok=verificarPermiso(nombre);e.setText(ok?"✓":"✗");e.setTextColor(ok?COLOR_VERDE:COLOR_ROJO);e.setTextSize(18);e.setTypeface(null,Typeface.BOLD);f.addView(e,new LinearLayout.LayoutParams(dp(28),dp(28)));
        return f;}
 
    private boolean verificarPermiso(String nombre){
        switch(nombre){case"Mostrar sobre apps":return Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(this);
            case"Micrófono":return ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            case"Contactos":return ContextCompat.checkSelfPermission(this,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
            case"Teléfono":return ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;
            default:return false;}}
 
    private void actualizarEstadoPermisos(){if(seccionPermisos!=null)buscarYActualizar(seccionPermisos);}
    private void buscarYActualizar(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View h=g.getChildAt(i);
        if(h instanceof TextView){Object t=h.getTag();if(t instanceof String&&((String)t).startsWith("estado_")){String n=((String)t).replace("estado_","");boolean ok=verificarPermiso(n);((TextView)h).setText(ok?"✓":"✗");((TextView)h).setTextColor(ok?COLOR_VERDE:COLOR_ROJO);}}
        else if(h instanceof ViewGroup)buscarYActualizar((ViewGroup)h);}}
 
    // ── BOTÓN ACTIVAR ─────────────────────────────────────────────────────────────
    private Button crearBotonActivar(){
        Button b=new Button(this);b.setText("🚀  Activar "+emojiAnimal());b.setTextSize(17);b.setTypeface(null,Typeface.BOLD);b.setTextColor(COLOR_FONDO);b.setAllCaps(false);
        GradientDrawable f=new GradientDrawable();f.setCornerRadius(dp(50));f.setColor(COLOR_ORO);b.setBackground(f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));b.setLayoutParams(p);
        b.setOnClickListener(v->alPresionarActivar());return b;}
 
    private void alPresionarActivar(){
        // Guardar nombre si hay algo en el campo
        if(etNombre!=null&&!etNombre.getText().toString().trim().isEmpty())guardarNombre();
        boolean ok=verificarPermiso("Mostrar sobre apps")&&verificarPermiso("Micrófono")&&verificarPermiso("Contactos")&&verificarPermiso("Teléfono");
        if(!ok){solicitarPermisos();return;}iniciarServicio();}
 
    private void iniciarServicio(){
        Toast.makeText(this,"¡"+emojiAnimal()+" activo! Di \""+capitalize(nombreMascota.isEmpty()?"Quacky":nombreMascota)+"\" para hablar",Toast.LENGTH_LONG).show();
        stopService(new Intent(this,DuckOverlayService.class));
        new Handler().postDelayed(()->{Intent s=new Intent(this,DuckOverlayService.class);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)startForegroundService(s);else startService(s);
            moveTaskToBack(true);},600);}
 
    private void solicitarPermisos(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this)){
            new AlertDialog.Builder(this).setTitle("🖥️ Permiso de pantalla").setMessage("Busca 'Quacky' y activa el interruptor.").setPositiveButton("Ir a Ajustes",(d,w)->startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY_REQUEST)).setNegativeButton("Cancelar",null).show();return;}
        String[]perms={Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE};
        boolean falta=false;for(String p:perms)if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED){falta=true;break;}
        if(falta)new AlertDialog.Builder(this).setTitle("🎤 Permisos").setMessage("Necesito micrófono, contactos y teléfono para ser tu asistente.").setPositiveButton("Conceder",(d,w)->ActivityCompat.requestPermissions(this,perms,PERM_REQUEST)).setNegativeButton("Ahora no",null).show();}
 
    @Override public void onRequestPermissionsResult(int req,String[]perms,int[]res){super.onRequestPermissionsResult(req,perms,res);if(req==PERM_REQUEST){actualizarEstadoPermisos();if(verificarPermiso("Mostrar sobre apps")&&verificarPermiso("Micrófono")&&verificarPermiso("Contactos")&&verificarPermiso("Teléfono"))iniciarServicio();}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==OVERLAY_REQUEST){actualizarEstadoPermisos();if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&Settings.canDrawOverlays(this))solicitarPermisos();}}
 
    private String emojiAnimal(){for(String[]a:ANIMALES)if(a[0].equals(animalSeleccionado))return a[1];return"🦆";}
    private String capitalize(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private View crearDivisor(){View d=new View(this);d.setBackgroundColor(0xFF2A2A4A);return d;}
    private TextView crearEtiquetaSeccion(String t){TextView tv=new TextView(this);tv.setText(t);tv.setTextSize(15);tv.setTypeface(null,Typeface.BOLD);tv.setTextColor(COLOR_BLANCO);return tv;}
    private LinearLayout.LayoutParams margen(int i,int a,int d,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(dp(i),dp(a),dp(d),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
 
