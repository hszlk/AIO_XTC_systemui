#!/system/bin/sh
echo "=== put BOTH launcher + ours ==="
settings put secure enabled_notification_listeners "com.xtc.i3launcher/com.xtc.systemui.statusbar.notification.NotificationListener:com.android.systemui/com.xtc.statusbar.XtcNotificationListenerService"
sleep 3
echo "value=$(settings get secure enabled_notification_listeners)"
echo "=== dump ==="
dumpsys notification 2>/dev/null | sed -n '/Notification listeners/,/Snoozed/p' | head -12
