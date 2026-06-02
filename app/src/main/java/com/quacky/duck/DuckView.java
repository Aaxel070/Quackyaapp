package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
public class DuckView extends View {
 
    private static final int NUM_FRAMES = 4;
    private static final int DIR_ABAJO     = 0;
    private static final int DIR_ARRIBA    = 1;
    private static final int DIR_IZQUIERDA = 2;
    private static final int DIR_DERECHA   = 3;
 
    private final Bitmap[] strips = new Bitmap[4];
 
    private boolean isMoving  = false;
    private float   walkPhase = 0f;
    private int     currentDir = DIR_ABAJO;
    private long    startTime  = System.currentTimeMillis();
 
    // Estado de comer
    private boolean isEating     = false;
    private long    eatStartTime = 0;
    private int     hungerLevel  = 0; // 0=lleno, 100=hambriento
 
    private final Paint paintImg  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintBowl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect  srcRect   = new Rect();
    private final RectF dstRect   = new RectF();
 
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
 
    // ─────────────────────────────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────────────────────────────
    public void setWalkState(boolean moving, float phase, float direction) {
        if (isEating) return; // no moverse mientras come
        this.isMoving  = moving;
        this.walkPhase = phase;
        if (moving && currentDir != DIR_ARRIBA && currentDir != DIR_ABAJO)
            currentDir = direction >= 0 ? DIR_DERECHA : DIR_IZQUIERDA;
        invalidate();
    }
 
    public void setMovementDirection(float dx, float dy) {
        if (isEating) return;
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (ax < 1f && ay < 1f) { currentDir = DIR_ABAJO; return; }
        if (ay > ax * 1.2f)      currentDir = (dy > 0) ? DIR_ABAJO    : DIR_ARRIBA;
        else if (ax > ay * 1.2f) currentDir = (dx > 0) ? DIR_DERECHA  : DIR_IZQUIERDA;
        invalidate();
    }
 
    /** Activa/desactiva animación de comer */
    public void setEatingState(boolean eating) {
        this.isEating = eating;
        if (eating) {
            eatStartTime = System.currentTimeMillis();
            currentDir = DIR_ABAJO; // siempre de frente al comer
            isMoving   = false;
        }
        invalidate();
    }
 
