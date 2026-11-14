package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Small round "boost" ball placed on both sides of the brightness pill:
 * the LEFT one lifts the panel's apparent brightness (white tint overlay +
 * full backlight), the RIGHT one dims the screen (grey system overlay).
 */
public class BoostBallView extends View {

    public static final int MODE_BRIGHT = 0;
    public static final int MODE_DARK = 1;

    public interface OnBoostClickListener {
        void onBoostClick(BoostBallView view, int mode);
    }

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mMode = MODE_BRIGHT;
    private boolean mActive;
    private OnBoostClickListener mListener;

    public BoostBallView(Context context) {
        this(context, null);
    }

    public BoostBallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeCap(Paint.Cap.ROUND);
        mRimPaint.setStyle(Paint.Style.STROKE);
        setClickable(true);
    }

    public void setMode(int mode) {
        mMode = mode;
        requestLayout();
        invalidate();
    }

    public void setActive(boolean active) {
        mActive = active;
        invalidate();
    }

    public void setOnBoostClickListener(OnBoostClickListener listener) {
        mListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float r = Math.min(getWidth(), getHeight()) * 0.5f;
        float rim = Math.max(1.2f, r * 0.07f);

        if (mMode == MODE_BRIGHT) {
            mFillPaint.setShader(new RadialGradient(cx, cy - r * 0.2f, r,
                    new int[]{0xCCFFFFFF, 0x66FFFFFF, 0x22FFFFFF},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        } else {
            mFillPaint.setShader(new RadialGradient(cx, cy - r * 0.2f, r,
                    new int[]{0xCC3A3A3A, 0xCC242424, 0xCC141414},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(cx, cy, r - rim, mFillPaint);
        mFillPaint.setShader(null);
        mRimPaint.setStrokeWidth(rim);
        mRimPaint.setColor(mActive ? 0xFFFFFFFF
                : (mMode == MODE_BRIGHT ? 0x99FFFFFF : 0x66777777));
        canvas.drawCircle(cx, cy, r - rim, mRimPaint);

        mIconPaint.setStrokeWidth(Math.max(1.4f, r * 0.13f));
        mIconPaint.setColor(mMode == MODE_BRIGHT ? 0xFF333333 : 0xFFFFFFFF);
        if (mMode == MODE_BRIGHT) {
            // sun
            mIconPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, r * 0.26f, mIconPaint);
            mIconPaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                float c = (float) Math.cos(a);
                float s = (float) Math.sin(a);
                canvas.drawLine(cx + c * r * 0.42f, cy + s * r * 0.42f,
                        cx + c * r * 0.62f, cy + s * r * 0.62f, mIconPaint);
            }
        } else {
            // crescent moon
            mIconPaint.setStyle(Paint.Style.FILL);
            android.graphics.Path p = new android.graphics.Path();
            p.addCircle(cx, cy, r * 0.44f, android.graphics.Path.Direction.CW);
            p.addCircle(cx + r * 0.26f, cy - r * 0.18f, r * 0.38f,
                    android.graphics.Path.Direction.CCW);
            canvas.drawPath(p, mIconPaint);
            mIconPaint.setStyle(Paint.Style.STROKE);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setAlpha(0.7f);
                return true;
            case MotionEvent.ACTION_UP:
                setAlpha(1f);
                if (mListener != null) {
                    mListener.onBoostClick(this, mMode);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setAlpha(1f);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
