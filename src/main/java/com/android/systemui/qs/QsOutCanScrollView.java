package com.android.systemui.qs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * Scroll container of the quick-settings panel inside the pull-down
 * (layout_pull_down.xml: {@code ocsv_root}).
 *
 * The QS page is taller than the screen, so it scrolls; when the page is
 * scrolled all the way DOWN and the finger keeps pulling up, the pull-down
 * itself collapses (user's requested back gesture).
 */
public class QsOutCanScrollView extends ScrollView {

    private boolean mScrollEnabled = true;
    private float mDownY;
    private boolean mPullCollapse;
    private boolean mFromBottomEdge;
    private float mLastY;
    private int mTouchSlop;

    public QsOutCanScrollView(Context context) {
        super(context);
        init(context);
    }

    public QsOutCanScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QsOutCanScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mTouchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setScrollEnabled(boolean enabled) {
        mScrollEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mScrollEnabled && super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mScrollEnabled) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = ev.getY();
                mLastY = mDownY;
                mPullCollapse = false;
                // only a gesture that STARTS at the very bottom edge can exit
                mFromBottomEdge = mDownY > getHeight()
                        - Math.round(28 * getResources().getDisplayMetrics().density);
                break;
            case MotionEvent.ACTION_MOVE: {
                if (mFromBottomEdge
                        && mDownY - ev.getY() > Math.round(40
                        * getResources().getDisplayMetrics().density)) {
                    if (com.android.systemui.statusbar.phone.XTCPanelView.isCollapseLocked()) {
                        // edit mode: the page follows the finger instead of exiting
                        float delta = ev.getY() - mLastY;
                        scrollBy(0, -Math.round(delta));
                    } else {
                        mPullCollapse = true;
                    }
                } else if (ev.getY() > mDownY) {
                    mPullCollapse = false;
                }
                mLastY = ev.getY();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mFromBottomEdge = false;
                if (mPullCollapse) {
                    mPullCollapse = false;
                    com.android.systemui.statusbar.phone.XTCPanelView.collapseActive();
                    return true;
                }
                mPullCollapse = false;
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    private boolean isAtBottom() {
        if (getChildCount() == 0) {
            return false;
        }
        int content = getChildAt(0).getBottom();
        return getScrollY() + getHeight() >= content - 2;
    }
}
