package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Frosted-glass circular shortcut of the pull-down grid.
 *
 * The user's spec: two rows of four round buttons -
 *   Wi-Fi | mobile data | Bluetooth | hotspot
 *   screen flashlight | screen recording | eye comfort | keep screen awake
 * - and every one of them must have the frosted-glass look.
 *
 * Glass look: the panel already paints a blurred screenshot of the layer below
 * as its backdrop, so each button is drawn as a translucent radial "glass"
 * circle (soft white gradient + bright rim + top highlight) that lets that blur
 * show through.  The icons are hand-drawn paths (no bitmaps, no Lottie, no
 * allocations in onDraw) to stay light on the 1 GB / Wear-4100 hardware.
 */
public class GlassTileView extends View {

    public interface OnTileClickListener {
        void onTileClick(int index);
    }

    /** Long-press drag: lets the user swap a shortcut with the quick cube. */
    public interface OnTileDragListener {
        void onTileLongPress(GlassTileView tile);

        void onTileDragMove(GlassTileView tile, float totalDx, float totalDy);

        void onTileDragEnd(GlassTileView tile, boolean cancelled);
    }

    private final Paint mGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();
    private final RectF mHighlightRect = new RectF();
    private final Path mPath = new Path();

    private int mIndex;
    private boolean mOn;
    private boolean mPressed;
    private OnTileClickListener mListener;
    private android.graphics.Shader mGradient;
    private RadialGradient mGradientOn;
    private float mDownX;
    private float mDownY;
    private boolean mCancelled;
    private final int mTouchSlop;
    private OnTileDragListener mDragListener;
    private boolean mLongPressed;
    private boolean mDragging;
    private float mDownRawX;
    private float mDownRawY;
    private Runnable mLongPressRunnable;
    private boolean mShaking;
    private int mGradientTheme = -1;
    private float mOnProgress;
    private android.animation.ValueAnimator mIconAnimator;
    /** 0..1 flight animation for the airplane tile (out, then back in). */
    private float mAircraftPhase;
    private boolean mAirplaneAnimating;
    private android.animation.ValueAnimator mAircraftAnimator;
    private boolean mEditMode;
    /** Caption drawn under the ball while the drag-edit mode is active. */
    private String mCaption;
    private boolean mCaptionVisible;
    private boolean mCaptionDrawn;
    private float mBallCx, mBallCy, mBallR;
    private final android.text.TextPaint mCaptionPaint =
            new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable mShakeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mShaking) {
                return;
            }
            long t = System.nanoTime() / 1_000_000L;
            double a = (t % 170L) / 170.0 * Math.PI * 2.0;
            // very fast, very small wobble along the bottom-left <-> top-right diagonal
            float d = (float) Math.sin(a) * 1.1f;
            setRotation(0f);
            setTranslationX(d);
            setTranslationY(-d);
            postOnAnimation(this);
        }
    };

    public GlassTileView(Context context) {
        this(context, null);
    }

    public GlassTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setColor(0xCCFFFFFF);
        mHighlightPaint.setStyle(Paint.Style.STROKE);
        mHighlightPaint.setColor(0x33FFFFFF);
        mGlowPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeCap(Paint.Cap.ROUND);
        mIconPaint.setStrokeJoin(Paint.Join.ROUND);
        mIconPaint.setColor(Color.WHITE);
        setClickable(true);
        mTouchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setTileIndex(int index) {
        mIndex = index;
        invalidate();
    }

    public int getTileIndex() {
        return mIndex;
    }

    public void setOnTileClickListener(OnTileClickListener listener) {
        mListener = listener;
    }

    public void setOnTileDragListener(OnTileDragListener listener) {
        mDragListener = listener;
    }

    public void setOn(boolean on) {
        if (mOn != on) {
            mOn = on;
            animateIcon(on ? 1f : 0f);
            invalidate();
        }
    }

    /** Animates the per-layer "light up from the bottom / from the centre" effect. */
    /** Airplane feedback: the glyph flies out and then flies back in. */
    private void animateAirplane() {
        if (mAircraftAnimator != null) {
            mAircraftAnimator.cancel();
        }
        mAirplaneAnimating = true;
        mAircraftAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        mAircraftAnimator.setDuration(520L);
        mAircraftAnimator.addUpdateListener(
                new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                        mAircraftPhase = (Float) animation.getAnimatedValue();
                        invalidate();
                    }
                });
        mAircraftAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                mAirplaneAnimating = false;
                mAircraftPhase = 0f;
                invalidate();
            }
        });
        mAircraftAnimator.start();
    }

    private void animateIcon(float target) {
        if (mIconAnimator != null) {
            mIconAnimator.cancel();
        }
        mIconAnimator = android.animation.ValueAnimator.ofFloat(mOnProgress, target);
        mIconAnimator.setDuration(340L);
        mIconAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                mOnProgress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        mIconAnimator.start();
    }

    public boolean isOn() {
        return mOn;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildGradients();
    }

    /** Rebuilds the glass ramps for the current theme (white / grey). */
    private void buildGradients() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        mGradientTheme = PillBackground.getThemeVersion();
        // Edit mode draws a caption under the ball. QSPanel.setEditSpacing gives
        // the row balls extra height for that band (h > w); the quick-cube balls
        // keep their square cell and draw the caption outside their bounds.
        float capH = 0f;
        if (mCaptionVisible) {
            mCaptionPaint.setTextSize(Math.min(w, h) * 0.17f);
            mCaptionPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            float band = h - w;
            if (band > mCaptionPaint.getTextSize()) {
                capH = band;
            }
        }
        float size = Math.min(w, h - capH);
        float cx = w * 0.5f;
        float cy = size * 0.5f;
        mBallCx = cx;
        mBallCy = cy;
        mBallR = size * 0.5f;
        mOval.set(cx - size * 0.5f, cy - size * 0.5f, cx + size * 0.5f, cy + size * 0.5f);
        // clear centre (see-through) with a soft bright rim: radial white ramp
        // (grey ramp when the panel is in dark mode)
        boolean darkGlass = PillBackground.isDarkMode();
        int c0 = darkGlass ? 0x0AA0A0A0 : 0x06FFFFFF;
        int c1 = darkGlass ? 0x22A0A0A0 : 0x12FFFFFF;
        int c2 = darkGlass ? 0x48A0A0A0 : 0x3AFFFFFF;
        int c3 = darkGlass ? 0x70C0C0C0 : 0x62FFFFFF;
        mGradient = new RadialGradient(cx, cy, size * 0.56f,
                new int[]{c0, c1, c2, c3},
                new float[]{0f, 0.52f, 0.82f, 1f}, Shader.TileMode.CLAMP);
        // ON state: solid blue glass (HQB accent blue #108CFF)
        mGradientOn = new RadialGradient(cx, cy, size * 0.58f,
                new int[]{0xB8108CFF, 0xDA0B7DE0, 0xE6086BB8},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        mGlassPaint.setShader(mGradient);
        mRimPaint.setStrokeWidth(Math.max(1f, size * 0.025f));
        mHighlightPaint.setStrokeWidth(Math.max(1f, size * 0.02f));
        mGlowPaint.setStrokeWidth(Math.max(1.5f, size * 0.045f));
        mIconPaint.setStrokeWidth(Math.max(1.6f, size * 0.055f));
        mHighlightRect.set(cx - size * 0.34f, cy - size * 0.34f,
                cx + size * 0.34f, cy + size * 0.34f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mGradientTheme != PillBackground.getThemeVersion()
                || mCaptionVisible != mCaptionDrawn) {
            mCaptionDrawn = mCaptionVisible;
            buildGradients();
        }
        float cx = mBallCx > 0 ? mBallCx : getWidth() * 0.5f;
        float cy = mBallCy > 0 ? mBallCy : getHeight() * 0.5f;
        float radius = mBallR > 0 ? mBallR : Math.min(getWidth(), getHeight()) * 0.5f;
        if (radius <= 0) {
            return;
        }
        mGlassPaint.setShader(mOn ? mGradientOn : mGradient);
        mGlassPaint.setAlpha(mOn ? 255 : 210);
        canvas.drawCircle(cx, cy, radius - mRimPaint.getStrokeWidth(), mGlassPaint);
        // ON: bright rim + blue glow ring, OFF: soft white rim
        mRimPaint.setColor(mOn ? 0xFFFFFFFF : 0x99FFFFFF);
        canvas.drawCircle(cx, cy, radius - mRimPaint.getStrokeWidth(), mRimPaint);
        if (mOn) {
            mRimPaint.setColor(0x66108CFF);
            mRimPaint.setStrokeWidth(mRimPaint.getStrokeWidth() * 2.2f);
            canvas.drawCircle(cx, cy, radius - mRimPaint.getStrokeWidth(), mRimPaint);
            mRimPaint.setStrokeWidth(mRimPaint.getStrokeWidth() / 2.2f);
        }
        // frosted top highlight
        canvas.drawArc(mHighlightRect, 200f, 140f, false, mHighlightPaint);
        if (mPressed) {
            mGlassPaint.setAlpha(70);
            canvas.drawCircle(cx, cy, radius, mGlassPaint);
        }
        mIconPaint.setColor(0xFFFFFFFF);
        // rim glow: the TOP-LEFT edge is clearly brighter, fading to the bottom-right
        mGlowPaint.setShader(new android.graphics.LinearGradient(
                cx - radius, cy - radius, cx + radius, cy + radius,
                new int[]{0xA8FFFFFF, 0x44FFFFFF, 0x12FFFFFF, 0x04000000, 0x00000000},
                new float[]{0f, 0.22f, 0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius - mGlowPaint.getStrokeWidth(),
                mGlowPaint);
        mGlowPaint.setShader(null);
        drawIcon(canvas, cx, cy, radius * 0.58f, mOn ? 255 : 150);
        // edit mode: the shortcut's function name follows the ball
        if (mCaptionVisible && mCaption != null && !mCaption.isEmpty()) {
            mCaptionPaint.setColor(0xFFFFFFFF);
            mCaptionPaint.setTextAlign(Paint.Align.CENTER);
            mCaptionPaint.setShadowLayer(3f, 0f, 1f, 0xCC000000);
            float baseline = cy + radius + mCaptionPaint.getTextSize() * 1.05f;
            canvas.drawText(mCaption, cx, baseline, mCaptionPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPressed = true;
                mCancelled = false;
                mLongPressed = false;
                mDragging = false;
                mDownX = event.getX();
                mDownY = event.getY();
                mDownRawX = event.getRawX();
                mDownRawY = event.getRawY();
                invalidate();
                if (mDragListener != null) {
                    mLongPressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            stopSelfShake();
                            mLongPressed = true;
                            mPressed = false;
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            invalidate();
                            if (mDragListener != null) {
                                mDragListener.onTileLongPress(GlassTileView.this);
                            }
                        }
                    };
                    postDelayed(mLongPressRunnable, 450L);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mLongPressed) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    // dragging: the tile follows the finger, no click on release
                    mDragging = true;
                    setTranslationX(event.getRawX() - mDownRawX);
                    setTranslationY(event.getRawY() - mDownRawY);
                    setScaleX(1.15f);
                    setScaleY(1.15f);
                    setAlpha(0.92f);
                    if (mDragListener != null) {
                        mDragListener.onTileDragMove(this,
                                event.getRawX() - mDownRawX,
                                event.getRawY() - mDownRawY);
                    }
                    return true;
                }
                if (!mCancelled
                        && (Math.abs(event.getX() - mDownX) > mTouchSlop
                        || Math.abs(event.getY() - mDownY) > mTouchSlop)) {
                    // the finger is scrolling the panel, not tapping the ball
                    mCancelled = true;
                    mPressed = false;
                    cancelLongPressRunnable();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelLongPressRunnable();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                mPressed = false;
                invalidate();
                boolean wasDragging = mDragging;
                mDragging = false;
                if (wasDragging) {
                    // snap instantly: the panel must not show a fly-back animation
                    setTranslationX(0f);
                    setTranslationY(0f);
                    setScaleX(1f);
                    setScaleY(1f);
                    setAlpha(1f);
                    if (mDragListener != null) {
                        mDragListener.onTileDragEnd(this, false);
                    }
                    return true;
                }
                if (!mCancelled && !mLongPressed && mListener != null && isEnabled()) {
                    mListener.onTileClick(mIndex);
                }
                mLongPressed = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelLongPressRunnable();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                mPressed = false;
                mCancelled = true;
                boolean wasCancelledDrag = mDragging;
                mDragging = false;
                mLongPressed = false;
                if (wasCancelledDrag) {
                    resetDragVisual();
                    if (mDragListener != null) {
                        mDragListener.onTileDragEnd(this, true);
                    }
                }
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void cancelLongPressRunnable() {
        if (mLongPressRunnable != null) {
            removeCallbacks(mLongPressRunnable);
            mLongPressRunnable = null;
        }
    }

    /** Stops only this tile's wobble (used when its own drag starts). */
    private void stopSelfShake() {
        if (mShaking) {
            mShaking = false;
            removeCallbacks(mShakeRunnable);
            setRotation(0f);
        }
    }

    /** "Edit mode" wobble, started on all tiles while dragging a shortcut. */
    /** Edit mode: every tile can be dragged immediately (no long-press needed). */
    public void setEditMode(boolean editMode) {
        mEditMode = editMode;
    }

    /** Sets the function name shown under the ball while editing. */
    public void setCaption(String caption) {
        if (caption == null ? mCaption == null : caption.equals(mCaption)) {
            return;
        }
        mCaption = caption;
        invalidate();
    }

    /** Shows / hides the caption (only on while the drag-edit mode is active). */
    public void setCaptionVisible(boolean visible) {
        if (mCaptionVisible == visible) {
            return;
        }
        mCaptionVisible = visible;
        buildGradients();
        invalidate();
    }

    public void startShake() {
        if (!mShaking) {
            mShaking = true;
            postOnAnimation(mShakeRunnable);
        }
    }

    public void stopShake() {
        if (mShaking) {
            mShaking = false;
            removeCallbacks(mShakeRunnable);
            setRotation(0f);
            if (!mDragging && !mEditMode) {
                setTranslationX(0f);
                setTranslationY(0f);
            }
        }
    }

    /** Restores scale/alpha/translation after a drag. */
    public void resetDragVisual() {
        animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(120L).start();
    }

    // ------------------------------------------------------------ icon painter

    private void drawIcon(Canvas canvas, float cx, float cy, float r, int alpha) {
        Paint.Style old = mIconPaint.getStyle();
        mIconPaint.setStyle(Paint.Style.STROKE);
        switch (mIndex) {
            case QsToggles.TILE_WIFI:
                drawWifi(canvas, cx, cy, r, alpha);
                mIconPaint.setStyle(old);
                return;
            case QsToggles.TILE_MOBILE_DATA:
                drawSignal(canvas, cx, cy, r, alpha);
                mIconPaint.setStyle(old);
                return;
            case QsToggles.TILE_BLUETOOTH:
                // original XTC vector shape: grey base, then a WHITE fill that
                // rises from the bottom while the switch is ON (fill animation)
                mIconPaint.setStyle(Paint.Style.FILL);
                mIconPaint.setColor(0xFF8E8E8E);
                mIconPaint.setAlpha(alpha);
                QsIcons.drawTile(canvas, mIndex, cx, cy, r * 1.85f, mIconPaint);
                if (mOnProgress > 0.01f) {
                    int save = canvas.save();
                    float half = r * 0.94f;
                    float top = (cy + half) - (half * 2f) * mOnProgress;
                    canvas.clipRect(cx - half, top, cx + half, cy + half);
                    mIconPaint.setColor(0xFFFFFFFF);
                    mIconPaint.setAlpha(alpha);
                    QsIcons.drawTile(canvas, mIndex, cx, cy, r * 1.85f, mIconPaint);
                    canvas.restoreToCount(save);
                }
                mIconPaint.setColor(0xFFFFFFFF);
                mIconPaint.setStyle(old);
                return;
            case QsToggles.TILE_HOTSPOT:
                drawHotspot(canvas, cx, cy, r, alpha);
                mIconPaint.setStyle(old);
                return;
            case QsToggles.TILE_FLASHLIGHT: {
                mIconPaint.setStyle(Paint.Style.FILL);
                mIconPaint.setAlpha(alpha);
                QsIcons.drawTile(canvas, mIndex, cx, cy, r * 2.45f, mIconPaint);
                mIconPaint.setAlpha(alpha);
                mIconPaint.setStyle(old);
                return;
            }
            case QsToggles.TILE_AIRPLANE: {
                // grey base glyph, then a white fill that sweeps in from the LEFT
                float box = r * 1.85f;
                float half = box * 0.5f;
                mIconPaint.setStyle(Paint.Style.FILL);
                mIconPaint.setColor(0xFF8E8E8E);
                mIconPaint.setAlpha(alpha);
                QsIcons.drawTile(canvas, mIndex, cx, cy, box, mIconPaint);
                if (mOnProgress > 0.01f) {
                    int save = canvas.save();
                    canvas.clipRect(cx - half, cy - half,
                            cx - half + box * mOnProgress, cy + half);
                    mIconPaint.setColor(0xFFFFFFFF);
                    mIconPaint.setAlpha(alpha);
                    QsIcons.drawTile(canvas, mIndex, cx, cy, box, mIconPaint);
                    canvas.restoreToCount(save);
                }
                mIconPaint.setStyle(old);
                return;
            }
            default:
                break;
        }
        mIconPaint.setAlpha(alpha);
        mIconPaint.setStyle(Paint.Style.FILL);
        if (QsIcons.drawTile(canvas, mIndex, cx, cy, r * 1.85f, mIconPaint)) {
            mIconPaint.setStyle(old);
            return;
        }
        mIconPaint.setStyle(Paint.Style.STROKE);
        switch (mIndex) {
            case QsToggles.TILE_WIFI:
            case QsToggles.TILE_MOBILE_DATA:
            case QsToggles.TILE_BLUETOOTH:
            case QsToggles.TILE_HOTSPOT:
                break;
            case QsToggles.TILE_FLASHLIGHT:
                drawFlashlight(canvas, cx, cy, r);
                break;
            case QsToggles.TILE_SCREEN_RECORD:
                drawScreenRecord(canvas, cx, cy, r);
                break;
            case QsToggles.TILE_EYE_COMFORT:
                drawEye(canvas, cx, cy, r);
                break;
            case QsToggles.TILE_KEEP_AWAKE:
                drawSun(canvas, cx, cy, r);
                break;
            case QsToggles.TILE_AIRPLANE:
                drawAirplane(canvas, cx, cy, r);
                break;
            case QsToggles.TILE_MULTI_TOUCH:
                drawMultiTouch(canvas, cx, cy, r, alpha);
                mIconPaint.setStyle(old);
                return;
            case QsToggles.TILE_DND:
                drawDnd(canvas, cx, cy, r);
                break;
            case QsToggles.TILE_ROTATION:
                drawRotation(canvas, cx, cy, r);
                break;
            default:
                break;
        }
        mIconPaint.setStyle(old);
    }

    private int scaledAlpha(int base, float factor) {
        return Math.max(0, Math.min(255, (int) (base * factor)));
    }

    /** Wi-Fi: dot first, then the arcs light up layer by layer from the bottom. */
    private void drawWifi(Canvas canvas, float cx, float cy, float r, int alpha) {
        float baseY = cy + r * 0.50f;
        // dot (bottom layer): keep it clearly visible even when Wi-Fi is off
        float dotP = Math.min(1f, mOnProgress * 4f);
        mIconPaint.setStyle(Paint.Style.FILL);
        mIconPaint.setAlpha(scaledAlpha(alpha, 0.45f + 0.55f * dotP));
        mIconPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, baseY - r * 0.02f, r * 0.135f, mIconPaint);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.6f, r * 0.13f));
        // three arcs, inner -> outer
        for (int i = 0; i < 3; i++) {
            float rr = r * (0.34f + 0.25f * i);
            mOval.set(cx - rr, baseY - rr, cx + rr, baseY + rr);
            float layer = Math.max(0f, Math.min(1f, mOnProgress * 3f - i));
            mIconPaint.setAlpha(scaledAlpha(alpha, 0.25f + 0.75f * layer));
            canvas.drawArc(mOval, 215f, 110f, false, mIconPaint);
        }
        mIconPaint.setAlpha(alpha);
    }

    /** Mobile data: the four bars light up one by one, shortest first. */
    private void drawSignal(Canvas canvas, float cx, float cy, float r, int alpha) {
        int bars = 4;
        float barW = r * 0.17f;
        float gap = r * 0.075f;
        float totalW = bars * barW + (bars - 1) * gap;
        float left = cx - totalW * 0.5f;
        float baseY = cy + r * 0.44f;
        mIconPaint.setStyle(Paint.Style.FILL);
        mIconPaint.setColor(0xFFFFFFFF);
        for (int i = 0; i < bars; i++) {
            float h = r * (0.24f + 0.21f * i);
            float layer = Math.max(0f, Math.min(1f, mOnProgress * bars - i));
            mIconPaint.setAlpha(scaledAlpha(alpha, 0.22f + 0.78f * layer));
            float x = left + i * (barW + gap);
            mOval.set(x, baseY - h, x + barW, baseY);
            canvas.drawRoundRect(mOval, barW * 0.35f, barW * 0.35f, mIconPaint);
        }
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setAlpha(alpha);
    }

    /** Bluetooth: grey hand-drawn strokes, never highlighted. */
    private void drawBluetoothGray(Canvas canvas, float cx, float cy, float r, int alpha) {
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.6f, r * 0.135f));
        mIconPaint.setStrokeJoin(Paint.Join.ROUND);
        mIconPaint.setStrokeCap(Paint.Cap.ROUND);
        mIconPaint.setColor(0xFF8E8E8E);
        mIconPaint.setAlpha(scaledAlpha(alpha, 0.90f));
        mPath.reset();
        // vertical stem
        mPath.moveTo(cx, cy - r * 0.72f);
        mPath.lineTo(cx, cy + r * 0.72f);
        // upper wing
        mPath.moveTo(cx - r * 0.42f, cy - r * 0.34f);
        mPath.lineTo(cx + r * 0.42f, cy + r * 0.32f);
        // lower wing
        mPath.moveTo(cx - r * 0.42f, cy + r * 0.34f);
        mPath.lineTo(cx + r * 0.42f, cy - r * 0.32f);
        canvas.drawPath(mPath, mIconPaint);
        mIconPaint.setStrokeCap(Paint.Cap.BUTT);
        mIconPaint.setAlpha(alpha);
    }

    /** Hotspot: centre dot first, then the wide arcs ring out from the centre. */
    private void drawHotspot(Canvas canvas, float cx, float cy, float r, int alpha) {
        mIconPaint.setColor(0xFFFFFFFF);
        mIconPaint.setStyle(Paint.Style.FILL);
        float dotP = Math.min(1f, mOnProgress * 4f);
        mIconPaint.setAlpha(scaledAlpha(alpha, 0.30f + 0.70f * dotP));
        canvas.drawCircle(cx, cy, r * 0.13f, mIconPaint);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.6f, r * 0.12f));
        for (int i = 0; i < 2; i++) {
            float rr = r * (0.36f + 0.24f * i);
            mOval.set(cx - rr, cy - rr, cx + rr, cy + rr);
            float layer = Math.max(0f, Math.min(1f, mOnProgress * 2f - i));
            mIconPaint.setAlpha(scaledAlpha(alpha, 0.22f + 0.78f * layer));
            // wide (major) arcs, opening at the bottom-left / bottom-right
            canvas.drawArc(mOval, 132f, 276f, false, mIconPaint);
        }
        mIconPaint.setAlpha(alpha);
    }

    /** Multi-touch: two fingertips with touch ripples. */
    private void drawMultiTouch(Canvas canvas, float cx, float cy, float r, int alpha) {
        mIconPaint.setColor(0xFFFFFFFF);
        mIconPaint.setAlpha(alpha);
        mIconPaint.setStyle(Paint.Style.FILL);
        // two fingertips
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.10f, r * 0.17f, mIconPaint);
        canvas.drawCircle(cx + r * 0.30f, cy - r * 0.28f, r * 0.15f, mIconPaint);
        // touch ripples under the fingers
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.4f, r * 0.09f));
        mOval.set(cx - r * 0.52f, cy + r * 0.06f, cx - r * 0.04f, cy + r * 0.54f);
        canvas.drawArc(mOval, 200f, 140f, false, mIconPaint);
        mOval.set(cx + r * 0.06f, cy - r * 0.12f, cx + r * 0.54f, cy + r * 0.36f);
        canvas.drawArc(mOval, 200f, 140f, false, mIconPaint);
    }

    private void drawFlashlight(Canvas canvas, float cx, float cy, float r) {
        mIconPaint.setColor(0xFFFFFFFF);
        mIconPaint.setStyle(Paint.Style.FILL);
        // head
        mOval.set(cx - r * 0.40f, cy - r * 0.52f, cx + r * 0.40f, cy - r * 0.04f);
        canvas.drawRoundRect(mOval, r * 0.10f, r * 0.10f, mIconPaint);
        // body
        mOval.set(cx - r * 0.24f, cy + r * 0.02f, cx + r * 0.24f, cy + r * 0.62f);
        canvas.drawRoundRect(mOval, r * 0.10f, r * 0.10f, mIconPaint);
        // light rays
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.4f, r * 0.10f));
        canvas.drawLine(cx - r * 0.20f, cy - r * 0.68f, cx - r * 0.44f, cy - r * 0.86f, mIconPaint);
        canvas.drawLine(cx, cy - r * 0.74f, cx, cy - r * 0.96f, mIconPaint);
        canvas.drawLine(cx + r * 0.20f, cy - r * 0.68f, cx + r * 0.44f, cy - r * 0.86f, mIconPaint);
    }

    private void drawScreenRecord(Canvas canvas, float cx, float cy, float r) {
        mIconPaint.setColor(0xFFFFFFFF);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.6f, r * 0.12f));
        mOval.set(cx - r * 0.62f, cy - r * 0.34f, cx + r * 0.18f, cy + r * 0.34f);
        canvas.drawRoundRect(mOval, r * 0.10f, r * 0.10f, mIconPaint);
        mPath.reset();
        mPath.moveTo(cx + r * 0.18f, cy - r * 0.10f);
        mPath.lineTo(cx + r * 0.60f, cy - r * 0.32f);
        mPath.lineTo(cx + r * 0.60f, cy + r * 0.32f);
        mPath.lineTo(cx + r * 0.18f, cy + r * 0.10f);
        mPath.close();
        mIconPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(mPath, mIconPaint);
    }

    private void drawEye(Canvas canvas, float cx, float cy, float r) {
        mIconPaint.setColor(0xFFFFFFFF);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.6f, r * 0.12f));
        mOval.set(cx - r * 0.62f, cy - r * 0.36f, cx + r * 0.62f, cy + r * 0.36f);
        canvas.drawOval(mOval, mIconPaint);
        mIconPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r * 0.17f, mIconPaint);
    }

    private void drawAirplane(Canvas canvas, float cx, float cy, float r) {
        mPath.reset();
        mPath.moveTo(cx, cy - r * 0.62f);
        mPath.lineTo(cx + r * 0.16f, cy - r * 0.04f);
        mPath.lineTo(cx + r * 0.66f, cy + r * 0.14f);
        mPath.lineTo(cx + r * 0.66f, cy + r * 0.30f);
        mPath.lineTo(cx + r * 0.16f, cy + r * 0.20f);
        mPath.lineTo(cx + r * 0.12f, cy + r * 0.52f);
        mPath.lineTo(cx + r * 0.34f, cy + r * 0.64f);
        mPath.lineTo(cx + r * 0.34f, cy + r * 0.74f);
        mPath.lineTo(cx, cy + r * 0.62f);
        mPath.lineTo(cx - r * 0.34f, cy + r * 0.74f);
        mPath.lineTo(cx - r * 0.34f, cy + r * 0.64f);
        mPath.lineTo(cx - r * 0.12f, cy + r * 0.52f);
        mPath.lineTo(cx - r * 0.16f, cy + r * 0.20f);
        mPath.lineTo(cx - r * 0.66f, cy + r * 0.30f);
        mPath.lineTo(cx - r * 0.66f, cy + r * 0.14f);
        mPath.lineTo(cx - r * 0.16f, cy - r * 0.04f);
        mPath.close();
        mIconPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(mPath, mIconPaint);
        mIconPaint.setStyle(Paint.Style.STROKE);
    }

    private void drawPowerSave(Canvas canvas, float cx, float cy, float r) {
        // battery body + leaf
        mOval.set(cx - r * 0.46f, cy - r * 0.28f, cx + r * 0.34f, cy + r * 0.28f);
        canvas.drawRoundRect(mOval, r * 0.10f, r * 0.10f, mIconPaint);
        canvas.drawLine(cx + r * 0.34f, cy - r * 0.12f, cx + r * 0.34f,
                cy + r * 0.12f, mIconPaint);
        mPath.reset();
        mPath.moveTo(cx - r * 0.10f, cy + r * 0.06f);
        mPath.lineTo(cx + r * 0.12f, cy - r * 0.22f);
        mPath.lineTo(cx + r * 0.12f, cy - r * 0.02f);
        mPath.lineTo(cx + r * 0.24f, cy - r * 0.02f);
        mPath.lineTo(cx + r * 0.04f, cy + r * 0.24f);
        mPath.lineTo(cx + r * 0.04f, cy + r * 0.06f);
        mPath.close();
        mIconPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(mPath, mIconPaint);
        mIconPaint.setStyle(Paint.Style.STROKE);
    }

    private void drawDnd(Canvas canvas, float cx, float cy, float r) {
        // crescent moon + slash (do not disturb)
        mIconPaint.setStyle(Paint.Style.FILL);
        mPath.reset();
        mPath.addCircle(cx, cy, r * 0.56f, Path.Direction.CW);
        mPath.addCircle(cx + r * 0.30f, cy - r * 0.22f, r * 0.46f, Path.Direction.CCW);
        canvas.drawPath(mPath, mIconPaint);
        mIconPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f,
                cy + r * 0.62f, mIconPaint);
    }

    private void drawRotation(Canvas canvas, float cx, float cy, float r) {
        mIconPaint.setColor(0xFFFFFFFF);
        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(Math.max(1.6f, r * 0.12f));
        if (mOn) {
            // a ring broken on both sides, with arrow heads in the gaps
            float rad = r * 0.56f;
            mOval.set(cx - rad, cy - rad, cx + rad, cy + rad);
            canvas.drawArc(mOval, 24f, 132f, false, mIconPaint);   // upper-right arc
            canvas.drawArc(mOval, 204f, 132f, false, mIconPaint);  // lower-left arc
            mIconPaint.setStyle(Paint.Style.FILL);
            drawRingArrow(canvas, cx, cy, rad, 24f, true);
            drawRingArrow(canvas, cx, cy, rad, 204f, true);
        } else {
            // rotation locked: padlock
            mIconPaint.setStyle(Paint.Style.STROKE);
            mOval.set(cx - r * 0.44f, cy - r * 0.10f, cx + r * 0.44f, cy + r * 0.56f);
            canvas.drawRoundRect(mOval, r * 0.12f, r * 0.12f, mIconPaint);
            mOval.set(cx - r * 0.26f, cy - r * 0.50f, cx + r * 0.26f, cy + r * 0.02f);
            canvas.drawArc(mOval, 180f, 180f, false, mIconPaint);
        }
        mIconPaint.setStyle(Paint.Style.STROKE);
    }

    /** Small triangle sitting in a ring gap, pointing along the tangent. */
    private void drawRingArrow(Canvas canvas, float cx, float cy, float rad,
            float degrees, boolean clockwise) {
        float a = (float) Math.toRadians(degrees);
        float ux = (float) Math.cos(a);
        float uy = (float) Math.sin(a);
        float tx = clockwise ? -uy : uy;
        float ty = clockwise ? ux : -ux;
        float tip = rad * 0.34f;
        float base = rad * 0.17f;
        float px = cx + ux * rad;
        float py = cy + uy * rad;
        mPath.reset();
        mPath.moveTo(px + tx * tip, py + ty * tip);
        mPath.lineTo(px + ux * base, py + uy * base);
        mPath.lineTo(px - ux * base, py - uy * base);
        mPath.close();
        canvas.drawPath(mPath, mIconPaint);
    }

    private void drawSun(Canvas canvas, float cx, float cy, float r) {
        mIconPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r * 0.30f, mIconPaint);
        mIconPaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.drawLine(cx + cos * r * 0.46f, cy + sin * r * 0.46f,
                    cx + cos * r * 0.70f, cy + sin * r * 0.70f, mIconPaint);
        }
    }
}
