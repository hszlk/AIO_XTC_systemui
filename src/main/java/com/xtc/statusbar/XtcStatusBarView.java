package com.xtc.statusbar;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Calendar;
import java.util.Locale;

/**
 * XTC watch status bar row (top header line of the pull-down).
 *
 * Layout parity: this reproduces {@code res/layout-xhdpi/statusbar_bar.xml} of
 * the sibling SystemUI build the user pointed at
 * (D:\DEBUG\apknx\DC_SYSUI) - left group: signal level, airplane mode, mobile
 * data traffic (network type + up/down arrows + roaming), Wi-Fi, hotspot, IM
 * state, download, HD voice; centre: time (the view is named
 * tv_telecom_operators in the vendor layout but is fed HH:mm by
 * XtcStatusBarView); right group: GPS, Bluetooth, headset, alarm, battery icon
 * + percentage.
 *
 * All drawables used here already exist in the 1.8.00 resource table
 * (ic_signal_*, ic_wifi_*, ic_data_traffic_*, ic_airplane_mode_on_status_bar,
 * ic_gps, ic_bluetooth(_mate), ic_alarm_clock, ic_battery_N and charging icons) and the
 * dimens/colors are the original resources, so the row looks like the source
 * build without adding any resource to our APK (Mode A keeps the table fixed).
 *
 * The vendor implementation is a RelativeLayout that inflates statusbar_bar and
 * feeds it from vendor controllers (SignalController / WifiController /
 * BatteryHelper / baseservice).  Here the row builds its children in code and
 * updates them from the public framework APIs + broadcasts, without the vendor
 * SDK (no baseservice, no reporting).
 */
public class XtcStatusBarView extends RelativeLayout {

    private static final int GAP = 2; // dimen_status_bar_gap_2 fallback (dp)

    private ImageView mSignal;
    private ImageView mAirplane;
    private LinearLayout mDataTraffic;
    private ImageView mDataNetworkType;
    private ImageView mDataReceived;
    private ImageView mDataTransmitting;
    private ImageView mRoaming;
    private ImageView mWifi;
    private ImageView mHotspot;
    private ImageView mImState;
    private ImageView mDownload;
    private ImageView mHdVoice;
    private TextView mTime;
    private ImageView mGps;
    private ImageView mBluetooth;
    private ImageView mHeadset;
    private ImageView mAlarm;
    private ImageView mBattery;
    private TextView mBatteryNum;
    /** Charging current (mA) shown only in the long-press battery card. */
    private TextView mPopupPercent;
    private TextView mPopupState;
    private TextView mPopupCurrent;
    private View mBatteryPopup;
    private android.view.WindowManager mPopupWm;
    private HandlerThread mCurrentThread;
    private Handler mCurrentHandler;
    private volatile boolean mCurrentPolling;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TelephonyManager mTelephony;
    private PhoneStateListener mPhoneStateListener;
    private boolean mAttached;
    private int mBatteryLevel = -1;
    private boolean mCharging;

