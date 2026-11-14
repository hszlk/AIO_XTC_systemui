package com.xtc.assistantapi.launch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.util.Log;

import com.android.systemui.R;
import com.xtc.assistantapi.AssistantIntentUtil;
import com.xtc.assistantapi.BaseDeviceModule;

import java.util.List;

/**
 * Foreground service base class of an assistant device module host.
 *
 * Original APK logic: {@code com.xtc.assistantapi.launch.AssistantForegroundService}
 * - creates the {@code assistant_service} notification channel and posts
 * {@code startForeground(100001)} before dispatching the directive intent in
 * {@code onStartCommand}. The channel id/name/importance and the notification id
 * are part of the runtime contract with the assistant app, so they are kept
 * verbatim.
 */
public abstract class AssistantForegroundService extends Service {

    private static final String TAG = "AssistantFgService";

    private static final int NOTIFICATION_ID = 100001;
    private static final String CHANNEL_ID = "assistant_service";
    private static final String CHANNEL_NAME = "xtc assistant";

    /** Modules hosted by this service; called on every directive intent. */
    public abstract List<? extends BaseDeviceModule> getDeviceModules();

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Throwable t) {
            Log.w(TAG, "unable to enter foreground state", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            AssistantIntentUtil.dispatch(this, intent, getDeviceModules());
        } catch (Throwable t) {
            Log.e(TAG, "directive dispatch crashed", t);
        }
        return START_STICKY;
    }

    private void createChannel() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        } catch (Throwable t) {
            Log.w(TAG, "unable to create assistant_service channel", t);
        }
    }

    private Notification buildNotification() {
        return new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quick_screen_record)
                .setContentTitle(CHANNEL_NAME)
                .setOngoing(true)
                .build();
    }
}
