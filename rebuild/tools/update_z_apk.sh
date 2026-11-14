#!/system/bin/sh
# Replace the z overlay APK with the freshly built one (it loads AFTER
# XTCSystemUI_HS, so it must carry the same build or it shadows the module).
set -e
BB=/data/adb/magisk/busybox
ZAPK=/data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk
SRC=/sdcard/XTCSystemUI-v82.apk

echo "== before =="
md5sum "$ZAPK" 2>&1

echo "== clear immutable =="
"$BB" chattr -R -i /data/adb/modules/z 2>/dev/null || true

echo "== copy =="
cat "$SRC" > "$ZAPK"
chmod 644 "$ZAPK"
md5sum "$ZAPK"

echo "== relock =="
"$BB" chattr +i "$ZAPK" 2>/dev/null || true
echo "== done =="
