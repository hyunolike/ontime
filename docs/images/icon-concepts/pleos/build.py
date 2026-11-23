#!/usr/bin/env python3
"""Rebuild the Pleos icon studies. Writes only beside this script."""
import hashlib
import io
import json
import math
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
GRAY = '#353535'
GREEN = '#1A9365'
WHITE = '#FFFFFF'

# All coordinates are in Android's 108 dp adaptive-icon foreground space.
# Quadratic/cubic control points stay in the r=33 disk, giving a conservative
# containment proof through the convex-hull property of Bezier curves.
CONCEPTS = {
    'p1-road-story': {
        'name': 'P1 — 길을 품은 말풍선',
        'path': (
            'M36 32 H72 C78 32 83 39 83 45 V61 Q83 69 75 69 '
            'H49 L38 79 V69 H33 Q25 69 25 61 V45 C25 39 30 32 36 32 Z '
            'M60 40 C48 44 54 50 52 54 C50 58 44 61 42 64 '
            'H66 C69 57 64 52 60 49 C56 46 56 43 60 40 Z'
        ),
        'description': '흰 말풍선 안을 굽은 도로 모양으로 비워, 앞으로 지나갈 길의 이야기를 들려주는 기능을 담았습니다. 말풍선의 넓은 면과 아래로 벌어지는 도로가 작은 크기에서도 남으며, 흰 단색 실루엣과 회색 타일이 Pleos 기본 앱의 표현 방식에 어울립니다.',
    },
    'p2-tour-flag': {
        'name': 'P2 — 길가의 가이드 깃발',
        'path': (
            'M28 74 L39 52 H48 L54 69 H55 V29 H60 V34 '
            'C68 29 74 41 82 37 V52 C74 56 68 45 60 50 '
            'V74 Z M42 61 H45 V70 H42 Z'
        ),
        'description': '짧은 도로 옆에 패키지여행 가이드의 사각 깃발을 세웠습니다. 위아래 가장자리가 함께 출렁이는 넓은 천으로 삼각 골프 깃발과 구분하고, 도로와 깃대를 하나의 흰 면으로 연결해 Pleos 아이콘의 단순한 실루엣을 따랐습니다.',
    },
    'p3-side-window': {
        'name': 'P3 — 이야기가 보이는 차창',
        'path': (
            'M33 36 H65 Q70 36 73 42 L81 61 V66 '
            'Q81 72 75 72 H33 Q27 72 27 66 V42 Q27 36 33 36 Z '
            'M34 43 H43 V64 H34 Z '
            'M61 43 L64 50 L71 53 L64 56 L61 63 L58 56 L51 53 L58 50 Z'
        ),
        'description': '옆 유리창을 넓은 흰 면으로 그리고, 한쪽 창의 반짝임을 음각으로 넣었습니다. 어느 쪽 창밖을 볼지 알려 주는 길벗의 역할을 나타내며, 굵은 창틀과 채워진 유리 면으로 가는 선 없이 형태를 유지합니다.',
    },
}


def luminance(color):
    rgb = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb]
    return sum(c * w for c, w in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    bright, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (bright + .05) / (dark + .05)


def write(name, content):
    target = ROOT / name
    assert target.parent == ROOT
    target.write_text(content, encoding='utf-8')


def render(source, size, target):
    subprocess.run(['rsvg-convert', '-w', str(size), '-h', str(size),
                    str(ROOT / source), '-o', str(ROOT / target)], check=True)


def control_points(path):
    # These deliberately restricted paths contain M/L/H/V/Q/C/Z only.
    tokens = re.findall(r'[A-Za-z]|-?\d+(?:\.\d+)?', path)
    cursor = (0., 0.)
    origin = cursor
    points = []
    i = 0
    while i < len(tokens):
        cmd = tokens[i]
        i += 1
        if cmd == 'Z':
            cursor = origin
            continue
        count = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'Q': 4, 'C': 6}[cmd]
        v = [float(x) for x in tokens[i:i + count]]
        i += count
        if cmd == 'H':
            cursor = (v[0], cursor[1])
            points.append(cursor)
        elif cmd == 'V':
            cursor = (cursor[0], v[0])
            points.append(cursor)
        else:
            points.extend(zip(v[::2], v[1::2]))
            cursor = tuple(v[-2:])
            if cmd == 'M':
                origin = cursor
    return points


def tile_svg(path, color):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="40" fill="{color}"/>
  <path transform="scale({512 / 108:.14f})" fill="#FFFFFF" fill-rule="evenodd" d="{path}"/>
</svg>
'''


def foreground_svg(path):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <!-- White filled paths only. Artwork stays inside r=33 centered at (54,54). -->
  <path fill="#FFFFFF" fill-rule="evenodd" d="{path}"/>
</svg>
'''


