#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read back every entry of an APK and verify its CRC-32.

aapt2 / zipalign / apksigner all tolerate a wrong entry CRC (the v2 signature
covers the uncompressed payload, not the zip CRC), so a broken CRC only shows up
later on a device or in tools such as `zipfile`. This check makes it a build
gate.

Usage: py zip_check.py <apk>
Exit:  0 = every entry readable with matching CRC, 1 = failure, 2 = unreadable zip
"""
import sys
import zipfile


def main():
    if len(sys.argv) < 2:
        print("usage: zip_check.py <apk>")
        return 2
    apk = sys.argv[1]
    try:
        z = zipfile.ZipFile(apk)
    except Exception as exc:  # noqa: BLE001 - report any zip parse failure
        print("apk: %s" % apk)
        print("FAIL: cannot open as zip: %s" % exc)
        return 2

    infos = z.infolist()
    bad = []
    for info in infos:
        try:
            z.read(info.filename)
        except Exception as exc:  # noqa: BLE001
            bad.append((info.filename, str(exc)))

    print("apk: %s" % apk)
    print("entries: %d" % len(infos))
    if bad:
        print("FAIL: %d entr(y/ies) failed CRC/read verification" % len(bad))
        for name, err in bad[:20]:
            print("  %s -> %s" % (name, err))
        return 1
    print("PASS: all %d entries read back with matching CRC-32" % len(infos))
    return 0


if __name__ == "__main__":
    sys.exit(main())
