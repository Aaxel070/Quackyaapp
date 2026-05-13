package com.quacky.duck;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Vista que dibuja un patito 3D tipo goma de baño (estilo de la foto)
 * con animación de pasos y efecto de "alzar" una pata.
 */
public class DuckView extends View {

    // ── Estado de animación ───────────────────────────────────────────────────
    private float walkPhase = 0f;   // 0..1 ciclo de caminar
    private boolean isWalking = false;
    private float facingScaleX = 1f; // 1 = derecha, -1 = izquierda

    // ── Pinturas ──────────────────────────────────────────────────────────────
    private final Paint bodyPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beakPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beakShadow  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wingPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightP  = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DuckView(Context ctx) { super(ctx); init(); }
    public DuckView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public DuckView(Context ctx, AttributeSet a, int d) { super(ctx, a, d); init(); }

    private void init() {
        // Amarillo cuerpo
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setShadowLayer(10f, 3f, 5f, 0x55000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Sombra elipse suelo
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x40000000);

        // Ojo blanco
        eyePaint.setStyle(Paint.Style.FILL);
        eyePaint.setColor(Color.WHITE);

        // Pupila negra
        pupilPaint.setStyle(Paint.Style.FILL);
        pupilPaint.setColor(Color.BLACK);

        // Pico naranja-café
        beakPaint.setStyle(Paint.Style.FILL);

        // Sombra pico
        beakShadow.setStyle(Paint.Style.FILL);
        beakShadow.setColor(0xFFB85C1A);

        // Ala
        wingPaint.setStyle(Paint.Style.FILL);

        // Patas
        footPaint.setStyle(Paint.Style.FILL);
        footPaint.setStrokeCap(Paint.Cap.ROUND);

        // Brillo
        highlightP.setStyle(Paint.Style.FILL);
        highlightP.setColor(0xAAFFFFDD);
    }

    /** Llamar desde el servicio para actualizar animación */
    public void setWalkState(boolean walking, float phase, float scaleX) {
        this.isWalking = walking;
        this.walkPhase = phase;
        this.facingScaleX = scaleX;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // Flip horizontal
        canvas.save();
        canvas.scale(facingScaleX, 1f, cx, cy);

        // Escala base
        float scale = Math.min(w, h) / 130f;

        // Elevación del cuerpo al caminar (bob)
        float bob = isWalking ? (float)(-Math.abs(Math.sin(walkPhase * Math.PI * 2)) * 5f * scale) : 0f;

        // ── Sombra en el suelo ────────────────────────────────────────────────
        float shadowW = 44f * scale;
        float shadowH = 12f * scale;
        float shadowY = cy + 44f * scale + bob;
        canvas.drawOval(cx - shadowW, shadowY - shadowH/2,
                        cx + shadowW, shadowY + shadowH/2, shadowPaint);

        // ── Patas ─────────────────────────────────────────────────────────────
        drawLegs(canvas, cx, cy, scale, bob);

        // ── Cuerpo principal ──────────────────────────────────────────────────
        // Gradiente amarillo 3D
        float bodyTop    = cy - 20f * scale + bob;
        float bodyBottom = cy + 38f * scale + bob;
        LinearGradient bodyGrad = new LinearGradient(
            cx - 30f*scale, bodyTop,
            cx + 30f*scale, bodyBottom,
            new int[]{0xFFFFE84B, 0xFFFFCC00, 0xFFE6A800},
            new float[]{0f, 0.6f, 1f},
            Shader.TileMode.CLAMP
        );
        bodyPaint.setShader(bodyGrad);

        RectF bodyOval = new RectF(
            cx - 36f*scale, bodyTop,
            cx + 36f*scale, bodyBottom
        );
        canvas.drawOval(bodyOval, bodyPaint);

        // ── Cabeza ────────────────────────────────────────────────────────────
        float headCX = cx + 8f*scale;
        float headCY = cy - 28f*scale + bob;
        float headR  = 24f*scale;

        LinearGradient headGrad = new LinearGradient(
            headCX - headR, headCY - headR,
            headCX + headR, headCY + headR,
            new int[]{0xFFFFEE55, 0xFFFFCC00, 0xFFE6A800},
            new float[]{0f, 0.55f, 1f},
            Shader.TileMode.CLAMP
        );
        bodyPaint.setShader(headGrad);
        canvas.drawCircle(headCX, headCY, headR, bodyPaint);

        // ── Ala ───────────────────────────────────────────────────────────────
        float wingTilt = isWalking ? (float)(Math.sin(walkPhase * Math.PI * 2) * 8f) : 0f;
        canvas.save();
        canvas.rotate(wingTilt, cx - 10f*scale, cy + 5f*scale + bob);
        LinearGradient wingGrad = new LinearGradient(
            cx - 32f*scale, cy + bob,
            cx + 5f*scale, cy + 28f*scale + bob,
            new int[]{0xFFFFDD00, 0xFFE6A800},
            null, Shader.TileMode.CLAMP
        );
        wingPaint.setShader(wingGrad);
        RectF wingOval = new RectF(
            cx - 34f*scale, cy - 5f*scale + bob,
            cx + 2f*scale,  cy + 30f*scale + bob
        );
        canvas.drawOval(wingOval, wingPaint);
        canvas.restore();

        // ── Pico ──────────────────────────────────────────────────────────────
        float beakX = headCX + headR - 4f*scale;
        float beakY = headCY + 2f*scale;
        // Sombra pico
        canvas.drawOval(beakX - 1f*scale, beakY + 2f*scale,
                        beakX + 18f*scale, beakY + 10f*scale, beakShadow);
        // Pico principal
        LinearGradient beakGrad = new LinearGradient(
            beakX, beakY - 4f*scale,
            beakX, beakY + 8f*scale,
            new int[]{0xFFFF8C00, 0xFFCC5500},
            null, Shader.TileMode.CLAMP
        );
        beakPaint.setShader(beakGrad);
        RectF beakOval = new RectF(beakX, beakY - 4f*scale,
                                   beakX + 18f*scale, beakY + 8f*scale);
        canvas.drawOval(beakOval, beakPaint);
        // Línea media del pico
        Paint beakLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        beakLine.setColor(0xFFAA4400);
        beakLine.setStrokeWidth(1.5f*scale);
        canvas.drawLine(beakX + 1f*scale, beakY + 2f*scale,
                        beakX + 16f*scale, beakY + 2f*scale, beakLine);

        // ── Ojo ───────────────────────────────────────────────────────────────
        float eyeX = headCX + 8f*scale;
        float eyeY = headCY - 4f*scale;
        canvas.drawCircle(eyeX, eyeY, 7f*scale, eyePaint);
        canvas.drawCircle(eyeX + 1.5f*scale, eyeY + 1f*scale, 4f*scale, pupilPaint);
        // Brillo en ojo
        highlightP.setColor(0xCCFFFFFF);
        canvas.drawCircle(eyeX - 1f*scale, eyeY - 2f*scale, 2f*scale, highlightP);

        // ── Brillo cuerpo ──────────────────────────────────────────────────────
        highlightP.setColor(0x55FFFFFF);
        canvas.drawOval(cx - 18f*scale, bodyTop + 4f*scale,
                        cx + 10f*scale, bodyTop + 20f*scale, highlightP);
        // Brillo cabeza
        canvas.drawCircle(headCX - 8f*scale, headCY - 10f*scale, 7f*scale, highlightP);

        canvas.restore(); // flip
    }

