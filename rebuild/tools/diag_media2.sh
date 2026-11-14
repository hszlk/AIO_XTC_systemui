#!/system/bin/sh
echo "=== media sessions now ==="
dumpsys media_session 2>/dev/null | sed -n '1,40p'
echo "=== count of sessions ==="
dumpsys media_session 2>/dev/null | grep -c "Sessions Stack"
echo "=== notification listeners enabled ==="
settings get secure enabled_notification_listeners
echo "=== media notifications ==="
dumpsys notification 2>/dev/null | grep -i "pkg=" | grep -iE "music|netease|kugou|tme|karaoke|player" | head -10
echo "=== active audio players ==="
dumpsys audio 2>/dev/null | grep -B2 -A6 "started" | head -40
