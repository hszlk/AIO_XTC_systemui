#!/system/bin/sh
sleep 0.3
PID=$(pidof com.android.systemui)
echo "before=$PID"
if [ -n "$PID" ]; then kill "$PID"; fi
sleep 6
echo "after=$(pidof com.android.systemui)"
echo "apk=$(md5sum /system/priv-app/XTCSystemUI/XTCSystemUI.apk | cut -d' ' -f1)"
echo "pkg=$(pm path com.android.systemui)"
