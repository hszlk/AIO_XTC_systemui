"""Dump npOl / npTc chunk payloads and attempt to decode the 9-patch layout."""
import struct, sys, os

def read_chunks(path):
    with open(path, 'rb') as f:
        data = f.read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not png'
    off = 8
    out = []
    while off < len(data):
        (ln,) = struct.unpack('>I', data[off:off + 4])
        typ = data[off + 4:off + 8].decode('latin-1')
        out.append((typ, data[off + 8:off + 8 + ln]))
        off += 12 + ln
    return out

def hexdump(b):
    return ' '.join(f'{x:02x}' for x in b)

for p in sys.argv[1:]:
    ch = read_chunks(p)
    ihdr = dict(ch)['IHDR']
    w, h = struct.unpack('>II', ihdr[:8])
    print(f'== {p}  {w}x{h}')
    for typ, payload in ch:
        if typ.startswith('np'):
            print(f'  {typ} len={len(payload)}')
            print(f'    hex: {hexdump(payload)}')
            print(f'    i32be: {[struct.unpack_from(">i", payload, i)[0] for i in range(0, len(payload) - 3, 4)]}')
            print(f'    i32le: {[struct.unpack_from("<i", payload, i)[0] for i in range(0, len(payload) - 3, 4)]}')
