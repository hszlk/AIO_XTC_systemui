#!/system/bin/sh
# Restart SystemUI so it re-reads /system/priv-app/XTCSystemUI/XTCSystemUI.apk
# (the file is a Magisk bind mount into the z module, already replaced).
PID=$(pidof com.android.systemui)
echo "before pid=$PID"
if [ -n "$PID" ]; then
  kill "$PID"
fi
sleep 5
NEW=$(pidof com.android.systemui)
echo "after pid=$NEW"
echo "apk md5:"
md5sum /system/priv-app/XTCSystemUI/XTCSystemUI.apk
