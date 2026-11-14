package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Vertical brightness / volume pill for the pull-down.
 *
 * Styling copied from the HQB (AOSP 9) brightness slider:
 *   - ONE solid (opaque) pill, no knob,
 *   - progress = smooth FLAT-TOP blue fill rising from the bottom
 *     (the reference slider fill is a plain rectangle, not a capsule),
 *   - the HQB glyph sits at the bottom of the pill (sun / music note);
 *     it switches to white while the blue fill covers it.
 */
public class VerticalSeekBar extends View {

    public interface OnChangeListener {
        void onProgressChanged(VerticalSeekBar bar, int progress, boolean fromUser);
    }

    /** Long-press: enlarge this single bar for fine adjustment. */
    public interface OnMagnifyListener {
        void onMagnifyRequested(VerticalSeekBar bar);
    }

    public static final int ICON_BRIGHTNESS = 0;
    public static final int ICON_VOLUME = 1;

    /** HQB accent blue used for the fill. */
    private static final int FILL_COLOR = 0xFF108CFF;

    private static final String[] SUN_PATHS = new String[]{
        "M18,14.48V18h-3.52L12,20.48L9.52,18H6v-3.52L3.52,12L6,9.52V6h3.52L12,3.52L14.48,6H18v3.52L20.48,12L18,14.48z",
        " M20,8.69 V4h-4.69L12,0.69L8.69,4H4v4.69L0.69,12L4,15.31V20h4.69L12,23.31L15.31,20H20v-4.69L23.31,12L20,8.69z M18,14.48V18h-3.52L12,20.48L9.52,18H6v-3.52L3.52,12L6,9.52V6h3.52L12,3.52L14.48,6H18v3.52L20.48,12L18,14.48z M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5S14.76,7 12,7z",
    };

    private static final String[] VOLUME_PATHS = new String[]{
        "M12,3l0.01,10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55C7.79,13 6,14.79 6,17c0,2.21 1.79,4 4.01,4S14,19.21 14,17V7h4V3H12zM10.01,19c-1.1,0 -2,-0.9 -2,-2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2C12.01,18.1 11.11,19 10.01,19z",
    };

    // ---- ringer glyphs (24x24 material paths), cycled by tapping the icon ----
    private static final String[] MUTE_PATHS = new String[]{
        "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zm2.5,0c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27L7.73,9H3v6h4l5,5v-6.73L16.27,17L4.27,3zM12,4L9.91,6.09L12,8.18V4z",
    };
    private static final String[] VIBRATE_PATHS = new String[]{
        "M8,2h8v1.5H8V2z M8,20.5h8V22H8V20.5z M17,4h1.5v16H17V4z M5.5,4H7v16H5.5V4z M8,18h8V6H8V18z",
    };
    private static final String[] RING_PATHS = new String[]{
        "M3,9v6h4l5,5V4L7,9H3z M13.5,4.06v2.07c2.89,0.86 5,3.54 5,6.86s-2.11,6 -5,6.86v2.07c4.01,-0.91 7,-4.49 7,-8.93S17.51,4.97 13.5,4.06z",
    };

    /** Ringer states cycled by tapping the volume bar's bottom icon. */
    public static final int RINGER_MUTE = 0;
    public static final int RINGER_VIBRATE = 1;
    public static final int RINGER_RING = 2;
    public static final int RINGER_RING_VIBRATE = 3;
    /** Settings.System key for "ring + vibrate"; hidden constant -> literal. */
    private static final String VIBRATE_WHEN_RINGING = "vibrate_when_ringing";
    private static Path[][] sRingerPaths;

    private AudioManager mAudio;
    private boolean mRingerIconEnabled;
    private int mRingerState = RINGER_RING;
    private float mRingerScale = 1f;
    private float mRingerAlpha = 1f;
    private android.animation.ValueAnimator mRingerAnim;
    private android.content.BroadcastReceiver mRingerReceiver;

