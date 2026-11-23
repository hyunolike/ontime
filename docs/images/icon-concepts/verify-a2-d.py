"""Verify the two new icons. Run here with Python, NumPy, Pillow and rsvg-convert.

Checks exact radial extrema of line/quadratic paths, including round stroke
radius; renders at 512 and 48; preserves original concept files by SHA-256.
All output stays beside this script.
"""
from pathlib import Path
from html.parser import HTMLParser
import hashlib
import json
import math
import re
import subprocess
import xml.etree.ElementTree as ET

import numpy as np
from numpy.polynomial import polynomial as poly
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
REPORT = HERE / 'verification-a2-d.json'
NAMES = ('concept-a2-road-story', 'concept-d-guide-flag')


def segments(d):
    tokens = iter(re.findall(r'[A-Za-z]|-?\d+(?:\.\d+)?', d))
    point = start = None
    for command in tokens:
        if command == 'M':
            point = start = np.array([float(next(tokens)), float(next(tokens))])
            continue
        if command == 'L':
            end = np.array([float(next(tokens)), float(next(tokens))])
        elif command == 'H':
            end = np.array([float(next(tokens)), point[1]])
        elif command == 'V':
            end = np.array([point[0], float(next(tokens))])
        elif command == 'Q':
            control = np.array([float(next(tokens)), float(next(tokens))])
            end = np.array([float(next(tokens)), float(next(tokens))])
            yield np.array([point, 2 * (control - point), point - 2 * control + end])
            point = end
            continue
        elif command == 'Z':
            end = start
        else:
            raise AssertionError(f'Unsupported path command: {command}')
        yield np.array([point, end - point])
        point = end


def roots_between(coefficients):
    if len(coefficients) <= 1:
        return []
    return [float(t.real) for t in poly.polyroots(coefficients)
            if abs(t.imag) < 1e-9 and 0 < t.real < 1]


def path_bound(path, stroke_radius):
    details = []
    for i, coeff in enumerate(segments(path)):
        relative = coeff.copy()
        relative[0] -= 54
        squared_radius = poly.polyadd(poly.polymul(relative[:, 0], relative[:, 0]),
                                      poly.polymul(relative[:, 1], relative[:, 1]))
        candidates = [(0., 'endpoint'), (1., 'endpoint')]
        for axis in (0, 1):
            candidates += [(t, 'xy_extreme') for t in roots_between(poly.polyder(coeff[:, axis]))]
        candidates += [(t, 'radial_extreme') for t in roots_between(poly.polyder(squared_radius))]
        for t, kind in candidates:
            point = [float(poly.polyval(t, coeff[:, axis])) for axis in (0, 1)]
            radius = math.hypot(point[0] - 54, point[1] - 54)
            details.append({'segment': i + 1, 'kind': kind, 't': t, 'point': point,
                            'radius': radius, 'with_stroke': radius + stroke_radius})
    bound = max(row['with_stroke'] for row in details)
    assert bound <= 29 + 1e-9, bound
    return {'path': path, 'stroke_radius': stroke_radius,
            'max_radius': bound, 'clearance': 29 - bound, 'samples': details}


original = json.loads(REPORT.read_text())['original_sha256']
for name, expected in original.items():
    if name not in ('README.md', 'preview.html'):
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == expected, name

report = {'original_sha256': original, 'center': [54, 54], 'safe_radius': 29,
          'method': 'All endpoints, x/y curve extrema, and roots of d(r^2)/dt; '
                    'add 3 for round strokes, 0 for the filled pennant.', 'concepts': {}}
ns = '{http://www.w3.org/2000/svg}'
for name in NAMES:
    root = ET.parse(HERE / f'{name}.svg').getroot()
    assert root.attrib == {'viewBox': '0 0 512 512'}
    bg, inner = list(root)
    assert bg.attrib == {'width': '512', 'height': '512', 'rx': '112', 'fill': '#0B0F14'}
    assert inner.attrib == {'width': '512', 'height': '512', 'viewBox': '18 18 72 72',
                            'fill': 'none', 'stroke-width': '6',
                            'stroke-linecap': 'round', 'stroke-linejoin': 'round'}
    assert 3 <= len(inner) <= 4
    bounds = []
    for shape in inner:
        assert shape.tag == ns + 'path'
        assert set(shape.attrib) <= {'d', 'stroke', 'fill'}
        for attribute in ('stroke', 'fill'):
            if attribute in shape.attrib:
                assert shape.attrib[attribute] in ('#3DDC97', '#E7EEF4', 'none')
        bounds.append(path_bound(shape.attrib['d'], 3 if 'stroke' in shape.attrib else 0))
    report['concepts'][name] = bounds
    for size, suffix in ((512, ''), (48, '-48')):
        subprocess.run(['rsvg-convert', '-w', str(size), '-h', str(size),
                        str(HERE / f'{name}.svg'), '-o', str(HERE / f'{name}{suffix}.png')], check=True)
        assert Image.open(HERE / f'{name}{suffix}.png').size == (size, size)
    print(name)
    for i, bound in enumerate(bounds, 1):
        print(f"  shape {i}: r={bound['max_radius']:.6f}; clearance={bound['clearance']:.6f}")


class Preview(HTMLParser):
    def __init__(self):
        super().__init__()
        self.images = []

    def handle_starttag(self, tag, attrs):
        if tag == 'img':
            self.images.append(dict(attrs))


preview = Preview()
preview.feed((HERE / 'preview.html').read_text())
sources = ['../icon.svg', 'concept-a-road-story.svg', 'concept-b-side-window.svg',
           'concept-c-free.svg', 'concept-a2-road-story.svg', 'concept-d-guide-flag.svg']
assert len(preview.images) == 36
for size in (512, 96, 48):
    images = [row for row in preview.images if row['width'] == str(size)]
    assert [row['src'] for row in images] == [source for source in sources for _ in range(2)]
    assert all(row['height'] == str(size) and (HERE / row['src']).is_file() for row in images)

# Native 48px renders composited on both preview backgrounds for visual inspection.
sheet = Image.new('RGB', (288, 176), '#0B0F14')
draw = ImageDraw.Draw(sheet)
draw.rectangle((144, 0, 287, 175), fill='white')
for col, label in ((0, 'Dark / 48px'), (1, 'Light / 48px')):
    draw.text((col * 144 + 8, 8), label, fill='#E7EEF4' if col == 0 else '#0B0F14')
    for row, (name, label) in enumerate(((NAMES[1], 'D'), (NAMES[0], 'A2'))):
        y = 32 + row * 72
        draw.text((col * 144 + 12, y + 16), label, fill='#E7EEF4' if col == 0 else '#0B0F14')
        icon = Image.open(HERE / f'{name}-48.png').convert('RGBA')
        sheet.paste(icon, (col * 144 + 64, y), icon)
sheet.save(HERE / 'legibility-a2-d-48.png')
report['preview_image_count'] = 36
report['original_concepts_unchanged'] = True
REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print('PASS: structure, palette, safe zone, PNG sizes, preview order and original file hashes.')
