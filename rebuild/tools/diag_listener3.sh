#!/system/bin/sh
echo "=== our permissions: media/notification ==="
dumpsys package com.android.systemui 2>/dev/null | grep -iE "MEDIA_CONTENT_CONTROL|BIND_NOTIFICATION_LISTENER" | head -5
echo "=== notification service bindings ==="
dumpsys notification 2>/dev/null | sed -n '/Notification listeners/,/^$/p' | head -20
echo "=== dumpsys notification head ==="
dumpsys notification 2>/dev/null | head -25
echo "=== our service running? ==="
ps -A 2>/dev/null | grep -i systemui