NEIGHBORS = {
    'navigation': ('내비게이션', 'M54 27 L75 79 L54 64 L33 79 Z'),
    'phone': ('전화', 'M36 27 L47 42 L41 49 Q46 61 59 67 L66 60 L81 71 '
                         'L76 82 Q73 86 66 83 Q31 70 25 40 Q24 35 28 32 Z'),
    'vehicle': ('차량 설정', 'M35 34 H73 L80 51 H85 V77 H76 V71 H32 V77 H23 V51 H28 Z '
                              'M39 39 L34 51 H74 L69 39 Z M31 57 H77 V62 H31 Z'),
}


def html():
    neighbors = {key: f'<svg viewBox="0 0 108 108" role="img" aria-label="{label}">'
                 f'<rect width="108" height="108" rx="8.4375" fill="{GRAY}"/>'
                 f'<path fill="white" fill-rule="evenodd" d="{path}"/></svg>'
                 for key, (label, path) in NEIGHBORS.items()}

    def neighbor(key):
        return f'<figure>{neighbors[key]}<figcaption>{NEIGHBORS[key][0]}</figcaption></figure>'

    def app(slug, color='gray', size=512):
        return f'<figure><img src="{slug}-{color}-{size}.png" width="{size}" height="{size}" alt="{CONCEPTS[slug]["name"]}, {color}"><figcaption>길벗</figcaption></figure>'

    sections = []
    for size in (96, 48):
        concepts = ''.join(app(slug, size=48 if size == 48 else 512) for slug in CONCEPTS)
        rows = (f'<div class="comparison"><p>내비게이션 → P1 → P2 → P3 → 전화 → 차량 설정</p>'
                f'<div class="library s{size}">{neighbor("navigation")}{concepts}'
                f'{neighbor("phone")}{neighbor("vehicle")}</div></div>')
        sections.append(f'<section><h2>{size}px 앱 라이브러리</h2>{rows}</section>')
    variants = ''.join(f'<article><h3>{item["name"]}</h3><div class="variants">'
                       f'{app(slug)}{app(slug, "green")}</div><p>{item["description"]}</p></article>'
                       for slug, item in CONCEPTS.items())
    write('preview.html', f'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>길벗 — Pleos 아이콘 비교</title>
<style>
*{{box-sizing:border-box}} body{{margin:0;background:#000;color:#fff;font-family:Arial,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;line-height:1.6}}
main{{max-width:1440px;margin:auto;padding:32px 24px 64px}}h1{{font-size:26px;margin:0 0 8px}}h2{{font-size:22px;margin:0 0 24px}}
h3{{font-size:18px}}p{{color:#c5c5c5}}.reference{{margin:0}}.reference img{{display:block;max-width:100%;height:auto}}.reference figcaption{{text-align:left;color:#aaa}}
section{{border-top:1px solid #292929;margin-top:40px;padding-top:28px}}.comparison{{margin:0 0 28px}}.comparison>p{{font-size:14px;margin:0 0 12px}}
.library{{display:flex;align-items:flex-start;gap:32px;background:#000;width:max-content;padding:8px 0}}figure{{margin:0;text-align:center}}
.library figure{{flex:none}}.library img,.library svg{{display:block;width:var(--size);height:var(--size)}}.s96{{--size:96px}}.s48{{--size:48px}}
.library figcaption{{font-size:14px;margin-top:12px;white-space:nowrap}}.s48 figcaption{{font-size:12px;margin-top:8px}}
.variants{{display:flex;gap:24px}}.variants img{{display:block;width:192px;height:192px;max-width:36vw;object-fit:contain}}.variants figcaption{{margin-top:8px}}
article{{margin-bottom:40px;max-width:720px}}.note{{font-size:14px;color:#aaa}}a{{color:#7ce5b6}}.scroll{{overflow:auto}}
</style></head><body><main>
<h1>길벗 · Pleos Connect 아이콘</h1>
<p>시스템의 흰 단색 실루엣과 회색 타일에 맞춘 세 가지 방향</p>
<figure class="reference"><img src="../reference-pleos-app-icons.png" alt="실제 Pleos 앱 라이브러리: Connect Mirroring, Gleo AI, 기존 길벗, 내비게이션, 전화, 차량 설정"><figcaption>제공된 실제 Pleos 레퍼런스</figcaption></figure>
<p class="note">아래 회색 이웃 아이콘은 배치 비교용 단순 대체 도형입니다. 타일은 #353535, 모서리 반경은 512px 기준 40px입니다. 브라우저 확대율 100%에서 비교하세요.</p>
<div class="scroll">{''.join(sections)}</div>
<section><h2>회색과 브랜드 녹색</h2><p>흰색 / #353535: {contrast(WHITE, GRAY):.2f}:1 · 흰색 / {GREEN}: {contrast(WHITE, GREEN):.2f}:1</p>{variants}</section>
<p class="note"><a href="README.md">제작 설명과 검증 수치</a> · <a href="inspection.png">48px, 96px, 512px 렌더 모아 보기</a></p>
</main></body></html>''')


def contact_sheet():
    sheet = Image.new('RGB', (1240, 2240), '#000000')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf', 18)
    for index, slug in enumerate(CONCEPTS):
        top = 18 + index * 740
        draw.text((24, top), slug, font=font, fill='white')
        for col, color in enumerate(('gray', 'green')):
            x = 24 + col * 610
            draw.text((x, top + 30), f'{color} | 48 px + 96 px (native pixels)', font=font, fill='#BBBBBB')
            small = Image.open(ROOT / f'{slug}-{color}-48.png').convert('RGBA')
            sheet.paste(small, (x, top + 62), small)
            # 96px display corresponds to 512px artwork downscaled by the browser.
            medium = Image.open(ROOT / f'{slug}-{color}-512.png').convert('RGBA').resize((96, 96), Image.Resampling.LANCZOS)
            sheet.paste(medium, (x + 88, top + 62), medium)
            draw.text((x, top + 174), '512 px (native pixels)', font=font, fill='#BBBBBB')
            full = Image.open(ROOT / f'{slug}-{color}-512.png').convert('RGBA')
            sheet.paste(full, (x, top + 202), full)
    sheet.save(ROOT / 'inspection.png')


def main():
    report = {'renderer': subprocess.check_output(['rsvg-convert', '--version'], text=True).strip(),
              'contrast': {color: contrast(WHITE, color) for color in (GRAY, GREEN, '#3DDC97', '#0B0F14')},
              'safe_zone': {'center': [54, 54], 'radius': 33, 'method': 'Bezier convex hull: every endpoint and control point inside disk'},
              'concepts': {}, 'files': {}}
    assert contrast(WHITE, GREEN) >= 3
    for slug, item in CONCEPTS.items():
        path = item['path']
        points = control_points(path)
        radius = max(math.hypot(x - 54, y - 54) for x, y in points)
        assert radius <= 33, (slug, radius)
        width = max(x for x, _ in points) - min(x for x, _ in points)
        report['concepts'][slug] = {'control_hull_max_radius_dp': radius,
                                    'radial_margin_dp': 33 - radius,
                                    'glyph_width_percent_upper_bound': width / 108 * 100}
        write(f'{slug}-foreground.svg', foreground_svg(path))
        # An independent raster check complements the analytic convex-hull bound.
        raster = subprocess.check_output(['rsvg-convert', '-w', '2160', '-h', '2160',
                                          str(ROOT / f'{slug}-foreground.svg')])
        alpha = Image.open(io.BytesIO(raster)).convert('RGBA').getchannel('A')
        visible = [(x, y) for y in range(alpha.height) for x in range(alpha.width)
                   if alpha.getpixel((x, y)) > 0]
        raster_radius = max(math.hypot((x + .5) / 20 - 54, (y + .5) / 20 - 54)
                            for x, y in visible)
        assert raster_radius <= 33, (slug, raster_radius)
        report['concepts'][slug]['raster_max_radius_dp_20x'] = raster_radius
        report['concepts'][slug]['foreground_alpha_bbox_20x'] = alpha.getbbox()
        for color, value in (('gray', GRAY), ('green', GREEN)):
            name = f'{slug}-{color}'
            write(f'{name}.svg', tile_svg(path, value))
            for size in (512, 48):
                render(f'{name}.svg', size, f'{name}-{size}.png')
                with Image.open(ROOT / f'{name}-{size}.png') as im:
                    assert im.size == (size, size)
            document = ET.parse(ROOT / f'{name}.svg')
            assert document.getroot().attrib['viewBox'] == '0 0 512 512'
            assert not any('stroke' in el.attrib for el in document.iter())
            assert len(list(document.getroot())) == 2
        # Background-colored samples deep inside each negative-space feature.
        samples = {'p1-road-story': [(24, 26)],
                   'p2-tour-flag': [(19, 29)],
                   'p3-side-window': [(17, 24), (27, 23)]}[slug]
        report['concepts'][slug]['negative_space_48px_samples'] = {}
        for color, value in (('gray', GRAY), ('green', GREEN)):
            rendered = Image.open(ROOT / f'{slug}-{color}-48.png').convert('RGB')
            expected = tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))
            actual = [rendered.getpixel(p) for p in samples]
            assert all(pixel == expected for pixel in actual), (slug, color, actual, expected)
            report['concepts'][slug]['negative_space_48px_samples'][color] = {
                'positions': samples, 'rgb': actual, 'match_background': True}
    html()
    contact_sheet()
    for f in sorted(ROOT.iterdir()):
        if f.is_file() and f.name != 'verification.json':
            report['files'][f.name] = hashlib.sha256(f.read_bytes()).hexdigest()
    write('verification.json', json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'files'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
