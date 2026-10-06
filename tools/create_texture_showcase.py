#!/usr/bin/env python3
"""
Generates high-fidelity Create Mod / Modern Vanilla (Jappa) style texture comparison showcase.
Creates side-by-side comparison images at 8x scale and raw native pixel-art textures in
tools/preview/texture_comparison/.
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, 'preview', 'texture_comparison')
os.makedirs(OUT_DIR, exist_ok=True)

# ---------------------------------------------------------------------------
# COLOR PALETTES (Create & Vanilla inspired ramps)
# ---------------------------------------------------------------------------
P_LEATHER = [
    (24, 22, 26, 255),    # 0: deep shadow / under-crease
    (42, 38, 44, 255),    # 1: dark leather
    (68, 62, 70, 255),    # 2: base black leather
    (96, 88, 100, 255),   # 3: worn highlight / soft shine
    (135, 126, 142, 255), # 4: specular top light
]

P_STITCH = [
    (140, 95, 45, 255),   # dark thread
    (210, 165, 95, 255),  # bright gold thread
]

P_GUMWALL = [
    (110, 72, 36, 255),   # deep shadow gum
    (150, 102, 54, 255),  # base tan
    (185, 134, 78, 255),  # warm mid gum
    (218, 168, 108, 255), # bright gum highlight
]

P_TIRE_RUBBER = [
    (14, 15, 18, 255),    # void / deep tread shadow
    (26, 28, 32, 255),    # dark rubber
    (40, 43, 50, 255),    # base charcoal rubber
    (62, 66, 76, 255),    # knob edge highlight
    (92, 98, 112, 255),   # dust / surface sheen
]

P_KASHIMA = [
    (98, 62, 18, 255),    # 0: deep bronze shadow
    (148, 102, 32, 255),  # 1: rich amber
    (204, 152, 48, 255),  # 2: base Kashima gold
    (238, 196, 92, 255),  # 3: light reflection
    (255, 235, 165, 255), # 4: specular glint
]

P_ALLOY_RAW = [
    (45, 48, 56, 255),    # shadow steel
    (75, 80, 92, 255),    # dark alloy
    (118, 125, 138, 255), # base raw brushed aluminium
    (168, 176, 192, 255), # bevel highlight
    (220, 226, 240, 255), # specular weld crown
]

P_FLAME = [
    (160, 20, 20, 255),   # dark red outline
    (220, 50, 20, 255),   # fiery red
    (245, 130, 25, 255),  # blazing orange
    (255, 210, 40, 255),  # bright yellow core
    (255, 255, 180, 255), # hot white center
]

P_RETRO = [
    (180, 30, 30, 255),   # red stripe
    (235, 110, 25, 255),  # orange stripe
    (250, 200, 35, 255),  # yellow stripe
    (245, 245, 240, 255), # vintage cream white
    (25, 25, 28, 255),    # dark border
]

# ---------------------------------------------------------------------------
# TEXTURE 1: DIRT JUMP PIVOTAL SADDLE (32x32)
# ---------------------------------------------------------------------------
def make_saddle_old():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    # Flat procedural box like current python scripts
    draw.rectangle([6, 4, 25, 27], fill=(24, 24, 26, 255))
    draw.rectangle([11, 4, 20, 11], fill=(28, 28, 30, 255))
    return im

def make_saddle_new():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()

    # Create / Vanilla style: contoured teardrop with chunky bevels and stitched panels
    for y in range(32):
        for x in range(32):
            dx = abs(x - 15.5)
            # Saddle width curve: narrow nose at top (y 3..11), flared rear (y 14..27)
            if 3 <= y <= 11:
                w = 2.8 + (y - 3) * 0.35
            elif 12 <= y <= 20:
                w = 5.6 + (y - 12) * 0.65
            elif 21 <= y <= 27:
                w = 10.8 - (y - 21) * 0.85
            else:
                continue

            if dx <= w:
                dist_edge = w - dx
                # Base shading (top-left lighting)
                light = 2
                if dist_edge < 1.0:
                    light = 0 # dark perimeter outline
                elif dist_edge < 2.0:
                    light = 1 if x > 15 else 2 # shadow on right edge
                else:
                    if x < 15 and y < 22:
                        light = 3 # top-left surface highlight
                    elif y >= 22:
                        light = 2 # rear bowl

                # Diamond quilt stitching on rear deck
                if 14 <= y <= 25 and dx < w - 1.5:
                    if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                        px[x, y] = P_STITCH[1]
                        continue
                    elif (x + y + 1) % 4 == 0:
                        light = max(0, light - 1)

                # Nose tip reinforcement bumper
                if 3 <= y <= 6 and dx <= w - 0.5:
                    light = 1 if y == 3 else 2

                # Pivotal bolt hole in center
                if 12 <= y <= 15 and dx <= 1.8:
                    if dx <= 0.8 and 13 <= y <= 14:
                        px[x, y] = (16, 16, 18, 255) # deep recess
                    else:
                        px[x, y] = (80, 84, 94, 255) # rubber plug border
                    continue

                # Rear rubber scuff bumper
                if y == 27 and dx < 8:
                    px[x, y] = (20, 20, 24, 255)
                    continue

                px[x, y] = P_LEATHER[min(4, max(0, light))]

    # Add contrast top-edge highlight
    for x in range(13, 19):
        if im.getpixel((x, 4))[3] > 0:
            px[x, 4] = P_LEATHER[3]
    return im

# ---------------------------------------------------------------------------
# TEXTURE 2: MTB / MOTO KNOBBY TIRE (32x32)
# ---------------------------------------------------------------------------
def make_tire_old():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    # Flat grey band + flat tan sidewalls
    draw.rectangle([0, 0, 31, 31], fill=(22, 22, 24, 255))
    draw.rectangle([0, 0, 7, 31], fill=(196, 156, 104, 255))
    draw.rectangle([24, 0, 31, 31], fill=(196, 156, 104, 255))
    return im

def make_tire_new():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()

    # 32x32 repeating tire section:
    # x 0..7: Left Gumwall with woven casing cord texture
    # x 8..23: Center Tread with aggressive alternating knobby blocks & siping
    # x 24..31: Right Gumwall with woven casing cord texture

    # 1. Fill gumwall sidewalls with vintage cord weave
    for y in range(32):
        for x in range(32):
            if x <= 7 or x >= 24:
                # Woven cord angle & subtle radial gradient
                shade = 1
                if x in (0, 31):
                    shade = 0 # bead rim seam
                elif x in (6, 7, 24, 25):
                    shade = 0 # tread transition shadow
                elif (x + y * 2) % 3 == 0:
                    shade = 2 # cord highlight
                elif (x - y) % 4 == 0:
                    shade = 3 # bright grain
                px[x, y] = P_GUMWALL[shade]
            else:
                # Base tread channel (dark void)
                px[x, y] = P_TIRE_RUBBER[0]

    # 2. Add aggressive shoulder knobs (L-shaped aggressive cornering lugs)
    for y in range(0, 32, 8):
        # Left shoulder lug
        for dy in range(5):
            for dx in range(3):
                col = P_TIRE_RUBBER[2]
                if dy == 0 or dx == 0: col = P_TIRE_RUBBER[3] # top-left bevel
                if dy == 4 or dx == 2: col = P_TIRE_RUBBER[1] # shadow edge
                px[8 + dx, (y + dy) % 32] = col
        # L-notch siping cut
        px[9, (y + 2) % 32] = P_TIRE_RUBBER[0]

        # Right shoulder lug (offset for realistic tire pattern)
        for dy in range(5):
            for dx in range(3):
                col = P_TIRE_RUBBER[2]
                if dy == 0 or dx == 0: col = P_TIRE_RUBBER[3]
                if dy == 4 or dx == 2: col = P_TIRE_RUBBER[1]
                px[21 + dx, (y + dy + 4) % 32] = col
        px[22, (y + 6) % 32] = P_TIRE_RUBBER[0]

    # 3. Add alternating Center Tread blocks (ramped rolling blocks)
    for y in range(0, 32, 8):
        # Center pair 1 (paired ramped knobs)
        for dy in range(3):
            for dx in range(3):
                col = P_TIRE_RUBBER[2]
                if dy == 0: col = P_TIRE_RUBBER[4] # ramped leading edge
                elif dy == 2: col = P_TIRE_RUBBER[1]
                px[12 + dx, (y + dy) % 32] = col
                px[17 + dx, (y + dy) % 32] = col
        # Sipe in center
        px[13, (y + 1) % 32] = P_TIRE_RUBBER[0]
        px[18, (y + 1) % 32] = P_TIRE_RUBBER[0]

        # Center paddle 2 (wide square braking knob, staggered)
        for dy in range(3):
            for dx in range(6):
                col = P_TIRE_RUBBER[2]
                if dy == 0: col = P_TIRE_RUBBER[3]
                elif dy == 2: col = P_TIRE_RUBBER[1]
                px[13 + dx, (y + dy + 4) % 32] = col
        px[15, (y + 5) % 32] = P_TIRE_RUBBER[0]
        px[16, (y + 5) % 32] = P_TIRE_RUBBER[0]

    # Add printed yellow brand hotpatch on gumwall (like Maxxis / Michelin yellow badge)
    for dy in range(4):
        for dx in range(4):
            px[2 + dx, 14 + dy] = (245, 205, 30, 255)
    px[3, 15] = (25, 25, 25, 255)
    px[4, 16] = (25, 25, 25, 255)

    return im

# ---------------------------------------------------------------------------
# TEXTURE 3: KASHIMA GOLD FORK STANCHION & SAG GRADIENTS (16x32)
# ---------------------------------------------------------------------------
def make_fork_old():
    im = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.rectangle([3, 0, 12, 31], fill=(201, 162, 75, 255)) # flat gold
    return im

def make_fork_new():
    im = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    px = im.load()

    for y in range(32):
        for x in range(3, 13):
            # Cylindrical light curvature across cylinder (width 10 px)
            cx = x - 3
            if cx == 0:
                tone = 0 # dark occlusion edge
            elif cx in (1, 2):
                tone = 4 # brilliant specular highlight streak
            elif cx in (3, 4, 5):
                tone = 3 # bright front reflection
            elif cx in (6, 7, 8):
                tone = 2 # rich golden Kashima base
            else:
                tone = 1 # back-side shadow

            px[x, y] = P_KASHIMA[tone]

            # Laser-etched sag gradients (red ring and white tick marks)
            if y == 20: # 30% sag O-ring (red nitrile rubber)
                px[x, y] = (210, 35, 30, 255)
            elif y in (10, 15, 25): # laser etched sag ticks
                if cx in (5, 6):
                    px[x, y] = (255, 255, 255, 220)

    # Top stanchion cap & seal wiper
    for x in range(2, 14):
        px[x, 31] = (25, 25, 28, 255) # rubber dust wiper seal
        px[x, 30] = (65, 70, 78, 255) # seal collar
    return im

# ---------------------------------------------------------------------------
# TEXTURE 4: TIG WELDS & RAW ALLOY TUBING (32x32)
# ---------------------------------------------------------------------------
def make_welds_old():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.rectangle([0, 0, 31, 31], fill=(176, 184, 198, 255)) # flat grey
    return im

def make_welds_new():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()

    # Tube cylinder across vertical
    for y in range(32):
        for x in range(32):
            # Horizontal cylindrical gradient
            norm_x = abs(x - 15.5) / 15.5
            if norm_x > 0.85: tone = 0
            elif norm_x > 0.6: tone = 1
            elif x < 12 and norm_x > 0.2: tone = 4 # highlight band
            elif norm_x < 0.3: tone = 3
            else: tone = 2
            px[x, y] = P_ALLOY_RAW[tone]

            # Subtle brushed metal micro-texture
            if (x * 3 + y * 7) % 11 == 0 and tone < 4:
                px[x, y] = P_ALLOY_RAW[tone + 1]

    # Beautiful stacked-dime TIG weld seam (angled weld joint)
    for i, (wx, wy) in enumerate([(8 + k, 10 + k) for k in range(16)]):
        # Each "dime" is a crescent puddle with highlight and shadow puddle edge
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                dist = dx*dx + dy*dy
                if dist <= 4:
                    cur_x, cur_y = wx + dx, wy + dy
                    if 0 <= cur_x < 32 and 0 <= cur_y < 32:
                        if dx + dy < 0:
                            px[cur_x, cur_y] = P_ALLOY_RAW[4] # weld pool crest
                        elif dx + dy > 1:
                            px[cur_x, cur_y] = P_ALLOY_RAW[0] # weld pool under-shadow
                        else:
                            px[cur_x, cur_y] = P_ALLOY_RAW[3]
    return im

# ---------------------------------------------------------------------------
# TEXTURE 5: RETRO & MOTO STICKERS (32x16 & 16x16)
# ---------------------------------------------------------------------------
def make_stickers_sheet():
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)

    # 1. RETRO RACING STRIPES (32x16, left half)
    # Cream badge with classic 3-stripe speed angle
    draw.rounded_rectangle([2, 2, 29, 13], radius=2, fill=P_RETRO[3], outline=P_RETRO[4])
    for x in range(8, 22):
        for y in range(4, 12):
            diag = (x - y)
            if 3 <= diag <= 5:
                im.putpixel((x, y), P_RETRO[0]) # Red
            elif 6 <= diag <= 8:
                im.putpixel((x, y), P_RETRO[1]) # Orange
            elif 9 <= diag <= 11:
                im.putpixel((x, y), P_RETRO[2]) # Yellow

    # Moto Number 07 on the right of the badge
    num_color = P_RETRO[4]
    for dy in range(6):
        im.putpixel((24, 5 + dy), num_color)
    for dx in range(4):
        im.putpixel((21 + dx, 5), num_color)

    # 2. FOX HEAD DOWNHILL EMBLEM (16x16, top right: x 34..48, y 0..15)
    ox, oy = 34, 0
    draw.rectangle([ox, oy, ox+15, oy+15], fill=(30, 30, 34, 255))
    # Geometric fox head mask in gold & white
    fox_gold = (235, 140, 25, 255)
    fox_white = (250, 250, 250, 255)
    # Ears
    im.putpixel((ox+3, oy+2), fox_gold); im.putpixel((ox+12, oy+2), fox_gold)
    im.putpixel((ox+4, oy+3), fox_gold); im.putpixel((ox+11, oy+3), fox_gold)
    im.putpixel((ox+5, oy+4), fox_gold); im.putpixel((ox+10, oy+4), fox_gold)
    # Forehead & snout
    for x in range(ox+4, ox+12):
        im.putpixel((x, oy+5), fox_gold)
        im.putpixel((x, oy+6), fox_gold)
    for x in range(ox+5, ox+11):
        im.putpixel((x, oy+7), fox_gold)
    for x in range(ox+6, ox+10):
        im.putpixel((x, oy+8), fox_gold)
    im.putpixel((ox+7, oy+9), (20, 20, 20, 255)) # nose tip
    im.putpixel((ox+8, oy+9), (20, 20, 20, 255))
    # Slanted aggressive eyes
    im.putpixel((ox+5, oy+6), fox_white); im.putpixel((ox+10, oy+6), fox_white)

    # 3. CLASSIC FLAME (16x16, bottom right: x 48..63, y 16..31)
    fx, fy = 48, 16
    draw.rectangle([fx, fy, fx+15, fy+15], fill=(22, 22, 24, 255))
    flame_pts = [
        # Flame tongues leaping from bottom-left to top-right
        (1, 14, 0), (2, 13, 1), (3, 11, 2), (4, 9, 3), (5, 7, 4), (6, 5, 3), (7, 4, 1), # tongue 1
        (5, 13, 0), (6, 12, 1), (7, 10, 2), (8, 8, 3), (9, 6, 4), (10, 4, 3), (11, 2, 2), (12, 1, 1), # tongue 2
        (9, 14, 0), (10, 13, 1), (11, 11, 2), (12, 9, 3), (13, 7, 2), (14, 6, 1) # tongue 3
    ]
    for lx, ly, c_idx in flame_pts:
        if 0 <= lx < 16 and 0 <= ly < 16:
            im.putpixel((fx + lx, fy + ly), P_FLAME[c_idx])
            if ly + 1 < 16:
                im.putpixel((fx + lx, fy + ly + 1), P_FLAME[max(0, c_idx - 1)])

    return im

# ---------------------------------------------------------------------------
# MASTER COMPARISON POSTER BUILDER (8x UPSCALE WITH LABELS)
# ---------------------------------------------------------------------------
def build_comparison_poster():
    scale = 8
    pad = 24
    row_h = 32 * scale
    col_w = 32 * scale

    # Poster layout: 4 rows x 2 cols (Old vs New)
    # Row 0: Dirt Jump Saddle
    # Row 1: MTB / Moto Knobby Gumwall Tire
    # Row 2: Kashima Fork Stanchion & Sag
    # Row 3: TIG Welds & Alloy Tubing

    poster_w = pad * 3 + col_w * 2
    poster_h = pad * 5 + row_h * 4 + 70 # extra top banner for title

    poster = Image.new('RGB', (poster_w, poster_h), (28, 30, 36))
    draw = ImageDraw.Draw(poster)

    # Title Banner
    draw.rectangle([0, 0, poster_w, 64], fill=(20, 21, 25))
    draw.text((pad, 16), "DESCENT MTB — TEXTURE REWORK COMPARISON (CREATE / VANILLA STYLE)", fill=(240, 242, 248))
    draw.text((pad, 38), "Porovnani: Vlevo stara proceduralni grafika | Vpravo novy detailni pixel-art", fill=(160, 168, 185))

    rows = [
        ("DIRT JUMP PIVOTAL SEDLO", make_saddle_old(), make_saddle_new()),
        ("KNOBBY PLAST S GUMWALL BOKEM", make_tire_old(), make_tire_new()),
        ("KASHIMA GOLD VIDLICE A SAG", make_fork_old(), make_fork_new()),
        ("TIG SVARY A HLINIKOVY RAM", make_welds_old(), make_welds_new()),
    ]

    for i, (title, old_tex, new_tex) in enumerate(rows):
        y_top = 70 + pad + i * (row_h + pad)

        # Label
        draw.text((pad, y_top - 18), f"#{i+1}: {title}", fill=(235, 180, 50))
        draw.text((pad, y_top + row_h + 4), "STARE (Proceduralni plochy)", fill=(140, 145, 155))
        draw.text((pad * 2 + col_w, y_top + row_h + 4), "NOVE (Create / Jappa pixel-art)", fill=(100, 220, 140))

        # Checkerboard background behind transparent textures
        for c, tex in enumerate([old_tex, new_tex]):
            x_left = pad + c * (col_w + pad)
            # draw checkerboard
            for cy in range(0, row_h, 16):
                for cx in range(0, col_w, 16):
                    chk = (38, 40, 48) if ((cx // 16 + cy // 16) % 2 == 0) else (32, 34, 40)
                    draw.rectangle([x_left + cx, y_top + cy, x_left + cx + 15, y_top + cy + 15], fill=chk)

            # Paste upscaled texture (NEAREST filter)
            tex_scaled = tex.resize((tex.width * scale, tex.height * scale), Image.Resampling.NEAREST)
            # Center if narrower than 32
            offset_x = (col_w - tex_scaled.width) // 2
            poster.paste(tex_scaled, (x_left + offset_x, y_top), tex_scaled)
            draw.rectangle([x_left, y_top, x_left + col_w - 1, y_top + row_h - 1], outline=(55, 60, 72), width=2)

    poster_path = os.path.join(OUT_DIR, 'porovnani_textur_create_style.png')
    poster.save(poster_path)
    print(f"Generated comparison poster: {poster_path}")

    # Also save native individual textures
    make_saddle_new().save(os.path.join(OUT_DIR, 'saddle_dirt_pivotal_32x32.png'))
    make_tire_new().save(os.path.join(OUT_DIR, 'tire_knobby_gumwall_32x32.png'))
    make_fork_new().save(os.path.join(OUT_DIR, 'fork_kashima_gold_16x32.png'))
    make_welds_new().save(os.path.join(OUT_DIR, 'frame_welds_alloy_32x32.png'))
    make_stickers_sheet().save(os.path.join(OUT_DIR, 'stickers_showcase_64x32.png'))

    # And 8x scaled versions of individual textures
    for name, img in [
        ('saddle_dirt_pivotal_8x.png', make_saddle_new()),
        ('tire_knobby_gumwall_8x.png', make_tire_new()),
        ('stickers_showcase_8x.png', make_stickers_sheet()),
    ]:
        img.resize((img.width * 8, img.height * 8), Image.Resampling.NEAREST).save(os.path.join(OUT_DIR, name))

if __name__ == '__main__':
    build_comparison_poster()