    /** Uniform height (dp) for every glyph in the status row. */
    private static final int ICON_HEIGHT_DP = 11;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateBattery(intent);
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                updateAirplaneMode();
            } else if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                updateTime();
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                updateHeadset();
            } else if (WifiManager_ACTION_WIFI_STATE_CHANGED.equals(action)
                    || WifiManager_ACTION_WIFI_RSSI_CHANGED.equals(action)) {
                updateWifi();
            } else if (ACTION_WIFI_AP_STATE_CHANGED.equals(action)) {
                updateHotspot();
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                updateImState();
            }
        }
    };

    // public WifiManager constants kept as literals where the constant is hidden
    private static final String WifiManager_ACTION_WIFI_STATE_CHANGED = "android.net.wifi.WIFI_STATE_CHANGED";
    private static final String WifiManager_ACTION_WIFI_RSSI_CHANGED = "android.net.wifi.RSSI_CHANGED";
    /** Hidden broadcast fired when the SoftAP state changes. */
    private static final String ACTION_WIFI_AP_STATE_CHANGED =
            "android.net.wifi.WIFI_AP_STATE_CHANGED";

    public XtcStatusBarView(Context context) {
        this(context, null);
    }

    public XtcStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER_VERTICAL);
        buildChildren(context);
    }

    // ------------------------------------------------------------------ layout

    private void buildChildren(Context ctx) {
        int gap = dp(GAP);
        int corner = dimen(R.dimen.rounded_corner_radius);

        // ---- left group -------------------------------------------------
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams leftLp = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        left.setId(android.view.View.generateViewId());
        leftLp.addRule(ALIGN_PARENT_START);
        leftLp.addRule(CENTER_VERTICAL);
        if (corner > 0) {
            leftLp.setMarginStart(corner);
        }
        addView(left, leftLp);

        mSignal = addIcon(left, R.drawable.ic_signal_zero, 1, gap);
        mAirplane = addIcon(left, R.drawable.ic_airplane_mode_on_status_bar, 1, gap);
        mAirplane.setVisibility(GONE);

        // mobile data traffic group (network type + up/down arrows + roaming)
        mDataTraffic = new LinearLayout(ctx);
        mDataTraffic.setOrientation(LinearLayout.HORIZONTAL);
        mDataTraffic.setGravity(Gravity.CENTER_VERTICAL);
        mDataTraffic.setVisibility(GONE);
        LinearLayout.LayoutParams dtLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        left.addView(mDataTraffic, dtLp);
        mDataNetworkType = addIcon(mDataTraffic, R.drawable.ic_data_traffic_network_4g, 1, gap);
        FrameLayout upDown = new FrameLayout(ctx);
        mDataTransmitting = addIcon(upDown, R.drawable.ic_data_traffic_transmitted_normal, 0, 0);
        mDataReceived = addIcon(upDown, R.drawable.ic_data_traffic_received_normal, 0, 0);
        mDataTraffic.addView(upDown, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mRoaming = addIcon(mDataTraffic, R.drawable.ic_international_roaming, 1, gap);
        mRoaming.setVisibility(GONE);

        mWifi = addIcon(left, R.drawable.ic_wifi_three, 1, gap);
        mWifi.setVisibility(GONE);
        mHotspot = addIcon(left, R.drawable.icon_hot_spot, 1, gap);
        mHotspot.setVisibility(GONE);
        mImState = addIcon(left, R.drawable.im_offline, 1, gap);
        mImState.setVisibility(GONE);
        mDownload = addIcon(left, R.drawable.ic_download, 1, gap);
        mDownload.setVisibility(GONE);
        mHdVoice = addIcon(left, R.drawable.ic_hd_voice, 0, 0);
        mHdVoice.setVisibility(GONE);

        // ---- centre: time ----------------------------------------------
        mTime = new TextView(ctx);
        mTime.setTextColor(Color.WHITE);
        mTime.setGravity(Gravity.CENTER);
        mTime.setSingleLine(true);
        mTime.setEllipsize(TextUtils.TruncateAt.END);
        mTime.setIncludeFontPadding(false);
        mTime.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.dimen_status_bar_time_size));
        RelativeLayout.LayoutParams timeLp = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeLp.addRule(CENTER_IN_PARENT);
        timeLp.leftMargin = dp(5);
        timeLp.rightMargin = dp(5);
        addView(mTime, timeLp);

        // ---- right group -----------------------------------------------
        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams rightLp = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        right.setId(android.view.View.generateViewId());
        rightLp.addRule(ALIGN_PARENT_END);
        rightLp.addRule(CENTER_VERTICAL);
        if (corner > 0) {
            rightLp.setMarginEnd(corner);
        }
        addView(right, rightLp);

        mGps = addIcon(right, R.drawable.ic_gps, 1, gap);
        mGps.setVisibility(GONE);
        mBluetooth = addIcon(right, R.drawable.ic_bluetooth, 1, gap);
        mBluetooth.setVisibility(GONE);
        mHeadset = addIcon(right, R.drawable.ic_battery_status_bar_5, 1, gap);
        mHeadset.setVisibility(GONE);
        mAlarm = addIcon(right, R.drawable.ic_alarm_clock, 1, gap);
        mAlarm.setVisibility(GONE);

        // battery column: battery icon on top, charging current underneath -
        // a separate row so the mA text never overlaps the other status icons
        LinearLayout batteryCol = new LinearLayout(ctx);
        batteryCol.setOrientation(LinearLayout.VERTICAL);
        batteryCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        colLp.setMarginEnd(dp(1));
        right.addView(batteryCol, colLp);

        FrameLayout batteryBox = new FrameLayout(ctx);
        batteryCol.addView(batteryBox, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mBattery = new ImageView(ctx);
        mBattery.setImageResource(R.drawable.ic_battery_7);
        // battery keeps its ORIGINAL intrinsic size (user request: do not unify
        // it with the other glyphs - it carries the percentage digits inside)
        mBattery.setAdjustViewBounds(true);
        mBattery.setScaleType(ImageView.ScaleType.FIT_CENTER);
        batteryBox.addView(mBattery, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mBatteryNum = new TextView(ctx);
        mBatteryNum.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.sp_8));
        mBatteryNum.setTypeface(Typeface.DEFAULT_BOLD);
        mBatteryNum.setTextColor(getResources().getColor(R.color.color_ff333333));
        FrameLayout.LayoutParams numLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        numLp.bottomMargin = dp(3);
        numLp.rightMargin = dp(1);
        batteryBox.addView(mBatteryNum, numLp);

        // charging current (mA) is NOT drawn in this 14dp row any more - it is
        // shown in the enlarged card that a long-press on the battery opens
        // (user decision 2026-11-14: the row is too short for a second line).
        batteryCol.setClickable(true);
        batteryCol.setLongClickable(true);
        batteryCol.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showBatteryPopup();
                return true;
            }
        });
    }

    private ImageView addIcon(LinearLayout parent, int resId, int endDp, int gapDp) {
        return addIcon(parent, resId, endDp, gapDp, ICON_HEIGHT_DP);
    }

    /**
     * Adds a status icon with a UNIFORM height so the row looks consistent
     * (the drawables ship at 7dp..14dp; without this the row mixes tiny and
     * large glyphs). Width follows the drawable aspect ratio.
     */
    private ImageView addIcon(LinearLayout parent, int resId, int endDp, int gapDp,
            int heightDp) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(resId);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(heightDp));
        lp.setMarginEnd(endDp > 0 ? dp(gapDp) : 0);
        parent.addView(iv, lp);
        return iv;
    }

    private ImageView addIcon(FrameLayout parent, int resId, int endDp, int gapDp) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(resId);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        parent.addView(iv, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(ICON_HEIGHT_DP)));
        return iv;
    }

    // ---------------------------------------------------------------- updates

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached) {
            return;
        }
        mAttached = true;
        // the vendor layout only sets layout_marginStart(5dp) on this header; mirror it
        // on the end side so the row is symmetric with the content below (no "misaligned" look)
        try {
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int startMargin = mlp.getMarginStart();
                if (startMargin > 0 && mlp.getMarginEnd() == 0) {
                    mlp.setMarginEnd(startMargin);
                    setLayoutParams(mlp);
                }
            }
        } catch (Throwable ignored) {
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(WifiManager_ACTION_WIFI_STATE_CHANGED);
        filter.addAction(WifiManager_ACTION_WIFI_RSSI_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ACTION_WIFI_AP_STATE_CHANGED);
        getContext().registerReceiver(mReceiver, filter);

        updateTime();
        updateAirplaneMode();
        updateWifi();
        updateHotspot();
        updateSignal();
        updateBluetooth();
        updateAlarm();
        updateGps();
        updateHeadset();
        updateImState();
        schedulePeriodicRefresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttached = false;
        mHandler.removeCallbacksAndMessages(null);
        try {
            dismissBatteryPopup();
        } catch (Throwable ignored) {
        }
        stopCurrentPolling();
        if (mCurrentThread != null) {
            mCurrentThread.quitSafely();
            mCurrentThread = null;
            mCurrentHandler = null;
        }
        try {
            getContext().unregisterReceiver(mReceiver);
        } catch (Throwable ignored) {
        }
        stopSignalListener();
        super.onDetachedFromWindow();
    }

    private void schedulePeriodicRefresh() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mAttached) {
                    return;
                }
                updateWifi();
                updateBluetooth();
                updateAlarm();
                updateGps();
                updateTime();
                updateImState();
                schedulePeriodicRefresh();
            }
        }, 30_000L);
    }

    /** Compatibility entry point used by StatusBar's push. */
    public void updateStatus(String time, int batteryLevel, boolean charging) {
        if (time != null) {
            mTime.setText(time);
        }
        if (batteryLevel >= 0) {
            applyBattery(batteryLevel, charging);
        }
    }

    public void setSignalLevel(int level) {
        applySignal(level);
    }

    public void setWifiEnabled(boolean enabled) {
        updateWifi();
    }

    public void setAirplaneMode(boolean on) {
        updateAirplaneMode();
    }

    private void updateTime() {
        if (mTime == null) {
            return;
        }
        mTime.setText(DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "hh:mm",
                Calendar.getInstance()));
    }

    private void updateBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return;
        }
        int percent = (int) (level * 100f / scale);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
                || intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        applyBattery(percent, charging);
    }

    private void applyBattery(int percent, boolean charging) {
        mBatteryLevel = percent;
        mCharging = charging;
        if (mBattery != null) {
            mBattery.setImageResource(batteryIcon(percent, charging));
        }
        if (mBatteryNum != null) {
            mBatteryNum.setText(String.valueOf(percent));
            mBatteryNum.setTextColor(charging
                    ? getResources().getColor(R.color.expanded_clock_text_color)
                    : getResources().getColor(R.color.color_ff333333));
        }
        // the enlarged card (if open) follows the live value
        if (mBatteryPopup != null) {
            updateBatteryPopup();
        }
    }

    // -------------------------------------------------- battery long-press card

    /**
     * Long-press on the battery opens an enlarged card with the level, the live
     * charging current and the charging state. It replaces the old inline mA
     * text, which could not fit the 14dp status row.
     */
    private void showBatteryPopup() {
        if (mBatteryPopup != null) {
            dismissBatteryPopup();
            return;
        }
        try {
            Context ctx = getContext();
            if (mPopupWm == null) {
                mPopupWm = (android.view.WindowManager) ctx
                        .getSystemService(Context.WINDOW_SERVICE);
            }
            if (mPopupWm == null) {
                return;
            }

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int padH = dp(16);
            int padV = dp(11);
            card.setPadding(padH, padV, padH, padV);
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xF2202020);
            bg.setCornerRadius(dp(14));
            bg.setStroke(Math.max(1, dp(1)), 0x66FFFFFF);
            card.setBackground(bg);
            card.setClickable(true);
            card.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissBatteryPopup();
                }
            });

            mPopupPercent = new TextView(ctx);
            mPopupPercent.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(24));
            mPopupPercent.setTypeface(Typeface.DEFAULT_BOLD);
            mPopupPercent.setTextColor(Color.WHITE);
            mPopupPercent.setIncludeFontPadding(false);
            mPopupPercent.setGravity(Gravity.CENTER);
            card.addView(mPopupPercent);

            mPopupState = new TextView(ctx);
            mPopupState.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(9));
            mPopupState.setTextColor(0xCCFFFFFF);
            mPopupState.setIncludeFontPadding(false);
            mPopupState.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            stateLp.topMargin = dp(3);
            card.addView(mPopupState, stateLp);

            mPopupCurrent = new TextView(ctx);
            mPopupCurrent.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13));
            mPopupCurrent.setTypeface(Typeface.DEFAULT_BOLD);
            mPopupCurrent.setTextColor(0xFF6FD3FF);
            mPopupCurrent.setIncludeFontPadding(false);
            mPopupCurrent.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            curLp.topMargin = dp(4);
            card.addView(mPopupCurrent, curLp);

            android.view.WindowManager.LayoutParams lp =
                    new android.view.WindowManager.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = 0f;
            lp.setTitle("XTCBatteryCard");
            mPopupWm.addView(card, lp);

            mBatteryPopup = card;
            updateBatteryPopup();
            startPopupTicker();
        } catch (Throwable t) {
            android.util.Log.w("XtcStatusBarView", "battery popup failed: " + t);
            mBatteryPopup = null;
        }
    }

    private void dismissBatteryPopup() {
        stopCurrentPolling();
        if (mBatteryPopup != null && mPopupWm != null) {
            try {
                mPopupWm.removeView(mBatteryPopup);
            } catch (Throwable ignored) {
            }
        }
        mBatteryPopup = null;
        mPopupPercent = null;
        mPopupState = null;
        mPopupCurrent = null;
    }

    /** Refreshes the card content from the last battery broadcast + live current. */
    private void updateBatteryPopup() {
        if (mPopupPercent != null && mBatteryLevel >= 0) {
            mPopupPercent.setText(mBatteryLevel + "%");
        }
        if (mPopupState != null) {
            mPopupState.setText(mCharging ? "充电中" : "未充电");
        }
        if (mPopupCurrent != null) {
            String text = readChargeCurrent();
            mPopupCurrent.setText(text == null ? "--" : text);
        }
    }

    /** While the card is visible the current is re-read once per second. */
    private void startPopupTicker() {
        stopCurrentPolling();
        if (mCurrentThread == null) {
            mCurrentThread = new HandlerThread("battery-current");
            mCurrentThread.start();
            mCurrentHandler = new Handler(mCurrentThread.getLooper());
        }
        mCurrentPolling = true;
        mCurrentHandler.removeCallbacksAndMessages(null);
        mCurrentHandler.post(mCurrentPollTask);
    }

    // -------------------------------------------------- charging current (mA)

    /**
     * Polls the current once per second while the battery card is open.
     *
     * The node is read on a dedicated background thread (never on the UI thread);
     * the thread is created on first use and torn down with the view. Values
     * follow the vendor convention: raw current_now is uA when |value| > 1000
     * (divided by 1000 into mA), otherwise already mA; the absolute value wins.
     */
    private void startCurrentPolling() {
        if (mCurrentThread == null) {
            mCurrentThread = new HandlerThread("battery-current");
            mCurrentThread.start();
            mCurrentHandler = new Handler(mCurrentThread.getLooper());
        }
        if (mCurrentPolling) {
            return;
        }
        mCurrentPolling = true;
        mCurrentHandler.removeCallbacksAndMessages(null);
        mCurrentHandler.post(mCurrentPollTask);
    }

    private void stopCurrentPolling() {
        mCurrentPolling = false;
        if (mCurrentHandler != null) {
            mCurrentHandler.removeCallbacksAndMessages(null);
        }
    }

    private final Runnable mCurrentPollTask = new Runnable() {
        @Override
        public void run() {
            if (!mCurrentPolling) {
                return;
            }
            final String text = readChargeCurrent();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mCurrentPolling) {
                        return;
                    }
                    if (mPopupCurrent != null) {
                        mPopupCurrent.setText(text == null ? "--" : text);
                    }
                }
            });
            if (mCurrentPolling) {
                mCurrentHandler.postDelayed(this, 1000L);
            }
        }
    };

    /** Reads current_now (fallback current_avg / BatteryManager) and formats "xxx.x mA". */
    private String readChargeCurrent() {
        String raw = readSysfsFile("/sys/class/power_supply/battery/current_now");
        if (raw.isEmpty()) {
            raw = readSysfsFile("/sys/class/power_supply/battery/current_avg");
        }
        if (!raw.isEmpty()) {
            try {
                long value = Long.parseLong(raw.trim());
                double ma = Math.abs(value) > 1000 ? value / 1000.0 : (double) value;
                return String.format(Locale.US, "%.1f mA", Math.abs(ma));
            } catch (NumberFormatException ignored) {
            }
        }
        // sysfs may be unreadable from the SystemUI context; ask BatteryManager
        // (returns uA) before giving up
        try {
            BatteryManager bm = (BatteryManager) getContext()
                    .getSystemService(android.content.Context.BATTERY_SERVICE);
            if (bm != null) {
                long ua = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (ua != Long.MIN_VALUE && ua != 0) {
                    return String.format(Locale.US, "%.1f mA", Math.abs(ua) / 1000.0);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String readSysfsFile(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Throwable t) {
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Vendor mapping: 100%->11, 90..99->10, 80..89->9, ... 0..9->1. */
    private static int batteryIconIndex(int percent) {
        if (percent >= 100) {
            return 11;
        }
        int idx = percent / 10 + 1;
        return Math.max(1, Math.min(11, idx));
    }

    private int batteryIcon(int percent, boolean charging) {
        int idx = batteryIconIndex(percent);
        switch (idx) {
            case 1: return charging ? R.drawable.ic_battery_charging_1 : R.drawable.ic_battery_1;
            case 2: return charging ? R.drawable.ic_battery_charging_2 : R.drawable.ic_battery_2;
            case 3: return charging ? R.drawable.ic_battery_charging_3 : R.drawable.ic_battery_3;
            case 4: return charging ? R.drawable.ic_battery_charging_4 : R.drawable.ic_battery_4;
            case 5: return charging ? R.drawable.ic_battery_charging_5 : R.drawable.ic_battery_5;
            case 6: return charging ? R.drawable.ic_battery_charging_6 : R.drawable.ic_battery_6;
            case 7: return charging ? R.drawable.ic_battery_charging_7 : R.drawable.ic_battery_7;
            case 8: return charging ? R.drawable.ic_battery_charging_8 : R.drawable.ic_battery_8;
            case 9: return charging ? R.drawable.ic_battery_charging_9 : R.drawable.ic_battery_9;
            case 10: return charging ? R.drawable.ic_battery_charging_10 : R.drawable.ic_battery_10;
            default: return charging ? R.drawable.ic_battery_charging_11 : R.drawable.ic_battery_11;
        }
    }

    private void updateAirplaneMode() {
        boolean on = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        mAirplane.setVisibility(on ? VISIBLE : GONE);
        if (mSignal != null) {
            mSignal.setVisibility(on ? GONE : VISIBLE);
        }
        if (!on) {
            updateSignal();
        }
    }

    private void updateSignal() {
        if (mSignal == null) {
            return;
        }
        if (mTelephony == null) {
            mTelephony = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        }
        if (mTelephony == null) {
            applySignal(0);
            return;
        }
        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    super.onSignalStrengthsChanged(signalStrength);
                    int level;
                    try {
                        level = signalStrength.getLevel();
                    } catch (Throwable t) {
                        level = 0;
                    }
                    applySignal(level);
                }
            };
            try {
                mTelephony.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            } catch (Throwable ignored) {
            }
        }
    }

    private void stopSignalListener() {
        if (mTelephony != null && mPhoneStateListener != null) {
            try {
                mTelephony.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            } catch (Throwable ignored) {
            }
            mPhoneStateListener = null;
        }
    }

    private void applySignal(int level) {
        if (mSignal == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(4, level));
        int icon;
        switch (clamped) {
            case 0: icon = R.drawable.ic_signal_zero; break;
            case 1: icon = R.drawable.ic_signal_one; break;
            case 2: icon = R.drawable.ic_signal_two; break;
            case 3: icon = R.drawable.ic_signal_three; break;
            default: icon = R.drawable.ic_signal_four; break;
        }
        mSignal.setImageResource(icon);
    }

    /** Hotspot (SoftAP) indicator: shown while the watch shares its connection. */
    @SuppressWarnings("PrivateApi")
    private void updateHotspot() {
        if (mHotspot == null) {
            return;
        }
        boolean on = false;
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getContext().getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                java.lang.reflect.Method m =
                        android.net.wifi.WifiManager.class.getDeclaredMethod("isWifiApEnabled");
                m.setAccessible(true);
                Object r = m.invoke(wm);
                on = (r instanceof Boolean) && ((Boolean) r);
            }
        } catch (Throwable ignored) {
        }
        mHotspot.setVisibility(on ? VISIBLE : GONE);
    }

    private void updateWifi() {
        if (mWifi == null) {
            return;
        }
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getContext().getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                mWifi.setVisibility(GONE);
                return;
            }
            mWifi.setVisibility(VISIBLE);
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            int rssi = info == null ? Integer.MIN_VALUE : info.getRssi();
            if (rssi > -50) {
                mWifi.setImageResource(R.drawable.ic_wifi_four);
            } else if (rssi > -60) {
                mWifi.setImageResource(R.drawable.ic_wifi_three);
            } else if (rssi > -70) {
                mWifi.setImageResource(R.drawable.ic_wifi_two);
            } else if (rssi > -80) {
                mWifi.setImageResource(R.drawable.ic_wifi_one);
            } else {
                mWifi.setImageResource(R.drawable.ic_wifi_zero);
            }
        } catch (Throwable t) {
            mWifi.setVisibility(GONE);
        }
    }

    private void updateBluetooth() {
        if (mBluetooth == null) {
            return;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                mBluetooth.setVisibility(GONE);
                return;
            }
            mBluetooth.setVisibility(VISIBLE);
            int state = adapter.getProfileConnectionState(BluetoothProfile.GATT);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                mBluetooth.setImageResource(R.drawable.ic_bluetooth_mate);
            } else {
                mBluetooth.setImageResource(R.drawable.ic_bluetooth);
            }
        } catch (Throwable t) {
            mBluetooth.setVisibility(GONE);
        }
    }

    /** Paper-plane connectivity indicator: green when the device is online, red when offline. */
    private void updateImState() {
        if (mImState == null) {
            return;
        }
        boolean online = isNetworkOnline();
        android.graphics.drawable.Drawable plane = NetworkPlaneIcons.get(getContext(), online);
        if (plane != null) {
            mImState.setImageDrawable(plane);
        }
        mImState.setVisibility(VISIBLE);
    }

    private boolean isNetworkOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateHeadset() {
        if (mHeadset == null) {
            return;
        }
        try {
            android.media.AudioManager am = (android.media.AudioManager)
                    getContext().getSystemService(Context.AUDIO_SERVICE);
            boolean plugged = am != null && am.isWiredHeadsetOn();
            mHeadset.setVisibility(plugged ? VISIBLE : GONE);
        } catch (Throwable t) {
            mHeadset.setVisibility(GONE);
        }
    }

    private void updateAlarm() {
        if (mAlarm == null) {
            return;
        }
        try {
            android.app.AlarmManager am = (android.app.AlarmManager)
                    getContext().getSystemService(Context.ALARM_SERVICE);
            mAlarm.setVisibility(am != null && am.getNextAlarmClock() != null ? VISIBLE : GONE);
        } catch (Throwable t) {
            mAlarm.setVisibility(GONE);
        }
    }

    private void updateGps() {
        if (mGps == null) {
            return;
        }
        try {
            LocationManager lm = (LocationManager)
                    getContext().getSystemService(Context.LOCATION_SERVICE);
            boolean on = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            mGps.setVisibility(on ? VISIBLE : GONE);
        } catch (Throwable t) {
            mGps.setVisibility(GONE);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int dimen(int resId) {
        return getResources().getDimensionPixelSize(resId);
    }
}
