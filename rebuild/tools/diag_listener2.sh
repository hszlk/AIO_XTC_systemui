#!/system/bin/sh
echo "=== listener state ==="
logcat -d -v brief 2>/dev/null | grep -i "XtcNotificationListener" | tail -10
echo "=== NMS rebind ==="
logcat -d -v brief 2>/dev/null | grep -i "rebind\|NotificationListener" | tail -10
echo "=== sessions ==="
dumpsys media_session 2>/dev/null | grep -A4 "Sessions Stack"
echo "=== setting ==="
settings get secure enabled_notification_listeners
