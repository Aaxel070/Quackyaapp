package com.quacky.duck;
 
import android.content.Context;
import android.graphics.*;
import android.view.View;
 
public class PetView extends View {
 
    private Bitmap bitmap;
    private final Paint paintImg  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintPata = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBowl = new Paint(Paint.ANTI_ALIAS_FLAG);
 
    private boolean isMoving   = false;
    private float   walkPhase  = 0f;
    private float   dir        = 1f;
 
    // Comer
    private boolean isEating     = false;
    private long    eatStartTime = 0;
    private int     hungerLevel  = 0;
 
    private int colorPata      = 0xFFAA8844;
    private int colorGarra     = 0xFF886622;
    private boolean coloresMuestreados = false;
 
    public PetView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
 
    public void setAnimalBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        coloresMuestreados = false;
        invalidate();
    }
 
    public void setWalkState(boolean moving, float phase, float direction) {
        if (isEating) return;
        this.isMoving  = moving;
        this.walkPhase = phase;
        this.dir       = direction >= 0 ? 1f : -1f;
        invalidate();
    }
 
    public void setEatingState(boolean eating) {
        this.isEating = eating;
        if (eating) eatStartTime = System.currentTimeMillis();
        invalidate();
    }
 
    public void setHungerLevel(int nivel) {
        hungerLevel = nivel;
        invalidate();
    }
 
    private void muestrearColores() {
        if (bitmap == null || bitmap.isRecycled() || coloresMuestreados) return;
        int bw = bitmap.getWidth(), bh = bitmap.getHeight();
        int mejorAlpha = 0; int mejorColor = 0xFFAA8844;
        int[] ys = {(int)(bh*.75f),(int)(bh*.82f),(int)(bh*.88f)};
        int[] xs = {(int)(bw*.25f),(int)(bw*.40f),(int)(bw*.55f),(int)(bw*.70f)};
        for (int y : ys) for (int x : xs) {
            try { int p=bitmap.getPixel(x,y); if(Color.alpha(p)>mejorAlpha){mejorAlpha=Color.alpha(p);mejorColor=Color.argb(255,Color.red(p),Color.green(p),Color.blue(p));} } catch (Exception ignored) {}
        }
        colorPata  = mejorColor;
        colorGarra = Color.argb(255,Math.max(0,Color.red(mejorColor)-50),Math.max(0,Color.green(mejorColor)-50),Math.max(0,Color.blue(mejorColor)-50));
        coloresMuestreados = true;
    }
 
    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        muestrearColores();
 
        if (isEating) {
            dibujarComiendo(canvas, w, h);
            return;
        }
 
        // Indicador de hambre
        if (hungerLevel >= 80) {
            dibujarIndicadorHambre(canvas, w, h);
        }
 
        canvas.save();
        if (dir < 0) canvas.scale(-1f, 1f, w/2f, h/2f);
 
        if (isMoving) {
            float bodyH = h * 0.78f;
            float bob   = (float)(Math.sin(walkPhase * Math.PI * 2) * h * 0.028f);
            float lean  = (float)(Math.sin(walkPhase * Math.PI * 2) * 3.5f);
            canvas.translate(0, bob);
            canvas.rotate(lean, w/2f, h/2f);
            canvas.drawBitmap(bitmap, null, new RectF(w*.04f,0,w*.96f,bodyH), paintImg);
            dibujarPatas(canvas, w, h, bodyH);
        } else {
            canvas.drawBitmap(bitmap, null, new RectF(0, 0, w, h), paintImg);
        }
        canvas.restore();
    }
 
    private void dibujarPatas(Canvas c,int w,int h,float bodyH){
        float faseA=walkPhase,faseB=(walkPhase+0.5f)%1f;
        float liftA=(float)(Math.max(0.0,Math.sin(faseA*Math.PI))*h*0.12f);
        float liftB=(float)(Math.max(0.0,Math.sin(faseB*Math.PI))*h*0.12f);
        float pw=w*0.14f,ph=h*0.18f;
        dibujarUnaPata(c,w*.18f,bodyH,liftA,pw,ph);
        dibujarUnaPata(c,w*.40f,bodyH,liftB,pw,ph);
        dibujarUnaPata(c,w*.58f,bodyH,liftB,pw,ph);
        dibujarUnaPata(c,w*.80f,bodyH,liftA,pw,ph);
    }
 
    private void dibujarUnaPata(Canvas c,float cx,float baseY,float lift,float pw,float ph){
        float top=baseY-lift,bot=top+ph;
        paintPata.setColor(colorPata);paintPata.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(cx-pw/2f,top,cx+pw/2f,bot-pw/2f),pw/3f,pw/3f,paintPata);
        paintPata.setColor(colorGarra);
        c.drawOval(new RectF(cx-pw*.65f,bot-pw*.7f,cx+pw*.65f,bot+pw*.15f),paintPata);
    }
 
    // ─────────────────────────────────────────────────────────────────────────
    //  Animación de COMER para gato/perro
    // ─────────────────────────────────────────────────────────────────────────
    private void dibujarComiendo(Canvas canvas, int w, int h) {
        long elapsed = System.currentTimeMillis() - eatStartTime;
        int  ciclo   = (int)(elapsed / 400) % 4;
        boolean cabajo = ciclo == 1 || ciclo == 3;
 
        // ── Animal sentado (imagen comprimida + inclinado hacia abajo) ─────────
        canvas.save();
        float inclinacion = cabajo ? 12f : 2f;
        canvas.rotate(inclinacion, w/2f, h*0.5f);
        float bodyH = h * (cabajo ? 0.62f : 0.68f);
        canvas.drawBitmap(bitmap, null, new RectF(w*.08f, h*.05f, w*.92f, bodyH), paintImg);
        canvas.restore();
 
        // ── Tazón ─────────────────────────────────────────────────────────────
        float cx = w * 0.45f; // ligeramente a la izquierda (frente al hocico)
        float bowlY = h * 0.82f;
        float bowlW = w * 0.52f;
        float bowlH = h * 0.11f;
 
        // Sombra
        paintBowl.setColor(0x22000000);
        canvas.drawOval(new RectF(cx-bowlW/2f+3,bowlY-bowlH/2f+3,cx+bowlW/2f+3,bowlY+bowlH/2f+3),paintBowl);
 
        // Tazón
        paintBowl.setColor(0xFFBBBBBB); paintBowl.setStyle(Paint.Style.FILL);
        canvas.drawOval(new RectF(cx-bowlW/2f,bowlY-bowlH/2f,cx+bowlW/2f,bowlY+bowlH/2f),paintBowl);
        paintBowl.setColor(0xFF999999); paintBowl.setStyle(Paint.Style.STROKE); paintBowl.setStrokeWidth(dp(2));
        canvas.drawOval(new RectF(cx-bowlW/2f,bowlY-bowlH/2f,cx+bowlW/2f,bowlY+bowlH/2f),paintBowl);
        paintBowl.setStyle(Paint.Style.FILL);
 
        // Comida
        int[] cols = {0xFFFF6B35,0xFFFF8C42,0xFFFF6B35,0xFFE85D04,0xFFFF8C42};
        float radio = h * 0.026f;
        float[] fx = {cx-bowlW*.22f,cx,cx+bowlW*.22f,cx-bowlW*.1f,cx+bowlW*.1f};
        float[] fy = {bowlY-bowlH*.08f,bowlY-bowlH*.18f,bowlY-bowlH*.08f,bowlY+bowlH*.1f,bowlY+bowlH*.1f};
        for (int i=0;i<5;i++){paintBowl.setColor(cols[i]);canvas.drawCircle(fx[i],fy[i],radio,paintBowl);}
 
        // Partículas nom nom
        if (cabajo) {
            paintBowl.setColor(0xAAFF8C42);
            canvas.drawCircle(cx+(float)(Math.random()*bowlW*.5f-bowlW*.25f), bowlY-bowlH*.5f, h*.018f, paintBowl);
            // Texto
            paintBowl.setColor(0xFF555555); paintBowl.setTextSize(dp(10)); paintBowl.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("ñom ñom ñom", w/2f, h*0.97f, paintBowl);
            paintBowl.setTextAlign(Paint.Align.LEFT);
        }
 
        postInvalidateDelayed(400);
    }
 
    private void dibujarIndicadorHambre(Canvas canvas, int w, int h) {
        long elapsed = System.currentTimeMillis();
        float pulso = (float) Math.abs(Math.sin(elapsed / 500.0));
        paintBowl.setColor(Color.argb((int)(180*(0.5f+pulso*0.5f)),255,60,60));
        paintBowl.setTextSize(dp(14)); paintBowl.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(hungerLevel >= 100 ? "😵" : "😋", w/2f, dp(16), paintBowl);
        paintBowl.setTextAlign(Paint.Align.LEFT);
        postInvalidateDelayed(500);
    }
 
    private int dp(int val){return Math.round(val*getResources().getDisplayMetrics().density);}
 
    @Override
    protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(bitmap!=null&&!bitmap.isRecycled()){bitmap.recycle();bitmap=null;}}
}
 
