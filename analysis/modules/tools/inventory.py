#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Class inventory for the XTCSystemUI 1.8.00 replication project.

Scans the decompiled sources (jadx18b), the handwritten source tree, and emits
a CSV + per-module statistics that drive the remaining-work analysis.

Module assignment is by package prefix; impl status by exact FQCN match between
the decompiled class and the handwritten tree (same package + class name).
"""
import csv
import os
import sys

JADX = r'D:\DEBUG\apk\sysui\work\jadx18b\sources'
SRC = r'D:\DEBUG\apk\sysui\src\main\java'
OUT_CSV = r'D:\DEBUG\apk\sysui\analysis\modules\_inventory.csv'

# top-level areas we care about (decompiled tree relative paths)
AREAS = {
    'com/android/systemui/': 'systemui',
    'com/xtc/statusbar/': 'xtc-statusbar',
    'com/xtc/ui/': 'xtc-ui',
    'com/xtc/architecture/': 'xtc-architecture',
    'com/xtc/baseservice/': 'xtc-baseservice',
    'com/xtc/bigdata/': 'xtc-bigdata',
    'com/xtc/client/': 'xtc-client-ble',
    'com/xtc/dns/': 'xtc-dns',
    'com/xtc/domain/': 'xtc-domain',
    'com/xtc/httplib/': 'xtc-httplib',
    'com/xtc/ipc/': 'xtc-ipc',
    'com/xtc/log/': 'xtc-log',
    'com/xtc/ultraframework/': 'xtc-ultraframework',
    'com/xtc/utils/': 'xtc-utils',
    'com/xtc/assistantapi/': 'xtc-assistantapi',
    'com/xtc/funlist/': 'xtc-funlist',
    'com/xtc/xtcoco/': 'xtc-xtcoco',
    'com/android/settingslib/': 'settingslib',
    'com/android/common/': 'xtc-common-lib',
}

# module owner per package (analysis task owner)
OWNERS = [
    ('com/android/systemui/statusbar/', 'risk-scan/statusbar-notification'),
    ('com/xtc/statusbar/', 'risk-scan/statusbar-notification'),
    ('com/android/systemui/qs/', 'mod-qs/qs-volume'),
    ('com/android/systemui/screenrecord/', 'mod-tools/media-tools'),
    ('com/android/systemui/flashlight/', 'mod-tools/media-tools'),
    ('com/android/systemui/vassistant/', 'mod-tools/media-tools'),
    ('com/android/systemui/media/', 'mod-tools/media-tools'),
    ('com/android/systemui/plugins/', 'dead-or-core'),
    ('com/xtc/ui/widget/privacy/', 'mod-misc/misc-local'),
    ('com/xtc/ui/widget/selfstart/', 'mod-misc/misc-local'),
    ('com/xtc/architecture/', 'mod-misc/misc-local'),
]

REMOVED = ('com/xtc/bigdata/', 'com/xtc/log/', 'com/xtc/dns/', 'com/xtc/domain/',
           'com/xtc/httplib/', 'com/xtc/funlist/', 'com/xtc/xtcoco/')
DEAD = ('com/xtc/client/', 'com/xtc/ipc/', 'com/xtc/im/')


def relpaths(root):
    out = []
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith('.java'):
                out.append(os.path.relpath(os.path.join(dirpath, f), root).replace('\\', '/'))
    return out


def fqcn(rel):
    return rel[:-5].replace('/', '.')


def area_of(rel):
    for prefix, name in AREAS.items():
        if rel.startswith(prefix):
            return name
    return 'other'


def owner_of(rel):
    for prefix, owner in OWNERS:
        if rel.startswith(prefix):
            return owner
    return ''


def status_of(rel):
    if rel.startswith(REMOVED):
        return 'REMOVED(不实现)'
    if rel in ('com/xtc/client/', ):
        return 'DEAD(死代码)'
    if rel.startswith(DEAD):
        return 'DEAD/待确认'
    if rel.startswith('com/android/systemui/plugins/'):
        return 'PLUGIN(插件框架)'
    if rel.startswith(('androidx/', 'kotlin/', 'kotlinx/', 'okhttp3/', 'okio/',
                       'retrofit2/', 'rx/', 'org/')):
        return 'THIRDPARTY(不打包)'
    return ''


def main():
    jadx = relpaths(JADX)
    src_rel = set(relpaths(SRC))

    rows = []
    for rel in jadx:
        if not rel.startswith(tuple(AREAS) + ('androidx/', 'kotlin/', 'kotlinx/',
                                              'okhttp3/', 'okio/', 'retrofit2/', 'rx/', 'org/')):
            continue
        st = status_of(rel)
        impl = 'YES' if rel in src_rel else ('n/a' if st else 'no')
        owner = owner_of(rel)
        rows.append({
            'fqcn': fqcn(rel),
            'rel': rel,
            'area': area_of(rel),
            'owner': owner,
            'impl': impl,
            'status': st,
            'kb': round(os.path.getsize(os.path.join(JADX, rel)) / 1024.0, 1),
        })

    os.makedirs(os.path.dirname(OUT_CSV), exist_ok=True)
    with open(OUT_CSV, 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.DictWriter(f, fieldnames=['fqcn', 'rel', 'area', 'owner', 'impl', 'status', 'kb'])
        w.writeheader()
        w.writerows(rows)

    # statistics
    stats = {}
    for r in rows:
        key = (r['owner'] or r['status'] or 'core-pending', r['area'])
        s = stats.setdefault(key, {'total': 0, 'impl': 0, 'kb': 0.0})
        s['total'] += 1
        s['kb'] += r['kb']
        if r['impl'] == 'YES':
            s['impl'] += 1
    print('csv:', OUT_CSV)
    print('%-42s %-18s %6s %6s %9s' % ('owner/status', 'area', 'total', 'impl', 'KB'))
    for (owner, area), s in sorted(stats.items()):
        print('%-42s %-18s %6d %6d %9.1f' % (owner[:42], area, s['total'], s['impl'], s['kb']))
    tot = sum(s['total'] for s in stats.values())
    impl = sum(s['impl'] for s in stats.values())
    print('TOTAL classes=%d implemented=%d' % (tot, impl))


if __name__ == '__main__':
    main()
