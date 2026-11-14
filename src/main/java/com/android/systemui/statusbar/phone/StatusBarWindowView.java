package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Root view of the status bar window (super_status_bar.xml).
 *
 * Original APK logic: a full-window FrameLayout that intercepts touch events,
 * decides between status-bar panel gestures (pull down / collapse) and normal
 * child touches, tracks velocity, and forwards "touch outside" to the panel.
 *
 * Reimplementation: window host + drag/fling gesture detector.  The window
 * itself is only {@code status_bar_height} tall while collapsed, so this view
 * is the touch target for a pull-down; ACTION_DOWN/MOVE are tracked in
 * {@link #onTouchEvent} (the view consumes DOWN, so onInterceptTouchEvent is
 * not part of this path).  A fling (>600 px/s) also opens/closes the panel.
 */
public class StatusBarWindowView extends FrameLayout {


    /** Callback used by the panel to expand/collapse. */
    public interface PanelGestureListener {
        boolean onPullDown();

        boolean onPullUp();
    }

    private PanelGestureListener mListener;
    private final int mTouchSlop;
    /** Fling speed (px/s) that opens/closes the panel even with a short drag. */
    private static final float FLING_VELOCITY = 600f;
    private VelocityTracker mVelocityTracker;
    private float mDownY;
    private float mDownX;
    private boolean mDragging;
    /** True while the pull-down is open (then up-swipes may start anywhere). */
    private boolean mPanelExpanded;
    /** Height of the status bar row: up-swipes there collapse the panel. */
    private final int mHeaderHeight;
    /** Right-half only pull-down: the left half of the collapsed strip is inert. */
    private boolean mRightSideGesture;

    public StatusBarWindowView(Context context) {
        this(context, null);
    }

    public StatusBarWindowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        int header = 0;
        try {
            header = getResources().getDimensionPixelSize(
                    com.android.systemui.R.dimen.status_bar_height);
        } catch (Throwable ignored) {
        }
        mHeaderHeight = header > 0 ? header : Math.round(14 * getResources().getDisplayMetrics().density);
    }

    /** Keeps the gesture logic in sync with the panel state. */
    public void setPanelExpanded(boolean expanded) {
        mPanelExpanded = expanded;
    }

    public void setPanelGestureListener(PanelGestureListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Once the panel is open, vertical drags over the content belong to the
        // QS scroll view; only the header strip keeps the collapse gesture.
        if (mPanelExpanded && ev.getY() >= mHeaderHeight) {
            return false;
        }
        // The root view consumes the gesture itself in onTouchEvent (the
        // collapsed window is only the bar strip), so keep children untouched
        // while a vertical drag is in progress.
        return mDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = ev.getY();
                mDownX = ev.getX();
                mDragging = false;
                // DC_SYSUI-style trigger: the pull-down only opens from the RIGHT
                // half of the collapsed status bar strip; the left half does
                // nothing.  While the panel is open, up-swipes may start anywhere.
                // Content drags while expanded are left to the scroll view.
                if (mPanelExpanded && ev.getY() >= mHeaderHeight) {
                    return false;
                }
                mRightSideGesture = mPanelExpanded || ev.getX() >= getWidth() * 0.5f;
                if (!mRightSideGesture) {
                    return false;
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!mRightSideGesture) {
                    return false;
                }
                float dy = ev.getY() - mDownY;
                float dx = ev.getX() - mDownX;
                if (!mDragging && Math.abs(dy) > mTouchSlop && Math.abs(dy) > Math.abs(dx) * 0.5f) {
                    mDragging = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean handled = false;
                if (mListener != null && mDragging && mRightSideGesture) {
                    float dy = ev.getY() - mDownY;
                    float vy = 0f;
                    if (mVelocityTracker != null) {
                        mVelocityTracker.computeCurrentVelocity(1000);
                        vy = mVelocityTracker.getYVelocity();
                    }
                    boolean flingDown = vy > FLING_VELOCITY;
                    boolean flingUp = vy < -FLING_VELOCITY;
                    if (dy > mTouchSlop * 2 || flingDown) {
                        handled = mListener.onPullDown();
                    } else if (dy < -mTouchSlop * 2 || flingUp) {
                        handled = mListener.onPullUp();
                    }
                }
                mDragging = false;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                return handled;
            }
            default:
                return true;
        }
    }
}
