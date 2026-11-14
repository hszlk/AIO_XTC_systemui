package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Collapsed status bar view (inflated from status_bar.xml into
 * status_bar_container).
 *
 * Original APK logic: the vendor {@code status_bar.xml} is an EMPTY
 * PhoneStatusBarView - the real top row only exists inside the pull-down panel
 * (layout_pull_down -> XtcStatusBarView).  Nothing is drawn while the panel is
 * collapsed.
 *
 * Reimplementation: keep that behaviour.  Earlier builds hand-drew HH:mm and the
 * battery percentage in this view, which made text appear at the top of the
 * screen even when the panel was closed (user-reported bug).  The time /
 * battery / charging current are only drawn by the panel header
 * (com.xtc.statusbar.XtcStatusBarView); this collapsed view stays empty.
 */
public class PhoneStatusBarView extends FrameLayout {

    private String mTimeText = "";
    private int mBatteryLevel = -1;
    private boolean mBatteryCharging;

    public PhoneStatusBarView(Context context) {
        this(context, null);
    }

    public PhoneStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(true);
    }

    public void updateTime(String text) {
        mTimeText = text == null ? "" : text;
    }

    public void updateBattery(int level, boolean charging) {
        mBatteryLevel = level;
        mBatteryCharging = charging;
    }

    /** Collapsed-bar clock text (kept for API parity; not drawn). */
    public String getTimeText() {
        return mTimeText;
    }

    /** Collapsed-bar battery level (kept for API parity; not drawn). */
    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public boolean isBatteryCharging() {
        return mBatteryCharging;
    }

    /** Layout gravity hint kept for parity with the original view API. */
    public int getBarGravity() {
        return android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL;
    }
}
