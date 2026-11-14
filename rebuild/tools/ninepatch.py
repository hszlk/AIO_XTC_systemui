"""Decode the aapt2 'npTc' (nine-patch) chunk of crunched PNGs.

Hypothesis (validated against all 113 files in the res tree):
  offset 0 : u8  wasDeserialized
  offset 1 : u8  numXDivs
  offset 2 : u8  numYDivs
  offset 3 : u8  numColors
  offset 4 : i32 LE  xDivsOffset      (relative to byte 4)
  offset 8 : i32 LE  yDivsOffset      (relative to byte 4)
  offset 32: i32 LE  colorsOffset     (relative to byte 4)
  at 4+xDivsOffset : numXDivs  * i32 BE  (divider positions)
  at 4+yDivsOffset : numYDivs  * i32 BE
  at 4+colorsOffset: numColors * i32 BE  (ARGB region colours)
"""
import struct
import sys
import os

PNG_SIG = b'\x89PNG\r\n\x1a\n'


def read_chunks(data):
    assert data[:8] == PNG_SIG
    off = 8
    out = []
    while off < len(data):
        (ln,) = struct.unpack('>I', data[off:off + 4])
        typ = data[off + 4:off + 8].decode('latin-1')
        out.append((typ, data[off + 8:off + 8 + ln]))
        off += 12 + ln
    return out


def header_size(payload):
    """Bytes from the image start: 4 flag bytes + 8 offset slots (32 bytes)."""
    return 4 + 8 * 4


def parse_nptc(payload):
    if len(payload) < header_size(payload) + 4:
        return None
    version, n_x, n_y, n_c = payload[0], payload[1], payload[2], payload[3]
    off_x, = struct.unpack_from('<i', payload, 4)
    off_y, = struct.unpack_from('<i', payload, 8)
    off_c, = struct.unpack_from('<i', payload, 32)
    base = 4  # offsets are relative to the byte after the 4 flag bytes
    p_x, p_y, p_c = base + off_x, base + off_y, base + off_c
    if not (0 < p_x < len(payload) and 0 <= p_y <= len(payload) and 0 <= p_c <= len(payload)):
        return None
    need = p_y + 4 * n_y
    if need > len(payload):
        return None
    divs_x = [struct.unpack_from('>i', payload, p_x + 4 * i)[0] for i in range(n_x)]
    divs_y = [struct.unpack_from('>i', payload, p_y + 4 * i)[0] for i in range(n_y)]
    n_ctail = (len(payload) - p_c) // 4
    colors = [struct.unpack_from('>I', payload, p_c + 4 * i)[0] for i in range(max(n_ctail, 0))]
    return {
        'version': version, 'n_x': n_x, 'n_y': n_y, 'n_c': n_c,
        'p_x': p_x, 'p_y': p_y, 'p_c': p_c,
        'divs_x': divs_x, 'divs_y': divs_y, 'colors': colors,
        'size': len(payload),
    }


def ninepatch_info(data):
    """Return npTc info for a PNG that still carries aapt2's private chunks."""
    for typ, payload in read_chunks(data):
        if typ == 'npTc':
            return parse_nptc(payload)
    return None


def main():
    bad = 0
    for p in sorted(sys.argv[1:]):
        with open(p, 'rb') as f:
            data = f.read()
        chunks = read_chunks(data)
        ihdr = dict(chunks)['IHDR']
        w, h = struct.unpack('>II', ihdr[:8])
        info = ninepatch_info(data)
        name = os.path.basename(p)
        if info is None:
            print(f'NO npTc  {name}')
            bad += 1
            continue
        dx, dy = info['divs_x'], info['divs_y']
        ok = (all(0 <= v <= w for v in dx) and all(0 <= v <= h for v in dy)
              and dx == sorted(dx) and dy == sorted(dy)
              and info['p_y'] == info['p_x'] + 4 * info['n_x']
              and info['p_c'] == info['p_y'] + 4 * info['n_y'])
        if not ok:
            bad += 1
        print(f'{"OK " if ok else "BAD"} {name:52s} {w}x{h} nx={info["n_x"]} ny={info["n_y"]} '
              f'nc={len(info["colors"])} x={dx} y={dy} c0={info["colors"][0] if info["colors"] else None:#010x}')
    print(f'-- total={len(sys.argv)-1} bad={bad}')


if __name__ == '__main__':
    main()
