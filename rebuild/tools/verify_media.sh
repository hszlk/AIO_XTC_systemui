#!/system/bin/sh
echo "=== listeners setting ==="
settings get secure enabled_notification_listeners
echo "=== NowPlaying logs ==="
logcat -d -v brief 2>/dev/null | grep -i "NowPlaying" | tail -15
echo "=== listener logs ==="
logcat -d -v brief 2>/dev/null | grep -i "XtcNotificationListener" | tail -8
echo "=== karaoke notification still there? ==="
dumpsys notification 2>/dev/null | grep -c "pkg=com.tme.karaokewatch"
echo "=== expand panel + dump ==="
input keyevent KEYCODE_WAKEUP
sleep 0.5
input swipe 160 12 160 265 240
sleep 2
uiautomator dump /sdcard/ui_media.xml >/dev/null 2>&1
echo "dump done"
