import os, re, json, collections, io, sys

RES = r'D:\DEBUG\apk\sysui\nx\resources\res'
OUT = r'D:\DEBUG\apk\sysui\analysis\res'
report = []

def w(s=''):
    report.append(str(s))

# ---------- 1. res type aggregation ----------
files = []
for root, dirs, fns in os.walk(RES):
    for fn in fns:
        p = os.path.join(root, fn)
        files.append((os.path.basename(root), fn, os.path.getsize(p), p))
w('## RES TYPE AGGREGATION (by type prefix)')
bytype = collections.defaultdict(lambda: [0, 0, set()])
for d, fn, sz, p in files:
    t = d.split('-')[0]
    bytype[t][0] += 1
    bytype[t][1] += sz
    bytype[t][2].add(d)
for t in sorted(bytype, key=lambda x: -bytype[x][1]):
    w(f'{t}\tfiles={bytype[t][0]}\tbytes={bytype[t][1]}\tdirs={len(bytype[t][2])}')
w(f'TOTAL\tfiles={len(files)}\tbytes={sum(f[2] for f in files)}')

# ---------- 2. base values string grouping ----------
def parse_strings(path):
    """return list of (name, text) preserving order"""
    s = io.open(path, encoding='utf-8').read()
    out = []
    for m in re.finditer(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', s, re.S):
        name, attrs, text = m.group(1), m.group(2), m.group(3)
        out.append((name, text, attrs))
    return out

base = parse_strings(os.path.join(RES, 'values', 'strings.xml'))
w('')
w(f'## BASE values/strings.xml total={len(base)}')
groups = collections.OrderedDict()
def groupof(n):
    if n.startswith('abc_') or n.startswith('search_menu') or n.startswith('status_bar_notification_info'):
        return 'A. appcompat/androidx 英文内置'
    if n.startswith('privacy') or n.startswith('cta_') or n.startswith('about_settings') or n in ('agree','disagree','allow'):
        return 'B. 隐私协议/权限弹窗'
    if 'forbid' in n or 'ban' in n or 'durtaion' in n or 'duration' in n or n.startswith('tip_') or 'unsupport' in n:
        return 'C. 禁用/限额话术（家长管控）'
    if n.startswith('screen_record') or n in ('by_screen_record','from_screen_record','ready_for_screen_record','start_screen_recording','record_internal_audio','recording','string_recording','able_record','second_to_record','can_not_record_screen_cause_storage','disable_screen_record_when_open_camera','interrupt_screen_record_when_open_camera'):
        return 'D. 录屏/录音'
    if n.startswith('flash') or n.startswith('string_flash') or n.startswith('string_light') or 'sos' in n or 'camera' in n:
        return 'E. 手电筒/摄像头'
    if n.startswith(('cube_ptr','ptr_','pull_down','swipe_up','load','sina_load','refresh','last_update','noMore','no_more','seconds_ago','minutes_ago','hours_ago','search')):
        return 'F. 下拉刷新/加载更多'
    if n.startswith(('wifi','blue','airplane','auto_brightness','brightness','volume','music_volume','voice_call','remind_mode','watch_long','long_battery','low_power','operator','string_sim','string_no_sim','system_volume','slide','shut_down','step','unknow')):
        return 'G. 快捷开关/音量/运营商状态'
    if n.startswith('privacy'):
        return 'B'
    return 'H. 其它/通用'
for name, text, attrs in base:
    groups.setdefault(groupof(name), []).append(name)
for g in sorted(groups):
    w(f'### {g}  count={len(groups[g])}')
    w('    ' + ', '.join(groups[g]))

# ---------- 3. $${...}$ rich text in base ----------
w('')
w('## BASE strings containing $${...}$ or $...$ rich markers')
for name, text, attrs in base:
    if '$${' in text or '}$' in text:
        w(f'{name}\t{text}')

# ---------- 4. locale stats ----------
w('')
w('## LOCALE DIRS WITH strings.xml')
loc = []
for d in sorted(os.listdir(RES)):
    dp = os.path.join(RES, d)
    if not os.path.isdir(dp) or not d.startswith('values'):
        continue
    sp = os.path.join(dp, 'strings.xml')
    fl = sorted(os.listdir(dp))
    if os.path.exists(sp):
        ss = parse_strings(sp)
        loc.append((d, len(ss), os.path.getsize(sp), ','.join(fl)))
    else:
        loc.append((d, 0, 0, ','.join(fl)))
for d, n, sz, fl in loc:
    w(f'{d}\tstrings={n}\tbytes={sz}\tfiles={fl}')
w(f'values dirs total={len(loc)}, with strings.xml={sum(1 for x in loc if x[3].find("strings.xml")>=0)}')

# ---------- 5. zh-rCN full list ----------
zh = parse_strings(os.path.join(RES, 'values-zh-rCN', 'strings.xml'))
w('')
w(f'## values-zh-rCN/strings.xml total={len(zh)}')
for name, text, attrs in zh:
    w(f'{name}\t{text}')

# ---------- 6. appcompat / androidx residue ----------
w('')
w('## APPCOMPAT/ANDROIDX RESIDUE FILE NAMES')
res_names = collections.defaultdict(list)
for d, fn, sz, p in files:
    res_names[fn.split('.')[0]].append(f'{d}/{fn}')

names = sorted(res_names)
abc = [n for n in names if n.startswith('abc_')]
androidx = [n for n in names if n.startswith('androidx_')]
other_support = [n for n in names if n.startswith(('notification_template','notification_action','select_dialog_','support_simple_spinner','compatible_'))]
w(f'abc_*={len(abc)} android_support_style={len(other_support)}')
w('abc_resources: ' + ' '.join(abc))
w('support_resources: ' + ' '.join(other_support))

# ---------- 7. references check ----------
# scan all layout & values files for @drawable/abc... @layout/abc... R references
w('')
w('## REFERENCE CHECK for abc_* / support residues')
allfiles = []
for root, dirs, fns in os.walk(RES):
    for fn in fns:
        if fn.endswith('.xml') and (root.replace(RES, '').startswith('\\layout') or root.replace(RES, '').startswith('\\values') or root.replace(RES,'').startswith('\\xml')):
            allfiles.append(os.path.join(root, fn))
refs = collections.defaultdict(set)
for p in allfiles:
    try:
        s = io.open(p, encoding='utf-8').read()
    except Exception:
        continue
    for m in re.finditer(r'(?:@|\+)(?:drawable|layout|color|dimen|string|style|id|anim|xml|integer|bool)/(\w+)', s):
        refs[m.group(1)].add(os.path.relpath(p, RES))
# also find "abc_" names that are referenced by any non-abc file
for n in abc:
    r = refs.get(n, set())
    nonabc = [x for x in r if not os.path.basename(x).startswith('abc_')]
    if r:
        w(f'{n}\treferenced_by_total={len(r)}\tnon_abc_refs={nonabc if nonabc else "NONE(仅abc内部互相引用)"}')

# ---------- 8. unused check for abc_* files (referenced only by other abc_ files or nowhere) ----------
w('')
w('## abc_* FILES WITH ZERO REFERENCE')
unused_abc = [n for n in abc if n not in refs]
w(f'count={len(unused_abc)}')
w(' '.join(unused_abc))

# ---------- 9. assets json ----------
w('')
w('## ASSETS JSON TOP-LEVEL KEYS')
A = r'D:\DEBUG\apk\sysui\nx\resources\assets'
for root, dirs, fns in os.walk(A):
    for fn in sorted(fns):
        if not fn.endswith('.json'):
            continue
        p = os.path.join(root, fn)
        raw = io.open(p, 'rb').read()
        try:
            raw.decode('utf-8')
            enc = 'utf-8'
        except Exception:
            enc = 'GBK?'
        s = raw.decode(enc, errors='replace')
        try:
            j = json.loads(s)
            keys = list(j.keys())
            nlayers = len(j.get('layers', []))
            nassets = len(j.get('assets', []))
            w(f'{os.path.relpath(p, A)}\tbytes={len(raw)}\tenc={enc}\ttopkeys={keys}\tlayers={nlayers}\tassets={nassets}\tfr={j.get("fr")}\tw={j.get("w")}\th={j.get("h")}\tip={j.get("ip")}\top={j.get("op")}\tnm={j.get("nm")}\tv={j.get("v")}')
        except Exception as e:
            w(f'{os.path.relpath(p, A)}\tPARSE_FAIL {e}')

# embedded images in lottie assets
w('')
w('## LOTTIE embedded image assets (u/p fields, layer ty=2)')
for root, dirs, fns in os.walk(A):
    for fn in sorted(fns):
        if not fn.endswith('.json'):
            continue
        p = os.path.join(root, fn)
        s = io.open(p, encoding='utf-8', errors='replace').read()
        try:
            j = json.loads(s)
        except Exception:
            continue
        imgs = [a for a in j.get('assets', []) if isinstance(a, dict) and (a.get('p') or a.get('u'))]
        if imgs:
            for a in imgs:
                w(f'{os.path.relpath(p,A)}\tid={a.get("id")}\tw={a.get("w")} h={a.get("h")}\tu={a.get("u")} p={a.get("p")}')
        else:
            comps = [a.get('id') for a in j.get('assets', []) if isinstance(a, dict)]
            w(f'{os.path.relpath(p,A)}\tno_embedded_images\tprecomps={comps[:6]}')

io.open(os.path.join(OUT, '_scan_report.txt'), 'w', encoding='utf-8').write('\n'.join(report))
print('WROTE', os.path.join(OUT, '_scan_report.txt'), len(report), 'lines')
