package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
/**
 * Vista animada del pato que usa sprite sheets reales.
 *
 * Recursos necesarios en res/drawable/:
 *   duck_walk.png  — tira horizontal de 8 frames (125×125 c/u = 1000×125 total)
 *   duck_idle.png  — tira horizontal de 4 frames  (125×125 c/u =  500×125 total)
 *
 * Uso (misma interfaz que antes):
 *   duckView.setWalkState(moving, walkPhase, direction)
 */
public class DuckView extends View {
 
    // ── Constantes de animación ───────────────────────────────────────────────
    private static final int WALK_FRAMES = 8;   // frames en duck_walk.png
    private static final int IDLE_FRAMES = 4;   // frames en duck_idle.png
    private static final int FRAME_W     = 125; // ancho de cada frame en px
    private static final int FRAME_H     = 125; // alto  de cada frame en px
 
    // ── Bitmaps ───────────────────────────────────────────────────────────────
    private Bitmap walkBitmap;   // tira de 8 frames de caminar
    private Bitmap idleBitmap;   // tira de 4 frames de reposo
 
    // ── Pinturas ──────────────────────────────────────────────────────────────
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
 
    // ── Estado ────────────────────────────────────────────────────────────────
    private boolean isMoving  = false;
    private float   walkPhase = 0f;    // 0.0 – 1.0 (avanza en setupWalkAnimation)
    private float   direction = 1f;    // 1 = derecha, -1 = izquierda
 
    // Para la animación idle (tiempo real, no walkPhase)
    private long startTimeMs = System.currentTimeMillis();
 
    // Rectángulos reutilizables para evitar allocations en onDraw
    private final Rect    srcRect = new Rect();
    private final RectF   dstRect = new RectF();
    private final Matrix  matrix  = new Matrix();
 
    // ─────────────────────────────────────────────────────────────────────────
    public DuckView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        cargarSprites(context);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Cargar sprites desde res/drawable/
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarSprites(Context context) {
        try {
            int walkId = context.getResources().getIdentifier(
                "duck_walk", "drawable", context.getPackageName());
            int idleId  = context.getResources().getIdentifier(
                "duck_idle", "drawable", context.getPackageName());
 
            if (walkId != 0) {
                walkBitmap = BitmapFactory.decodeResource(context.getResources(), walkId);
            }
            if (idleId != 0) {
                idleBitmap = BitmapFactory.decodeResource(context.getResources(), idleId);
            }
        } catch (Exception ignored) {}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Interfaz pública — misma firma que la versión anterior
    // ─────────────────────────────────────────────────────────────────────────
    public void setWalkState(boolean moving, float phase, float dir) {
        this.isMoving  = moving;
        this.walkPhase = phase;
        this.direction = dir >= 0 ? 1f : -1f;
        invalidate();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Dibujado
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
 
        Bitmap bitmap;
        int    frameIndex;
        int    totalFrames;
 
        if (isMoving && walkBitmap != null && !walkBitmap.isRecycled()) {
            // ── CAMINANDO: ciclar por 8 frames según walkPhase ────────────────
            bitmap      = walkBitmap;
            totalFrames = WALK_FRAMES;
            frameIndex  = (int)(walkPhase * totalFrames) % totalFrames;
 
        } else if (!isMoving && idleBitmap != null && !idleBitmap.isRecycled()) {
            // ── IDLE: ciclar por 4 frames lentamente (cada 300ms) ─────────────
            bitmap      = idleBitmap;
            totalFrames = IDLE_FRAMES;
            long elapsed = System.currentTimeMillis() - startTimeMs;
            frameIndex  = (int)(elapsed / 300) % totalFrames;
            // Repintar para mantener animación idle activa
            postInvalidateDelayed(300);
 
        } else {
            // ── Sin sprites: dibujar fallback (pato simple en código) ─────────
            dibujarPatoFallback(canvas, vw, vh);
            return;
        }
 
        // Ancho del frame en el bitmap (puede diferir de FRAME_W si se escaló)
        int bitmapFrameW = bitmap.getWidth() / totalFrames;
 
        // Rectángulo fuente: frame actual en la tira
        srcRect.set(
            frameIndex * bitmapFrameW,
            0,
            (frameIndex + 1) * bitmapFrameW,
            bitmap.getHeight()
        );
 
        canvas.save();
 
        // Voltear horizontalmente si el pato va hacia la izquierda
        if (direction < 0) {
            canvas.scale(-1f, 1f, vw / 2f, vh / 2f);
        }
 
        // Dibujar el frame escalado al tamaño de la vista
        dstRect.set(0, 0, vw, vh);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
 
        canvas.restore();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Fallback: si los sprites no cargaron, dibujar pato en código
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarPatoFallback(Canvas canvas, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
 
        // Cuerpo
        p.setColor(0xFFFFDD00);
        canvas.drawOval(new RectF(w*0.15f, h*0.35f, w*0.85f, h*0.85f), p);
 
        // Cabeza
        canvas.drawCircle(w*0.62f, h*0.28f, h*0.22f, p);
 
        // Pico
        p.setColor(0xFFFF8800);
        canvas.drawOval(new RectF(w*0.80f, h*0.22f, w*1.0f, h*0.35f), p);
 
        // Ojo
        p.setColor(Color.BLACK);
        canvas.drawCircle(w*0.70f, h*0.22f, h*0.04f, p);
 
        // Pata izquierda
        p.setColor(0xFFFF8800);
        canvas.drawRoundRect(new RectF(w*0.30f, h*0.78f, w*0.48f, h*0.95f), dp(4), dp(4), p);
 
        // Pata derecha
        canvas.drawRoundRect(new RectF(w*0.55f, h*0.78f, w*0.73f, h*0.95f), dp(4), dp(4), p);
    }
 
    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Limpiar memoria al destruir
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (walkBitmap != null && !walkBitmap.isRecycled()) {
            walkBitmap.recycle();
            walkBitmap = null;
        }
        if (idleBitmap != null && !idleBitmap.isRecycled()) {
            idleBitmap.recycle();
            idleBitmap = null;
        }
    }
}
 
