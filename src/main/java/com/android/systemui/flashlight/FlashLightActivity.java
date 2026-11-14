package com.android.systemui.flashlight;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

import java.lang.ref.WeakReference;

/**
 * Screen flashlight ("屏幕手电") - the API 27 flashlight implementation.
 *
 * On Android 8.1 the vendor does NOT call {@code CameraManager.setTorchMode}
 * (that path is only selected on API 30+); the flashlight is a full screen
 * white page whose backlight is driven to maximum. This activity is that page:
 *
 *  - full screen, {@code FLAG_KEEP_SCREEN_ON | FLAG_SHOW_WHEN_LOCKED};
 *  - window brightness animated 100 -> 255 over 2 s (the exact range of the
 *    original implementation), applied both to the window attribute (smooth,
 *    no permission needed) and to {@code Settings.System.SCREEN_BRIGHTNESS}
 *    (persisted; the original value is restored on pause/destroy);
 *  - "照明模式" (steady white) and "闪烁模式" (white blinking) pages, switched by
 *    a horizontal swipe (the original used a vendor ViewPager, which is not
 *    available to a framework-only rebuild, so the two pages are rendered by the
 *    same full screen light view);
 *  - a tap toggles the overlay tips, 180 s auto-exit, exit on pause.
 *
 * Original APK logic: {@code flashlight.FlashLightActivity} (570 lines,
 * vendor ViewPager + vendor MVP base + RxJava timers). Reimplemented with
 * framework-only widgets and Handler timers; the vendor guide dialog / Lottie
 * animation (API 30 only) is intentionally not rebuilt.
 *
 * Also the public entry point used by the assistant directive and by the
 * flashlight/SOS dialog: {@link #requestOn(Context)}, {@link #requestOff()},
 * {@link #toggle(Context)} and {@link #isOn()}.
 */
public class FlashLightActivity extends Activity {

    private static final String TAG = "FlashLightActivity";

    /** Original intent action constant (kept for parity with external callers). */
    public static final String ACTION_FLASH_LIGHT = "android.settings.FLASH_LIGHT";

    private static final long BRIGHTNESS_ANIMATION_MS = 2000L;
    private static final float BRIGHTNESS_RANGE_START = 100f;
    private static final float BRIGHTNESS_RANGE_END = 255f;
    private static final int MAX_BRIGHTNESS = 255;
    private static final long AUTO_EXIT_MS = 180_000L;
    private static final long BRIGHTNESS_WRITE_INTERVAL_MS = 200L;
    private static final long FLASH_HALF_PERIOD_MS = 130L;
    private static final int TOUCH_SLOP_DP = 12;
    private static final int MODE_ILLUMINATE = 0;
    private static final int MODE_FLASH = 1;
    /** Gesture guide, per page (user spec: swipe left = flash, right = exit). */
    private static final String HINT_ILLUMINATE =
            "左滑进入闪光 · 右滑直接退出 · 轻触隐藏提示 · 180 秒后自动关闭";
    private static final String HINT_FLASH =
            "右滑返回照明 · 轻触隐藏提示 · 180 秒后自动关闭";

    private static WeakReference<FlashLightActivity> sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private FrameLayout mRoot;
    private View mLightPanel;
    private LinearLayout mTipGroup;
    private TextView mTitle;
    private TextView mHint;
    private ImageView mLightDot;
    private ImageView mFlashDot;

    private ValueAnimator mBrightnessAnimator;
    private ValueAnimator mFlashAnimator;

    private WindowManager.LayoutParams mWindowParams;
    private float mOriginalWindowBrightness = -1f;
    private int mOriginalSystemBrightness = -1;
    private long mLastSystemBrightnessWrite;
    private int mMode = MODE_ILLUMINATE;
    private boolean mUiVisible = true;

    private float mDownX;
    private boolean mTouchMoved;
    private int mTouchSlopPx;
    private float mDownY;

