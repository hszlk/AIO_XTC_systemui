#!/system/bin/sh
C=com.android.systemui/com.xtc.statusbar.XtcNotificationListenerService
logcat -c
echo "=== allow_listener ==="
cmd notification allow_listener "$C"; echo "rc=$?"
sleep 3
echo "=== logcat ==="
logcat -d -v time 2>/dev/null | grep -iE "notification" | tail -40
echo "=== setting ==="
settings get secure enabled_notification_listeners
echo "=== dump ==="
dumpsys notification 2>/dev/null | sed -n '/Notification listeners/,/Snoozed/p' | head -12
