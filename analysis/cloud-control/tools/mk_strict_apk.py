#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build the strict cloud-removal APK variant.

Mode A layout: take the vendor APK, replace the binary AndroidManifest.xml with
the stripped one, drop the stale v1 signature files, append the freshly built
classes.dex (from the standard build), preserve every other entry and its
compression method.

Usage:
  py mk_strict_apk.py <vendor.apk> <stripped-manifest.xml> <classes.dex> <out-unsigned.apk>
"""
import struct
import sys
import zipfile

SKIP = {"META-INF/CERT.SF", "META-INF/CERT.RSA", "META-INF/MANIFEST.MF"}
MANIFEST = "AndroidManifest.xml"
DEX = "classes.dex"


def main():
    vendor, manifest, dex, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    new_manifest = open(manifest, "rb").read()
    new_dex = open(dex, "rb").read()
    zin = zipfile.ZipFile(vendor, "r")
    zout = zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED)
    written = 0
    for info in zin.infolist():
        if info.filename in SKIP:
            continue
        data = zin.read(info.filename)
        if info.filename == MANIFEST:
            data = new_manifest
        # preserve the original compression method
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zi.internal_attr = info.internal_attr
        zi.create_system = info.create_system
        zout.writestr(zi, data)
        written += 1
    zi = zipfile.ZipInfo(DEX, date_time=(2009, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zout.writestr(zi, new_dex)
    written += 1
    zout.close()
    zin.close()
    print("wrote %s (%d entries, %d bytes)" % (out, written, len(open(out, "rb").read())))


if __name__ == "__main__":
    sys.exit(main())
