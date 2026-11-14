package com.android.systemui.screenrecord;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;

/**
 * MediaProjection + MediaRecorder capture pipeline (no UI, no state machine).
 *
 * Mirrors the parameters of the original {@code screenrecord.f}
 * (ScreenMediaRecorder): H.264 in an MPEG-4 container written straight to a
 * temp {@code .mp4} (no muxer is needed - the platform does the muxing),
 * 30 fps, bitrate = width * height * 30 * 0.5, max duration from the service
 * and a 100 MB file-size cap. Audio is optional and uses the microphone with
 * AAC/44.1 kHz/196 kbps when requested.
 *
 * The recorder is created and driven on the caller's worker thread: MediaRecorder
 * delivers its callbacks on the looper of the thread that created it, so the
 * service keeps that thread around (HandlerThread).
 */
public class ScreenMediaRecorder {

    private static final String TAG = "ScreenMediaRecorder";

    private static final int VIDEO_FRAME_RATE = 30;
    private static final int AUDIO_BITRATE = 196000;
    private static final int AUDIO_CHANNELS = 1;
    private static final int AUDIO_SAMPLING_RATE = 44100;
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L;
    private static final int VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 16;
    private static final String VIRTUAL_DISPLAY_NAME = "Recording Display";

    private final Context mContext;
    private final ScreenRecordingAudioSource mAudioSource;

    private MediaProjection mProjection;
    private MediaRecorder mRecorder;
    private VirtualDisplay mVirtualDisplay;
    private Surface mSurface;
    private File mOutputFile;
    private boolean mStarted;

    public ScreenMediaRecorder(Context context, ScreenRecordingAudioSource audioSource) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mAudioSource = audioSource == null ? ScreenRecordingAudioSource.NONE : audioSource;
    }

    /**
     * Prepares and starts the capture. Call on the service worker thread.
     *
     * @param projection       projection obtained from MediaProjectionManager
     * @param onInfoListener   forwarded MediaRecorder info callbacks (max duration/size)
     */
    public void start(MediaProjection projection, MediaRecorder.OnInfoListener onInfoListener)
            throws IOException {
        if (projection == null) {
            throw new IOException("media projection is null");
        }
        File dir = RecordingStorageHelper.getCacheDir(mContext);
        if (dir == null) {
            throw new IOException("screen record cache dir is null");
        }
        mProjection = projection;
        mOutputFile = File.createTempFile("temp", RecordingStorageHelper.FILE_SUFFIX, dir);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null || windowManager.getDefaultDisplay() == null) {
            throw new IOException("window manager/display unavailable");
        }
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (width <= 0 || height <= 0) {
            throw new IOException("invalid display size " + width + "x" + height);
        }

        MediaRecorder recorder = new MediaRecorder();
        mRecorder = recorder;
        recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int what, int extra) {
                Log.e(TAG, "media recorder error what=" + what + " extra=" + extra);
            }
        });
        try {
            if (mAudioSource.usesMicrophone()) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(width, height);
            recorder.setVideoFrameRate(VIDEO_FRAME_RATE);
            recorder.setCaptureRate(VIDEO_FRAME_RATE);
            recorder.setVideoEncodingBitRate((int) (width * (long) height * VIDEO_FRAME_RATE * 0.5d));
            // no time limit (user request); the 100 MB file cap stays as the
            // only stop condition besides a manual stop
            recorder.setMaxFileSize(MAX_FILE_SIZE_BYTES);
            if (mAudioSource.usesMicrophone()) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioChannels(AUDIO_CHANNELS);
                recorder.setAudioEncodingBitRate(AUDIO_BITRATE);
                recorder.setAudioSamplingRate(AUDIO_SAMPLING_RATE);
            }
            recorder.setOutputFile(mOutputFile.getAbsolutePath());
            recorder.prepare();
            mSurface = recorder.getSurface();
            mVirtualDisplay = projection.createVirtualDisplay(VIRTUAL_DISPLAY_NAME, width, height,
                    metrics.densityDpi, VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mSurface, null, null);
            recorder.setOnInfoListener(onInfoListener);
            recorder.start();
            mStarted = true;
            Log.d(TAG, "recording started " + width + "x" + height + " audio=" + mAudioSource);
        } catch (IOException | RuntimeException e) {
            releaseInternal(false);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("unable to start media recorder", e);
        }
    }

    /**
     * Stops and releases the pipeline. Returns true when the recorder stopped
     * cleanly (i.e. the temp file should contain a valid clip).
     */
    public boolean stop() {
        boolean cleanStop = false;
        if (mRecorder != null && mStarted) {
            try {
                mRecorder.stop();
                cleanStop = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "stop media recorder failed", e);
            }
        }
        releaseInternal(true);
        Log.d(TAG, "recording stopped clean=" + cleanStop + " file=" + mOutputFile);
        return cleanStop;
    }

    /** Temp output file, valid after {@link #start}. */
    public File getOutputFile() {
        return mOutputFile;
    }

    public boolean isStarted() {
        return mStarted;
    }

    private void releaseInternal(boolean unregisterProjection) {
        MediaRecorder recorder = mRecorder;
        mRecorder = null;
        mStarted = false;
        if (recorder != null) {
            try {
                recorder.setOnInfoListener(null);
                recorder.setOnErrorListener(null);
                recorder.reset();
            } catch (Throwable t) {
                Log.w(TAG, "unable to reset media recorder", t);
            }
            try {
                recorder.release();
            } catch (Throwable t) {
                Log.w(TAG, "unable to release media recorder", t);
            }
        }
        if (mVirtualDisplay != null) {
            try {
                mVirtualDisplay.release();
            } catch (Throwable t) {
                Log.w(TAG, "unable to release virtual display", t);
            }
            mVirtualDisplay = null;
        }
        if (mSurface != null) {
            try {
                mSurface.release();
            } catch (Throwable t) {
                Log.w(TAG, "unable to release surface", t);
            }
            mSurface = null;
        }
        MediaProjection projection = mProjection;
        mProjection = null;
        if (projection != null && unregisterProjection) {
            try {
                projection.stop();
            } catch (Throwable t) {
                Log.w(TAG, "unable to stop media projection", t);
            }
        }
    }
}
