package com.xtc.xtcoco;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Jacoco coverage trigger receiver (declared exported, action
 * "com.xtc.report.generate.ec").
 *
 * Original APK logic: generated code-coverage collector; on the broadcast it
 * dumped execution coverage and handed it to the bigdata reporting stack. This
 * is a pure reporting/telemetry entry point.
 *
 * Reimplementation decision: REMOVED (telemetry). The class and its manifest
 * declaration are kept so the package's declared component surface stays
 * identical to the original APK and any stray broadcast cannot crash the
 * process, but the receiver no longer collects or reports anything.
 */
public class CodeCoverageReceiver extends BroadcastReceiver {

    private static final String TAG = "CodeCoverageReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "coverage reporting removed in reimplementation; ignoring " + intent);
    }
}
