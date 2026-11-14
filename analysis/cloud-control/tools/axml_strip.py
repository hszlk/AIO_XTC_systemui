#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Binary AndroidManifest.xml (AXML) surgery for the cloud-control removal build.

Removes whole manifest elements (component declarations / meta-data /
uses-permission) whose android:name attribute matches a target.  The AXML
string pool and resource map are left untouched - removed names simply become
unreferenced - so every surviving resource id and attribute keeps its original
encoding.  Only the outer chunk size is recomputed.

Usage:
  py axml_strip.py <in-manifest.xml> <out-manifest.xml> [--targets targets.json]

targets.json format: {"service": ["..."], "receiver": ["..."], "provider": ["..."],
                      "meta-data": ["..."], "uses-permission": ["..."]}
"""
import json
import struct
import sys

CHUNK_XML = 0x0003
CHUNK_STRING_POOL = 0x0001
CHUNK_RESOURCE_MAP = 0x0180

RES_XML_START_NAMESPACE = 0x0100
RES_XML_END_NAMESPACE = 0x0101
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
RES_XML_CDATA = 0x0104

TYPE_STRING = 0x03


class StringPool:
    def __init__(self, data, base):
        (self.type, self.header_size, self.size) = struct.unpack_from("<HHI", data, base)
        self.count, self.style_count, self.flags, strings_start, styles_start = \
            struct.unpack_from("<IIIII", data, base + 8)
        self.utf8 = bool(self.flags & 0x100)
        self.offsets = struct.unpack_from("<%dI" % self.count, data, base + self.header_size)
        self.base = base
        self.raw = data
        self.strings_start = strings_start
        self.cache = {}

    def get(self, idx):
        if idx == 0xFFFFFFFF:
            return None
        if idx in self.cache:
            return self.cache[idx]
        off = self.base + self.strings_start + self.offsets[idx]
        raw = self.raw
        if self.utf8:
            # utf16 length (1-2 bytes), then utf8 length (1-2 bytes), then bytes
            p = off
            n = raw[p]
            p += 1
            if n & 0x80:
                p += 1
            n = raw[p]
            p += 1
            if n & 0x80:
                n = ((n & 0x7F) << 8) | raw[p]
                p += 1
            s = raw[p:p + n].decode("utf-8", "replace")
        else:
            p = off
            n = struct.unpack_from("<H", raw, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", raw, p)[0]
                p += 2
            s = raw[p:p + n * 2].decode("utf-16-le", "replace")
        self.cache[idx] = s
        return s


def iter_chunks(data):
    """Yield (type, header_size, size, offset) for every chunk from offset 8 on."""
    pos = 8
    total = len(data)
    while pos + 8 <= total:
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if csize < 8 or pos + csize > total:
            break
        yield ctype, hsize, csize, pos
        pos += csize


def start_element(data, off, hsize, pool):
    line, comment, ns, name = struct.unpack_from("<IIII", data, off + 8)
    attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, off + 24)
    attrs = []
    apos = off + 16 + attr_start
    for i in range(attr_count):
        ans, aname, raw_value = struct.unpack_from("<III", data, apos)
        tsize, res0, dtype, tdata = struct.unpack_from("<HBBI", data, apos + 12)
        attrs.append({
            "ns": pool.get(ans),
            "name": pool.get(aname),
            "raw": pool.get(raw_value) if raw_value != 0xFFFFFFFF else None,
            "type": dtype,
            "data": tdata,
            "string_value": pool.get(tdata) if dtype == TYPE_STRING else None,
        })
        apos += attr_size or 20
    return {"line": line, "ns": pool.get(ns), "name": pool.get(name), "attrs": attrs}


def strip(data, targets):
    pool = None
    head_chunks = []
    node_chunks = []
    chunks = list(iter_chunks(data))
    for ctype, hsize, csize, off in chunks:
        if ctype == CHUNK_STRING_POOL:
            pool = StringPool(data, off)
            head_chunks.append(off)
        elif ctype == CHUNK_RESOURCE_MAP:
            head_chunks.append(off)
        else:
            node_chunks.append((ctype, hsize, csize, off))
    if pool is None:
        raise SystemExit("no string pool")

    removed = []
    out_nodes = []
    skip = 0
    for ctype, hsize, csize, off in node_chunks:
        keep = True
        if ctype == RES_XML_START_ELEMENT:
            if skip > 0:
                skip += 1
                keep = False
            else:
                el = start_element(data, off, hsize, pool)
                name_attr = None
                for a in el["attrs"]:
                    if a["name"] == "name" and a["string_value"] is not None:
                        name_attr = a["string_value"]
                        break
                wanted = targets.get(el["name"], [])
                if name_attr is not None and name_attr in wanted:
                    skip = 1
                    keep = False
                    removed.append((el["name"], name_attr))
        elif ctype == RES_XML_END_ELEMENT:
            if skip > 0:
                skip -= 1
                keep = False
        elif ctype in (RES_XML_CDATA, RES_XML_START_NAMESPACE, RES_XML_END_NAMESPACE):
            if skip > 0:
                keep = False
        if keep:
            out_nodes.append(off)

    out = bytearray()
    out += data[0:8]                                # file header, size patched below
    for off in head_chunks:
        ctype, hsize, csize = struct.unpack_from("<HHI", data, off)
        out += data[off:off + csize]
    for off in out_nodes:
        ctype, hsize, csize = struct.unpack_from("<HHI", data, off)
        out += data[off:off + csize]
    struct.pack_into("<I", out, 4, len(out))
    return bytes(out), removed


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    targets = {}
    if len(sys.argv) >= 5 and sys.argv[3] == "--targets":
        targets = json.load(open(sys.argv[4], encoding="utf-8"))
    data = open(src, "rb").read()
    if struct.unpack_from("<H", data, 0)[0] != CHUNK_XML:
        raise SystemExit("not an AXML manifest")
    out, removed = strip(data, targets)
    open(dst, "wb").write(out)
    print("stripped %d element(s):" % len(removed))
    for kind, name in removed:
        print("  - <%s android:name=\"%s\">" % (kind, name))
    print("manifest: %d -> %d bytes" % (len(data), len(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
