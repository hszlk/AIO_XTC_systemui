package com.android.systemui.screenrecord;

/**
 * Audio source used for a screen recording session.
 *
 * API 27 has no AudioPlaybackCapture (that arrived in API 29), so on the target
 * device only {@link #NONE} and {@link #MIC} are functional; the two "internal"
 * values are kept for API compatibility with the original protocol/Intent
 * extras and degrade to {@link #NONE} in the recorder.
 *
 * Original APK logic: enum with NONE/INTERNAL/MIC/MIC_AND_INTERNAL, selected by
 * the {@code extra_useAudio} ordinal on the START intent.
 */
public enum ScreenRecordingAudioSource {
    NONE,
    INTERNAL,
    MIC,
    MIC_AND_INTERNAL;

    /** True when the session should record the device microphone. */
    public boolean usesMicrophone() {
        return this == MIC || this == MIC_AND_INTERNAL;
    }

    /** Parses an ordinal coming from an Intent extra; falls back to NONE. */
    public static ScreenRecordingAudioSource fromOrdinal(int ordinal) {
        ScreenRecordingAudioSource[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return NONE;
        }
        return values[ordinal];
    }
}
