package com.android.systemui.screenrecord;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

/**
 * Floating recording indicator content, built in code (no new layout resource).
 *
 * Original APK logic (RecordingFloatView): inflate layout_recording_float_view,
 * find iv_tips_icon (red dot) + tv_tips (Chronometer), breathing alpha animation,
 * text-swap transition when switching between "recording" and "saved" states.
 * This reimplementation keeps the same visual model - red dot + chronometer +
 * a small stop hint - but constructs the view hierarchy programmatically so the
 * rebuild does not depend on the vendor private widgets (no extra resource).
 */
public class RecordingFloatView extends LinearLayout {

    private static final long BREATHE_DURATION_MS = 1500L;
    private static final float BREATHE_MIN_ALPHA = 0.35f;

    private final ImageView mDot;
    private final Chronometer mClock;
    private final TextView mAction;

    private ValueAnimator mBreathe;

    public RecordingFloatView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipToPadding(false);

        int padStart = dimenPx(context, R.dimen.recording_float_view_padding_start);
        int padEnd = dimenPx(context, R.dimen.recording_float_view_padding_end);
        int padVertical = dimenPx(context, R.dimen.recording_float_view_padding_vertical);
        setPadding(padStart, padVertical, padEnd, padVertical);
        int elevation = dimenPx(context, R.dimen.recording_float_view_elevation);
        setElevation(elevation);
        setBackgroundResource(R.drawable.recording_float_view_background);

        int iconSize = dimenPx(context, R.dimen.recording_float_view_icon_size);
        mDot = new ImageView(context);
        mDot.setImageResource(R.drawable.screen_recording_red);
        mDot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LayoutParams dotParams = new LayoutParams(iconSize, iconSize);
        addView(mDot, dotParams);

        mClock = new Chronometer(context);
        mClock.setFormat("%s");
        // the vendor pill background is white; dark text is required
        mClock.setTextColor(0xFF000000);
        mClock.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mClock.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.recording_float_view_text_size));
        LayoutParams clockParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        clockParams.setMarginStart(dimenPx(context, R.dimen.recording_float_text_margin_start));
        addView(mClock, clockParams);

        mAction = new TextView(context);
        mAction.setText("停止");
        mAction.setTextColor(0xFF000000);
        mAction.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        mAction.setAlpha(0.7f);
        LayoutParams actionParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        actionParams.setMarginStart(dimenPx(context, R.dimen.recording_float_text_margin_start));
        addView(mAction, actionParams);
    }

    private static int dimenPx(Context context, int resId) {
        return context.getResources().getDimensionPixelSize(resId);
    }

    /** Starts the chronometer at the given elapsed-realtime base and breathes. */
    public void startTimer(long baseElapsedRealtime) {
        mClock.setVisibility(View.VISIBLE);
        mClock.setBase(baseElapsedRealtime);
        mClock.start();
        showActionHint(false);
        startBreathing();
    }

    /** Stops the chronometer (recording finished). */
    public void stopTimer() {
        mClock.stop();
        startBreathingStop();
    }

    /** Replaces the chronometer with a status message ("saving"/"saved"/error). */
    public void setStatusText(CharSequence text) {
        mClock.stop();
        mClock.setVisibility(View.VISIBLE);
        mClock.setText(text == null ? "" : text);
        startBreathingStop();
    }

    /** Hides the "停止" hint while a message is shown. */
    public void showActionHint(boolean show) {
        mAction.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void startBreathing() {
        stopBreathing();
        mBreathe = ValueAnimator.ofFloat(1f, BREATHE_MIN_ALPHA, 1f);
        mBreathe.setDuration(BREATHE_DURATION_MS);
        mBreathe.setRepeatMode(ValueAnimator.REVERSE);
        mBreathe.setRepeatCount(ValueAnimator.INFINITE);
        mBreathe.setInterpolator(new PathInterpolator(0.42f, 0f, 0.58f, 1f));
        mBreathe.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Object value = animation.getAnimatedValue();
                if (value instanceof Float) {
                    mDot.setAlpha((Float) value);
                }
            }
        });
        mBreathe.start();
    }

    private void startBreathingStop() {
        stopBreathing();
        mDot.setAlpha(1f);
    }

    private void stopBreathing() {
        if (mBreathe != null) {
            mBreathe.cancel();
            mBreathe = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreathing();
        mClock.stop();
        super.onDetachedFromWindow();
    }
}