    private static Path[] sSun;
    private static Path[] sVolume;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint ICON_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mPillRect = new RectF();
    private final RectF mClipRect = new RectF();
    private final Path mClipPath = new Path();
    private int mMax = 100;
    private int mProgress;
    private boolean mDragging;
    private int mIcon = ICON_BRIGHTNESS;
    private float mIconSize;
    private OnChangeListener mListener;
    private OnMagnifyListener mMagnifyListener;
    private Runnable mMagnifyRunnable;
    private float mDownY;
    private boolean mMagnifyFired;
    private boolean mFastScrolling;
    private long mDownTime;
    private float mLastY;

    public VerticalSeekBar(Context context) {
        this(context, null);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        FILL_PAINT.setColor(FILL_COLOR);
        ICON_PAINT.setStyle(Paint.Style.FILL);
        setClickable(true);
    }

    /** Kept for API compatibility; the caption text is intentionally not drawn. */
    public void setLabel(String label) {
    }

    public void setIcon(int icon) {
        mIcon = icon;
        invalidate();
    }

    public void setMax(int max) {
        mMax = Math.max(1, max);
        invalidate();
    }

    public int getMax() {
        return mMax;
    }

    public void setProgress(int progress) {
        int clamped = Math.max(0, Math.min(mMax, progress));
        if (clamped != mProgress) {
            mProgress = clamped;
            invalidate();
        }
    }

    public int getProgress() {
        return mProgress;
    }

    public void setOnChangeListener(OnChangeListener listener) {
        mListener = listener;
    }

    public void setOnMagnifyListener(OnMagnifyListener listener) {
        mMagnifyListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float margin = w * 0.06f;
        float radius = w * 0.5f - margin;
        mPillRect.set(w * 0.5f - radius, h * 0.03f, w * 0.5f + radius, h * 0.97f);
        // user request: the brightness / volume glyphs must be noticeably larger
        mIconSize = Math.min(w * 0.66f, h * 0.130f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mPillRect.isEmpty()) {
            return;
        }
        float radius = mPillRect.width() * 0.5f;
        // frosted-glass board (not white) - same pill style as the cards
        PillBackground.draw(canvas, mPillRect, radius);
        // smooth flat-top blue fill rising from the bottom, clipped by the pill
        float frac = (float) mProgress / mMax;
        float fillTop = mPillRect.bottom - mPillRect.height() * frac;
        if (fillTop < mPillRect.bottom - 1f) {
            int save = canvas.save();
            // inset so the blue never paints over the pill's bright rim
            float inset = Math.max(2f, mPillRect.width() * 0.075f);
            mClipRect.set(mPillRect.left + inset, mPillRect.top + inset,
                    mPillRect.right - inset, mPillRect.bottom - inset);
            mClipPath.reset();
            mClipPath.addRoundRect(mClipRect, mClipRect.width() * 0.5f,
                    mClipRect.width() * 0.5f, Path.Direction.CW);
            canvas.clipPath(mClipPath);
            FILL_PAINT.setColor(FILL_COLOR);
            canvas.drawRect(mClipRect.left, fillTop, mClipRect.right,
                    mClipRect.bottom, FILL_PAINT);
            canvas.restoreToCount(save);
        }
        // HQB glyph at the bottom (sun / music note / ringer state), centred
        float cx = getWidth() * 0.5f;
        float iconCy = mPillRect.bottom - mPillRect.width() * 0.42f;
        float box = mIconSize;
        if (mRingerIconEnabled) {
            drawRinger(canvas, cx, iconCy, box);
            return;
        }
        Path[] paths = getIconPaths();
        if (paths != null && paths.length > 0) {
            float scale = box / 24f;
            ICON_PAINT.setColor(0xFFFFFFFF);
            canvas.save();
            canvas.translate(cx - 12f * scale, iconCy - 12f * scale);
            canvas.scale(scale, scale);
            for (Path p : paths) {
                canvas.drawPath(p, ICON_PAINT);
            }
            canvas.restore();
        }
    }

    /** Draws the current ringer-state glyph with the tap animation applied. */
    private void drawRinger(Canvas canvas, float cx, float cy, float box) {
        Path[][] all = ringerPaths();
        float scale = box / 24f * mRingerScale;
        ICON_PAINT.setColor(0xFFFFFFFF);
        ICON_PAINT.setAlpha(Math.round(255f * mRingerAlpha));
        drawPaths(canvas, all[mRingerState], cx, cy, scale);
        if (mRingerState == RINGER_RING_VIBRATE) {
            // ring + vibrate: a small vibrate badge in the bottom-right corner
            float sub = box * 0.52f * mRingerScale;
            drawPaths(canvas, all[RINGER_VIBRATE],
                    cx + box * 0.42f, cy + box * 0.42f, sub / 24f);
        }
        ICON_PAINT.setAlpha(255);
    }

