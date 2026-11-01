package com.appletv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * Pure hardware-accelerated Apple Watch / iOS StandBy analog clock dial.
 * Features:
 * - Ultra crisp tick marks (12 major hour ticks, 48 subtle minute ticks).
 * - Smooth Apple watchOS hour, minute, and orange/accent second hand.
 * - Central pinion with contrast cap.
 * - Dynamic color support (e.g. Clean White/Graphite or StandBy Red Tint).
 */
public class StandByAnalogClock extends View {

    private final Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hourHandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minuteHandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondHandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int primaryColor = 0xFFFFFFFF;
    private int accentColor = 0xFFFF453A; // Apple vibrant orange-red
    private int secondaryColor = 0x88FFFFFF;

    public StandByAnalogClock(Context context) {
        super(context);
        init();
    }

    public StandByAnalogClock(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StandByAnalogClock(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        hourHandPaint.setStrokeCap(Paint.Cap.ROUND);
        minuteHandPaint.setStrokeCap(Paint.Cap.ROUND);
        secondHandPaint.setStrokeCap(Paint.Cap.ROUND);
        updateColors();
    }

    public void setColors(int primary, int secondary, int accent) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
        this.accentColor = accent;
        updateColors();
        invalidate();
    }

    private void updateColors() {
        hourHandPaint.setColor(primaryColor);
        minuteHandPaint.setColor(primaryColor);
        secondHandPaint.setColor(accentColor);
        centerCapPaint.setColor(primaryColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float cx = width / 2.0f;
        float cy = height / 2.0f;
        float radius = Math.min(cx, cy) - 16.0f;

        // 1. Draw Hour and Minute ticks
        dialPaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 60; i++) {
            float angle = (float) (i * Math.PI * 2 / 60);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            if (i % 5 == 0) {
                // Major hour marker
                dialPaint.setColor(primaryColor);
                dialPaint.setStrokeWidth(radius * 0.035f);
                float startR = radius * 0.82f;
                float endR = radius * 0.96f;
                canvas.drawLine(cx + startR * sin, cy - startR * cos,
                                cx + endR * sin, cy - endR * cos, dialPaint);
            } else {
                // Minor minute marker
                dialPaint.setColor(secondaryColor);
                dialPaint.setStrokeWidth(radius * 0.015f);
                float startR = radius * 0.88f;
                float endR = radius * 0.95f;
                canvas.drawLine(cx + startR * sin, cy - startR * cos,
                                cx + endR * sin, cy - endR * cos, dialPaint);
            }
        }

        // 2. Compute live angles
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int millis = calendar.get(Calendar.MILLISECOND);

        float secondAngle = (second + millis / 1000.0f) * 6.0f;
        float minuteAngle = (minute + second / 60.0f) * 6.0f;
        float hourAngle = (hour + minute / 60.0f) * 30.0f;

        // 3. Draw Hour Hand
        hourHandPaint.setStrokeWidth(radius * 0.055f);
        drawHand(canvas, cx, cy, hourAngle, radius * 0.52f, hourHandPaint);

        // 4. Draw Minute Hand
        minuteHandPaint.setStrokeWidth(radius * 0.038f);
        drawHand(canvas, cx, cy, minuteAngle, radius * 0.78f, minuteHandPaint);

        // 5. Center Pinion
        centerCapPaint.setStyle(Paint.Style.FILL);
        centerCapPaint.setColor(primaryColor);
        canvas.drawCircle(cx, cy, radius * 0.05f, centerCapPaint);

        // 6. Draw Second Hand (Thin orange/red line extending slightly behind center)
        secondHandPaint.setStrokeWidth(radius * 0.018f);
        float secRad = (float) Math.toRadians(secondAngle);
        float sSin = (float) Math.sin(secRad);
        float sCos = (float) Math.cos(secRad);
        // tail
        float tail = radius * 0.16f;
        float length = radius * 0.88f;
        canvas.drawLine(cx - tail * sSin, cy + tail * sCos,
                        cx + length * sSin, cy - length * sCos, secondHandPaint);

        // Second hand center dot
        secondHandPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * 0.03f, secondHandPaint);
    }

    private void drawHand(Canvas canvas, float cx, float cy, float angleDeg, float length, Paint paint) {
        float rad = (float) Math.toRadians(angleDeg);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        canvas.drawLine(cx, cy, cx + length * sin, cy - length * cos, paint);
    }
}

