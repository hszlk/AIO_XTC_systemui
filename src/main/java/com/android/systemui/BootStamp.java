package com.android.systemui;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Personal boot stamp: every boot drops an empty marker file
 * {@code /sdcard/system_ui by hszlk} (no extension).
 *
 * Why a file: the mod author wants a visible, harmless footprint that the
 * rebuilt SystemUI has been installed.  The file is created once per boot from
 * a background thread (never on the main thread), is idempotent, and can never
 * crash the process - storage errors are swallowed on purpose.
 *
 * Permission: the vendor manifest already declares WRITE_EXTERNAL_STORAGE and
 * SystemUI runs as a privileged platform app, so /sdcard is writable on the
 * target Android 8.1 device.
 */
public final class BootStamp {

    /** Exact file name requested by the user (spaces included, no extension). */
    private static final String FILE_NAME = "system_ui by hszlk";

    private BootStamp() {
    }

    /** Drops the stamp in the background; safe to call repeatedly. */
    public static void ensure(Context context) {
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                writeStamp(appContext);
            }
        }, "systemui-boot-stamp").start();
    }

    private static void writeStamp(Context context) {
        try {
            File dir = Environment.getExternalStorageDirectory();
            if (dir == null || !dir.isDirectory()) {
                dir = new File("/sdcard");
            }
            if (!dir.exists()) {
                return; // storage not mounted yet (direct-boot stage) - boot receiver retries
            }
            File stamp = new File(dir, FILE_NAME);
            if (!stamp.exists()) {
                stamp.createNewFile();
            }
        } catch (Throwable ignored) {
            // never let the author stamp break SystemUI
        }
    }
}
