package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
/**
 * Vista del pato con animación direccional real.
 *
 * Sprites en res/drawable/ (cada uno: 4 frames de 340x460 = 1360x460 total):
 *   duck_abajo.png     — pato de frente  (idle / giroscopio abajo)
 *   duck_arriba.png    — pato de espaldas (giroscopio arriba)
 *   duck_izquierda.png — pato caminando izquierda
 *   duck_derecha.png   — pato caminando derecha
 */
public class DuckView extends View {
 
    private static final int NUM_FRAMES = 4;
    private static final int DIR_ABAJO     = 0;
    private static final int DIR_ARRIBA    = 1;
    private static final int DIR_IZQUIERDA = 2;
    private static final int DIR_DERECHA   = 3;
 
    private final Bitmap[] strips = new Bitmap[4];
    private boolean isMoving   = false;
    private float   walkPhase  = 0f;
    private int     currentDir = DIR_ABAJO;
    private long    startTime  = System.currentTimeMillis();
 
    private final Paint paint   = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect  srcRect = new Rect();
    private final RectF dstRect = new RectF();
 
    public DuckView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        cargarStrips(context);
    }
 
    private void cargarStrips(Context ctx) {
        String[] nombres = {"duck_abajo","duck_arriba","duck_izquierda","duck_derecha"};
        for (int i = 0; i < 4; i++) {
            try {
                int id = ctx.getResources().getIdentifier(nombres[i],"drawable",ctx.getPackageName());
                if (id != 0) strips[i] = BitmapFactory.decodeResource(ctx.getResources(), id);
            } catch (Exception ignored) {}
        }
    }
 
    /** Retrocompatible con DuckOverlayService existente */
    public void setWalkState(boolean moving, float phase, float direction) {
        this.isMoving  = moving;
        this.walkPhase = phase;
        if (moving && currentDir != DIR_ARRIBA && currentDir != DIR_ABAJO)
            currentDir = direction >= 0 ? DIR_DERECHA : DIR_IZQUIERDA;
        invalidate();
    }
 
    /**
     * NUEVO — llamado desde DuckOverlayService con deltas de movimiento.
     * Elige el sprite según la dirección predominante del giroscopio.
     */
    public void setMovementDirection(float dx, float dy) {
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (ax < 1f && ay < 1f) { currentDir = DIR_ABAJO; return; }
        if (ay > ax * 1.2f)      currentDir = (dy > 0) ? DIR_ABAJO    : DIR_ARRIBA;
        else if (ax > ay * 1.2f) currentDir = (dx > 0) ? DIR_DERECHA  : DIR_IZQUIERDA;
        invalidate();
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
 
        Bitmap strip = strips[currentDir];
        if (strip == null || strip.isRecycled()) { dibujarFallback(canvas,vw,vh); return; }
 
        int frameIndex;
        if (isMoving) {
            frameIndex = (int)(walkPhase * NUM_FRAMES) % NUM_FRAMES;
        } else {
            // Idle: alterna frames 0-1 cada 350ms
            frameIndex = (int)((System.currentTimeMillis() - startTime) / 350) % 2;
            postInvalidateDelayed(350);
        }
 
        int fw = strip.getWidth() / NUM_FRAMES;
        srcRect.set(frameIndex * fw, 0, (frameIndex+1) * fw, strip.getHeight());
        dstRect.set(0, 0, vw, vh);
        canvas.drawBitmap(strip, srcRect, dstRect, paint);
    }
 
    private void dibujarFallback(Canvas canvas, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFDD00);
        canvas.drawOval(new RectF(w*.15f,h*.35f,w*.85f,h*.85f),p);
        canvas.drawCircle(w*.62f,h*.28f,h*.22f,p);
        p.setColor(0xFFFF8800);
        canvas.drawOval(new RectF(w*.80f,h*.22f,w*1.0f,h*.35f),p);
        p.setColor(Color.BLACK);
        canvas.drawCircle(w*.70f,h*.22f,h*.04f,p);
        p.setColor(0xFFFF8800);
        canvas.drawRoundRect(new RectF(w*.30f,h*.78f,w*.48f,h*.95f),dp(4),dp(4),p);
        canvas.drawRoundRect(new RectF(w*.55f,h*.78f,w*.73f,h*.95f),dp(4),dp(4),p);
    }
 
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
 
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i=0;i<strips.length;i++){
            if(strips[i]!=null&&!strips[i].isRecycled()){strips[i].recycle();strips[i]=null;}}
    }
}
 
