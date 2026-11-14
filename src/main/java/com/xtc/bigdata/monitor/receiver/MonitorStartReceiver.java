package com.xtc.bigdata.monitor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Watchdog / monitor bootstrap receiver (declared exported, priority 100000,
 * actions "com.xtc.watchdogs" and "com.xtc.launcher.ACTION_CHANGE_REGION").
 *
 * Original APK logic: armed the bigdata monitor stack (watchdog heartbeats,
 * Dropbox/crash scanning, region switch handling, log upload scheduling). This
 * is the remote-monitoring / reporting arm of the original app.
 *
 * Reimplementation decision: REMOVED (cloud monitoring + reporting). The
 * broadcast entry point is kept as a no-op so the external watchdog broadcasts
 * cannot start any monitoring or network activity, while the manifest still
 * advertises the same component (APK information parity).
 */
public class MonitorStartReceiver extends BroadcastReceiver {

    private static final String TAG = "MonitorStartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "monitor/report stack removed in reimplementation; ignoring " + intent);
    }
}
