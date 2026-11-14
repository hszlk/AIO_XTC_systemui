package com.xtc.statusbar;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.systemui.qs.NowPlayingBridge;

/**
 * Notification listener service (declared with BIND_NOTIFICATION_LISTENER_SERVICE).
 *
 * Original APK logic: receives the framework notification stream and forwards
 * it to the XTC status bar renderer (XtcStatusBarView) - the watch shows
 * notifications from WeChat/phone etc. through this path; music notifications
 * also fed the vendor music card (qs.tiles.music.MusicController).
 *
 * Reimplementation: same listener contract; notifications whose extras carry a
 * MediaSession token are forwarded to {@link NowPlayingBridge} so the pull-down
 * now-playing card can build a MediaController and show title / album art /
 * transport buttons. Everything else is ignored for now (no notification list
 * exists in this build - see analysis/modules/statusbar-notification/SPEC.md).
 */
public class XtcNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "XtcNotificationListener";

    @Override
    public void onListenerConnected() {
        Log.i(TAG, "notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        Log.i(TAG, "notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        android.os.Bundle extras = sbn.getNotification().extras;
        if (extras == null) {
            return;
        }
        // standard MediaStyle notification (has a session token) ...
        if (extras.containsKey("android.mediaSession")) {
            NowPlayingBridge.onNotificationPosted(sbn.getNotification(), sbn.getKey());
            return;
        }
        // ... or the vendor "custom music notification" protocol: players such as
        // com.tme.karaokewatch never publish a MediaSession, they only post a
        // notification whose extras carry SONG_NAME / SINGER_NAME / PLAY_STATE
        // and the transport PendingIntents.
        if (extras.containsKey("SONG_NAME")) {
            NowPlayingBridge.onNotificationPosted(sbn.getNotification(), sbn.getKey());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        android.os.Bundle extras = sbn.getNotification().extras;
        if (extras == null) {
            return;
        }
        if (extras.containsKey("android.mediaSession") || extras.containsKey("SONG_NAME")) {
            NowPlayingBridge.onNotificationRemoved(sbn.getKey());
        }
    }
}
