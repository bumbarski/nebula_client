#!/usr/bin/env python3
"""Laczy mapowania Fabric intermediary z oficjalnymi mapowaniami Mojanga (to samo, co robi Loom) i zapisuje tabele intermediary -> nazwa Mojanga."""
import json, re, sys, urllib.request, zipfile, io

MC = sys.argv[1]

def get(url):
    return urllib.request.urlopen(url, timeout=120).read()

manifest = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
vurl = next(v['url'] for v in manifest['versions'] if v['id'] == MC)
vjson = json.loads(get(vurl))
moj = get(vjson['downloads']['client_mappings']['url']).decode('utf-8')
z = zipfile.ZipFile(io.BytesIO(get(f'https://maven.fabricmc.net/net/fabricmc/intermediary/{MC}/intermediary-{MC}-v2.jar')))
tiny = z.read('mappings/mappings.tiny').decode('utf-8').splitlines()

# Mojang: nazwa klasy <-> obf
moj_cls = {}      # mojang -> obf
obf_cls = {}      # obf -> mojang
moj_members = {}  # obf class -> list of (kind, mojname, obfname, desc-parts)
cur = None
for line in moj.splitlines():
    if line.startswith('#') or not line.strip():
        continue
    if not line.startswith(' '):
        m = re.match(r'(\S+) -> (\S+):', line)
        cur = m.group(2)
        moj_cls[m.group(1)] = cur
        obf_cls[cur] = m.group(1)
        moj_members[cur] = []
    else:
        s = line.strip()
        m = re.match(r'(?:\d+:\d+:)?(\S+) (\w+)\((.*?)\)(?::\d+:\d+)? -> (\S+)', s)
        if m:
            moj_members[cur].append(('m', m.group(2), m.group(4), m.group(1), m.group(3)))
        else:
            m = re.match(r'(\S+) (\S+) -> (\S+)', s)
            if m:
                moj_members[cur].append(('f', m.group(2), m.group(3), m.group(1), ''))

PRIM = {'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S', 'void': 'V'}

def jvm(t):
    dims = 0
    while t.endswith('[]'):
        dims += 1
        t = t[:-2]
    if t in PRIM:
        base = PRIM[t]
    else:
        base = 'L' + moj_cls.get(t, t).replace('.', '/') + ';'
    return '[' * dims + base

def desc(ret, params):
    ps = [p for p in params.split(',') if p] if params else []
    return '(' + ''.join(jvm(p) for p in ps) + ')' + jvm(ret)

# lookup po (klasa obf, nazwa obf, deskryptor obf)
mm = {}
ff = {}
for oc, members in moj_members.items():
    for kind, name, obf, t, params in members:
        if kind == 'm':
            mm[(oc, obf, desc(t, params))] = name
        else:
            ff[(oc, obf)] = name

out_cls, out_m, out_f = {}, [], []
cur = None
for line in tiny[1:]:
    if line.startswith('c\t'):
        _, obf, inter = line.split('\t')[:3]
        cur = obf
        out_cls[inter] = obf_cls.get(obf, '?')
    elif line.startswith('\tm\t'):
        parts = line.split('\t')
        d, obf, inter = parts[2], parts[3], parts[4]
        out_m.append((inter, mm.get((cur, obf, d), '?'), obf_cls.get(cur, '?')))
    elif line.startswith('\tf\t'):
        parts = line.split('\t')
        obf, inter = parts[3], parts[4]
        out_f.append((inter, ff.get((cur, obf), '?'), obf_cls.get(cur, '?')))

with open(f'mappings-{MC}.tsv', 'w', encoding='utf-8') as fh:
    for k, v in out_cls.items():
        fh.write(f'C\t{k}\t{v}\n')
    for k, v, o in out_m:
        fh.write(f'M\t{k}\t{v}\t{o}\n')
    for k, v, o in out_f:
        fh.write(f'F\t{k}\t{v}\t{o}\n')
print(len(out_cls), len(out_m), len(out_f))
