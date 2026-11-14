package com.android.systemui.screenrecord;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import com.android.systemui.R;

/**
 * Owns the floating recording indicator window.
 *
 * Original APK logic (screenrecord.b / RecordingFloatController): a
 * {@code TYPE_PHONE} overlay with wrap_content size, gravity TOP|CENTER,
 * FLAG_KEEP_SCREEN_ON|FLAG_NOT_FOCUSABLE, wrapper padding, alpha/scale entrance
 * animation and a click handler that cancels/stops the recording. The vendor
 * also added a private {@code flagsEx} extension and a second translucent
 * chronometer window; both are dropped here (flagsEx does not exist in the
 * public API, and the second window is only a cosmetic duplicate).
 *
 * This reimplementation uses {@code TYPE_APPLICATION_OVERLAY} (2038, the public
 * API 26+ replacement for the vendor type 2003) and builds the whole content in
 * code. The window is draggable: a touch that does not exceed the system touch
 * slop is treated as a click and asks the recording service to stop.
 */
public class RecordingFloatController {

    private static final String TAG = "RecordingFloat";

    private static final int WINDOW_TYPE_APPLICATION_OVERLAY = 2038;
    private static final int WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    private static final long MESSAGE_HIDE_DELAY_MS = 2600L;
    private static final long ENTER_ANIMATION_MS = 300L;
    private static final long CLICK_MAX_DURATION_MS = 500L;

    /** Asked to stop the current recording (float view clicked). */
    public interface Callback {
        void onStopRequested();
    }

    private final Context mContext;
    private final Callback mCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mTouchSlop;

    private WindowManager mWindowManager;
    private FrameLayout mContainer;
    private RecordingFloatView mFloatView;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mAdded;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    public RecordingFloatController(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** Shows the indicator and starts the elapsed-time chronometer. Call on the main thread. */
    public void show(long chronometerBase) {
        mHandler.removeCallbacks(mHideRunnable);
        ensureView();
        if (!mAdded) {
            addWindow();
        }
        mFloatView.showActionHint(true);
        mFloatView.startTimer(chronometerBase);
    }

    /** Keeps the window visible while the recording is being exported. */
    public void showStatus(CharSequence message) {
        mHandler.removeCallbacks(mHideRunnable);
        ensureView();
        if (!mAdded) {
            addWindow();
        }
        mFloatView.showActionHint(false);
        mFloatView.setStatusText(message);
    }

    /** Shows a final status message and removes the window afterwards. */
    public void showFinalMessage(CharSequence message) {
        showStatus(message);
        mHandler.removeCallbacks(mHideRunnable);
        mHandler.postDelayed(mHideRunnable, MESSAGE_HIDE_DELAY_MS);
    }

    /** Removes the window immediately. Call on the main thread. */
    public void hide() {
        mHandler.removeCallbacks(mHideRunnable);
        if (mFloatView != null) {
            mFloatView.stopTimer();
        }
        if (mContainer == null || !mAdded || mWindowManager == null) {
            mAdded = false;
            return;
        }
        try {
            mWindowManager.removeView(mContainer);
        } catch (Throwable t) {
            Log.w(TAG, "hide: unable to remove overlay", t);
        }
        mAdded = false;
    }

    private void ensureView() {
        if (mContainer != null) {
            return;
        }
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mFloatView = new RecordingFloatView(mContext);
        mContainer = new FrameLayout(mContext);
        int wrapperPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.recording_float_wrapper_view_padding);
        mContainer.setPadding(wrapperPadding, wrapperPadding, wrapperPadding, wrapperPadding);
        mContainer.setClipToPadding(false);
        mContainer.addView(mFloatView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        // Pinned indicator: it must NOT be draggable (the user reported being able
        // to drag it away).  A plain tap still stops the recording.
        mContainer.setClickable(true);
        mContainer.setOnTouchListener(new View.OnTouchListener() {
            private float mDownY;
            private boolean mDragged;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownY = event.getRawY();
                        mDragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawY() - mDownY) > mTouchSlop) {
                            mDragged = true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (mDragged) {
                            // The pill covers the top-centre strip, so a swipe
                            // down there must still open the pull-down panel.
                            if (event.getRawY() - mDownY > 0) {
                                com.android.systemui.statusbar.phone.XTCPanelView.expandActive();
                            }
                        } else if (mCallback != null) {
                            mCallback.onStopRequested();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void addWindow() {
        if (mWindowManager == null) {
            return;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WINDOW_TYPE_APPLICATION_OVERLAY;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WINDOW_FLAGS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // Keep the pill at the top of the screen (vendor value) and rely on the
        // overlay layer + dark text for visibility; the "stop" tap is handled by
        // the pill itself, which sits above the status bar in the same layer.
        params.y = mContext.getResources()
                .getDimensionPixelSize(R.dimen.recording_float_view_margin_vertical);
        params.setTitle("RecordingFloatView");
        mLayoutParams = params;
        try {
            mWindowManager.addView(mContainer, params);
            mAdded = true;
        } catch (Throwable t) {
            Log.e(TAG, "addWindow: unable to add overlay", t);
            mAdded = false;
            // Continue without the indicator: recording must not depend on it.
            return;
        }
        mContainer.setAlpha(0f);
        mContainer.setScaleX(0.5f);
        mContainer.setScaleY(0.5f);
        mContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ENTER_ANIMATION_MS)
                .setInterpolator(new PathInterpolator(0.42f, 0f, 0.58f, 1f))
                .start();
    }

    private void moveWindow(float rawDx, float rawDy) {
        if (mLayoutParams == null || mWindowManager == null || mContainer == null || !mAdded) {
            return;
        }
        mLayoutParams.x = Math.round(rawDx);
        mLayoutParams.y = Math.round(rawDy);
        try {
            mWindowManager.updateViewLayout(mContainer, mLayoutParams);
        } catch (Throwable t) {
            Log.w(TAG, "moveWindow failed", t);
        }
    }

    /** Tap-vs-drag detection, same idea as the vendor ClickHelper used by the original. */
    private final class DragTouchListener implements View.OnTouchListener {

        private float mDownRawX;
        private float mDownRawY;
        private int mStartX;
        private int mStartY;
        private long mDownTime;
        private boolean mDragging;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownRawX = event.getRawX();
                    mDownRawY = event.getRawY();
                    mStartX = mLayoutParams != null ? mLayoutParams.x : 0;
                    mStartY = mLayoutParams != null ? mLayoutParams.y : 0;
                    mDownTime = SystemClock.uptimeMillis();
                    mDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - mDownRawX;
                    float dy = event.getRawY() - mDownRawY;
                    if (!mDragging && (Math.abs(dx) > mTouchSlop || Math.abs(dy) > mTouchSlop)) {
                        mDragging = true;
                    }
                    if (mDragging) {
                        moveWindow(mStartX + dx, mStartY + dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!mDragging
                            && SystemClock.uptimeMillis() - mDownTime < CLICK_MAX_DURATION_MS
                            && mCallback != null) {
                        mCallback.onStopRequested();
                    }
                    mDragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    return true;
                default:
                    return false;
            }
        }
    }
}
