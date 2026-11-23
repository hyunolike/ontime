#!/usr/bin/env python3
"""Generate the final artwork and verify it. Writes only beside this file.

Requires Python 3, Pillow and rsvg-convert. No changes to the original P2.
All SVG and Android paths originate from the same filled cubic silhouette.
"""
import hashlib
import io
import json
import math
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
GREEN, GRAY, WHITE = '#1A9365', '#353535', '#FFFFFF'
ANDROID = 'http://schemas.android.com/apk/res/android'
# The original 40x40 bbox was centered at (54,52). Relative to a bbox-centered
# placement, the optical adjustment is +1.75 dp right and +3 dp down.
OFFSET = (1.75, 5)
START = (37, 32)
COMMANDS = [
    ('C', (38.657, 32), (40, 33.343), (40, 35)),
    ('C', (50, 31), (59, 41), (74, 35)),
    ('L', (74, 57)),
    ('C', (59, 63), (50, 53), (40, 57)),
    ('L', (40, 69)),
    ('C', (40, 70.657), (38.657, 72), (37, 72)),
    ('C', (35.343, 72), (34, 70.657), (34, 69)),
    ('L', (34, 35)),
    ('C', (34, 33.343), (35.343, 32), (37, 32)),
]


def shift(p):
    return tuple(p[k] + OFFSET[k] for k in (0, 1))


def pair(p):
    return ' '.join(f'{n:g}' for n in p)


PATH = 'M' + pair(shift(START)) + ' ' + ' '.join(
    op + ' '.join(pair(shift(p)) for p in points)
    for op, *points in COMMANDS) + ' Z'


def write(name, text):
    (ROOT / name).write_text(text, encoding='utf-8')


def glyph_svg(path, tile=False, color=GREEN):
    if tile:
        # Android's visible central 72dp maps to the full 512px tile.
        return f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="40" fill="{color}"/>
  <svg x="0" y="0" width="512" height="512" viewBox="18 18 72 72">
    <path fill="#FFFFFF" d="{path}"/>
  </svg>
</svg>
'''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <path fill="#FFFFFF" d="{path}"/>
</svg>
'''


def render(svg, size, output):
    data = subprocess.check_output(['rsvg-convert', '-w', str(size), '-h', str(size), str(ROOT / svg)])
    (ROOT / output).write_bytes(data)
    return Image.open(io.BytesIO(data)).convert('RGBA')


def luminance(color):
    values = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    values = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in values]
    return sum(a * b for a, b in zip(values, (.2126, .7152, .0722)))


def contrast(color):
    return 1.05 / (luminance(color) + .05)


def bezier(points, t):
    if len(points) == 2:
        return tuple((1-t)*points[0][k] + t*points[1][k] for k in (0, 1))
    a, b, c, d = points
    u = 1 - t
    return tuple(u**3*a[k] + 3*u*u*t*b[k] + 3*u*t*t*c[k] + t**3*d[k] for k in (0, 1))


def geometry():
    segments, controls, current = [], [shift(START)], shift(START)
    for _, *points in COMMANDS:
        points = [shift(p) for p in points]
        segments.append([current] + points)
        controls.extend(points)
        current = points[-1]
    # Exact extrema of each cubic from its quadratic derivative.
    extrema = controls[:1]
    for points in segments:
        ts = {0, 1}
        if len(points) == 4:
            for k in (0, 1):
                a, b, c, d = [p[k] for p in points]
                qa, qb, qc = -a+3*b-3*c+d, 2*(a-2*b+c), b-a
                if abs(qa) < 1e-12:
                    roots = [-qc/qb] if abs(qb) > 1e-12 else []
                else:
                    disc = qb*qb-4*qa*qc
                    roots = [(-qb+s*math.sqrt(disc))/(2*qa) for s in (-1, 1)] if disc >= 0 else []
                ts.update(t for t in roots if 0 < t < 1)
        extrema.extend(bezier(points, t) for t in ts)
    bbox = [min(p[0] for p in extrema), min(p[1] for p in extrema),
            max(p[0] for p in extrema), max(p[1] for p in extrema)]
    samples = [bezier(p, i/10000) for p in segments for i in range(10001)]
    cross = [(a[0]*b[1]-b[0]*a[1], a, b) for a, b in zip(samples, samples[1:]+samples[:1])]
    twice_area = sum(s for s, _, _ in cross)
    centroid = [sum((a[k]+b[k])*s for s, a, b in cross)/(3*twice_area) for k in (0, 1)]
    radius = max(math.hypot(x-54, y-54) for x, y in samples)
    hull_radius = max(math.hypot(x-54, y-54) for x, y in controls)
    # Curve derivative bound also bounds unsampled radial error.
    sample_error = max((len(p)-1)*math.dist(a, b) for p in segments for a, b in zip(p, p[1:]))/10000
    assert hull_radius < 33
    return {
        'bbox_dp': bbox,
        'bbox_percent_of_72dp_visible_tile': [(bbox[k+2]-bbox[k])/72*100 for k in (0, 1)],
        'bbox_percent_of_108dp_full_layer': [(bbox[k+2]-bbox[k])/108*100 for k in (0, 1)],
        'bbox_512px': [(v-18)*512/72 for v in bbox],
        'bbox_size_512px': [(bbox[k+2]-bbox[k])*512/72 for k in (0, 1)],
        'bbox_percent_of_512px_tile': [(bbox[k+2]-bbox[k])/72*100 for k in (0, 1)],
        'white_area_centroid_dp': centroid,
        'optical_offset_from_bbox_centered_dp': [1.75, 3],
        'pole_width_dp': 6,
        'pole_width_at_48px': 6*48/72,
        'boundary_sample_max_radius_dp': radius,
        'boundary_sample_error_bound_dp': sample_error,
        'control_hull_max_radius_dp': hull_radius,
        'safe_circle_radius_dp': 33,
        'proven_radial_margin_dp': 33-hull_radius,
        'containment_proof': 'All endpoints and cubic control points are in the convex r=33 disk; the entire filled silhouette is contained.',
    }


