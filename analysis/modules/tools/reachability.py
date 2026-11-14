#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Reachability analysis: which decompiled classes are actually used by the
SystemUI application code (com.android.systemui.* + com.xtc.statusbar.*)?

Roots = all classes in those two packages.
Edges = import statements between decompiled classes of this APK.
Outputs:
  analysis/modules/_reachable.csv     used classes (with area + removed flag)
  analysis/modules/_dead_sdk.csv      SDK classes never imported by the app code
  summary on stdout
"""
import csv
import os
import re
from collections import deque

JADX = r'D:\DEBUG\apk\sysui\work\jadx18b\sources'
OUT = r'D:\DEBUG\apk\sysui\analysis\modules'
APP_PREFIXES = ('com.android.systemui.', 'com.xtc.statusbar.')
REMOVED_PREFIXES = ('com.xtc.bigdata.', 'com.xtc.log.', 'com.xtc.dns.',
                    'com.xtc.domain.', 'com.xtc.httplib.', 'com.xtc.funlist.',
                    'com.xtc.xtcoco.')
DEAD_PREFIXES = ('com.xtc.client.', 'com.xtc.ipc.', 'com.xtc.im.')

AREAS = [
    ('com.android.systemui.', 'systemui'),
    ('com.xtc.statusbar.', 'xtc-statusbar'),
    ('com.xtc.ui.', 'xtc-ui'),
    ('com.xtc.architecture.', 'xtc-architecture'),
    ('com.xtc.baseservice.', 'xtc-baseservice'),
    ('com.xtc.bigdata.', 'xtc-bigdata'),
    ('com.xtc.client.', 'xtc-client-ble'),
    ('com.xtc.dns.', 'xtc-dns'),
    ('com.xtc.domain.', 'xtc-domain'),
    ('com.xtc.httplib.', 'xtc-httplib'),
    ('com.xtc.ipc.', 'xtc-ipc'),
    ('com.xtc.im.', 'xtc-im'),
    ('com.xtc.log.', 'xtc-log'),
    ('com.xtc.ultraframework.', 'xtc-ultraframework'),
    ('com.xtc.utils.', 'xtc-utils'),
    ('com.xtc.assistantapi.', 'xtc-assistantapi'),
    ('com.xtc.funlist.', 'xtc-funlist'),
    ('com.xtc.xtcoco.', 'xtc-xtcoco'),
    ('com.android.common.', 'xtc-common-lib'),
    ('com.android.settingslib.', 'settingslib'),
    ('androidx.', 'thirdparty'),
    ('kotlin.', 'thirdparty'),
    ('kotlinx.', 'thirdparty'),
    ('okhttp3.', 'thirdparty'),
    ('okio.', 'thirdparty'),
    ('retrofit2.', 'thirdparty'),
    ('rx.', 'thirdparty'),
    ('org.', 'thirdparty'),
]

IMPORT_RE = re.compile(r'^\s*import\s+(static\s+)?([\w.]+);', re.M)


def area_of(fq):
    for p, a in AREAS:
        if fq.startswith(p):
            return a
    return 'other'


def classify(fq):
    if fq.startswith(REMOVED_PREFIXES):
        return 'REMOVED'
    if fq.startswith(DEAD_PREFIXES):
        return 'DEAD?'
    return 'KEEP'


def main():
    classes = {}
    for dirpath, _, files in os.walk(JADX):
        for f in files:
            if not f.endswith('.java'):
                continue
            path = os.path.join(dirpath, f)
            rel = os.path.relpath(path, JADX).replace('\\', '/')
            fq = rel[:-5].replace('/', '.')
            try:
                txt = open(path, encoding='utf-8', errors='replace').read()
            except OSError:
                continue
            classes[fq] = set(m.group(2) for m in IMPORT_RE.finditer(txt))

    # resolve inner classes: import may point to outer class only
    roots = [c for c in classes if c.startswith(APP_PREFIXES)]
    seen = set()
    q = deque(roots)
    edges_from_app = set()
    while q:
        cur = q.popleft()
        if cur in seen:
            continue
        seen.add(cur)
        for imp in classes.get(cur, ()):
            # try exact, then prefix match (inner classes not imported explicitly)
            target = imp if imp in classes else None
            if target is None:
                for cand in classes:
                    if cand.startswith(imp + '$'):
                        target = cand
                        break
            if target is None:
                continue
            if target.startswith(APP_PREFIXES):
                edges_from_app.add(target)
            if target not in seen:
                q.append(target)

    reachable_app = sorted(c for c in seen if c.startswith(APP_PREFIXES))
    reachable_other = sorted(c for c in seen if not c.startswith(APP_PREFIXES))

    # used SDK classes (excluding third-party) that the app code transitively needs
    used_sdk = [c for c in reachable_other if area_of(c) not in ('thirdparty', 'other')]
    used_area_counts = {}
    for c in used_sdk:
        used_area_counts.setdefault(area_of(c), []).append(c)

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, '_reachable.csv'), 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f)
        w.writerow(['fqcn', 'area', 'classify'])
        for c in reachable_app:
            w.writerow([c, area_of(c), 'APP'])
        for c in used_sdk:
            w.writerow([c, area_of(c), classify(c)])

    dead_sdk = [c for c in classes
                if not c.startswith(APP_PREFIXES) and c not in seen
                and area_of(c) not in ('thirdparty', 'other')]
    with open(os.path.join(OUT, '_dead_sdk.csv'), 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f)
        w.writerow(['fqcn', 'area', 'classify'])
        for c in sorted(dead_sdk):
            w.writerow([c, area_of(c), classify(c)])

    print('app classes total        :', len(roots))
    print('app classes reachable    :', len(reachable_app))
    print('used SDK classes (trans) :', sum(len(v) for v in used_area_counts.values()))
    print('dead SDK classes         :', len(dead_sdk))
    print()
    print('%-22s %6s %6s %8s %8s' % ('area', 'used', 'dead', 'usedKB', 'deadKB'))
    areas = sorted(set([area_of(c) for c in classes if area_of(c) not in ('thirdparty', 'other')]))
    for a in areas:
        u = len(used_area_counts.get(a, []))
        d = len([c for c in dead_sdk if area_of(c) == a])
        ukb = sum(os.path.getsize(os.path.join(JADX, c.replace('.', '/') + '.java'))
                  for c in used_area_counts.get(a, []) if os.path.exists(
                      os.path.join(JADX, c.replace('.', '/') + '.java'))) / 1024.0
        dkb = sum(os.path.getsize(os.path.join(JADX, c.replace('.', '/') + '.java'))
                  for c in dead_sdk if area_of(c) == a and os.path.exists(
                      os.path.join(JADX, c.replace('.', '/') + '.java'))) / 1024.0
        if u or d:
            print('%-22s %6d %6d %8.1f %8.1f' % (a, u, d, ukb, dkb))
    print()
    for a, lst in sorted(used_area_counts.items()):
        removed = [c for c in lst if classify(c) == 'REMOVED']
        if removed:
            print('%s: %d used, of which REMOVED=%d' % (a, len(lst), len(removed)))


if __name__ == '__main__':
    main()
