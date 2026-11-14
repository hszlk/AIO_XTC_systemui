#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static cloud-control / network residue scanner for the rebuilt APK.

Two independent checks on classes.dex:
  1. class descriptors: no class may live in the removed cloud/telemetry
     packages (HTTPlib, domain/route config, DNS strategy, bigdata, funlist,
     okhttp, retrofit, rx, kotlin, moshi, ...). The single deliberate exception
     is our own no-op com.xtc.dns.provider.HttpDnsContentProvider.
  2. raw string table: no cloud endpoint, domain or network API name may be
     present.  (The rebuilt app is framework-only and must not be able to talk
     to the network at all.)

Usage: py check_nocloud.py <apk> [--report out.md]
"""
import struct
import sys
import zipfile
import zlib


BANNED_CLASS_PREFIXES = [
    "Lcom/xtc/domain/", "Lcom/xtc/httplib/", "Lcom/xtc/funlist/",
    "Lcom/xtc/bigdata/", "Lcom/xtc/log/", "Lcom/xtc/switchs/",
    "Lcom/xtc/ultraframework/", "Lcom/xtc/ipc/", "Lcom/xtc/im/",
    "Lokhttp3/", "Lretrofit2/", "Lokio/", "Lrx/", "Lkotlin/", "Lkotlinx/",
    "Lorg/apache/http/", "Lcom/squareup/", "Lcom/google/protobuf/",
]
CLASS_WHITELIST = set()  # strict build compiles no cloud-package class at all

BANNED_STRINGS = [
    # cloud config / route downlink
    "route-service", "getRouteInfo", "route.okii.com", "route-sa.imoo.com",
    "watchda.eebbk.net", "watchda-eu.imoo.com",
    "okii.com", "imoo.com", "tiancaixing.com", "eebbk.net", "eebbk.com",
    # httpdns
    "httpdns", "HttpDns", "119.29.29.98",
    # reporting sinks
    "com.xtc.i3launcher.provider", "com.xtc.http.bigdata.provider",
    "HttpBigdataProvider", "bigdata", "watchdogs", "report.generate.ec",
    # protocol endpoints
    "wss://", "ws://", "https://", "http://",
    # network APIs
    "OkHttpClient", "HttpURLConnection", "URLConnection", "java.net.",
    "SSLSocket", "HttpsURLConnection",
    # remote switch / config cache
    "moduleSwitch", "funSwitch", "confupdate", "ConfUpdate",
]


def uleb(buf, off):
    r = 0
    s = 0
    while True:
        b = buf[off]
        off += 1
        r |= (b & 0x7F) << s
        if not (b & 0x80):
            return r, off
        s += 7


def dex_strings(dex):
    n, off = struct.unpack_from("<II", dex, 56)
    out = []
    for i in range(n):
        data_off = struct.unpack_from("<I", dex, off + 4 * i)[0]
        _, p = uleb(dex, data_off)          # utf16 length
        end = p
        while dex[end] != 0:
            end += 1
        out.append(dex[p:end].decode("utf-8", "replace"))
    return out


def dex_classes(dex):
    n_str = struct.unpack_from("<I", dex, 56)[0]
    str_off = struct.unpack_from("<I", dex, 60)[0]
    n_type = struct.unpack_from("<I", dex, 64)[0]
    type_off = struct.unpack_from("<I", dex, 68)[0]
    n_cls = struct.unpack_from("<I", dex, 96)[0]
    cls_off = struct.unpack_from("<I", dex, 100)[0]
    strs = dex_strings(dex)
    classes = []
    for i in range(n_cls):
        type_idx = struct.unpack_from("<I", dex, cls_off + i * 32)[0]
        desc_idx = struct.unpack_from("<I", dex, type_off + type_idx * 4)[0]
        classes.append(strs[desc_idx])
    return classes


def main():
    apk = sys.argv[1]
    report = None
    if len(sys.argv) >= 4 and sys.argv[2] == "--report":
        report = sys.argv[3]
    z = zipfile.ZipFile(apk)
    dex = z.read("classes.dex")
    print("apk: %s" % apk)
    print("classes.dex: %d bytes" % len(dex))

    classes = dex_classes(dex)
    banned = []
    for c in classes:
        if c in CLASS_WHITELIST:
            continue
        for p in BANNED_CLASS_PREFIXES:
            if c.startswith(p):
                banned.append(c)
                break
    strings = dex_strings(dex)
    hits = {}
    for s in strings:
        for pat in BANNED_STRINGS:
            if pat in s:
                hits.setdefault(pat, []).append(s)
    network_classes = [c for c in classes if c.startswith("Ljava/net/")]
    https_classes = [c for c in classes
                     if "HttpURLConnection" in c or "OkHttp" in c or "SSL" in c]

    lines = []
    lines.append("# 云控残留静态扫描报告\n")
    lines.append("- APK：`%s`" % apk)
    lines.append("- classes.dex：%d 字节，%d 个类" % (len(dex), len(classes)))
    lines.append("- 规则：类包名前缀黑名单 %d 条 + dex 字符串黑名单 %d 条\n" %
                 (len(BANNED_CLASS_PREFIXES), len(BANNED_STRINGS)))
    ok = True
    lines.append("## 1. 云控/上报/网络依赖类扫描\n")
    if banned:
        ok = False
        lines.append("**FAIL** — 命中 %d 个类：" % len(banned))
        for c in banned[:50]:
            lines.append("- `%s`" % c)
    else:
        lines.append("**PASS** — 0 命中（域名路由/HTTPlib/HTTPDNS/bigdata/okhttp/retrofit/rx/kotlin 均不存在）")
    lines.append("")
    if CLASS_WHITELIST:
        lines.append("白名单（刻意保留的离线空实现）：")
        for c in sorted(CLASS_WHITELIST):
            lines.append("- `%s`（无网络、无上报）" % c)
    lines.append("")
    lines.append("## 2. 云控/端点/网络 API 字符串扫描\n")
    if hits:
        ok = False
        lines.append("**FAIL** — 命中 %d 类特征：" % len(hits))
        for pat in sorted(hits):
            sample = hits[pat][:3]
            lines.append("- `%s` ×%d，例：%s" % (pat, len(hits[pat]),
                                                ", ".join("`%s`" % s for s in sample)))
    else:
        lines.append("**PASS** — 0 命中（无 http(s)/ws 端点、无域名、无上报 provider、无网络 API）")
    lines.append("")
    lines.append("## 3. 网络/加密类结构扫描\n")
    lines.append("- `Ljava/net/*` 类：%d" % len(network_classes))
    lines.append("- HTTP/OKHttp/SSL 相关类：%d" % len(https_classes))
    if network_classes or https_classes:
        ok = False
        for c in (network_classes + https_classes)[:20]:
            lines.append("  - `%s`" % c)
    lines.append("")
    lines.append("## 结论\n")
    lines.append("**%s**" % ("PASS — 云控/上报/网络能力静态残留为 0" if ok else "FAIL — 存在残留，需处理"))
    text = "\n".join(lines) + "\n"
    print(text)
    if report:
        open(report, "w", encoding="utf-8", newline="\n").write(text)
        print("report -> %s" % report)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
