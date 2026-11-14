package com.android.systemui.flashlight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

import java.lang.ref.WeakReference;

/**
 * SOS screen-flashlight activity (manifest: dialog-styled Activity, singleTask,
 * showWhenLocked, {@code XtcStayFront=true}).
 *
 * Drives a screen light with the exact rhythm of the original vendor
 * implementation ({@code statusbar.policy.q}) so the SOS signal is standard
 * "3 short - 3 long - 3 short":
 *
 * <pre>
 *   toggle points (ms, relative to the start of one 8180 ms cycle):
 *   1000, 1230, 1330, 1560, 1660, 1890,
 *   2390, 3190, 3690,
 *   4490, 4990, 5790,
 *   6290, 6520, 6620, 6850, 6950, 7180
 *   period = 8180 ms
 * </pre>
 *
 * Behaviour:
 *  - full screen light panel toggled at every point above (screen flashlight,
 *    because API 27 has no torch path),
 *  - warning text from the vendor resources ({@code flash_sos_tip}),
 *  - {@code SOS灯光信号} title button starts/stops the pattern,
 *  - a tap on the top strip ("切换成普通手电") closes the dialog and opens
 *    {@link FlashLightActivity}, mirroring the original {@code v_switch_instead}
 *    hit area,
 *  - a partial wake lock is held (30 min timeout) and the SOS stops
 *    automatically after 30 minutes (original WakeLock 1805000 ms / auto-off
 *    1800000 ms),
 *  - screen stays on and the activity shows over the lock screen.
 *
 * Original APK logic: {@code flashlight.FlashLightSOSDialog} +
 * {@code flashlight.a} (8180 ms period TimerTask scheduler) +
 * {@code statusbar.policy.q} (SOS rhythm). Rebuilt with a main-thread Handler;
 * the vendor Lottie guide (API 30 only) is intentionally not rebuilt.
 */
public class FlashLightSOSDialog extends Activity {

    private static final String TAG = "FlashLightSOS";

    /** Toggle points of one SOS cycle, copied from the original implementation. */
    private static final int[] SOS_PATTERN_MS = new int[]{
            1000, 1230, 1330, 1560, 1660, 1890,
            2390, 3190, 3690,
            4490, 4990, 5790,
            6290, 6520, 6620, 6850, 6950, 7180
    };
    private static final long SOS_CYCLE_MS = 8180L;
    private static final long SOS_MAX_DURATION_MS = 1_800_000L;
    private static final long WAKE_LOCK_TIMEOUT_MS = 1_805_000L;
    private static final String WAKE_LOCK_TAG = "SystemUI:FlashLightSOS";

    private static WeakReference<FlashLightSOSDialog> sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private FrameLayout mRoot;
    private View mLightPanel;
    private TextView mTitleButton;
    private TextView mStateText;

    private PowerManager.WakeLock mWakeLock;
    private boolean mSosRunning;
    private boolean mLightShowing;
    private int mStepIndex;
    private long mCycleStartElapsed;

    private final Runnable mStepTask = new Runnable() {
        @Override
        public void run() {
            if (!mSosRunning) {
                return;
            }
            toggleLight();
            mStepIndex++;
            if (mStepIndex >= SOS_PATTERN_MS.length) {
                mStepIndex = 0;
                mCycleStartElapsed += SOS_CYCLE_MS;
            }
            long nextAt = mCycleStartElapsed + SOS_PATTERN_MS[mStepIndex];
            long delay = Math.max(1L, nextAt - SystemClock.uptimeMillis());
            mHandler.postDelayed(this, delay);
        }
    };

