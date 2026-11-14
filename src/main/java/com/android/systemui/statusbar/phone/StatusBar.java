package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.provider.PanelStateHolder;

import java.util.Date;

/**
 * Watch status bar component (configured by @string/config_statusBarComponent).
 *
 * Original APK logic: builds StatusBarWindowView from super_status_bar.xml, gets
 * the window layout from the vendor StatusBarServiceEx (falls back to
 * TYPE_STATUS_BAR + translucent + full-width/bar-height params), registers
 * IStatusBar with the system StatusBarManagerService, then drives phone/panel
 * state (clock, icons, panel expansion) through CommandQueue callbacks.
 *
 * W2 reimplementation (this stage, public API only):
 *   - window  : a STATUS_BAR type overlay sized to the bar height, expanded to
 *               the full screen while the pull-down is open (same geometry the
 *               original StatusBarWindowManager uses);
 *   - content : super_status_bar inflate, status_bar into status_bar_container,
 *               XTCPanelView pull-down host;
 *   - live data: ACTION_TIME_TICK/TIME_CHANGED battery/idle clock + battery,
 *               painted by PhoneStatusBarView and the panel header.
 * The CommandQueue/IStatusBar registration and the tile/notification content are
 * reimplemented in later waves on top of this window.
 */
public class StatusBar extends SystemUI
        implements StatusBarWindowView.PanelGestureListener, XTCPanelView.PanelStateListener {

    private static final String TAG = "StatusBar";

    private WindowManager mWindowManager;
    private StatusBarWindowView mRoot;
    private PhoneStatusBarView mBarView;
    private XTCPanelView mPanelView;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private int mBarHeight;

    private String mTimeText = "";
    private int mBatteryLevel = -1;
    private boolean mCharging;

    private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                updateClock();
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateBattery(intent);
            }
        }
    };

    @Override
    protected void start() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mBarHeight = mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_height);
        inflateStatusBarWindow();
        addStatusBarWindow();
        registerStatusReceiver();
        updateClock();
        // bind the external keyguard service (com.xtc.i3launcher) lazily, as the
        // original StatusBar did after registering the status bar service
        try {
            KeyguardController.get(mContext).ensureConnected();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ window

    private void inflateStatusBarWindow() {
        mRoot = (StatusBarWindowView) View.inflate(mContext, R.layout.super_status_bar, null);
        InfoToast.bind(mRoot);
        ViewGroup container = mRoot.findViewById(R.id.status_bar_container);
        mBarView = (PhoneStatusBarView) View.inflate(mContext, R.layout.status_bar, null);
        container.addView(mBarView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mBarHeight));
        mPanelView = mRoot.findViewById(R.id.notification_panel);
        mPanelView.setBarHeight(mBarHeight);
        mPanelView.setPanelStateListener(this);
        mPanelView.setupContent();
        mRoot.setPanelGestureListener(this);
        // launcher integration: collapse request from StatusBarProxyProvider
        PanelStateHolder.setCollapseCallback(new Runnable() {
            @Override
            public void run() {
                if (mPanelView != null) {
                    mPanelView.collapse();
                }
            }
        });
    }

    private void addStatusBarWindow() {
        mWindowLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                mBarHeight,
                // TYPE_STATUS_BAR (2000) is policy-managed by PhoneWindowManager:
                // without registering an IStatusBar with StatusBarManagerService
                // the window stays policy-hidden (mPolicyVisibility=false), so it
                // is never composed and receives no touch events.
                // TYPE_SYSTEM_OVERLAY (2006) gets FLAG_NOT_TOUCHABLE forced by the
                // window manager, so it cannot receive gestures either.
                // TYPE_APPLICATION_OVERLAY (2038) is the overlay type meant for
                // touchable system overlays: it sits above the launcher windows
                // and takes input for a platform-signed uid 1000.
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.gravity = Gravity.TOP;
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mWindowLayoutParams.setTitle("StatusBar");
        mWindowLayoutParams.packageName = mContext.getPackageName();
        mWindowManager.addView(mRoot, mWindowLayoutParams);
    }

    /** Resizes the status bar window: bar-height while collapsed, full screen while opened. */
    private void updateWindowHeight(boolean panelExpanded) {
        if (mWindowLayoutParams == null) {
            return;
        }
        int height = panelExpanded ? WindowManager.LayoutParams.MATCH_PARENT : mBarHeight;
        if (mWindowLayoutParams.height == height) {
            return;
        }
        mWindowLayoutParams.height = height;
        mWindowManager.updateViewLayout(mRoot, mWindowLayoutParams);
    }

    // ------------------------------------------------------------- panel state

    @Override
    public void onPanelExpandedChanged(boolean expanded) {
        updateWindowHeight(expanded);
        if (mRoot != null) {
            mRoot.setPanelExpanded(expanded);
        }
        PanelStateHolder.setPanelState(expanded, expanded);
        if (expanded) {
            updateHeaderConnectivity();
        }
        mRoot.setSystemUiVisibility(expanded
                ? (mRoot.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                : (mRoot.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
    }

    /** Refreshes the Wi-Fi / airplane glyphs of the panel header row. */
    private void updateHeaderConnectivity() {
        if (mPanelView == null) {
            return;
        }
        boolean wifi = false;
        boolean airplane = false;
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifi = wm != null && wm.isWifiEnabled();
            airplane = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                    android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        } catch (Throwable ignored) {
        }
        mPanelView.updateHeaderConnectivity(wifi && !airplane, airplane);
    }

    @Override
    public boolean onPullDown() {
        if (mPanelView != null && !mPanelView.isExpanded()) {
            mPanelView.expand();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPullUp() {
        if (mPanelView != null && mPanelView.isExpanded()) {
            mPanelView.collapse();
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------- live data

    private void registerStatusReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mStatusReceiver, filter);
    }

    private void updateClock() {
        mTimeText = DateFormat.format("HH:mm", new Date()).toString();
        pushStatusToViews();
    }

    private void updateBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            mBatteryLevel = (int) (level * 100f / scale);
        }
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        mCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        pushStatusToViews();
    }

    private void pushStatusToViews() {
        if (mBarView != null) {
            mBarView.updateTime(mTimeText);
            mBarView.updateBattery(mBatteryLevel, mCharging);
        }
        if (mPanelView != null) {
            mPanelView.updateHeaderStatus(mTimeText, mBatteryLevel, mCharging);
        }
    }
}