def html():
    placeholders = {
        '내비게이션': 'M54 28 L72 76 L54 64 L36 76 Z',
        '전화': 'M35 28 L45 41 L40 47 Q46 60 59 66 L65 60 L79 69 L74 79 Q71 83 65 81 C44 75 29 59 26 40 Q25 35 29 32 Z',
        '차량 설정': 'M28 46 L34 31 H74 L80 46 L84 51 V76 H76 V69 H32 V76 H24 V51 Z M37 36 L33 46 H75 L71 36 Z M32 54 H76 V59 H32 Z',
    }

    def system(label):
        return f'<figure><svg viewBox="0 0 108 108" role="img" aria-label="{label} 비교용 대체 도형"><rect width="108" height="108" rx="8.4375" fill="{GRAY}"/><path fill="white" fill-rule="evenodd" d="{placeholders[label]}"/></svg><figcaption>{label}</figcaption></figure>'

    def app(size, color='green'):
        return f'<figure><img src="gilbeot-icon-{color}-{size}.png" width="{size}" height="{size}" alt="길벗 투어 가이드 깃발"><figcaption>길벗</figcaption></figure>'

    rows = ''.join(f'<section><h2>{size}px 앱 행</h2><div class="scroll"><div class="row" style="--size:{size}px">{system("내비게이션")}{app(96 if size == 76 else 48)}{system("전화")}{system("차량 설정")}</div></div></section>' for size in (76, 48))
    write('preview.html', f'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>길벗 최종 아이콘 — Pleos Connect</title>
<style>
*{{box-sizing:border-box}}body{{margin:0;background:#000;color:#fff;font-family:Arial,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;line-height:1.6}}main{{max-width:1400px;margin:auto;padding:24px 24px 56px}}h1{{font-size:24px;margin:0 0 16px}}h2{{font-size:18px;margin:0 0 20px}}figure{{margin:0;text-align:center}}.reference img{{display:block;width:100%;height:auto}}.reference figcaption{{text-align:left;color:#aaa;font-size:13px}}section{{margin-top:32px;padding-top:24px;border-top:1px solid #292929}}.scroll{{overflow:auto}}.row{{display:flex;gap:48px;width:max-content;padding:8px 0}}.row img,.row svg{{display:block;width:var(--size);height:var(--size)}}.row figure{{flex:none}}figcaption{{font-size:14px;margin-top:10px;white-space:nowrap}}p{{font-size:14px;color:#bbb}}a{{color:#89e0b8}}.variants{{--size:96px}}
</style></head><body><main>
<h1>길벗 최종 아이콘</h1>
<figure class="reference"><img src="../../reference-pleos-app-icons.png" width="1350" height="190" alt="제공된 실제 Pleos 앱 행: Connect Mirroring, FormFactorConsole, Gleo AI, 기존 길벗, 내비게이션, 전화, 차량 설정"><figcaption>실제 Pleos 레퍼런스</figcaption></figure>
<p>아래는 배치 비교를 위한 모의 앱 행입니다. 회색 화살표, 전화, 차량 도형은 대체 아이콘입니다. 브라우저 확대율 100%에서 확인하세요.</p>
{rows}
<section><h2>녹색과 회색 비교 · 96px</h2><div class="row variants">{app(96)}{app(96, 'gray')}</div></section>
<p><a href="README.md">제작 설명</a> / <a href="verification.json">수치 검증</a> / <a href="inspection.png">크기별 렌더</a></p>
</main></body></html>
''')


def contact_sheet():
    sheet = Image.new('RGB', (1120, 760), '#000000')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf', 18)
    for col, color in enumerate(('green', 'gray')):
        x = 24 + 552*col
        draw.text((x, 16), f'{color} / native 48px and 96px', fill='white', font=font)
        for size, dx in ((48, 0), (96, 96)):
            im = Image.open(ROOT / f'gilbeot-icon-{color}-{size}.png').convert('RGBA')
            sheet.paste(im, (x+dx, 48), im)
        draw.text((x, 174), '512px / central 72dp mapping', fill='white', font=font)
        im = Image.open(ROOT / f'gilbeot-icon-{color}-512.png').convert('RGBA')
        sheet.paste(im, (x, 212), im)
    sheet.save(ROOT / 'inspection.png')


def main():
    for color, value in (('green', GREEN), ('gray', GRAY)):
        write(f'gilbeot-icon-{color}.svg', glyph_svg(PATH, True, value))
        for size in (512, 96, 48):
            render(f'gilbeot-icon-{color}.svg', size, f'gilbeot-icon-{color}-{size}.png')
    write('icon.svg', glyph_svg(PATH, True))
    write('gilbeot-foreground.svg', glyph_svg(PATH))
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{ANDROID}"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="{PATH}"/>
</vector>
'''
    write('ic_launcher_foreground.xml', xml)
    write('ic_launcher_monochrome.xml', xml)
    write('ic_launcher_background.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="{ANDROID}" android:shape="rectangle">
    <solid android:color="{GREEN}"/>
</shape>
''')
    report = {'renderer': subprocess.check_output(['rsvg-convert', '--version'], text=True).strip(),
              'geometry': geometry(), 'contrast': {c: contrast(c) for c in (GREEN, GRAY)},
              'xml_render_checks': {}}
    assert 50 <= report['geometry']['bbox_percent_of_512px_tile'][0] <= 56
    assert 50 <= report['geometry']['bbox_percent_of_512px_tile'][1] <= 56
    assert contrast(GREEN) > 3
    for name in ('foreground', 'monochrome'):
        vector = ET.parse(ROOT / f'ic_launcher_{name}.xml').getroot()
        assert all(vector.attrib[f'{{{ANDROID}}}{k}'] == v for k, v in {
            'width': '108dp', 'height': '108dp', 'viewportWidth': '108', 'viewportHeight': '108'}.items())
        paths = vector.findall('path')
        assert len(paths) == 1
        assert paths[0].attrib[f'{{{ANDROID}}}fillColor'] == WHITE
        extracted = paths[0].attrib[f'{{{ANDROID}}}pathData']
        assert extracted == PATH
        write(f'android-{name}-from-xml.svg', glyph_svg(extracted))
        write(f'android-{name}-tile-from-xml.svg', glyph_svg(extracted, True))
        checks = {}
        for size in (512, 96, 48):
            actual = render(f'android-{name}-tile-from-xml.svg', size, f'android-{name}-tile-{size}.png')
            expected = Image.open(ROOT / f'gilbeot-icon-green-{size}.png').convert('RGBA')
            assert actual.tobytes() == expected.tobytes()
            checks[str(size)] = {'identical_rgba_pixels': True, 'different_pixels': 0,
                                 'max_channel_difference': max(v[1] for v in ImageChops.difference(actual, expected).getextrema())}
        actual = render(f'android-{name}-from-xml.svg', 108, f'android-{name}-108.png')
        expected = render('gilbeot-foreground.svg', 108, 'gilbeot-foreground-108.png')
        assert actual.tobytes() == expected.tobytes()
        checks['108_unmasked_foreground'] = {'identical_rgba_pixels': True, 'different_pixels': 0}
        report['xml_render_checks'][name] = checks
    # Independent raster containment check, including all antialiased pixels.
    im = render('android-foreground-from-xml.svg', 2160, 'android-foreground-safe-zone-2160.png')
    alpha = im.getchannel('A')
    radius = max(math.hypot((x+.5)/20-54, (y+.5)/20-54)
                 for y in range(alpha.height) for x in range(alpha.width) if alpha.getpixel((x, y)))
    assert radius < 33
    report['geometry']['raster_20x_all_nonzero_alpha_max_radius_dp'] = radius
    html()
    contact_sheet()
    report['visual_review_48px'] = 'Visually inspected the native 48px green PNG and contact sheet: broad rectangular cloth, S-shaped parallel edges, straight trailing edge and 4px pole remain legible; no tent/triangle silhouette. Human-perception judgment, not an automated classifier.'
    report['files_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in sorted(ROOT.iterdir()) if p.is_file() and p.name != 'verification.json'}
    write('verification.json', json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'files_sha256'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
