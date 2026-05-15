package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
/**
 * Vista animada para gato y perro con animación de caminar.
 * Muestra el cuerpo del animal y anima 4 patas alternadas debajo.
 */
public class PetView extends View {
 
    private Bitmap bitmap;
    private final Paint paintImg  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintPata = new Paint(Paint.ANTI_ALIAS_FLAG);
 
    private boolean isMoving  = false;
    private float   walkPhase = 0f;
    private float   scaleXDir = 1f;
 
    // Color de las patas, muestreado del bitmap
    private int colorPata      = 0xFF999999;
    private int colorPataOscuro = 0xFF666666;
 
    public PetView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
 
    public void setAnimalBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        if (bmp != null && !bmp.isRecycled()) {
            // Muestrear color de pata desde la parte inferior del bitmap
            try {
                int cx = bmp.getWidth() / 2;
                int cy = (int)(bmp.getHeight() * 0.88f);
                colorPata = bmp.getPixel(cx, cy);
                // Versión más oscura para la pezuña/garra
                int r = Math.max(0, Color.red(colorPata)   - 40);
                int g = Math.max(0, Color.green(colorPata) - 40);
                int b = Math.max(0, Color.blue(colorPata)  - 40);
                colorPataOscuro = Color.rgb(r, g, b);
            } catch (Exception ignored) {}
        }
        invalidate();
    }
 
    /**
     * Misma interfaz que DuckView.
     * @param moving    ¿Se mueve?
     * @param phase     Fase de caminar 0.0–1.0
     * @param direction 1.0 = derecha, -1.0 = izquierda
     */
    public void setWalkState(boolean moving, float phase, float direction) {
        isMoving  = moving;
        walkPhase = phase;
        scaleXDir = direction >= 0 ? 1f : -1f;
        invalidate();
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
 
        canvas.save();
 
        // Voltear según dirección
        if (scaleXDir < 0) canvas.scale(-1f, 1f, w / 2f, h / 2f);
 
        if (isMoving) {
            // ── CAMINAR ───────────────────────────────────────────────────
            // Cuerpo ocupa 78% de la altura, dejando espacio para patas abajo
            float bodyH    = h * 0.78f;
            float bodyY    = 0f;
 
            // Bob (rebote vertical)
            float bob = (float)(Math.sin(walkPhase * Math.PI * 2) * h * 0.028f);
            // Lean (inclinación lateral al caminar)
            float lean = (float)(Math.sin(walkPhase * Math.PI * 2) * 3.5f);
 
            canvas.translate(0, bob);
            canvas.rotate(lean, w / 2f, h / 2f);
 
            // Dibujar cuerpo
            canvas.drawBitmap(bitmap, null, new RectF(w * 0.04f, bodyY, w * 0.96f, bodyH), paintImg);
 
            // Dibujar 4 patas animadas
            dibujarPatas(canvas, w, h, bodyH);
 
        } else {
            // ── PARADO ────────────────────────────────────────────────────
            canvas.drawBitmap(bitmap, null, new RectF(0, 0, w, h), paintImg);
        }
 
        canvas.restore();
    }
 
    /**
     * Dibuja 4 patas con ciclo de caminar alterno.
     * Par A (delantera-izq + trasera-der) y Par B (delantera-der + trasera-izq)
     * se mueven en oposición de fase (0.5 de diferencia), igual que un cuadrúpedo real.
     */
    private void dibujarPatas(Canvas canvas, int w, int h, float bodyH) {
        float pataAncho = w * 0.09f;
        float pataAlto  = h * 0.20f;
        float pezunaR   = pataAncho * 0.65f;
 
        // Posiciones X de las 4 patas
        float xDI = w * 0.18f; // delantera izquierda
        float xDD = w * 0.38f; // delantera derecha
        float xTI = w * 0.56f; // trasera izquierda
        float xTD = w * 0.76f; // trasera derecha
 
        // Fases de cada pata (cuadrúpedo: diagonal pairs)
        float faseDI = walkPhase;
        float faseTD = walkPhase;             // par A: misma fase
        float faseDD = (walkPhase + 0.5f) % 1f;
        float faseTI = (walkPhase + 0.5f) % 1f; // par B: fase opuesta
 
        dibujarUnaPata(canvas, xDI, bodyH, faseDI, pataAncho, pataAlto, pezunaR, h);
        dibujarUnaPata(canvas, xDD, bodyH, faseDD, pataAncho, pataAlto, pezunaR, h);
        dibujarUnaPata(canvas, xTI, bodyH, faseTI, pataAncho, pataAlto, pezunaR, h);
        dibujarUnaPata(canvas, xTD, bodyH, faseTD, pataAncho, pataAlto, pezunaR, h);
    }
 
    private void dibujarUnaPata(Canvas canvas, float cx, float bodyH, float fase,
                                 float pw, float ph, float pr, int viewH) {
        // Levantamiento: la pata sube cuando sin(fase*PI) > 0
        float lift = (float)(Math.max(0, Math.sin(fase * Math.PI)) * ph * 0.55f);
 
        float top = bodyH - lift;
        float bot = top + ph;
 
        // Pierna (rectángulo redondeado)
        paintPata.setColor(colorPata);
        paintPata.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(
            new RectF(cx - pw / 2f, top, cx + pw / 2f, bot - pr),
            pw / 3f, pw / 3f, paintPata
        );
 
        // Pezuña / pata (óvalo en la punta)
        paintPata.setColor(colorPataOscuro);
        canvas.drawOval(
            new RectF(cx - pr, bot - pr * 1.3f, cx + pr, bot + pr * 0.2f),
            paintPata
        );
    }
}
 
