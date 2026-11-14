#!/system/bin/sh
C=com.android.systemui/com.xtc.statusbar.XtcNotificationListenerService
echo "=== current ==="
settings get secure enabled_notification_listeners
echo "=== put ==="
settings put secure enabled_notification_listeners "$C"
sleep 1
echo "read1: $(settings get secure enabled_notification_listeners)"
echo "=== listeners right after ==="
dumpsys notification 2>/dev/null | grep -A6 "Notification listeners:" | head -12
echo "=== NMS errors (last 30) ==="
logcat -d -v brief 2>/dev/null | grep -iE "NotificationManagerService|NotificationListeners" | tail -30
sleep 4
echo "read2: $(settings get secure enabled_notification_listeners)"
