package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
public class PetView extends View {
 
    private Bitmap bitmap;
    private final Paint paintImg  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintPata = new Paint(Paint.ANTI_ALIAS_FLAG);
 
    private boolean isMoving  = false;
    private float   walkPhase = 0f;
    private float   dir       = 1f; // 1=derecha, -1=izquierda
 
    // Colores de patas (se calculan del bitmap)
    private int colorPata  = 0xFFAA8844;
    private int colorGarra = 0xFF886622;
    private boolean coloresMuestreados = false;
 
    public PetView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        // Software layer para que Canvas funcione correctamente
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
 
    public void setAnimalBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        coloresMuestreados = false;
        invalidate();
    }
 
    /** Misma firma que DuckView.setWalkState */
    public void setWalkState(boolean moving, float phase, float direction) {
        this.isMoving  = moving;
        this.walkPhase = phase;
        this.dir       = direction >= 0 ? 1f : -1f;
        invalidate();
    }
 
    // Muestrea el color más representativo del animal para las patas
    private void muestrearColores() {
        if (bitmap == null || bitmap.isRecycled() || coloresMuestreados) return;
        int bw = bitmap.getWidth(), bh = bitmap.getHeight();
        // Buscar el píxel más opaco de la zona inferior del bitmap
        int mejorAlpha = 0;
        int mejorColor = 0xFFAA8844;
        int[] ys = {(int)(bh*0.75f), (int)(bh*0.80f), (int)(bh*0.85f)};
        int[] xs = {(int)(bw*0.25f), (int)(bw*0.40f), (int)(bw*0.50f), (int)(bw*0.60f), (int)(bw*0.75f)};
        for (int y : ys) for (int x : xs) {
            try {
                int p = bitmap.getPixel(x, y);
                if (Color.alpha(p) > mejorAlpha) {
                    mejorAlpha = Color.alpha(p);
                    mejorColor = Color.argb(255, Color.red(p), Color.green(p), Color.blue(p));
                }
            } catch (Exception ignored) {}
        }
        colorPata  = mejorColor;
        // Versión más oscura para garras/pezuñas
        colorGarra = Color.argb(255,
            Math.max(0, Color.red(mejorColor)   - 50),
            Math.max(0, Color.green(mejorColor) - 50),
            Math.max(0, Color.blue(mejorColor)  - 50));
        coloresMuestreados = true;
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
 
        muestrearColores();
 
        canvas.save();
 
        // Voltear según dirección de movimiento
        if (dir < 0) canvas.scale(-1f, 1f, w / 2f, h / 2f);
 
        if (isMoving) {
            // ─── ANIMACIÓN DE CAMINAR ────────────────────────────────────────
            // El cuerpo ocupa el 78% superior; el 22% inferior son las patas
            float bodyH = h * 0.78f;
 
            // Ciclo de caminar: seno para bob y lean
            double ciclo = Math.sin(walkPhase * Math.PI * 2);
            float bob  = (float)(ciclo * h * 0.030f);   // rebote vertical
            float lean = (float)(ciclo * 4.0f);          // inclinación lateral
 
            canvas.translate(0, bob);
            canvas.rotate(lean, w / 2f, h * 0.5f);
 
            // Dibujar cuerpo en 78% del alto
            canvas.drawBitmap(bitmap, null, new RectF(0, 0, w, bodyH), paintImg);
 
            // ─── PATAS ANIMADAS ──────────────────────────────────────────────
            // Cuadrúpedo: par diagonal A (delantera-izq + trasera-der) y par B opuesto
            float faseA = walkPhase;
            float faseB = (walkPhase + 0.5f) % 1f;
 
            // Levantamiento máximo de cada par
            float liftA = (float)(Math.max(0.0, Math.sin(faseA * Math.PI)) * h * 0.12f);
            float liftB = (float)(Math.max(0.0, Math.sin(faseB * Math.PI)) * h * 0.12f);
 
            float pw = w * 0.14f;   // ancho de pata
            float ph = h * 0.18f;   // alto de pata
            float baseY = bodyH;    // Y donde empiezan las patas
 
            // Posiciones X de las 4 patas
            float xDI = w * 0.18f;  // delantera izquierda  → par A
            float xDD = w * 0.40f;  // delantera derecha     → par B
            float xTI = w * 0.58f;  // trasera izquierda     → par B
            float xTD = w * 0.80f;  // trasera derecha       → par A
 
            dibujarPata(canvas, xDI, baseY, liftA, pw, ph);
            dibujarPata(canvas, xDD, baseY, liftB, pw, ph);
            dibujarPata(canvas, xTI, baseY, liftB, pw, ph);
            dibujarPata(canvas, xTD, baseY, liftA, pw, ph);
 
        } else {
            // ─── PARADO ──────────────────────────────────────────────────────
            canvas.drawBitmap(bitmap, null, new RectF(0, 0, w, h), paintImg);
        }
 
        canvas.restore();
    }
 
    private void dibujarPata(Canvas canvas, float cx, float baseY,
                              float lift, float pw, float ph) {
        float top = baseY - lift;
        float bot = top + ph;
 
        // Pierna (rectángulo redondeado)
        paintPata.setColor(colorPata);
        paintPata.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(cx - pw/2f, top, cx + pw/2f, bot - pw/2f),
            pw/3f, pw/3f, paintPata);
 
        // Pezuña/garra (óvalo en la punta)
        paintPata.setColor(colorGarra);
        canvas.drawOval(new RectF(cx - pw*0.65f, bot - pw*0.7f,
            cx + pw*0.65f, bot + pw*0.15f), paintPata);
    }
}
 