    private final Runnable mAutoExitTask = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "auto exit after " + AUTO_EXIT_MS + " ms");
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
        mTouchSlopPx = (int) (TOUCH_SLOP_DP * getResources().getDisplayMetrics().density);

        buildUi();
        captureOriginalBrightness();
        startBrightnessAnimation();
        applyMode(MODE_ILLUMINATE);
        FlashlightStatus.setIlluminated(true);
        mHandler.postDelayed(mAutoExitTask, AUTO_EXIT_MS);
        Log.d(TAG, "screen flashlight on");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: restoring brightness and finishing");
        stopAnimations();
        restoreBrightness();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        stopAnimations();
        restoreBrightness();
        FlashlightStatus.setIlluminated(false);
        FlashLightActivity current = sInstance == null ? null : sInstance.get();
        if (current == this) {
            sInstance = null;
        }
        Log.d(TAG, "screen flashlight off");
    }

    // -------------------------------------------------------------------- UI

    private void buildUi() {
        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.BLACK);

        mLightPanel = new View(this);
        mLightPanel.setBackgroundColor(Color.WHITE);
        mRoot.addView(mLightPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        mTipGroup = new LinearLayout(this);
        mTipGroup.setOrientation(LinearLayout.VERTICAL);
        mTipGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(16);
        mTipGroup.setPadding(padding, padding, padding, padding);

        mTitle = new TextView(this);
        mTitle.setText(R.string.string_light_title);
        mTitle.setTextColor(Color.BLACK);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        mTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        mTipGroup.addView(mTitle);

        mHint = new TextView(this);
        mHint.setText(HINT_ILLUMINATE);
        mHint.setTextColor(0x99000000);
        mHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        mHint.setGravity(Gravity.CENTER_HORIZONTAL);
        mTipGroup.addView(mHint);

        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tipParams.topMargin = dp(28);
        mRoot.addView(mTipGroup, tipParams);

        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        int dotSize = dp(6);
        int dotMargin = dp(4);
        mLightDot = new ImageView(this);
        mLightDot.setImageResource(R.drawable.ic_point_selects);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.setMarginEnd(dotMargin);
        dots.addView(mLightDot, dotParams);
        mFlashDot = new ImageView(this);
        mFlashDot.setImageResource(R.drawable.ic_point_unselects);
        dots.addView(mFlashDot, new LinearLayout.LayoutParams(dotSize, dotSize));
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsParams.bottomMargin = dp(18);
        mRoot.addView(dots, dotsParams);
        // tappable page dots: reliable mode switch that cannot be eaten by the
        // system's edge-swipe back gesture
        mLightDot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyMode(MODE_ILLUMINATE);
            }
        });
        mFlashDot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyMode(MODE_FLASH);
            }
        });
        mLightDot.setPadding(dp(4), dp(4), dp(4), dp(4));
        mFlashDot.setPadding(dp(4), dp(4), dp(4), dp(4));

        // Gesture map (user spec): page 1 = illuminate, swipe LEFT goes to the
        // flash page, swipe RIGHT exits directly. Page 2 = flash, swipe RIGHT
        // returns to illuminate. All horizontal drags are claimed via
        // requestDisallowInterceptTouchEvent so the ROM's SwipeDismissLayout
        // cannot eat them; the exit drag finishes the activity itself.
        mRoot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownX = event.getRawX();
                        mDownY = event.getRawY();
                        mTouchMoved = false;
                        ViewParent vp = view.getParent();
                        if (vp != null) {
                            vp.requestDisallowInterceptTouchEvent(true);
                        }
                        showUiTemporarily();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float adx = Math.abs(event.getRawX() - mDownX);
                        float dy = Math.abs(event.getRawY() - mDownY);
                        if (adx > mTouchSlopPx && adx > dy) {
                            mTouchMoved = true;
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (mTouchMoved) {
                            float deltaX = event.getRawX() - mDownX;
                            if (mMode == MODE_ILLUMINATE) {
                                if (deltaX < 0) {
                                    applyMode(MODE_FLASH);
                                } else {
                                    finish(); // swipe right exits directly
                                }
                            } else if (deltaX > 0) {
                                applyMode(MODE_ILLUMINATE);
                            }
                        } else {
                            toggleUi();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        setContentView(mRoot);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyMode(int mode) {
        mMode = mode;
        stopFlashAnimation();
        mLightPanel.setAlpha(1f);
        mLightPanel.setBackgroundColor(Color.WHITE);
        if (mode == MODE_FLASH) {
            mTitle.setText(R.string.string_flash_title);
            mHint.setText(HINT_FLASH);
            startFlashAnimation();
        } else {
            mTitle.setText(R.string.string_light_title);
            mHint.setText(HINT_ILLUMINATE);
        }
        mLightDot.setImageResource(mode == MODE_ILLUMINATE
                ? R.drawable.ic_point_selects : R.drawable.ic_point_unselects);
        mFlashDot.setImageResource(mode == MODE_FLASH
                ? R.drawable.ic_point_selects : R.drawable.ic_point_unselects);
    }

    private void startFlashAnimation() {
        stopFlashAnimation();
        mFlashAnimator = ValueAnimator.ofFloat(1f, 0f);
        mFlashAnimator.setDuration(FLASH_HALF_PERIOD_MS);
        mFlashAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mFlashAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mFlashAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Object value = animation.getAnimatedValue();
                if (value instanceof Float) {
                    mLightPanel.setAlpha((Float) value);
                }
            }
        });
        mFlashAnimator.start();
    }

    private void stopFlashAnimation() {
        if (mFlashAnimator != null) {
            mFlashAnimator.cancel();
            mFlashAnimator = null;
        }
    }

    private void toggleUi() {
        mUiVisible = !mUiVisible;
        mTipGroup.animate().alpha(mUiVisible ? 1f : 0f).setDuration(200L).start();
    }

    private void showUiTemporarily() {
        if (!mUiVisible) {
            mUiVisible = true;
            mTipGroup.animate().alpha(1f).setDuration(200L).start();
        }
    }

    // ------------------------------------------------------------- brightness

    private void captureOriginalBrightness() {
        mWindowParams = getWindow().getAttributes();
        mOriginalWindowBrightness = mWindowParams.screenBrightness;
        try {
            mOriginalSystemBrightness = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
        } catch (Throwable t) {
            Log.w(TAG, "unable to read system brightness", t);
            mOriginalSystemBrightness = -1;
        }
    }

    private void startBrightnessAnimation() {
        stopBrightnessAnimation();
        mBrightnessAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(BRIGHTNESS_ANIMATION_MS);
        mBrightnessAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                int level = (int) (BRIGHTNESS_RANGE_START
                        + fraction * (BRIGHTNESS_RANGE_END - BRIGHTNESS_RANGE_START));
                applyWindowBrightness(level / (float) MAX_BRIGHTNESS);
                maybeWriteSystemBrightness(level);
            }
        });
        mBrightnessAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                applyWindowBrightness(1f);
                writeSystemBrightness(MAX_BRIGHTNESS);
            }
        });
        mBrightnessAnimator.start();
    }

    private void stopBrightnessAnimation() {
        if (mBrightnessAnimator != null) {
            mBrightnessAnimator.cancel();
            mBrightnessAnimator = null;
        }
    }

    private void stopAnimations() {
        stopBrightnessAnimation();
        stopFlashAnimation();
    }

    private void applyWindowBrightness(float brightness) {
        if (mWindowParams == null) {
            return;
        }
        mWindowParams.screenBrightness = Math.max(0.01f, Math.min(1f, brightness));
        try {
            getWindow().setAttributes(mWindowParams);
        } catch (Throwable t) {
            Log.w(TAG, "applyWindowBrightness failed", t);
        }
    }

    private void maybeWriteSystemBrightness(int level) {
        long now = SystemClock.uptimeMillis();
        if (now - mLastSystemBrightnessWrite < BRIGHTNESS_WRITE_INTERVAL_MS) {
            return;
        }
        mLastSystemBrightnessWrite = now;
        writeSystemBrightness(level);
    }

    private void writeSystemBrightness(int level) {
        int value = Math.max(1, Math.min(MAX_BRIGHTNESS, level));
        try {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, value);
        } catch (Throwable t) {
            // WRITE_SETTINGS may be denied in a non-platform build; the window
            // attribute above already produces the visible effect.
            Log.w(TAG, "unable to persist system brightness", t);
        }
    }

    private void restoreBrightness() {
        if (mWindowParams != null) {
            mWindowParams.screenBrightness = mOriginalWindowBrightness;
            try {
                getWindow().setAttributes(mWindowParams);
            } catch (Throwable t) {
                Log.w(TAG, "restore window brightness failed", t);
            }
        }
        if (mOriginalSystemBrightness >= 0) {
            try {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, mOriginalSystemBrightness);
            } catch (Throwable t) {
                Log.w(TAG, "restore system brightness failed", t);
            }
        }
    }

    // --------------------------------------------------------------- static API

    /** True when the screen flashlight activity is showing. */
    public static boolean isOn() {
        return FlashlightStatus.isIlluminated();
    }

    /** Opens the screen flashlight (no-op when already on). */
    public static void requestOn(Context context) {
        if (context == null || FlashlightStatus.isIlluminated()) {
            return;
        }
        try {
            Intent intent = new Intent(context, FlashLightActivity.class);
            intent.setAction(ACTION_FLASH_LIGHT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "requestOn: unable to start activity", t);
        }
    }

    /** Closes the screen flashlight and clears the shared state. */
    public static void requestOff() {
        FlashLightActivity current = sInstance == null ? null : sInstance.get();
        FlashlightStatus.setIlluminated(false);
        if (current != null) {
            try {
                current.finish();
            } catch (Throwable t) {
                Log.w(TAG, "requestOff: unable to finish activity", t);
            }
        }
    }

    /** Starts the screen flashlight when off, closes it when on. */
    public static void toggle(Context context) {
        if (FlashlightStatus.isIlluminated()) {
            requestOff();
        } else {
            requestOn(context);
        }
    }
}
