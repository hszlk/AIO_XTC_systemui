package com.android.systemui.qs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

/**
 * Volume back-end of the pull-down slider.
 *
 * Original APK logic: {@code qs.tiles.VolumeFuncSetting} drives
 * {@link AudioManager} STREAM_MUSIC through the vendor
 * {@code com.xtc.httplib.netstate.SwitchNetworkManager} listener stack and shows
 * {@code music_volume_value} / {@code volume_value}.
 *
 * Reimplementation (de-clouded, lightweight): plain AudioManager + the public
 * VOLUME_CHANGED broadcast; no network-state dependency, no vendor SDK.
 */
public class VolumeController {

    // Hidden framework constants; kept as literals so the app stays on the public SDK.
    private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    private final Context mContext;
    private final AudioManager mAudioManager;
    private OnVolumeChangedListener mListener;
    private BroadcastReceiver mReceiver;

    public interface OnVolumeChangedListener {
        void onVolumeChanged(int value, int max);
    }

    public VolumeController(Context context) {
        mContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public int getMax() {
        return mAudioManager == null ? 1 : Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    public int getVolume() {
        return mAudioManager == null ? 0 : mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public void setVolume(int value) {
        if (mAudioManager == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(getMax(), value));
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0);
    }

    public void register(OnVolumeChangedListener listener) {
        mListener = listener;
        if (mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (VOLUME_CHANGED_ACTION.equals(intent.getAction())
                            && intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                            == AudioManager.STREAM_MUSIC) {
                        dispatch();
                    }
                }
            };
            IntentFilter filter = new IntentFilter(VOLUME_CHANGED_ACTION);
            mContext.registerReceiver(mReceiver, filter);
        }
        dispatch();
    }

    public void unregister() {
        if (mReceiver != null) {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (Throwable ignored) {
            }
            mReceiver = null;
        }
        mListener = null;
    }

    private void dispatch() {
        if (mListener != null) {
            mListener.onVolumeChanged(getVolume(), getMax());
        }
    }
}
