package com.android.systemui.qs;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Brightness back-end of the pull-down slider.
 *
 * Original APK logic: {@code qs.tiles.BrightnessSettingSeekBar} +
 * {@code qs.tiles.BrightnessFuncSetting} read/write
 * {@link Settings.System#SCREEN_BRIGHTNESS} (and the auto-brightness mode),
 * refresh on the content-observer callback and show the {@code brightness_value}
 * toast at the ends.
 *
 * Reimplementation: same public setting channel, no vendor service, no
 * reporting. The panel slider writes the system setting directly (SystemUI is a
 * platform app and holds WRITE_SETTINGS in the manifest).
 */
public class BrightnessController {

    /** Hardware range used by the slider: darker minimum / full maximum. */
    private static final int MIN_BRIGHTNESS = 1;
    private static final int MAX_BRIGHTNESS = 255;
    /** Slider scale exposed to the UI. */
    private static final int SLIDER_MAX = 100;

    private final Context mContext;
    private final ContentResolver mResolver;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private OnBrightnessChangedListener mListener;
    private ContentObserver mObserver;

    public interface OnBrightnessChangedListener {
        void onBrightnessChanged(int value, int max);
    }

    public BrightnessController(Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    public int getMax() {
        return SLIDER_MAX;
    }

    /** Slider position (0..100) for the current system brightness. */
    public int getBrightness() {
        int system = Settings.System.getInt(mResolver, Settings.System.SCREEN_BRIGHTNESS, 128);
        int clamped = Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, system));
        return Math.round((clamped - MIN_BRIGHTNESS) * (float) SLIDER_MAX
                / (MAX_BRIGHTNESS - MIN_BRIGHTNESS));
    }

    /**
     * Writes the system brightness. The slider uses a slight gamma so the top of
     * the range reaches the full hardware maximum and the bottom gets darker.
     */
    public void setBrightness(int progress) {
        float f = Math.max(0f, Math.min(1f, progress / (float) SLIDER_MAX));
        // steeper gamma: bottom darker, top hits the full hardware maximum sooner
        int value = MIN_BRIGHTNESS + Math.round((float) Math.pow(f, 0.72f)
                * (MAX_BRIGHTNESS - MIN_BRIGHTNESS));
        try {
            Settings.System.putInt(mResolver, Settings.System.SCREEN_BRIGHTNESS, value);
            Settings.System.putInt(mResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (Throwable ignored) {
            // Setting the brightness must never crash the status bar.
        }
    }

    public void register(OnBrightnessChangedListener listener) {
        mListener = listener;
        if (mObserver == null) {
            mObserver = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    dispatch();
                }
            };
            mResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, mObserver);
        }
        dispatch();
    }

    public void unregister() {
        if (mObserver != null) {
            mResolver.unregisterContentObserver(mObserver);
            mObserver = null;
        }
        mListener = null;
    }

    private void dispatch() {
        if (mListener != null) {
            mListener.onBrightnessChanged(getBrightness(), getMax());
        }
    }
}
