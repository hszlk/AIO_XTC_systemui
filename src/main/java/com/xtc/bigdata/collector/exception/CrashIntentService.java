package com.xtc.bigdata.collector.exception;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/**
 * Crash-report intent service (declared in AndroidManifest.xml,
 * exported=false, no intent filter).
 *
 * Original APK logic: collected the packaged crash report written by the
 * crash handler and shipped it to the vendor bigdata sink
 * (content://com.xtc.i3launcher.provider/...), i.e. the crash-upload stage of
 * the reporting stack.
 *
 * Reimplementation decision: REMOVED (crash reporting). The class stays as an
 * inert placeholder for the parity build so the manifest declaration can never
 * raise ClassNotFoundException; it performs no work at all. The strict
 * cloud-removal build drops both this class and its manifest declaration.
 */
public class CrashIntentService extends IntentService {

    private static final String TAG = "CrashIntentService";

    public CrashIntentService() {
        super("CrashIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "crash reporting removed in reimplementation; ignoring " + intent);
    }
}