    private static void drawPaths(Canvas canvas, Path[] paths,
            float cx, float cy, float scale) {
        canvas.save();
        canvas.translate(cx - 12f * scale, cy - 12f * scale);
        canvas.scale(scale, scale);
        for (Path p : paths) {
            canvas.drawPath(p, ICON_PAINT);
        }
        canvas.restore();
    }

    private static Path[][] ringerPaths() {
        if (sRingerPaths == null) {
            sRingerPaths = new Path[][]{
                    parseAll(MUTE_PATHS), parseAll(VIBRATE_PATHS),
                    parseAll(RING_PATHS), parseAll(VIBRATE_PATHS)};
        }
        return sRingerPaths;
    }

    private Path[] getIconPaths() {
        if (mIcon == ICON_VOLUME) {
            if (sVolume == null) {
                sVolume = parseAll(VOLUME_PATHS);
            }
            return sVolume;
        }
        if (sSun == null) {
            sSun = parseAll(SUN_PATHS);
        }
        return sSun;
    }

    private static Path[] parseAll(String[] data) {
        Path[] out = new Path[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = QsIcons.parse(data[i]);
        }
        return out;
    }

    /** Turns the bottom icon into the tappable ringer-state cycler. */
    public void setRingerIconEnabled(boolean enabled) {
        if (mRingerIconEnabled == enabled) {
            return;
        }
        mRingerIconEnabled = enabled;
        if (enabled) {
            mAudio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            mRingerState = computeRingerState();
            registerRingerReceiver();
        } else {
            unregisterRingerReceiver();
        }
        invalidate();
    }

    /** Mute -> vibrate -> ring -> ring+vibrate, following system state. */
    private int computeRingerState() {
        try {
            int ringer = mAudio.getRingerMode();
            if (ringer == AudioManager.RINGER_MODE_SILENT) {
                return RINGER_MUTE;
            }
            if (ringer == AudioManager.RINGER_MODE_VIBRATE) {
                return RINGER_VIBRATE;
            }
            int vib = Settings.System.getInt(getContext().getContentResolver(),
                    VIBRATE_WHEN_RINGING, 1);
            return vib == 1 ? RINGER_RING_VIBRATE : RINGER_RING;
        } catch (Throwable t) {
            return RINGER_RING;
        }
    }

