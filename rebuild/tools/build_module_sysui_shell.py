"""Rebuild the XTCSystemUI Magisk module using the user's original sysui.zip shell.

Original layout (C:\\Users\\10413\\Downloads\\sysui.zip):
    META-INF/com/google/android/{update-binary,updater-script}
    module.prop          (vendor identity: XTCPatch_SystemUI / 1.7.50_Ver3.7.1)
    customize.sh         (SDK 27 -> unzip 4100.zip, SDK 30 -> unzip 5100.zip, then pm install)
    version.txt          (3.7.1)
    4100.zip             (W4 payload: vendor APK + libmmkv + libxtcSecurity)
    5100.zip             (W5 payload: vendor APK + libs + screenrecord + audiopolicy + feature xml)

Rebuild rule: keep the shell and the W4/W5 split, swap the vendor 14.9 MB APK for our
clean-room v82 build (R8 hardened, md5 0e6170f7...) and set our module identity.
"""
import io
import os
import zipfile
import hashlib

SRC = r"C:\Users\10413\Downloads\sysui.zip"
OUT = r"D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-v82-sysui-shell.zip"
APK = r"D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-hardened.apk"

OUR_PROP = (
    "id=XTCSystemUI_HS\n"
    "name=XTCSystemUI Rebuild\n"
    "author=hszlk\n"
    "version=v82 (1.8.00 clean-room)\n"
    "versionCode=18000\n"
    "minMagisk=1600\n"
    "description=\u8e0f\u8fc7\u4e07\u6c34\u5343\u5c71\uff0c\u63a2\u7d22\u6c38\u4e0d\u6b62\u6b65\uff0c"
    "\u6b65\u5c65\u4e0d\u505c\uff0c\u89e6\u78b0\u65b0\u4e16\u754c\u5f00\u7aef\n"
).encode("utf-8")

OUTER_CUSTOMIZE = r"""#!/system/bin/sh
# XTCSystemUI Rebuild (hand-written 1.8.00 clean-room) - dual-device module
#   W4 = Android 8.1 (SDK 27) -> payload 4100.zip (no audio-capture files: the 8.1
#        audio policy on this device exposes no remote_submix loopback device)
#   W5 = Android 11 (SDK 30) -> payload 5100.zip (adds screen-record audio restore)
MODID=XTCSystemUI_HS

[ -d "/data/adb/modules/$MODID" ] && /data/adb/magisk/busybox chattr -R -i "/data/adb/modules/$MODID" 2>/dev/null

SDK_VERSION=$(getprop ro.build.version.sdk)

if [ "$SDK_VERSION" = "27" ]; then
  ui_print "- Android 8.1 (SDK 27): installing 4100 payload"
  unzip -o "$ZIPFILE" '4100.zip' -d "$MODPATH" >/dev/null 2>&1
  cd "$MODPATH" && unzip -o "4100.zip" >/dev/null 2>&1 && rm -f "4100.zip"
  rm -f "$MODPATH/5100.zip"
elif [ "$SDK_VERSION" = "30" ]; then
  ui_print "- Android 11 (SDK 30): installing 5100 payload (with audio restore)"
  unzip -o "$ZIPFILE" '5100.zip' -d "$MODPATH" >/dev/null 2>&1
  cd "$MODPATH" && unzip -o "5100.zip" >/dev/null 2>&1 && rm -f "5100.zip"
  rm -f "$MODPATH/4100.zip"
else
  ui_print "! Unsupported SDK: $SDK_VERSION"
  abort
fi

# DO NOT call `pm uninstall com.android.systemui` here!
# It was in the first shell build and it removed the system package for user 0
# once the /data/app update was gone (the watch lost SystemUI entirely and the
# debug-signed APK cannot be reinstalled into shared uid android.uid.system).
# The /data/app update is instead handled by the last-loading `z` overlay module,
# which already carries the same hand-written APK.

set_perm_recursive "$MODPATH" 0 0 0755 0644
[ -d "$MODPATH/system/bin" ] && set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
[ -d "$MODPATH/system/etc/permissions" ] && set_perm_recursive "$MODPATH/system/etc/permissions" 0 0 0755 0644
[ -d "$MODPATH/system/lib" ] && set_perm_recursive "$MODPATH/system/lib" 0 0 0755 0644
ui_print "- Install done. Reboot to apply."
"""

W5_HOOK = r"""#!/system/bin/sh

MODPATH=${0%/*}

set_perm_recursive $MODPATH/system/bin 0 0 0755 0755
set_perm_recursive $MODPATH/system/etc/permissions 0 0 0755 0644

ui_print "- ScreenRecord Audio restore completed!"
"""


def add(zf, name, data, mode=0o644):
    zi = zipfile.ZipInfo(name, date_time=(2026, 11, 14, 0, 30, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = (mode << 16)
    zf.writestr(zi, data)


def build_inner(src_zip, apk_bytes, extra_gate, hook=False):
    """Rebuild a payload zip: same entries, vendor APK replaced by ours."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for info in src_zip.infolist():
            name = info.filename
            if name.endswith("/"):
                continue
            if name == "system/priv-app/XTCSystemUI/XTCSystemUI.apk":
                add(zf, name, apk_bytes)
                continue
            if name == "module.prop":
                add(zf, name, OUR_PROP)
                continue
            if name == "customize.sh":
                # Dropped on purpose: it would be unzipped into $MODPATH and
                # overwrite the outer customize.sh that is still executing.
                # The outer script already applies the W5 permissions itself.
                continue
            add(zf, name, src_zip.read(name), 0o755 if name.endswith("update-binary") else 0o644)
    return buf.getvalue()


def main():
    with open(APK, "rb") as f:
        apk_bytes = f.read()
    apk_md5 = hashlib.md5(apk_bytes).hexdigest()

    src = zipfile.ZipFile(SRC)
    inner4 = zipfile.ZipFile(io.BytesIO(src.read("4100.zip")))
    inner5 = zipfile.ZipFile(io.BytesIO(src.read("5100.zip")))

    payload4 = build_inner(inner4, apk_bytes, "W4")
    payload5 = build_inner(inner5, apk_bytes, "W5", hook=True)

    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        add(zf, "META-INF/com/google/android/update-binary",
            src.read("META-INF/com/google/android/update-binary"), 0o755)
        add(zf, "META-INF/com/google/android/updater-script",
            src.read("META-INF/com/google/android/updater-script"))
        add(zf, "module.prop", OUR_PROP)
        add(zf, "customize.sh", OUTER_CUSTOMIZE.encode("utf-8"), 0o755)
        add(zf, "version.txt", b"v82")
        add(zf, "4100.zip", payload4)
        add(zf, "5100.zip", payload5)

    print("APK md5:", apk_md5, len(apk_bytes))
    print("payload 4100:", len(payload4), "payload 5100:", len(payload5))
    print("wrote:", OUT, os.path.getsize(OUT))

    with zipfile.ZipFile(OUT) as z:
        print("\nouter entries:", len(z.namelist()))
        for n in z.namelist():
            print("  %-46s %9d" % (n, z.getinfo(n).file_size))
        for tag in ("4100.zip", "5100.zip"):
            iz = zipfile.ZipFile(io.BytesIO(z.read(tag)))
            got = hashlib.md5(iz.read("system/priv-app/XTCSystemUI/XTCSystemUI.apk")).hexdigest()
            print("\n%s: %d entries, APK md5 %s" % (tag, len(iz.namelist()), got))
            for n in iz.namelist():
                print("     %-56s %9d" % (n, iz.getinfo(n).file_size))


if __name__ == "__main__":
    main()