    public void setHungerLevel(int nivel) {
        hungerLevel = nivel;
        invalidate();
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Dibujado
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
 
        if (isEating) {
            dibujarComiendo(canvas, vw, vh);
            return;
        }
 
        // Indicador de hambre (burbuja pequeña de hambre sobre el pato)
        if (hungerLevel >= 80) {
            dibujarIndicadorHambre(canvas, vw, vh);
        }
 
        Bitmap strip = strips[currentDir];
        if (strip == null || strip.isRecycled()) { dibujarFallback(canvas,vw,vh); return; }
 
        int frameIndex;
        if (isMoving) {
            frameIndex = (int)(walkPhase * NUM_FRAMES) % NUM_FRAMES;
        } else {
            frameIndex = (int)((System.currentTimeMillis() - startTime) / 350) % 2;
            postInvalidateDelayed(350);
        }
 
        int fw = strip.getWidth() / NUM_FRAMES;
        srcRect.set(frameIndex * fw, 0, (frameIndex+1) * fw, strip.getHeight());
        dstRect.set(0, 0, vw, vh);
        canvas.drawBitmap(strip, srcRect, dstRect, paintImg);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Animación de COMER — sentado con tazón
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarComiendo(Canvas canvas, int w, int h) {
        long elapsed   = System.currentTimeMillis() - eatStartTime;
        int  nomCiclo  = (int)(elapsed / 350) % 4; // ciclo de comer
        boolean cabajo = nomCiclo == 1 || nomCiclo == 3; // cabeza abajo = comiendo
 
        // ── 1. Cuerpo sentado (imagen comprimida verticalmente) ───────────────
        Bitmap frente = strips[DIR_ABAJO];
        if (frente != null && !frente.isRecycled()) {
            int fw = frente.getWidth() / NUM_FRAMES;
            srcRect.set(0, 0, fw, frente.getHeight());
 
            // Sentado: más ancho, menos alto, posición central
            float bodyW = w * 0.85f;
            float bodyH = h * 0.58f;
            float bodyX = (w - bodyW) / 2f;
            float bodyY = cabajo ? h * 0.10f : h * 0.06f; // baja cabeza al comer
 
            // Escalar ligeramente para parecer sentado (más achatado)
            canvas.save();
            canvas.scale(1f, 0.88f, w/2f, h*0.4f);
            dstRect.set(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            canvas.drawBitmap(frente, srcRect, dstRect, paintImg);
            canvas.restore();
        } else {
            // Fallback: círculo amarillo
            paintBowl.setColor(0xFFFFDD00);
            canvas.drawCircle(w/2f, h*0.35f, h*0.25f, paintBowl);
        }
 
        // ── 2. Tazón de comida ────────────────────────────────────────────────
        float cx = w / 2f;
        float bowlY = h * 0.80f;
        float bowlW = w * 0.55f;
        float bowlH = h * 0.12f;
 
        // Sombra del tazón
        paintBowl.setColor(0x33000000);
        canvas.drawOval(new RectF(cx-bowlW/2f+4,bowlY-bowlH/2f+4,cx+bowlW/2f+4,bowlY+bowlH/2f+4), paintBowl);
 
        // Tazón (gris)
        paintBowl.setColor(0xFFAAAAAA);
        canvas.drawOval(new RectF(cx-bowlW/2f, bowlY-bowlH/2f, cx+bowlW/2f, bowlY+bowlH/2f), paintBowl);
 
        // Borde del tazón
        paintBowl.setColor(0xFF888888);
        paintBowl.setStyle(Paint.Style.STROKE);
        paintBowl.setStrokeWidth(dp(2));
        canvas.drawOval(new RectF(cx-bowlW/2f, bowlY-bowlH/2f, cx+bowlW/2f, bowlY+bowlH/2f), paintBowl);
        paintBowl.setStyle(Paint.Style.FILL);
 
        // Comida dentro del tazón (círculos amarillo-naranja = maíz/pellets)
        float[] foodX = {cx-bowlW*0.22f, cx, cx+bowlW*0.22f, cx-bowlW*0.10f, cx+bowlW*0.10f};
        float[] foodY = {bowlY-bowlH*0.1f, bowlY-bowlH*0.2f, bowlY-bowlH*0.1f, bowlY+bowlH*0.1f, bowlY+bowlH*0.1f};
        int[] colores = {0xFFFFCC00, 0xFFFFAA00, 0xFFFFCC00, 0xFFFF8800, 0xFFFFCC00};
        float radio = h * 0.028f;
        for (int i = 0; i < foodX.length; i++) {
            paintBowl.setColor(colores[i]);
            canvas.drawCircle(foodX[i], foodY[i], radio, paintBowl);
        }
 
        // ── 3. Partículas "nom nom" cuando la cabeza baja ─────────────────────
        if (cabajo) {
            paintBowl.setColor(0xBBFFCC00);
            float px = cx + (float)(Math.random() * bowlW * 0.6f - bowlW * 0.3f);
            float py = bowlY - bowlH * 0.5f;
            canvas.drawCircle(px, py, h * 0.02f, paintBowl);
        }
 
        // ── 4. Texto "Ñom ñom" animado ────────────────────────────────────────
        if (cabajo) {
            paintBowl.setColor(0xFF444444);
            paintBowl.setTextSize(dp(11));
            paintBowl.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("ñom ñom ñom", cx, h * 0.97f, paintBowl);
            paintBowl.setTextAlign(Paint.Align.LEFT);
        }
 
        postInvalidateDelayed(350);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Indicador de hambre (estrellitas/corazones encima del animal)
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarIndicadorHambre(Canvas canvas, int w, int h) {
        // Pulsación según nivel de hambre
        long elapsed = System.currentTimeMillis() - startTime;
        float pulso = (float) Math.abs(Math.sin(elapsed / 500.0));
        float alpha = 0.5f + pulso * 0.5f;
 
        paintBowl.setColor(Color.argb((int)(200 * alpha), 255, 80, 80));
        paintBowl.setTextSize(dp(14));
        paintBowl.setTextAlign(Paint.Align.CENTER);
 
        // Iconos de hambre arriba del pato
        float iconY = h * 0.02f + dp(14);
        if (hungerLevel >= 100) {
            canvas.drawText("😵 ¡Hambre!", w/2f, iconY, paintBowl);
        } else {
            canvas.drawText("😋", w/2f, iconY, paintBowl);
        }
        paintBowl.setTextAlign(Paint.Align.LEFT);
        postInvalidateDelayed(500);
    }
 
    private void dibujarFallback(Canvas canvas, int w, int h) {
        paintBowl.setColor(0xFFFFDD00);
        canvas.drawOval(new RectF(w*.15f,h*.35f,w*.85f,h*.85f),paintBowl);
        canvas.drawCircle(w*.62f,h*.28f,h*.22f,paintBowl);
        paintBowl.setColor(0xFFFF8800);
        canvas.drawOval(new RectF(w*.80f,h*.22f,w*1.0f,h*.35f),paintBowl);
        paintBowl.setColor(Color.BLACK);
        canvas.drawCircle(w*.70f,h*.22f,h*.04f,paintBowl);
    }
 
    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
 
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i=0;i<strips.length;i++){
            if(strips[i]!=null&&!strips[i].isRecycled()){strips[i].recycle();strips[i]=null;}}
    }
}
 