    private void applyRingerState(int state) {
        try {
            if (state == RINGER_MUTE) {
                mAudio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else if (state == RINGER_VIBRATE) {
                mAudio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            } else {
                mAudio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                Settings.System.putInt(getContext().getContentResolver(),
                        VIBRATE_WHEN_RINGING, state == RINGER_RING_VIBRATE ? 1 : 0);
            }
        } catch (Throwable t) {
            android.util.Log.w("VerticalSeekBar", "ringer cycle failed: " + t);
        }
        setRingerStateAnimated(state);
    }

    /** Squash-and-pop animation on the glyph (user request: dynamic switch). */
    private void setRingerStateAnimated(int state) {
        mRingerState = state;
        if (mRingerAnim != null) {
            mRingerAnim.cancel();
        }
        mRingerAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        mRingerAnim.setDuration(320L);
        mRingerAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float f = a.getAnimatedFraction();
                float wave = (float) Math.sin(f * Math.PI);
                mRingerScale = 1f - 0.28f * wave;
                mRingerAlpha = 1f - 0.55f * wave;
                invalidate();
            }
        });
        mRingerAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                mRingerScale = 1f;
                mRingerAlpha = 1f;
                invalidate();
            }
        });
        mRingerAnim.start();
    }

    private void registerRingerReceiver() {
        if (mRingerReceiver != null) {
            return;
        }
        mRingerReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context c, android.content.Intent intent) {
                int s = computeRingerState();
                if (s != mRingerState) {
                    setRingerStateAnimated(s);
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(
                AudioManager.RINGER_MODE_CHANGED_ACTION);
        try {
            getContext().getApplicationContext().registerReceiver(mRingerReceiver, filter);
        } catch (Throwable t) {
            mRingerReceiver = null;
        }
    }

    private void unregisterRingerReceiver() {
        if (mRingerReceiver != null) {
            try {
                getContext().getApplicationContext().unregisterReceiver(mRingerReceiver);
            } catch (Throwable ignored) {
            }
            mRingerReceiver = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        unregisterRingerReceiver();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // keep the QS page still while the finger drags this bar
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                mDownY = event.getY();
                mLastY = mDownY;
                mDownTime = android.os.SystemClock.uptimeMillis();
                mFastScrolling = false;
                mMagnifyFired = false;
                mDragging = false;
                if (mMagnifyListener != null) {
                    mMagnifyRunnable = new Runnable() {
                        @Override
                        public void run() {
                            mMagnifyFired = true;
                            mMagnifyListener.onMagnifyRequested(VerticalSeekBar.this);
                        }
                    };
                    postDelayed(mMagnifyRunnable, 520L);
                }
                // do NOT apply the value here: a long-press must magnify the
                // bar WITHOUT first jumping the level to the touch point
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mMagnifyRunnable != null
                        && Math.abs(event.getY() - mDownY) > 24f) {
                    removeCallbacks(mMagnifyRunnable);
                    mMagnifyRunnable = null;
                }
                long now = android.os.SystemClock.uptimeMillis();
                float dy = event.getY() - mLastY;
                long dt = Math.max(1L, now - mDownTime);
                float speed = Math.abs(event.getY() - mDownY) / dt * 1000f;
                if (!mFastScrolling && speed > 2200f) {
                    // a quick flick on the slider must scroll the page, not change the level
                    mFastScrolling = true;
                }
                if (mFastScrolling) {
                    scrollParentBy(-dy);
                    mLastY = event.getY();
                    return true;
                }
                mLastY = event.getY();
                if (mMagnifyFired) {
                    // the magnified bar owns the drag now; never re-apply here
                    return true;
                }
                if (!mDragging && Math.abs(event.getY() - mDownY) <= 24f) {
                    // still holding still (long-press in progress): keep frozen
                    return true;
                }
                mDragging = true;
                updateFromY(event.getY(), true);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mFastScrolling = false;
                if (mMagnifyRunnable != null) {
                    removeCallbacks(mMagnifyRunnable);
                    mMagnifyRunnable = null;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (!mMagnifyFired && mDragging) {
                    updateFromY(event.getY(), true);
                } else if (!mMagnifyFired && !mDragging) {
                    // clean tap: on the bottom icon cycle the ringer state,
                    // anywhere else keep the "tap sets the level" behaviour
                    if (mRingerIconEnabled && inRingerIconArea(event)) {
                        applyRingerState((mRingerState + 1) % 4);
                    } else {
                        updateFromY(event.getY(), true);
                    }
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /** True when a tap landed on the bottom ringer icon (with some slack). */
    private boolean inRingerIconArea(MotionEvent event) {
        float cx = getWidth() * 0.5f;
        float cy = mPillRect.bottom - mPillRect.width() * 0.42f;
        float hit = mIconSize * 0.8f;
        return Math.abs(event.getX() - cx) <= hit
                && Math.abs(event.getY() - cy) <= hit;
    }

    /** Scrolls the enclosing QS page (used when the user flicks across the bar). */
    private void scrollParentBy(float dy) {        android.view.ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof com.android.systemui.qs.QsOutCanScrollView) {
                ((com.android.systemui.qs.QsOutCanScrollView) parent)
                        .scrollBy(0, Math.round(dy));
                return;
            }
            parent = parent.getParent();
        }
    }

    private void updateFromY(float y, boolean fromUser) {
        if (mPillRect.isEmpty()) {
            return;
        }
        float frac = (mPillRect.bottom - y) / mPillRect.height();
        frac = Math.max(0f, Math.min(1f, frac));
        int progress = Math.round(frac * mMax);
        if (progress != mProgress) {
            mProgress = progress;
            invalidate();
            if (mListener != null) {
                mListener.onProgressChanged(this, progress, fromUser);
            }
        }
    }
}