    private void drawLegs(Canvas canvas, float cx, float cy, float scale, float bob) {
        footPaint.setColor(0xFFCC5500);
        footPaint.setStrokeWidth(4f*scale);
        footPaint.setStyle(Paint.Style.STROKE);
        footPaint.setStrokeCap(Paint.Cap.ROUND);

        float baseY = cy + 38f*scale + bob;

        // Fase de paso — una pata sube, la otra baja
        float leftLift  = isWalking ? (float)(Math.max(0, Math.sin(walkPhase * Math.PI * 2)) * 8f * scale) : 0f;
        float rightLift = isWalking ? (float)(Math.max(0, -Math.sin(walkPhase * Math.PI * 2)) * 8f * scale) : 0f;

        // Pata izquierda
        float lx = cx - 12f*scale;
        float ly = baseY - leftLift;
        canvas.drawLine(lx, cy + 30f*scale + bob, lx, ly, footPaint);
        drawFoot(canvas, lx, ly, scale);

        // Pata derecha
        float rx = cx + 8f*scale;
        float ry = baseY - rightLift;
        canvas.drawLine(rx, cy + 30f*scale + bob, rx, ry, footPaint);
        drawFoot(canvas, rx, ry, scale);
    }

    private void drawFoot(Canvas canvas, float x, float y, float scale) {
        footPaint.setStyle(Paint.Style.FILL);
        // Tres dedos tipo palmípedo
        Path foot = new Path();
        foot.moveTo(x, y);
        foot.lineTo(x - 9f*scale, y + 5f*scale);
        foot.lineTo(x - 4f*scale, y + 8f*scale);
        foot.lineTo(x + 1f*scale, y + 5f*scale);
        foot.lineTo(x + 6f*scale, y + 8f*scale);
        foot.lineTo(x + 10f*scale, y + 4f*scale);
        foot.close();
        canvas.drawPath(foot, footPaint);
        footPaint.setStyle(Paint.Style.STROKE);
    }
}
