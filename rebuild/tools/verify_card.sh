#!/system/bin/sh
echo "=== audio playing? ==="
dumpsys audio 2>/dev/null | grep -A2 "state:started" | head -8
echo "=== expand panel ==="
input keyevent KEYCODE_WAKEUP
sleep 0.5
input swipe 160 12 160 270 240
sleep 3
echo "=== NowPlaying logs ==="
logcat -d -v brief 2>/dev/null | grep -i "NowPlaying" | tail -10
echo "=== dump UI ==="
uiautomator dump /sdcard/ui_m2.xml >/dev/null 2>&1
echo "dump size: $(wc -c < /sdcard/ui_m2.xml)"