    private final Runnable mAutoStopTask = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "SOS auto-stop after " + SOS_MAX_DURATION_MS + " ms");
            stopSos();
            finish();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        try {
            setShowWhenLocked(true);
        } catch (Throwable t) {
            Log.w(TAG, "setShowWhenLocked unavailable", t);
        }
        sInstance = new WeakReference<>(this);
        buildUi();
        startSos();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSos();
        FlashLightSOSDialog current = sInstance == null ? null : sInstance.get();
        if (current == this) {
            sInstance = null;
        }
    }

    // -------------------------------------------------------------------- UI

    private void buildUi() {
        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.BLACK);

        mLightPanel = new View(this);
        mLightPanel.setBackgroundColor(Color.WHITE);
        mLightPanel.setVisibility(View.INVISIBLE);
        mRoot.addView(mLightPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(12);
        content.setPadding(padding, padding, padding, padding);

        mTitleButton = new TextView(this);
        mTitleButton.setText(R.string.flash_sos_title);
        mTitleButton.setTextColor(Color.WHITE);
        mTitleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        mTitleButton.setGravity(Gravity.CENTER);
        mTitleButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setCornerRadius(dp(18));
        buttonBackground.setColor(0x33FFFFFF);
        mTitleButton.setBackground(buttonBackground);
        mTitleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSosRunning) {
                    stopSos();
                } else {
                    startSos();
                }
            }
        });
        content.addView(mTitleButton);

        mStateText = new TextView(this);
        mStateText.setTextColor(0xB3FFFFFF);
        mStateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        mStateText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stateParams.topMargin = dp(6);
        content.addView(mStateText, stateParams);

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        mRoot.addView(content, contentParams);

        TextView warning = new TextView(this);
        warning.setText(R.string.flash_sos_tip);
        warning.setTextColor(0xB3FFFFFF);
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        warning.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams warningParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        warningParams.bottomMargin = dp(14);
        mRoot.addView(warning, warningParams);

        // Top strip: "switch to normal flashlight" (original v_switch_instead).
        View switchInstead = new View(this);
        switchInstead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "switch to normal flashlight");
                finish();
                FlashLightActivity.requestOn(FlashLightSOSDialog.this);
            }
        });
        FrameLayout.LayoutParams switchParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(40),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mRoot.addView(switchInstead, switchParams);

        setContentView(mRoot);
        updateTitleState();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateTitleState() {
        if (mTitleButton == null) {
            return;
        }
        boolean sos = mSosRunning;
        mTitleButton.setTextColor(sos
                ? getResources().getColor(R.color.color_sos_title_selected)
                : Color.WHITE);
        if (mStateText != null) {
            mStateText.setText(sos ? "SOS 信号发送中 · 轻触停止" : "轻触上方开始 SOS 信号");
        }
    }

    // -------------------------------------------------------------- SOS engine

    private void startSos() {
        if (mSosRunning) {
            return;
        }
        mSosRunning = true;
        mStepIndex = 0;
        mLightShowing = false;
        mCycleStartElapsed = SystemClock.uptimeMillis();
        acquireWakeLock();
        mHandler.removeCallbacks(mStepTask);
        mHandler.postDelayed(mStepTask, SOS_PATTERN_MS[0]);
        mHandler.removeCallbacks(mAutoStopTask);
        mHandler.postDelayed(mAutoStopTask, SOS_MAX_DURATION_MS);
        FlashlightStatus.setSos(true);
        updateTitleState();
        Log.d(TAG, "SOS started");
    }

    private void stopSos() {
        mHandler.removeCallbacks(mStepTask);
        mHandler.removeCallbacks(mAutoStopTask);
        releaseWakeLock();
        mSosRunning = false;
        mLightShowing = false;
        if (mLightPanel != null) {
            mLightPanel.setVisibility(View.INVISIBLE);
        }
        FlashlightStatus.setSos(false);
        updateTitleState();
        Log.d(TAG, "SOS stopped");
    }

    private void toggleLight() {
        mLightShowing = !mLightShowing;
        if (mLightPanel != null) {
            mLightPanel.setVisibility(mLightShowing ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void acquireWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            return;
        }
        try {
            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                return;
            }
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        } catch (Throwable t) {
            Log.w(TAG, "unable to acquire wake lock", t);
            mWakeLock = null;
        }
    }

    private void releaseWakeLock() {
        PowerManager.WakeLock wakeLock = mWakeLock;
        mWakeLock = null;
        if (wakeLock == null) {
            return;
        }
        try {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable t) {
            Log.w(TAG, "unable to release wake lock", t);
        }
    }

    // --------------------------------------------------------------- static API

    public static boolean isSosOn() {
        return FlashlightStatus.isSos();
    }

    public static boolean isShowing() {
        return sInstance != null && sInstance.get() != null;
    }

    /** Opens the SOS page (used by the flashlight tile equivalent / assistant). */
    public static void requestShow(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(context, FlashLightSOSDialog.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "requestShow: unable to start activity", t);
        }
    }

    /** Closes the SOS page and stops the pattern. */
    public static void requestClose() {
        FlashlightStatus.setSos(false);
        FlashLightSOSDialog current = sInstance == null ? null : sInstance.get();
        if (current != null) {
            try {
                current.finish();
            } catch (Throwable t) {
                Log.w(TAG, "requestClose: unable to finish activity", t);
            }
        }
    }
}
