package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
/**
 * Vista reutilizable para cualquier animal (gato, perro, etc.)
 * Muestra un Bitmap con animación de movimiento igual que DuckView.
 * Para el pato se sigue usando DuckView (ya funciona).
 */
public class PetView extends View {
 
    private Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
 
    // Estado de la animación
    private float bobOffset  = 0f;   // cuánto sube/baja al caminar
    private float scaleXDir  = 1f;   // 1 = mirando derecha, -1 = mirando izquierda
 
    public PetView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
 
    /** Poner la imagen del animal */
    public void setAnimalBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        invalidate();
    }
 
    /**
     * Misma interfaz que DuckView para que DuckOverlayService no necesite cambiar.
     * @param moving    ¿Se está moviendo?
     * @param phase     Fase de la animación de caminar (0.0 - 1.0)
     * @param direction 1.0 = derecha, -1.0 = izquierda
     */
    public void setWalkState(boolean moving, float phase, float direction) {
        // Bobbing suave arriba/abajo mientras camina
        bobOffset = moving ? (float)(Math.sin(phase * Math.PI * 2) * 2.8f) : 0f;
        scaleXDir = direction >= 0 ? 1f : -1f;
        invalidate();
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
 
        canvas.save();
 
        // Voltear horizontalmente cuando va hacia la izquierda
        if (scaleXDir < 0) {
            canvas.scale(-1f, 1f, w / 2f, h / 2f);
        }
 
        // Aplicar movimiento de bob (sube y baja)
        canvas.translate(0, bobOffset);
 
        // Dibujar la imagen ocupando todo el espacio de la vista
        RectF dst = new RectF(0, 0, w, h);
        canvas.drawBitmap(bitmap, null, dst, paint);
 
        canvas.restore();
    }
}
 
