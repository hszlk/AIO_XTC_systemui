#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""No-cloud / no-network gate with an explicit, auditable whitelist.

This is the *gate* used by rebuild\\tools\\verify.ps1. It deliberately does not
re-implement the scanning rules: it loads the lead's
analysis\\cloud-control\\tools\\check_nocloud.py as a module and reuses its
BANNED_CLASS_PREFIXES / BANNED_STRINGS / CLASS_WHITELIST tables and its dex
parsers, so the two tools can never drift apart.

Modes
  strict  no whitelist at all: any cloud class, cloud string or java.net class
          makes the gate FAIL (the strict variant must have zero residue).
  parity  the parity variant keeps four inert placeholder classes on purpose so
          that the manifest component surface stays identical to the vendor APK.
          Those four classes (and the handful of string constants that live
          inside them) are whitelisted *by exact value* and listed individually
          in the report; anything else still FAILs.

Usage:
  py nocloud_gate.py <apk> --checker <check_nocloud.py> --mode parity|strict
                      [--json out.json]

Exit: 0 = PASS, 1 = unexpected residue, 2 = usage/IO error.
"""
import argparse
import importlib.util
import json
import sys
import zipfile

# --- parity whitelist -------------------------------------------------------
# Four no-op placeholder classes that the parity variant MUST keep compiled so
# that every manifest component resolves (16/16) exactly like the vendor APK.
PARITY_ALLOWED_CLASSES = [
    "Lcom/xtc/xtcoco/CodeCoverageReceiver;",
    "Lcom/xtc/bigdata/monitor/receiver/MonitorStartReceiver;",
    "Lcom/xtc/dns/provider/HttpDnsContentProvider;",
    "Lcom/xtc/bigdata/collector/exception/CrashIntentService;",
]

# Exact string constants that only exist inside those four placeholders.
PARITY_ALLOWED_STRINGS = {
    "Lcom/xtc/xtcoco/CodeCoverageReceiver;":
        "class descriptor of the no-op coverage receiver placeholder",
    "Lcom/xtc/bigdata/monitor/receiver/MonitorStartReceiver;":
        "class descriptor of the no-op monitor receiver placeholder",
    "Lcom/xtc/dns/provider/HttpDnsContentProvider;":
        "class descriptor of the no-op httpdns provider placeholder",
    "Lcom/xtc/bigdata/collector/exception/CrashIntentService;":
        "class descriptor of the no-op crash-report service placeholder",
    "HttpDnsContentProvider.java":
        "SourceFile attribute of the no-op httpdns provider placeholder",
    "HttpDnsProvider":
        "log TAG constant inside the no-op httpdns provider placeholder",
    "httpdns provider disabled (cloud DNS removed); callers fall back to system resolver":
        "log message inside the no-op httpdns provider placeholder (states the cloud path is gone)",
    # Audited functional fetch (W46): NowPlayingController downloads the album
    # art URI published by the player (content:// or http(s) cover links, e.g.
    # NetEase online covers). Purely user-facing artwork display - NOT cloud
    # control, NOT telemetry. The gate still rejects every other URL string,
    # any okhttp/cloud SDK class, and any defined java.net class.
    "Ljava/net/HttpURLConnection;":
        "cover-art fetch in NowPlayingController (album art display, no telemetry)",
    "Ljava/net/URLConnection;":
        "superclass descriptor referenced by the cover-art HttpURLConnection",
    "http://":
        "scheme prefix test for cover-art URIs in NowPlayingController",
    "https://":
        "scheme prefix test for cover-art URIs in NowPlayingController",
}


def load_checker(path):
    spec = importlib.util.spec_from_file_location("check_nocloud", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load checker module: %s" % path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--checker", required=True, help="path to check_nocloud.py")
    ap.add_argument("--mode", choices=["parity", "strict"], required=True)
    ap.add_argument("--json", dest="json_out")
    args = ap.parse_args()

    try:
        checker = load_checker(args.checker)
        with zipfile.ZipFile(args.apk) as z:
            dex = z.read("classes.dex")
    except Exception as exc:  # noqa: BLE001
        print("FAIL: cannot read classes.dex from %s: %s" % (args.apk, exc))
        return 2

    classes = checker.dex_classes(dex)
    strings = checker.dex_strings(dex)

    class_hits = []
    for c in classes:
        if c in checker.CLASS_WHITELIST:
            continue
        if any(c.startswith(p) for p in checker.BANNED_CLASS_PREFIXES):
            class_hits.append(c)

    string_hits = []
    for s in strings:
        pats = [p for p in checker.BANNED_STRINGS if p in s]
        if pats:
            string_hits.append({"string": s, "patterns": sorted(set(pats))})

    network_classes = [c for c in classes if c.startswith("Ljava/net/")]
    network_classes += [c for c in classes
                        if ("HttpURLConnection" in c or "OkHttp" in c or "SSL" in c)]

    allowed_class_set = set(PARITY_ALLOWED_CLASSES) if args.mode == "parity" else set()
    allowed_string_map = PARITY_ALLOWED_STRINGS if args.mode == "parity" else {}

    class_allowed, class_unexpected = [], []
    for c in class_hits:
        (class_allowed if c in allowed_class_set else class_unexpected).append(c)

    string_allowed, string_unexpected = [], []
    for hit in string_hits:
        if hit["string"] in allowed_string_map:
            string_allowed.append({"string": hit["string"],
                                   "patterns": hit["patterns"],
                                   "reason": allowed_string_map[hit["string"]]})
        else:
            string_unexpected.append(hit)

    ok = not class_unexpected and not string_unexpected and not network_classes

    result = {
        "apk": args.apk,
        "mode": args.mode,
        "checker": args.checker,
        "dex_bytes": len(dex),
        "dex_classes": len(classes),
        "rules": {
            "banned_class_prefixes": len(checker.BANNED_CLASS_PREFIXES),
            "banned_strings": len(checker.BANNED_STRINGS),
        },
        "class_hits": sorted(class_hits),
        "class_hits_allowed": sorted(class_allowed),
        "class_hits_unexpected": sorted(class_unexpected),
        "string_hits": string_hits,
        "string_hits_allowed": string_allowed,
        "string_hits_unexpected": string_unexpected,
        "network_classes": sorted(set(network_classes)),
        "verdict": "PASS" if ok else "FAIL",
    }

    print("no-cloud gate mode=%s" % args.mode)
    print("apk: %s" % args.apk)
    print("classes.dex: %d bytes, %d classes, rules %d class-prefix + %d string"
          % (result["dex_bytes"], result["dex_classes"],
             result["rules"]["banned_class_prefixes"], result["rules"]["banned_strings"]))
    print("cloud classes hit: %d (allowed %d, unexpected %d)"
          % (len(class_hits), len(class_allowed), len(class_unexpected)))
    for c in class_allowed:
        print("  ALLOWED  %s" % c)
    for c in class_unexpected:
        print("  REJECTED %s" % c)
    print("cloud strings hit: %d (allowed %d, unexpected %d)"
          % (len(string_hits), len(string_allowed), len(string_unexpected)))
    for hit in string_allowed:
        print("  ALLOWED  [%s] %s" % (",".join(hit["patterns"]), hit["string"]))
    for hit in string_unexpected:
        print("  REJECTED [%s] %s" % (",".join(hit["patterns"]), hit["string"]))
    print("network classes: %d" % len(result["network_classes"]))
    for c in result["network_classes"]:
        print("  REJECTED %s" % c)
    print("VERDICT: %s" % result["verdict"])

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(result, fh, ensure_ascii=False, indent=2, sort_keys=True)
            fh.write("\n")
        print("json -> %s" % args.json_out)

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
