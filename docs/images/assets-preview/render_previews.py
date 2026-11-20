"""Convert the source VectorDrawables to SVG and render the comparison sheet.
Run from any directory: python3 docs/images/assets-preview/render_previews.py
Requires rsvg-convert and Pillow.
"""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
DRAWABLES = ROOT / 'vehicle-app/design-system/src/main/res/drawable'
A = '{http://schemas.android.com/apk/res/android}'
NEW = ['ic_battery', 'ic_seat_passenger', 'ic_restart', 'ic_volume']
EXISTING = ['ic_story', 'ic_stamp', 'ic_directions_walk', 'ic_ev_station']
BG = '#0B0F14'


def convert(name):
    vector = ET.parse(DRAWABLES / f'{name}.xml').getroot()
    width, height = (vector.get(A + attr) for attr in ('viewportWidth', 'viewportHeight'))
    svg = ET.Element('svg', {'xmlns': 'http://www.w3.org/2000/svg',
                            'viewBox': f'0 0 {width} {height}',
                            'width': width, 'height': height})
    if name != 'img_place_placeholder':
        ET.SubElement(svg, 'rect', {'width': width, 'height': height, 'fill': BG})
    for source in vector.findall('path'):
        attrs = {'d': source.get(A + 'pathData'),
                 'fill': source.get(A + 'fillColor').replace('@android:color/white', '#FFFFFF')}
        if source.get(A + 'fillType') == 'evenOdd':
            attrs['fill-rule'] = 'evenodd'
        if source.get(A + 'fillAlpha'):
            attrs['fill-opacity'] = source.get(A + 'fillAlpha')
        ET.SubElement(svg, 'path', attrs)
    ET.indent(svg, space='  ')
    ET.ElementTree(svg).write(HERE / f'{name}.svg', encoding='unicode', xml_declaration=True)


for name in NEW + EXISTING + ['img_place_placeholder']:
    convert(name)
    sizes = [(640, 360)] if name == 'img_place_placeholder' else [(96, 96), (24, 24)]
    for w, h in sizes:
        target = HERE / (f'{name}.png' if w == 640 else f'{name}-{w}.png')
        subprocess.run(['rsvg-convert', '--width', str(w), '--height', str(h),
                        '--output', str(target), str(HERE / f'{name}.svg')], check=True)

sheet = Image.new('RGB', (1200, 760), BG)
draw = ImageDraw.Draw(sheet)
font_path = '/System/Library/Fonts/Menlo.ttc'
font = ImageFont.truetype(font_path, 12)
heading = ImageFont.truetype(font_path, 17)
draw.text((28, 18), 'GILBEOT / VECTOR ASSETS', font=heading, fill='#FFFFFF')
draw.text((28, 53), 'NEW', font=font, fill='#3DDC97')
draw.text((628, 53), 'EXISTING REFERENCES', font=font, fill='#B9C2CE')
for i, name in enumerate(NEW + EXISTING):
    x = i * 150 + 75
    sheet.paste(Image.open(HERE / f'{name}-96.png'), (x - 48, 84))
    sheet.paste(Image.open(HERE / f'{name}-24.png'), (x - 12, 219))
    label = name.removeprefix('ic_')
    draw.text((x, 259), label, font=font, fill='#B9C2CE', anchor='mt')
draw.text((28, 189), '96 px', font=font, fill='#768394')
draw.text((28, 292), '24 px / native size', font=font, fill='#768394')
draw.line((600, 53, 600, 282), fill='#26313D')
draw.text((28, 337), 'PLACE FALLBACK / 640 x 360', font=font, fill='#B9C2CE')
sheet.paste(Image.open(HERE / 'img_place_placeholder.png'), (280, 370))
sheet.save(HERE / 'preview.png')
print(f'Rendered 9 SVGs, 17 asset PNGs, and {HERE / "preview.png"}')
