package com.android.systemui.screenrecord;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Records the DEVICE playback audio (what the watch is playing) into a PCM WAV
 * file while the screen is being recorded.
 *
 * Two paths (clean-room reimplementation of the vendor idea, not the vendor
 * code):
 *   - API 29+: the public AudioPlaybackCapture API against the recording
 *     MediaProjection (exactly what ScreenInternalAudioRecorderEx-class
 *     functionality is on the Android 11 build);
 *   - API < 29 (the 8.1 watch): the hidden REMOTE_SUBMIX loopback source (int
 *     value 8). Platform-signed system uid usually holds CAPTURE_AUDIO_OUTPUT,
 *     so this works on ROMs whose audio policy exposes a remote_submix device;
 *     a failed open degrades to "no device audio" (recording stays silent).
 */
final class InternalAudioRecorder {

    private static final String TAG = "InternalAudioRecorder";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    /** MediaRecorder.AudioSource.REMOTE_SUBMIX == 8 (hidden constant). */
    private static final int REMOTE_SUBMIX = 8;

    private AudioRecord mRecord;
    private Thread mThread;
    private volatile boolean mRunning;
    private final File mWavFile;
    private long mFramesWritten;

    InternalAudioRecorder(File wavFile) {
        mWavFile = wavFile;
    }

    File getFile() {
        return mWavFile;
    }

    /** Returns true when a device-audio stream was opened. */
    boolean start(Context context, MediaProjection projection) {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING);
        if (minBuf <= 0) {
            return false;
        }
        int bufSize = minBuf * 4;
        AudioRecord record = null;
        if (Build_SUPPORTS_PLAYBACK_CAPTURE && projection != null) {
            record = createPlaybackCapture(context, projection, bufSize);
        }
        if (record == null) {
            // 8.1 loopback attempt (system uid + CAPTURE_AUDIO_OUTPUT)
            record = createRemoteSubmix(bufSize);
        }
        if (record == null) {
            return false;
        }
        try {
            record.startRecording();
        } catch (Throwable t) {
            Log.w(TAG, "startRecording failed: " + t);
            record.release();
            return false;
        }
        if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Log.w(TAG, "audio record did not enter RECORDING state");
            record.release();
            return false;
        }
        mRecord = record;
        mRunning = true;
        mFramesWritten = 0;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                writeLoop();
            }
        }, "internal-audio-rec");
        mThread.start();
        Log.i(TAG, "device audio capture started (" + mWavFile.getName() + ")");
        return true;
    }

    private static final boolean Build_SUPPORTS_PLAYBACK_CAPTURE =
            android.os.Build.VERSION.SDK_INT >= 29;

    @TargetApi(29)
    private AudioRecord createPlaybackCapture(Context context, MediaProjection projection,
            int bufSize) {
        try {
            AudioPlaybackCaptureConfiguration config =
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(ENCODING)
                    .setChannelMask(CHANNEL_MASK)
                    .build();
            return new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build();
        } catch (Throwable t) {
            Log.w(TAG, "playback capture unavailable: " + t);
            return null;
        }
    }

    private AudioRecord createRemoteSubmix(int bufSize) {
        try {
            return new AudioRecord(REMOTE_SUBMIX, SAMPLE_RATE, CHANNEL_MASK,
                    ENCODING, bufSize);
        } catch (Throwable t) {
            Log.w(TAG, "remote_submix unavailable: " + t);
            return null;
        }
    }

    private void writeLoop() {
        int chunk = Math.max(2048, SAMPLE_RATE / 10) * 4; // ~100ms of stereo 16-bit
        byte[] pcm = new byte[chunk];
        OutputStream out = null;
        try {
            out = new FileOutputStream(mWavFile);
            writeWavHeader(out);
            while (mRunning) {
                int read = mRecord.read(pcm, 0, pcm.length);
                if (read <= 0) {
                    if (read < 0) {
                        Log.w(TAG, "audio read error " + read);
                        break;
                    }
                    continue;
                }
                out.write(pcm, 0, read);
                mFramesWritten += read / 4; // stereo 16-bit frames
            }
        } catch (Throwable t) {
            Log.w(TAG, "audio write failed: " + t);
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Writes a canonical 44-byte PCM WAV header (sizes patched on stop). */
    private static void writeWavHeader(OutputStream out) throws IOException {
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        putInt(h, 4, 36);          // patched on stop
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        putInt(h, 16, 16);
        putShort(h, 20, (short) 1); // PCM
        putShort(h, 22, (short) 2); // stereo
        putInt(h, 24, SAMPLE_RATE);
        putInt(h, 28, SAMPLE_RATE * 2 * 2); // byte rate
        putShort(h, 32, (short) 4); // block align
        putShort(h, 34, (short) 16); // bits per sample
        put(h, 36, "data");
        putInt(h, 40, 0);          // patched on stop
        out.write(h);
    }

    private static void put(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[off + i] = (byte) s.charAt(i);
        }
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }

    private static void putShort(byte[] b, int off, short v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
    }

    /** Stops capture; returns the wav file, or null when nothing usable. */
    File stop() {
        mRunning = false;
        if (mThread != null) {
            try {
                mThread.join(2000L);
            } catch (InterruptedException ignored) {
            }
            mThread = null;
        }
        if (mRecord != null) {
            try {
                mRecord.stop();
            } catch (Throwable ignored) {
            }
            try {
                mRecord.release();
            } catch (Throwable ignored) {
            }
            mRecord = null;
        }
        // fix the header sizes (writer patched nothing usable mid-stream)
        try {
            patchWavHeader(mWavFile);
        } catch (Throwable t) {
            Log.w(TAG, "wav header patch failed: " + t);
        }
        if (mWavFile == null || !mWavFile.exists() || mWavFile.length() <= 44L) {
            if (mWavFile != null) {
                mWavFile.delete();
            }
            return null;
        }
        return mWavFile;
    }

    private static void patchWavHeader(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        long dataBytes = Math.max(0L, file.length() - 44L);
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw");
        try {
            raf.seek(4);
            raf.write(le32((int) (36 + dataBytes)));
            raf.seek(40);
            raf.write(le32((int) dataBytes));
        } finally {
            raf.close();
        }
    }

    private static byte[] le32(int v) {
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }
}
