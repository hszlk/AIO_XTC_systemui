#!/system/bin/sh
echo "=== listener logs ==="
logcat -d -v brief 2>/dev/null | grep -i "XtcNotificationListener" | tail -8
echo "=== NowPlaying logs ==="
logcat -d -v brief 2>/dev/null | grep -i "NowPlaying" | tail -10
echo "=== sessions ==="
dumpsys media_session 2>/dev/null | grep -A6 "Sessions Stack"
echo "=== enabled listeners ==="
settings get secure enabled_notification_listeners
echo "=== karaoke notification extras ==="
dumpsys notification 2>/dev/null | grep -A25 "pkg=com.tme.karaokewatch" | head -40
