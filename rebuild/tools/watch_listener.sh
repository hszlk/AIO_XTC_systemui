#!/system/bin/sh
C=com.android.systemui/com.xtc.statusbar.XtcNotificationListenerService
logcat -c
settings put secure enabled_notification_listeners "$C"
echo "put done, value=$(settings get secure enabled_notification_listeners)"
sleep 6
echo "after 6s, value=$(settings get secure enabled_notification_listeners)"
echo "=== logcat since put ==="
logcat -d -v time 2>/dev/null | grep -iE "XtcNotificationListener|enabled_notification|NotificationListener|systemui" | tail -40
