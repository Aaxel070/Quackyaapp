package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
 
public class DuckView extends View {
 
    // Frames de caminar
    private static final int NUM_FRAMES = 4;
    private static final int DIR_ABAJO     = 0;
    private static final int DIR_ARRIBA    = 1;
    private static final int DIR_IZQUIERDA = 2;
    private static final int DIR_DERECHA   = 3;
 
    private final Bitmap[] strips = new Bitmap[4];
 
    // Estado normal
    private boolean isMoving   = false;
    private float   walkPhase  = 0f;
    private int     currentDir = DIR_ABAJO;
    private long    startTime  = System.currentTimeMillis();
 
    // Hambre
    private int hungerLevel = 0;
 
    // Comer
    private boolean isEating     = false;
    private long    eatStartTime = 0;
 
    // ── MUERTE ────────────────────────────────────────────────────────────────
    private Bitmap deathStrip;       // duck_muerte.png — 4 frames de 400×520
    private boolean isDead        = false;  // true = muerto permanente
    private boolean animandoMuerte = false; // true = animando la secuencia
    private int     deathFrame    = 0;      // frame actual (0-3)
    private static final int DEATH_FRAMES     = 4;
    private static final int DEATH_FRAME_W    = 400;
    private static final int DEATH_FRAME_H    = 520;
    private static final int DEATH_FRAME_MS   = 700; // ms por frame
    private final Handler  deathHandler  = new Handler(Looper.getMainLooper());
 
    // Pintura
    private final Paint paintImg  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintBowl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect  srcRect   = new Rect();
    private final RectF dstRect   = new RectF();
 
    public DuckView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        cargarStrips(context);
        cargarDeathStrip(context);
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
 
