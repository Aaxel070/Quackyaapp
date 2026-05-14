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
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    // ── Constantes ────────────────────────────────────────────────────────────
    private static final int  PERM_REQUEST    = 101;
    private static final int  OVERLAY_REQUEST = 102;

    // Colores de la interfaz
    private static final int COLOR_FONDO       = 0xFF0F0F23;
    private static final int COLOR_CARD        = 0xFF1A1A3A;
    private static final int COLOR_CARD_SELECT = 0xFF2A2A5A;
    private static final int COLOR_ORO         = 0xFFFFD700;
    private static final int COLOR_BLANCO      = 0xFFFFFFFF;
    private static final int COLOR_GRIS        = 0xFF888888;
    private static final int COLOR_VERDE       = 0xFF4CAF50;
    private static final int COLOR_NARANJA     = 0xFFFF9800;
    private static final int COLOR_ROJO        = 0xFFF44336;
    private static final int COLOR_CARD_PROX   = 0xFF141428;

    // Animales disponibles: {id, emoji, nombre, disponible}
    private static final String[][] ANIMALES = {
        { "duck",    "🦆", "Pato",    "si"  },
        { "cat",     "🐱", "Gato",    "no"  },
        { "dog",     "🐶", "Perro",   "no"  },
        { "frog",    "🐸", "Rana",    "no"  },
        { "hamster", "🐹", "Hámster", "no"  },
        { "fox",     "🦊", "Zorro",   "no"  },
    };

    // ── Estado ────────────────────────────────────────────────────────────────
    private String       animalSeleccionado = "duck";
    private LinearLayout cardSeleccionada   = null;
    private Button       btnActivar;
    private LinearLayout seccionPermisos;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(COLOR_FONDO);

        // Cargar animal guardado anteriormente
        SharedPreferences prefs = getSharedPreferences("quacky_prefs", MODE_PRIVATE);
        animalSeleccionado = prefs.getString("animal", "duck");

        construirUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refrescar el estado de los permisos cada vez que el usuario vuelve
        if (seccionPermisos != null) actualizarEstadoPermisos();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONSTRUCCIÓN DE LA INTERFAZ
    // ─────────────────────────────────────────────────────────────────────────
    private void construirUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_FONDO);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(32));

        // ── ENCABEZADO ─────────────────────────────────────────────────────
        root.addView(crearEncabezado());

        // ── DIVISOR ────────────────────────────────────────────────────────
        root.addView(crearDivisor(), margen(0, 0, 0, 24));

        // ── SECCIÓN: ELIGE TU COMPAÑERO ────────────────────────────────────
        root.addView(crearEtiquetaSeccion("🐾  Elige tu compañero"), margen(0, 0, 0, 14));
        root.addView(crearGridAnimales(), margen(0, 0, 0, 28));

        // ── SECCIÓN: PERMISOS ───────────────────────────────────────────────
        root.addView(crearEtiquetaSeccion("🔑  Permisos necesarios"), margen(0, 0, 0, 14));
        seccionPermisos = crearSeccionPermisos();
        root.addView(seccionPermisos, margen(0, 0, 0, 28));

        // ── BOTÓN ACTIVAR ───────────────────────────────────────────────────
        btnActivar = crearBotonActivar();
        root.addView(btnActivar, margen(0, 0, 0, 12));

        // ── NOTA INFERIOR ───────────────────────────────────────────────────
        TextView nota = new TextView(this);
        nota.setText("Quacky vivirá sobre tus apps. Tócalo para hablarle 🎤");
        nota.setTextColor(COLOR_GRIS);
        nota.setTextSize(12);
        nota.setGravity(Gravity.CENTER);
        root.addView(nota, margen(0, 0, 0, 0));

        scroll.addView(root);
        setContentView(scroll);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ENCABEZADO
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout crearEncabezado() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        // Emoji del animal grande con círculo dorado de fondo
        FrameLayout circulo = new FrameLayout(this);
        GradientDrawable fondoCirculo = new GradientDrawable();
        fondoCirculo.setShape(GradientDrawable.OVAL);
        fondoCirculo.setColor(0xFF2A2A4A);
        fondoCirculo.setStroke(dp(3), COLOR_ORO);
        circulo.setBackground(fondoCirculo);
        int tamCirculo = dp(100);
        FrameLayout.LayoutParams paramsCir = new FrameLayout.LayoutParams(tamCirculo, tamCirculo);
        paramsCir.gravity = Gravity.CENTER;

        TextView emojiGrande = new TextView(this);
        emojiGrande.setText("🦆");
        emojiGrande.setTextSize(52);
        emojiGrande.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams paramsEmoji = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        paramsEmoji.gravity = Gravity.CENTER;
        circulo.addView(emojiGrande, paramsEmoji);

        LinearLayout.LayoutParams paramsCirLL = new LinearLayout.LayoutParams(tamCirculo, tamCirculo);
        paramsCirLL.gravity = Gravity.CENTER;
        paramsCirLL.setMargins(0, 0, 0, dp(16));
        col.addView(circulo, paramsCirLL);

        // Título
        TextView titulo = new TextView(this);
        titulo.setText("Quacky");
        titulo.setTextSize(38);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(COLOR_ORO);
        titulo.setGravity(Gravity.CENTER);
        col.addView(titulo, margen(0, 0, 0, 4));

        // Subtítulo
        TextView sub = new TextView(this);
        sub.setText("Tu asistente IA de bolsillo");
        sub.setTextSize(14);
        sub.setTextColor(COLOR_GRIS);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, margen(0, 0, 0, 24));

        return col;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GRID DE ANIMALES (2 columnas)
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout crearGridAnimales() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < ANIMALES.length; i += 2) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams paramsCol = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            LinearLayout card1 = crearCardAnimal(ANIMALES[i]);
            fila.addView(card1, paramsCol);

            // Espacio entre columnas
            View espacio = new View(this);
            fila.addView(espacio, new LinearLayout.LayoutParams(dp(12), 0));

            if (i + 1 < ANIMALES.length) {
                LinearLayout card2 = crearCardAnimal(ANIMALES[i + 1]);
                fila.addView(card2, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                // Espacio vacío para mantener el grid alineado
                View vacio = new View(this);
                fila.addView(vacio, new LinearLayout.LayoutParams(0, 0, 1f));
            }

            LinearLayout.LayoutParams paramsFila = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            paramsFila.setMargins(0, 0, 0, dp(12));
            grid.addView(fila, paramsFila);
        }
        return grid;
    }

    private LinearLayout crearCardAnimal(String[] animal) {
        String id          = animal[0];
        String emoji       = animal[1];
        String nombre      = animal[2];
        boolean disponible = animal[3].equals("si");
        boolean esSeleccionado = id.equals(animalSeleccionado) && disponible;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(16), dp(12), dp(16));

        GradientDrawable fondo = new GradientDrawable();
        fondo.setCornerRadius(dp(16));
        if (disponible) {
            fondo.setColor(esSeleccionado ? COLOR_CARD_SELECT : COLOR_CARD);
            fondo.setStroke(dp(esSeleccionado ? 2 : 1),
                esSeleccionado ? COLOR_ORO : 0xFF2A2A4A);
        } else {
            fondo.setColor(COLOR_CARD_PROX);
            fondo.setStroke(dp(1), 0xFF222240);
        }
        card.setBackground(fondo);

        // Emoji del animal
        TextView emojiView = new TextView(this);
        emojiView.setText(emoji);
        emojiView.setTextSize(36);
        emojiView.setGravity(Gravity.CENTER);
        emojiView.setAlpha(disponible ? 1f : 0.4f);
        card.addView(emojiView, margen(0, 0, 0, 6));

        // Nombre del animal
        TextView nombreView = new TextView(this);
        nombreView.setText(nombre);
        nombreView.setTextSize(14);
        nombreView.setTypeface(null, Typeface.BOLD);
        nombreView.setGravity(Gravity.CENTER);
        nombreView.setTextColor(disponible ? COLOR_BLANCO : COLOR_GRIS);
        card.addView(nombreView, margen(0, 0, 0, 4));

        // Badge disponible / próximamente
        TextView badge = new TextView(this);
        if (disponible) {
            badge.setText(esSeleccionado ? "✓ Seleccionado" : "Disponible");
            badge.setTextColor(esSeleccionado ? COLOR_ORO : COLOR_VERDE);
        } else {
            badge.setText("Próximamente");
            badge.setTextColor(COLOR_GRIS);
        }
        badge.setTextSize(10);
        badge.setGravity(Gravity.CENTER);
        card.addView(badge);

        // Guardar referencia si es el seleccionado
        if (esSeleccionado) cardSeleccionada = card;

        // Toque en la card
        card.setOnClickListener(v -> {
            if (!disponible) {
                Toast.makeText(this, "¡" + nombre + " llegará pronto! 🐾", Toast.LENGTH_SHORT).show();
                return;
            }
            seleccionarAnimal(id, card);
        });

        return card;
    }

    private void seleccionarAnimal(String id, LinearLayout cardNueva) {
        // Quitar selección de la card anterior
        if (cardSeleccionada != null && cardSeleccionada != cardNueva) {
            GradientDrawable fondoAnterior = new GradientDrawable();
            fondoAnterior.setCornerRadius(dp(16));
            fondoAnterior.setColor(COLOR_CARD);
            fondoAnterior.setStroke(dp(1), 0xFF2A2A4A);
            cardSeleccionada.setBackground(fondoAnterior);
            // Actualizar badge de la card anterior
            if (cardSeleccionada.getChildCount() >= 3) {
                ((TextView) cardSeleccionada.getChildAt(2)).setText("Disponible");
                ((TextView) cardSeleccionada.getChildAt(2)).setTextColor(COLOR_VERDE);
            }
        }

        // Resaltar la nueva card seleccionada
        GradientDrawable fondoNuevo = new GradientDrawable();
        fondoNuevo.setCornerRadius(dp(16));
        fondoNuevo.setColor(COLOR_CARD_SELECT);
        fondoNuevo.setStroke(dp(2), COLOR_ORO);
        cardNueva.setBackground(fondoNuevo);
        if (cardNueva.getChildCount() >= 3) {
            ((TextView) cardNueva.getChildAt(2)).setText("✓ Seleccionado");
            ((TextView) cardNueva.getChildAt(2)).setTextColor(COLOR_ORO);
        }

        // Animación pequeña de rebote
        Animation rebote = new ScaleAnimation(0.95f, 1f, 0.95f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rebote.setDuration(150);
        cardNueva.startAnimation(rebote);

        cardSeleccionada   = cardNueva;
        animalSeleccionado = id;

        // Guardar preferencia
        getSharedPreferences("quacky_prefs", MODE_PRIVATE)
            .edit().putString("animal", id).apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SECCIÓN DE PERMISOS
    // ─────────────────────────────────────────────────────────────────────────
    private LinearLayout crearSeccionPermisos() {
        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable fondo = new GradientDrawable();
        fondo.setCornerRadius(dp(16));
        fondo.setColor(COLOR_CARD);
        fondo.setStroke(dp(1), 0xFF2A2A4A);
        contenedor.setBackground(fondo);
        contenedor.setPadding(dp(16), dp(16), dp(16), dp(16));

        String[][] permisos = {
            { "🖥️",  "Mostrar sobre apps",  "Permite que Quacky flote sobre tu pantalla" },
            { "🎤",  "Micrófono",           "Para escucharte cuando le hablas"           },
            { "👥",  "Contactos",           "Para llamar a tus contactos por voz"        },
            { "📞",  "Teléfono",            "Para marcar los números por ti"             },
        };

        for (String[] p : permisos) {
            contenedor.addView(crearFilaPermiso(p[0], p[1], p[2]));
        }

        // Botón para conceder todos los permisos faltantes
        Button btnPermisos = new Button(this);
        btnPermisos.setText("Conceder permisos pendientes");
        btnPermisos.setTextColor(COLOR_ORO);
        btnPermisos.setTextSize(13);
        btnPermisos.setBackgroundColor(Color.TRANSPARENT);
        btnPermisos.setOnClickListener(v -> solicitarPermisos());
        LinearLayout.LayoutParams paramsBtnP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsBtnP.setMargins(0, dp(8), 0, 0);
        contenedor.addView(btnPermisos, paramsBtnP);

        return contenedor;
    }

    private LinearLayout crearFilaPermiso(String icono, String nombre, String descripcion) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams paramsFila = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsFila.setMargins(0, 0, 0, dp(12));
        fila.setLayoutParams(paramsFila);

        // Icono
        TextView iconoView = new TextView(this);
        iconoView.setText(icono);
        iconoView.setTextSize(22);
        iconoView.setGravity(Gravity.CENTER);
        fila.addView(iconoView, new LinearLayout.LayoutParams(dp(36), dp(36)));

        // Texto (nombre + descripcion)
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams paramsTextos = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        paramsTextos.setMargins(dp(10), 0, 0, 0);

        TextView tNombre = new TextView(this);
        tNombre.setText(nombre);
        tNombre.setTextSize(14);
        tNombre.setTypeface(null, Typeface.BOLD);
        tNombre.setTextColor(COLOR_BLANCO);
        textos.addView(tNombre);

        TextView tDesc = new TextView(this);
        tDesc.setText(descripcion);
        tDesc.setTextSize(11);
        tDesc.setTextColor(COLOR_GRIS);
        textos.addView(tDesc);

        fila.addView(textos, paramsTextos);

        // Indicador de estado (verde=ok, rojo=falta)
        TextView estado = new TextView(this);
        estado.setTag("estado_" + nombre); // tag para actualizar después
        boolean concedido = verificarPermiso(nombre);
        estado.setText(concedido ? "✓" : "✗");
        estado.setTextColor(concedido ? COLOR_VERDE : COLOR_ROJO);
        estado.setTextSize(18);
        estado.setTypeface(null, Typeface.BOLD);
        fila.addView(estado, new LinearLayout.LayoutParams(dp(28), dp(28)));

        return fila;
    }

    private boolean verificarPermiso(String nombre) {
        switch (nombre) {
            case "Mostrar sobre apps":
                return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || Settings.canDrawOverlays(this);
            case "Micrófono":
                return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            case "Contactos":
                return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED;
            case "Teléfono":
                return ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED;
            default: return false;
        }
    }

    private void actualizarEstadoPermisos() {
        if (seccionPermisos == null) return;
        String[] nombres = { "Mostrar sobre apps", "Micrófono", "Contactos", "Teléfono" };
        actualizarIndicadoresEnContenedor(seccionPermisos, nombres);
    }

    private void actualizarIndicadoresEnContenedor(ViewGroup contenedor, String[] nombres) {
        for (int i = 0; i < contenedor.getChildCount(); i++) {
            View hijo = contenedor.getChildAt(i);
            if (hijo instanceof LinearLayout) {
                // Buscar el TextView de estado en esta fila
                LinearLayout fila = (LinearLayout) hijo;
                for (int j = 0; j < fila.getChildCount(); j++) {
                    View v = fila.getChildAt(j);
                    if (v instanceof TextView) {
                        Object tag = v.getTag();
                        if (tag instanceof String && ((String) tag).startsWith("estado_")) {
                            String nombreBuscado = ((String) tag).replace("estado_", "");
                            boolean ok = verificarPermiso(nombreBuscado);
                            ((TextView) v).setText(ok ? "✓" : "✗");
                            ((TextView) v).setTextColor(ok ? COLOR_VERDE : COLOR_ROJO);
                        }
                    }
                }
                // Recursivo para contenedores anidados
                actualizarIndicadoresEnContenedor(fila, nombres);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BOTÓN ACTIVAR
    // ─────────────────────────────────────────────────────────────────────────
    private Button crearBotonActivar() {
        Button btn = new Button(this);
        btn.setText("🚀  Activar Quacky");
        btn.setTextSize(17);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(COLOR_FONDO);
        btn.setAllCaps(false);

        GradientDrawable fondoBtn = new GradientDrawable();
        fondoBtn.setCornerRadius(dp(50));
        fondoBtn.setColor(COLOR_ORO);
        btn.setBackground(fondoBtn);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, 0, 0, 0);
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> alPresionarActivar());
        return btn;
    }

    private void alPresionarActivar() {
        // Verificar si todos los permisos están concedidos
        boolean todosOk = verificarPermiso("Mostrar sobre apps")
            && verificarPermiso("Micrófono")
            && verificarPermiso("Contactos")
            && verificarPermiso("Teléfono");

        if (!todosOk) {
            // Pedir permisos faltantes primero
            solicitarPermisos();
            return;
        }

        // Todo OK → iniciar el servicio del pato
        iniciarServicio();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SOLICITUD DE PERMISOS
    // ─────────────────────────────────────────────────────────────────────────
    private void solicitarPermisos() {
        // Primero el permiso de overlay (requiere ir a Ajustes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("🖥️ Permiso de pantalla")
                .setMessage("Quacky necesita permiso para mostrarse sobre otras apps.\n\n" +
                    "En la siguiente pantalla busca 'Quacky' y activa el interruptor.")
                .setPositiveButton("Ir a Ajustes", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivityForResult(i, OVERLAY_REQUEST);
                })
                .setNegativeButton("Cancelar", null)
                .show();
            return;
        }

        // Permisos normales (micrófono, contactos, teléfono)
        String[] permisosNecesarios = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        };

        boolean algunoFalta = false;
        for (String p : permisosNecesarios) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                algunoFalta = true;
                break;
            }
        }

        if (algunoFalta) {
            new AlertDialog.Builder(this)
                .setTitle("🎤 Permisos de Quacky")
                .setMessage("Quacky necesita acceso al micrófono, tus contactos y al teléfono " +
                    "para funcionar como tu asistente personal.\n\n" +
                    "¿Puedes concederlos?")
                .setPositiveButton("Conceder", (d, w) ->
                    ActivityCompat.requestPermissions(this, permisosNecesarios, PERM_REQUEST))
                .setNegativeButton("Ahora no", null)
                .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == PERM_REQUEST) {
            actualizarEstadoPermisos();

            boolean todosOk = verificarPermiso("Mostrar sobre apps")
                && verificarPermiso("Micrófono")
                && verificarPermiso("Contactos")
                && verificarPermiso("Teléfono");

            if (todosOk) {
                iniciarServicio();
            } else {
                Toast.makeText(this,
                    "Algunos permisos faltan. Quacky puede funcionar parcialmente.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            actualizarEstadoPermisos();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {
                // Overlay concedido → pedir el resto
                solicitarPermisos();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INICIAR EL SERVICIO DEL PATO
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarServicio() {
        Toast.makeText(this, "¡Quacky ya está activo! 🦆", Toast.LENGTH_SHORT).show();

        Intent servicio = new Intent(this, DuckOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(servicio);
        } else {
            startService(servicio);
        }

        // Animación del botón antes de cerrar
        if (btnActivar != null) {
            Animation pulse = new ScaleAnimation(1f, 0.95f, 1f, 0.95f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            pulse.setDuration(100);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(1);
            pulse.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    // Mover la app al fondo para que el pato sea visible
                    moveTaskToBack(true);
                }
            });
            btnActivar.startAnimation(pulse);
        } else {
            moveTaskToBack(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ELEMENTOS DE APOYO
    // ─────────────────────────────────────────────────────────────────────────
    private View crearDivisor() {
        View divisor = new View(this);
        divisor.setBackgroundColor(0xFF2A2A4A);
        return divisor;
    }

    private TextView crearEtiquetaSeccion(String texto) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(COLOR_BLANCO);
        return tv;
    }

    // Helpers de layout
    private LinearLayout.LayoutParams margen(int izq, int arr, int der, int aba) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(izq), dp(arr), dp(der), dp(aba));
        return p;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
