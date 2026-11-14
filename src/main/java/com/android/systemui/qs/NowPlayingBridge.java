package com.android.systemui.qs;

/**
 * Process-internal bridge between the notification listener service
 * ({@code com.xtc.statusbar.XtcNotificationListenerService}) and the
 * now-playing card controller.
 *
 * The listener lives in the com.xtc.statusbar package (same APK / same process),
 * the UI lives in com.android.systemui.qs; a tiny static bridge keeps the two
 * decoupled and avoids exporting anything.
 *
 * The latest media notification is cached so a controller that attaches after
 * the notification was posted (panel created later / service reconnect) still
 * gets the current media state immediately.
 */
public final class NowPlayingBridge {

    public interface Listener {
        void onMediaNotification(Object notification, String key);

        void onMediaNotificationRemoved(String key);
    }

    private static Listener sListener;
    private static Object sLastNotification;
    private static String sLastKey;

    private NowPlayingBridge() {
    }

    public static void setListener(Listener listener) {
        sListener = listener;
        if (listener != null && sLastNotification != null) {
            listener.onMediaNotification(sLastNotification, sLastKey);
        }
    }

    public static void onNotificationPosted(Object notification, String key) {
        sLastNotification = notification;
        sLastKey = key;
        Listener l = sListener;
        if (l != null) {
            l.onMediaNotification(notification, key);
        }
    }

    public static void onNotificationRemoved(String key) {
        if (key != null && key.equals(sLastKey)) {
            sLastNotification = null;
            sLastKey = null;
        }
        Listener l = sListener;
        if (l != null) {
            l.onMediaNotificationRemoved(key);
        }
    }

    /** Re-delivers the cached media notification to the current listener. */
    public static void replay() {
        Listener l = sListener;
        if (l != null && sLastNotification != null) {
            l.onMediaNotification(sLastNotification, sLastKey);
        }
    }
}