    private void cargarDeathStrip(Context ctx) {
        try {
            int id = ctx.getResources().getIdentifier("duck_muerte","drawable",ctx.getPackageName());
            if (id != 0) deathStrip = BitmapFactory.decodeResource(ctx.getResources(), id);
        } catch (Exception ignored) {}
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────────────────────────────
    public void setWalkState(boolean moving, float phase, float direction) {
        if (isDead || animandoMuerte || isEating) return;
        this.isMoving  = moving;
        this.walkPhase = phase;
        if (moving && currentDir != DIR_ARRIBA && currentDir != DIR_ABAJO)
            currentDir = direction >= 0 ? DIR_DERECHA : DIR_IZQUIERDA;
        invalidate();
    }
 
    public void setMovementDirection(float dx, float dy) {
        if (isDead || animandoMuerte || isEating) return;
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (ax < 1f && ay < 1f) { currentDir = DIR_ABAJO; return; }
        if (ay > ax * 1.2f)      currentDir = (dy > 0) ? DIR_ABAJO    : DIR_ARRIBA;
        else if (ax > ay * 1.2f) currentDir = (dx > 0) ? DIR_DERECHA  : DIR_IZQUIERDA;
        invalidate();
    }
 
    public void setEatingState(boolean eating) {
        if (isDead || animandoMuerte) return;
        this.isEating = eating;
        if (eating) { eatStartTime = System.currentTimeMillis(); currentDir = DIR_ABAJO; isMoving = false; }
        invalidate();
    }
 
    public void setHungerLevel(int nivel) {
        hungerLevel = nivel;
        invalidate();
    }
 
    /** Inicia la secuencia de muerte: 4 frames → se queda en el último */
    public void iniciarMuerte() {
        if (isDead || animandoMuerte) return;
        animandoMuerte = true;
        isEating       = false;
        isMoving       = false;
        deathFrame     = 0;
        invalidate();
        programarSiguienteFrameMuerte();
    }
 
    public boolean estaMuerto() { return isDead; }
    public boolean estaAnimandoMuerte() { return animandoMuerte; }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Animación de muerte — avanza frame a frame y termina en el último
    // ─────────────────────────────────────────────────────────────────────────
    private void programarSiguienteFrameMuerte() {
        deathHandler.postDelayed(() -> {
            if (deathFrame < DEATH_FRAMES - 1) {
                deathFrame++;
                invalidate();
                programarSiguienteFrameMuerte(); // continuar
            } else {
                // Último frame alcanzado → muerto permanente
                animandoMuerte = false;
                isDead         = true;
                invalidate();
            }
        }, DEATH_FRAME_MS);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  DIBUJADO
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
 
        // ── Muerte (animando o muerto permanente) ─────────────────────────
        if (animandoMuerte || isDead) {
            dibujarFrameMuerte(canvas, vw, vh);
            return;
        }
 
        // ── Comer ─────────────────────────────────────────────────────────
        if (isEating) {
            dibujarComiendo(canvas, vw, vh);
            return;
        }
 
        // ── Indicador de hambre ───────────────────────────────────────────
        if (hungerLevel >= 80 && !isDead) {
            dibujarIndicadorHambre(canvas, vw, vh);
        }
 
        // ── Caminar normal ────────────────────────────────────────────────
        Bitmap strip = strips[currentDir];
        if (strip == null || strip.isRecycled()) { dibujarFallback(canvas, vw, vh); return; }
 
        int frameIndex;
        if (isMoving) {
            frameIndex = (int)(walkPhase * NUM_FRAMES) % NUM_FRAMES;
        } else {
            frameIndex = (int)((System.currentTimeMillis() - startTime) / 350) % 2;
            postInvalidateDelayed(350);
        }
        int fw = strip.getWidth() / NUM_FRAMES;
        srcRect.set(frameIndex * fw, 0, (frameIndex + 1) * fw, strip.getHeight());
        dstRect.set(0, 0, vw, vh);
        canvas.drawBitmap(strip, srcRect, dstRect, paintImg);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Frame de muerte
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarFrameMuerte(Canvas canvas, int vw, int vh) {
        if (deathStrip == null || deathStrip.isRecycled()) {
            // Fallback: X roja
            paintBowl.setColor(0xFFFF0000); paintBowl.setTextSize(dp(28)); paintBowl.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("💀", vw/2f, vh*0.6f, paintBowl); paintBowl.setTextAlign(Paint.Align.LEFT);
            return;
        }
        // Calcular el ancho de cada frame en el strip
        int fw = deathStrip.getWidth() / DEATH_FRAMES;
        int idx = Math.min(deathFrame, DEATH_FRAMES - 1);
        srcRect.set(idx * fw, 0, (idx + 1) * fw, deathStrip.getHeight());
        dstRect.set(0, 0, vw, vh);
        canvas.drawBitmap(deathStrip, srcRect, dstRect, paintImg);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Animación de comer
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarComiendo(Canvas canvas, int vw, int vh) {
        long elapsed  = System.currentTimeMillis() - eatStartTime;
        int  nomCiclo = (int)(elapsed / 350) % 4;
        boolean cabajo = nomCiclo == 1 || nomCiclo == 3;
 
        Bitmap frente = strips[DIR_ABAJO];
        if (frente != null && !frente.isRecycled()) {
            int fw = frente.getWidth() / NUM_FRAMES;
            srcRect.set(0, 0, fw, frente.getHeight());
            float bodyW = vw * 0.85f, bodyH = vh * 0.58f;
            float bodyX = (vw - bodyW) / 2f;
            float bodyY = cabajo ? vh * 0.10f : vh * 0.06f;
            canvas.save(); canvas.scale(1f, 0.88f, vw/2f, vh*0.4f);
            dstRect.set(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            canvas.drawBitmap(frente, srcRect, dstRect, paintImg);
            canvas.restore();
        }
        // Tazón
        float cx = vw/2f, bowlY = vh*0.80f, bowlW = vw*0.55f, bowlH = vh*0.12f;
        paintBowl.setColor(0x33000000); canvas.drawOval(new RectF(cx-bowlW/2f+3,bowlY-bowlH/2f+3,cx+bowlW/2f+3,bowlY+bowlH/2f+3),paintBowl);
        paintBowl.setColor(0xFFAAAAAA); canvas.drawOval(new RectF(cx-bowlW/2f,bowlY-bowlH/2f,cx+bowlW/2f,bowlY+bowlH/2f),paintBowl);
        paintBowl.setColor(0xFF888888); paintBowl.setStyle(Paint.Style.STROKE); paintBowl.setStrokeWidth(dp(2));
        canvas.drawOval(new RectF(cx-bowlW/2f,bowlY-bowlH/2f,cx+bowlW/2f,bowlY+bowlH/2f),paintBowl);
        paintBowl.setStyle(Paint.Style.FILL);
        float[] fx={cx-bowlW*.22f,cx,cx+bowlW*.22f,cx-bowlW*.10f,cx+bowlW*.10f};
        float[] fy={bowlY-bowlH*.1f,bowlY-bowlH*.2f,bowlY-bowlH*.1f,bowlY+bowlH*.1f,bowlY+bowlH*.1f};
        int[] cols={0xFFFFCC00,0xFFFFAA00,0xFFFFCC00,0xFFFF8800,0xFFFFCC00};
        float radio = vh*0.028f;
        for(int i=0;i<5;i++){paintBowl.setColor(cols[i]);canvas.drawCircle(fx[i],fy[i],radio,paintBowl);}
        if(cabajo){
            paintBowl.setColor(0xBBFFCC00); canvas.drawCircle(cx+(float)(Math.random()*bowlW*.6f-bowlW*.3f),bowlY-bowlH*.5f,vh*.02f,paintBowl);
            paintBowl.setColor(0xFF444444); paintBowl.setTextSize(dp(11)); paintBowl.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("ñom ñom ñom",cx,vh*0.97f,paintBowl); paintBowl.setTextAlign(Paint.Align.LEFT);
        }
        postInvalidateDelayed(350);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Indicador de hambre
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarIndicadorHambre(Canvas canvas, int vw, int vh) {
        long elapsed = System.currentTimeMillis() - startTime;
        float pulso = (float) Math.abs(Math.sin(elapsed / 500.0));
        paintBowl.setColor(Color.argb((int)(200*(0.5f+pulso*0.5f)),255,80,80));
        paintBowl.setTextSize(dp(14)); paintBowl.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(hungerLevel >= 100 ? "😵" : "😋", vw/2f, dp(16), paintBowl);
        paintBowl.setTextAlign(Paint.Align.LEFT);
        postInvalidateDelayed(500);
    }
 
    private void dibujarFallback(Canvas canvas, int vw, int vh) {
        paintBowl.setColor(0xFFFFDD00);
        canvas.drawOval(new RectF(vw*.15f,vh*.35f,vw*.85f,vh*.85f),paintBowl);
        canvas.drawCircle(vw*.62f,vh*.28f,vh*.22f,paintBowl);
        paintBowl.setColor(0xFFFF8800);
        canvas.drawOval(new RectF(vw*.80f,vh*.22f,vw*1.0f,vh*.35f),paintBowl);
        paintBowl.setColor(Color.BLACK);
        canvas.drawCircle(vw*.70f,vh*.22f,vh*.04f,paintBowl);
    }
 
    private int dp(int val) { return Math.round(val * getResources().getDisplayMetrics().density); }
 
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        deathHandler.removeCallbacksAndMessages(null);
        for (int i = 0; i < strips.length; i++) {
            if (strips[i] != null && !strips[i].isRecycled()) { strips[i].recycle(); strips[i] = null; }
        }
        if (deathStrip != null && !deathStrip.isRecycled()) { deathStrip.recycle(); deathStrip = null; }
    }
}
 
