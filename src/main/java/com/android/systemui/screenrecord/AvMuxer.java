package com.android.systemui.screenrecord;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaExtractor;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Mixes the recorded device audio (a PCM WAV written by
 * {@link InternalAudioRecorder}) into the finished video mp4, producing
 * A/V muxed output at the requested destination.
 *
 * Pure public API: MediaExtractor demuxes the H.264 track from the video file
 * and the AAC track from the audio file, MediaMuxer writes the final mp4.
 * Returns the video-only file untouched when anything fails (graceful
 * degradation to a silent recording).
 */
final class AvMuxer {

    private static final String TAG = "AvMuxer";
    private static final int BUFFER_SIZE = 512 * 1024;

    private AvMuxer() {
    }

    /**
     * @param videoFile recorded mp4 (H.264, possibly silent)
     * @param audioFile wav written by InternalAudioRecorder
     * @param outFile   destination (usually the video file replaced in place)
     * @return true when outFile was written successfully
     */
    static boolean mux(File videoFile, File audioFile, File outFile) {
        if (videoFile == null || audioFile == null || outFile == null
                || !videoFile.exists() || !audioFile.exists()) {
            return false;
        }
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            videoExtractor.setDataSource(videoFile.getAbsolutePath());
            int videoTrack = selectTrack(videoExtractor, "video/");
            if (videoTrack < 0) {
                Log.w(TAG, "no video track in " + videoFile);
                return false;
            }

            audioExtractor.setDataSource(audioFile.getAbsolutePath());
            int audioTrack = selectTrack(audioExtractor, "audio/");
            if (audioTrack < 0) {
                Log.w(TAG, "no audio track in " + audioFile);
                return false;
            }

            muxer = new MediaMuxer(outFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoExtractor.selectTrack(videoTrack);
            MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrack);
            int muxVideo = muxer.addTrack(videoFormat);

            audioExtractor.selectTrack(audioTrack);
            MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrack);
            int muxAudio = muxer.addTrack(audioFormat);

            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();

            muxer.start();
            writeTrack(videoExtractor, muxer, muxVideo, buffer, info);
            writeTrack(audioExtractor, muxer, muxAudio, buffer, info);
            muxer.stop();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "mux failed: " + t);
            if (outFile.exists()) {
                outFile.delete();
            }
            return false;
        } finally {
            try {
                videoExtractor.release();
            } catch (Throwable ignored) {
            }
            try {
                audioExtractor.release();
            } catch (Throwable ignored) {
            }
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static int selectTrack(MediaExtractor extractor, String mimePrefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimePrefix)) {
                return i;
            }
        }
        return -1;
    }

    private static void writeTrack(MediaExtractor extractor, MediaMuxer muxer,
            int muxTrack, ByteBuffer buffer, android.media.MediaCodec.BufferInfo info) {
        while (true) {
            int index = extractor.getSampleTrackIndex();
            if (index < 0) {
                break;
            }
            long pts = extractor.getSampleTime();
            int flags = extractor.getSampleFlags();
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) {
                break;
            }
            info.set(0, size, pts, sampleFlagsToCodec(flags));
            muxer.writeSampleData(muxTrack, buffer, info);
            extractor.advance();
        }
    }

    private static int sampleFlagsToCodec(int extractorFlags) {
        // MediaExtractor.SAMPLE_FLAG_SYNC == MediaCodec.BUFFER_FLAG_KEY_FRAME == 1
        return (extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                ? android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
    }
}
