package com.android.systemui.screenrecord;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.android.systemui.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * File-system side of screen recording: temp cache directory, space checks,
 * final export to the public gallery and FileProvider sharing.
 *
 * Original APK logic (screenrecord.d / ScreenMediaRecorder.a): temp files lived
 * in {@code getExternalCacheDir()/ScreenRecord}, the final video was inserted in
 * the XTC album database ({@code com.xtc.data.b.b}) and the {@code .mp4} suffix
 * came from {@code com.xtc.data.d.a.k}. This reimplementation keeps the same
 * temp directory layout but exports through the framework: the final clip is
 * copied to {@code Movies/ScreenRecord}, indexed with
 * {@link MediaScannerConnection} (this is the public-API replacement for the
 * vendor album DB) and a same-named copy is dropped in the app cache so the
 * already declared FileProvider authority can hand out a share uri.
 *
 * No cloud component, no vendor database and no telemetry is involved.
 */
public final class RecordingStorageHelper {

    private static final String TAG = "RecordingStorage";

    /** Free space required before a recording may start (original default: 100 MB). */
    public static final long STORAGE_REQUIRED_MB = 100L;

    /** Final clip file name: date + time (user spec), e.g. 20261110_231605.mp4 */
    public static final String FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";
    public static final String FILE_SUFFIX = ".mp4";
    public static final String MIME_TYPE = "video/mp4";

    /** Authority declared in AndroidManifest.xml for androidx FileProvider. */
    public static final String FILE_PROVIDER_AUTHORITY = "com.android.systemui.fileprovider";

    private static final String CACHE_SUBDIR = "ScreenRecord";
    /** User spec: recordings go to DCIM/Video (not Movies/ScreenRecord). */
    private static final String ALBUM_SUBDIR = "Video";
    /**
     * Share-root of the declared FileProvider: res/xml/fileprovider declares
     * exactly {@code <cache-path name="leak" path="leak/"/>}, so only files under
     * {@code cache/leak/} can be handed out as content:// uris.
     */
    private static final String SHARE_SUBDIR = "leak";
    private static final int COPY_BUFFER = 8 * 1024;

    /** Result of exporting a finished recording. */
    public static final class SaveResult {
        /** Saved clip (gallery copy when possible, cache copy otherwise). */
        public final File file;
        /** content:// uri backed by the declared FileProvider; may be null. */
        public final Uri shareUri;
        /** Clip duration reported by MediaMetadataRetriever, in milliseconds. */
        public final long durationMs;

        SaveResult(File file, Uri shareUri, long durationMs) {
            this.file = file;
            this.shareUri = shareUri;
            this.durationMs = durationMs;
        }

        public boolean isValid() {
            return file != null && file.exists() && file.length() > 0 && durationMs > 0;
        }
    }

    private RecordingStorageHelper() {
    }

    /** Temp directory: external cache / ScreenRecord (created on demand). */
    public static File getCacheDir(Context context) {
        File base = context.getExternalCacheDir();
        if (base == null) {
            base = context.getCacheDir();
        }
        if (base == null) {
            return null;
        }
        File dir = new File(base, CACHE_SUBDIR);
        if (dir.isDirectory()) {
            return dir;
        }
        return dir.mkdirs() || dir.isDirectory() ? dir : null;
    }

    /** Deletes leftovers of previous (interrupted) sessions. */
    public static void cleanup(Context context) {
        File dir = getCacheDir(context);
        if (dir == null) {
            return;
        }
        File[] stale = dir.listFiles();
        if (stale == null) {
            return;
        }
        for (File file : stale) {
            if (!file.delete()) {
                Log.w(TAG, "cleanup: unable to delete " + file);
            }
        }
    }

    /** True when the cache volume has at least {@link #STORAGE_REQUIRED_MB} free. */
    public static boolean hasEnoughSpace(Context context) {
        File dir = getCacheDir(context);
        if (dir == null) {
            return false;
        }
        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long availableBytes = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
            return availableBytes >= STORAGE_REQUIRED_MB * 1024L * 1024L;
        } catch (Throwable t) {
            Log.w(TAG, "hasEnoughSpace: unable to stat " + dir, t);
            // Fail open: a stat failure must not permanently disable recording.
            return true;
        }
    }

    /** Reads the clip duration; returns 0 when the file is not a playable mp4. */
    public static long readDurationMs(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value == null) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (Throwable t) {
            Log.w(TAG, "readDurationMs: " + file, t);
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
                // nothing to do
            }
        }
    }

    /**
     * Exports a finished temp recording: public gallery copy + FileProvider
     * cache copy, then removes the temp file. Returns null when the clip is not
     * a valid recording.
     */
    public static SaveResult save(Context context, File temp) {
        if (temp == null || !temp.exists() || temp.length() <= 0) {
            Log.w(TAG, "save: empty temp file, nothing to save");
            return null;
        }
        long durationMs = readDurationMs(temp);
        if (durationMs <= 0) {
            Log.w(TAG, "save: invalid duration, dropping " + temp);
            return null;
        }

        String name = new java.text.SimpleDateFormat(FILE_TIMESTAMP_PATTERN,
                java.util.Locale.US).format(new java.util.Date()) + FILE_SUFFIX;

        // 1) share copy under cache/leak/ - the only root the declared
        //    FileProvider exposes (see res/xml/fileprovider and the rebuilt
        //    androidx.core.content.FileProvider path strategy).
        File shareDir = getShareDir(context);
        File cacheCopy = shareDir == null ? null : new File(shareDir, name);
        boolean cacheOk = cacheCopy != null && copyFile(temp, cacheCopy);

        // 2) public gallery copy, indexed by the media scanner.
        File albumCopy = null;
        try {
            File movies = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM);
            File dir = new File(movies, "Video");
            File parent = dir.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                Log.w(TAG, "save: cannot create " + parent);
            }
            if (dir.isDirectory() || dir.mkdirs()) {
                File target = new File(dir, name);
                if (copyFile(temp, target)) {
                    albumCopy = target;
                    MediaScannerConnection.scanFile(context,
                            new String[]{target.getAbsolutePath()},
                            new String[]{MIME_TYPE}, null);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "save: gallery export failed", t);
        }

        File result = albumCopy;
        if (result == null) {
            result = cacheOk && cacheCopy.exists() ? cacheCopy : null;
        }
        Uri shareUri = null;
        if (cacheOk && cacheCopy.exists()) {
            try {
                shareUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, cacheCopy);
            } catch (Throwable t) {
                Log.w(TAG, "save: unable to build FileProvider uri", t);
            }
        }

        if (!temp.equals(result)) {
            if (!temp.delete()) {
                Log.w(TAG, "save: unable to delete temp " + temp);
            }
        }
        Log.d(TAG, "save: result=" + result + " durationMs=" + durationMs + " uri=" + shareUri);
        return new SaveResult(result, shareUri, durationMs);
    }

    /** Share directory exposed by the FileProvider ({@code cache/leak/}). */
    public static File getShareDir(Context context) {
        File cache = context.getCacheDir();
        if (cache == null) {
            return null;
        }
        File dir = new File(cache, SHARE_SUBDIR);
        if (dir.isDirectory()) {
            return dir;
        }
        return dir.mkdirs() || dir.isDirectory() ? dir : null;
    }

    private static boolean copyFile(File source, File target) {
        if (source == null || target == null) {
            return false;
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "copyFile: " + source + " -> " + target, e);
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "copyFile: unable to remove partial " + target);
            }
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
