#!/system/bin/sh
echo "=== NowPlaying / MediaSession logs ==="
logcat -d -v brief 2>/dev/null | grep -i "nowplaying" | tail -20
echo "=== media_session related system logs ==="
logcat -d -v brief 2>/dev/null | grep -i "getActiveSessions\|MEDIA_CONTENT_CONTROL\|notification listener" | tail -20
echo "=== audio playback configs ==="
dumpsys audio 2>/dev/null | grep -i "AudioPlaybackConfiguration\|player piid\|state:started" | head -20
echo "=== notification listeners setting ==="
settings get secure enabled_notification_listeners
echo "=== our listener component declared? ==="
dumpsys package com.android.systemui 2>/dev/null | grep -i -A2 "XtcNotificationListenerService" | head -10
