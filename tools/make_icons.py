"""Generates the Ai Calculator launcher icons from the artwork in assets/app-icon.svg.

Run from the repository root:

    python3 tools/make_icons.py            # Pillow only, no other requirements

The SVG is the design source; this script draws the same artwork with Pillow
(no SVG renderer is available in CI, and Pillow is all that is needed) and writes:

    app/src/main/res/mipmap-*/ic_launcher.png                 legacy square
    app/src/main/res/mipmap-*/ic_launcher_round.png           legacy round
    app/src/main/res/mipmap-*/ic_launcher_background.png      adaptive, full bleed
    app/src/main/res/mipmap-*/ic_launcher_foreground.png      adaptive, safe zone
    app/src/main/res/mipmap-*/ic_launcher_monochrome.png      adaptive, themed
    assets/play-store-icon-512.png                            Play Store listing

Both the adaptive layers and the legacy icons come from one drawing routine, so
they can never drift apart. Re-run this after changing the design.
"""

import os
import math

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "assets")

SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"
SERIF_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
SANS_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# ---- palette, straight from the SVG ------------------------------------------
BASE_STOPS = [(0.0, (0x31, 0x2E, 0x81)), (0.40, (0x1E, 0x1B, 0x4B)), (1.0, (0x0F, 0x17, 0x2A))]
BTN_STOPS = [(0.0, (0x33, 0x41, 0x55)), (1.0, (0x1E, 0x29, 0x3B))]
OP_STOPS = [(0.0, (0x8B, 0x5C, 0xF6)), (1.0, (0x6D, 0x28, 0xD9))]
SCREEN_STOPS = [(0.0, (0x02, 0x06, 0x17)), (1.0, (0x0F, 0x17, 0x2A))]

CYAN = (0x22, 0xD3, 0xEE)
VIOLET = (0x8B, 0x5C, 0xF6)
MAGENTA = (0xD9, 0x46, 0xEF)
WHITE = (0xFF, 0xFF, 0xFF)
BODY_FILL = (0x0F, 0x17, 0x2A)
LINE = (0x1E, 0x29, 0x3B)
BTN_LINE = (0x47, 0x55, 0x69)
OP_LINE = (0x7C, 0x3A, 0xED)
SCREEN_EDGE = (0x1E, 0x29, 0x3B)

# Geometry in the SVG's 512×512 space.
BASE = (32, 32, 480, 480, 105)
BODY = (64, 64, 448, 448, 60)
SCREEN = (80, 80, 432, 220, 30)
GRID = 24
BTN_W, BTN_H, BTN_R = 70, 50, 15
BTN_X = (80, 162, 244, 326)
BTN_Y = (240, 300, 360, 420)
# (column, row) -> symbol; the right-hand column is the operator column.
KEYS = [
    (0, 0, "π", False), (1, 0, "e", False), (2, 0, "∫", False), (3, 0, "÷", True),
    (0, 1, "∑", False), (1, 1, "√", False), (2, 1, "Ai", False), (3, 1, "×", True),
    (0, 2, "Δ", False), (1, 2, "∂", False), (2, 2, "∇", False), (3, 2, "−", True),
    (0, 3, "±", False), (1, 3, "≈", False), (2, 3, "=", False), (3, 3, "+", True),
]

SS = 4  # supersampling factor
# Render scale: the SVG's 512-unit space -> pixels for the icon being drawn.
# Set by design() before any geometry is used, so every size shares one drawing
# routine without shrinking badly.
_SCALE = float(SS)


def px(value):
    """512-space -> render-space (rounded, so it can be used for sizes)."""
    return int(round(value * _SCALE))


def box(values, pad=0):
    """(x0, y0, x1, y1, r) -> ((x0, y0, x1, y1), r) in render space."""
    x0, y0, x1, y1 = values[:4]
    radius = values[4] if len(values) > 4 else 0
    return (px(x0 - pad), px(y0 - pad), px(x1 + pad), px(y1 + pad)), px(radius)


def gradient(size, stops, angle=90):
    """A linear gradient image: 90 = top→bottom, 135 = corner→corner."""
    span = max(2, int(size * 1.5))
    strip = Image.new("RGB", (1, span))
    for y in range(span):
        t = y / (span - 1)
        colour = stop_colour(stops, t)
        strip.putpixel((0, y), colour)
    strip = strip.resize((span, span), Image.BILINEAR)
    if angle == 90:
        image = strip.resize((size, size), Image.BILINEAR)
    else:
        rotated = strip.rotate(angle - 90, resample=Image.BILINEAR, expand=True)
        left = (rotated.width - size) // 2
        top = (rotated.height - size) // 2
        image = rotated.crop((left, top, left + size, top + size))
    return image


def stop_colour(stops, t):
    previous = stops[0]
    for current in stops:
        if t <= current[0]:
            span = current[0] - previous[0]
            local = 0.0 if span == 0 else (t - previous[0]) / span
            return tuple(
                int(previous[1][i] + (current[1][i] - previous[1][i]) * local) for i in range(3)
            )
        previous = current
    return stops[-1][1]


def rounded_mask(size, values, pad=0):
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    rect, radius = box(values, pad=pad)
    draw.rounded_rectangle(rect, radius=radius, fill=255)
    return mask


def paint(base, mask, fill):
    """Composites a solid colour or an image inside a mask."""
    layer = fill if isinstance(fill, Image.Image) else Image.new("RGB", base.size, fill)
    base.paste(layer, (0, 0), mask)


def soft_shadow(canvas, mask, offset_y, blur, opacity):
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shifted = Image.new("L", canvas.size, 0)
    shifted.paste(mask, (0, int(offset_y)))
    shadow.paste((0, 0, 0, int(255 * opacity)), (0, 0), shifted.filter(ImageFilter.GaussianBlur(blur)))
    return Image.alpha_composite(canvas, shadow)


def glow(mask, colour, blur, opacity):
    layer = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    tinted = Image.new("RGBA", mask.size, colour + (0,))
    tinted.putalpha(mask.filter(ImageFilter.GaussianBlur(blur)).point(lambda a: int(a * opacity)))
    layer = Image.alpha_composite(layer, tinted)
    return layer


def symbol_font(size, bold=False, family="serif"):
    if family == "sans":
        return ImageFont.truetype(SANS_BOLD if bold else SANS, size)
    return ImageFont.truetype(SERIF_BOLD if bold else SERIF, size)


def draw_equation(draw, canvas):
    """∫₀ e^-x² dx = √π, drawn piece by piece so the colours match the SVG."""
    parts = [
        ("∫", 42, 0, CYAN, "serif"),
        ("0", 22, 14, WHITE, "serif"),        # subscript
        (" e", 42, 0, WHITE, "serif"),
        ("-x²", 24, -16, WHITE, "serif"),     # superscript
        (" dx = ", 42, 0, WHITE, "serif"),
        ("√π", 42, 0, (0xA8, 0x55, 0xF7), "sans"),
    ]
    fonts = {}
    widths = []
    baseline_y = px(155)
    for text, size, dy, _colour, family in parts:
        key = (size, family)
        if key not in fonts:
            fonts[key] = symbol_font(px(size), family=family)
        left, top, right, bottom = draw.textbbox((0, 0), text, font=fonts[key])
        widths.append((text, fonts[key], right - left, dy, left))
    total = sum(width for _t, _f, width, _dy, _left in widths)
    cursor = px(256) - total / 2
    for text, font, width, dy, left in widths:
        colour = next(p[3] for p in parts if p[0] == text)
        draw.text((cursor - left, baseline_y + px(dy)), text, font=font, fill=colour)
        cursor += width


def draw_key(canvas, column, row, label, operator):
    x = BTN_X[column]
    y = BTN_Y[row]
    geometry = (x, y, x + BTN_W, y + BTN_H, BTN_R)
    mask = rounded_mask(canvas.width, geometry)

    if operator:
        canvas.alpha_composite(glow(mask, OP_STOPS[0][1], px(4), 0.55))

    # Body: vertical gradient, then the top-half highlight from the SVG.
    paint(canvas, mask, gradient(canvas.width, OP_STOPS if operator else BTN_STOPS, angle=90))
    highlight = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    upper = (x, y, x + BTN_W, y + BTN_H / 2, BTN_R)
    top_mask = rounded_mask(canvas.width, upper)
    strip = gradient(canvas.width, [(0.0, (0xFF, 0xFF, 0xFF)), (1.0, (0xFF, 0xFF, 0xFF))], 90)
    faded = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    faded.paste(strip, (0, 0))
    faded.putalpha(top_mask.point(lambda a: int(a * 0.20)))
    highlight = Image.alpha_composite(highlight, faded)
    canvas.alpha_composite(highlight)

    draw = ImageDraw.Draw(canvas)
    rect, radius = box(geometry)
    draw.rounded_rectangle(
        rect,
        radius=radius,
        outline=OP_LINE if operator else BTN_LINE,
        width=max(1, int(SS)),
    )
    font = symbol_font(px(26), bold=label == "=")
    draw.text(
        (px(x + BTN_W / 2), px(y + BTN_H / 2)),
        label,
        font=font,
        fill=WHITE,
        anchor="mm",
    )


def draw_particles(canvas):
    draw = ImageDraw.Draw(canvas)
    sparkle = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sparkle_draw = ImageDraw.Draw(sparkle)
    dots = [
        (430, 110, 3, CYAN, 1.0),
        (445, 125, 2, (0xA8, 0x55, 0xF7), 1.0),
        (420, 135, 4, WHITE, 0.9),
        (440, 150, 2, MAGENTA, 1.0),
        (130, 110, 3, CYAN, 0.8),
        (380, 120, 4, (0xA8, 0x55, 0xF7), 0.9),
        (150, 190, 2, MAGENTA, 0.7),
        (360, 180, 3, WHITE, 0.8),
    ]
    for cx, cy, r, colour, opacity in dots:
        alpha = int(255 * opacity)
        octagon = [
            (px(cx + r * math.cos(a * math.pi / 4)), px(cy + r * math.sin(a * math.pi / 4)))
            for a in range(8)
        ]
        sparkle_draw.polygon(octagon, fill=colour + (alpha,))
    sparkle_draw.polygon(
        [
            (px(455), px(95)), (px(460), px(110)), (px(475), px(115)), (px(460), px(120)),
            (px(455), px(135)), (px(450), px(120)), (px(435), px(115)), (px(450), px(110)),
        ],
        fill=CYAN + (204,),
    )
    canvas.alpha_composite(glow(sparkle.getchannel("A"), CYAN, px(2.5), 0.7))
    canvas.alpha_composite(sparkle)


def draw_screen(canvas):
    """The readout: gradient, inner shadow, grid, reflection and the equation."""
    screen_mask = rounded_mask(canvas.width, SCREEN)
    paint(canvas, screen_mask, gradient(canvas.width, SCREEN_STOPS, angle=90))

    # Grid lines, clipped to the screen.
    grid = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    grid_draw = ImageDraw.Draw(grid)
    start_x, start_y = px(SCREEN[0]), px(SCREEN[1])
    end_x, end_y = px(SCREEN[2]), px(SCREEN[3])
    step = px(GRID)
    x = start_x
    while x <= end_x:
        grid_draw.line([(x, start_y), (x, end_y)], fill=LINE + (255,), width=1)
        x += step
    y = start_y
    while y <= end_y:
        grid_draw.line([(start_x, y), (end_x, y)], fill=LINE + (255,), width=1)
        y += step
    grid.putalpha(grid.getchannel("A").point(lambda a: min(a, 102)))
    canvas.paste(Image.alpha_composite(canvas, grid), (0, 0), screen_mask)

    # Top-half reflection, like the SVG's screenReflect.
    reflection = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    reflection.paste(gradient(canvas.width, [(0.0, WHITE), (1.0, WHITE)], 90), (0, 0))
    upper = (SCREEN[0], SCREEN[1], SCREEN[2], SCREEN[1] + (SCREEN[3] - SCREEN[1]) / 2, SCREEN[4])
    reflection.putalpha(rounded_mask(canvas.width, upper).point(lambda a: int(a * 0.10)))
    canvas.alpha_composite(reflection)

    # Inner shadow along the top edge.
    ring = Image.new("L", canvas.size, 0)
    ring_draw = ImageDraw.Draw(ring)
    rect, radius = box(SCREEN)
    ring_draw.rounded_rectangle(rect, radius=radius, fill=255)
    inner = Image.new("L", canvas.size, 0)
    inner_draw = ImageDraw.Draw(inner)
    inset, inset_radius = box(SCREEN, pad=-4)
    inner_draw.rounded_rectangle(inset, radius=inset_radius, fill=255)
    ring = Image.composite(Image.new("L", canvas.size, 0), ring, inner)
    dark = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    dark.putalpha(ring.filter(ImageFilter.GaussianBlur(px(3))).point(lambda a: int(a * 0.85)))
    canvas.alpha_composite(dark)

    # Equation, glowing then crisp.
    equation = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw_equation(ImageDraw.Draw(equation), canvas)
    canvas.alpha_composite(glow(equation.getchannel("A"), CYAN, px(6), 0.85))
    canvas.alpha_composite(equation)

    draw = ImageDraw.Draw(canvas)
    rect, radius = box(SCREEN)
    draw.rounded_rectangle(rect, radius=radius, outline=SCREEN_EDGE, width=max(1, int(SS)))


def draw_art(canvas, with_body=True):
    """The calculator itself: body, screen, keys and particles."""
    if with_body:
        body_mask = rounded_mask(canvas.width, BODY)
        paint(canvas, body_mask, BODY_FILL)
        draw = ImageDraw.Draw(canvas)
        rect, radius = box(BODY)
        draw.rounded_rectangle(rect, radius=radius, outline=LINE, width=max(1, int(SS)))
    draw_screen(canvas)
    for column, row, label, operator in KEYS:
        draw_key(canvas, column, row, label, operator)
    draw_particles(canvas)


def render_base(canvas):
    """The gradient tile behind everything, plus its glass highlight."""
    base_mask = rounded_mask(canvas.width, BASE)
    paint(canvas, base_mask, gradient(canvas.width, BASE_STOPS, angle=135))

    # Glass sheen: a soft diagonal highlight in the top-left, like the SVG path.
    sheen = Image.new("L", canvas.size, 0)
    sheen_draw = ImageDraw.Draw(sheen)
    sheen_draw.polygon(
        [
            (px(32), px(137)), (px(137), px(32)), (px(375), px(32)),
            (px(256), px(100)), (px(137), px(261)), (px(80), px(330)), (px(32), px(385)),
        ],
        fill=255,
    )
    sheen = Image.composite(
        sheen.filter(ImageFilter.GaussianBlur(px(6))),
        Image.new("L", canvas.size, 0),
        base_mask,
    )
    veil = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    veil.putalpha(sheen.point(lambda a: int(a * 0.08)))
    canvas.alpha_composite(veil)

    draw = ImageDraw.Draw(canvas)
    rect, radius = box(BASE)
    draw.rounded_rectangle(rect, radius=radius, outline=WHITE + (38,), width=max(1, int(SS)))
    inner_rect, inner_radius = box(BASE, pad=-2)
    draw.rounded_rectangle(
        inner_rect, radius=inner_radius, outline=(0, 0, 0, 76), width=max(1, int(SS))
    )


def design(size, rounded=True, shadow=True, with_base=True, art_scale=1.0, monochrome=False):
    """Draws the whole icon at [size]×[size] and returns it as RGBA."""
    global _SCALE
    _SCALE = SS * size / 512.0
    canvas = Image.new("RGBA", (px(512), px(512)), (0, 0, 0, 0))

    if with_base and rounded:
        if shadow:
            canvas = soft_shadow(canvas, rounded_mask(canvas.width, BASE), px(12), px(20), 0.6)
        render_base(canvas)
    elif with_base:
        paint(canvas, Image.new("L", canvas.size, 255), gradient(canvas.width, BASE_STOPS, 135))

    if monochrome:
        draw_monochrome(canvas, art_scale)
    elif art_scale == 1.0:
        draw_art(canvas, with_body=True)
    else:
        art = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        draw_art(art, with_body=True)
        target = int(canvas.width * art_scale)
        art = art.resize((target, target), Image.LANCZOS)
        offset = (canvas.width - target) // 2
        canvas.alpha_composite(art, (offset, offset))

    return canvas.resize((size, size), Image.LANCZOS)


def draw_monochrome(canvas, art_scale=1.0):
    """A one-colour silhouette for Android 13+ themed icons (tinted by the OS)."""
    art = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    body_mask = rounded_mask(art.width, BODY)
    art.paste(WHITE + (46,), (0, 0), body_mask)
    screen_mask = rounded_mask(art.width, SCREEN)
    art.paste(WHITE + (64,), (0, 0), screen_mask)
    draw = ImageDraw.Draw(art)
    for column, row, _label, _operator in KEYS:
        x = BTN_X[column]
        y = BTN_Y[row]
        mask = rounded_mask(art.width, (x, y, x + BTN_W, y + BTN_H, BTN_R))
        art.paste(WHITE + (110,), (0, 0), mask)
    draw_equation(draw, canvas)
    equation = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw_equation(ImageDraw.Draw(equation), canvas)
    white_equation = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    white_equation.paste(WHITE + (255,), (0, 0), equation.getchannel("A"))
    art.alpha_composite(white_equation)

    if art_scale == 1.0:
        canvas.alpha_composite(art)
    else:
        target = int(canvas.width * art_scale)
        resized = art.resize((target, target), Image.LANCZOS)
        offset = (canvas.width - target) // 2
        canvas.alpha_composite(resized, (offset, offset))


def write(image, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    return os.path.getsize(path)


def main():
    written = 0

    # Legacy launcher icons, one per density.
    for density, scale in DENSITIES.items():
        size = int(48 * scale)
        folder = os.path.join(RES, "mipmap-" + density)
        square = design(size, rounded=True, shadow=True)
        written += write(square, os.path.join(folder, "ic_launcher.png"))

        circle = square.copy()
        round_mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(round_mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        circle.putalpha(round_mask)
        written += write(circle, os.path.join(folder, "ic_launcher_round.png"))

        # Adaptive layers: 108dp canvas, art kept inside the safe zone.
        layer = int(108 * scale)
        background = design(layer, with_base=True, rounded=False, shadow=False)
        written += write(background, os.path.join(folder, "ic_launcher_background.png"))

        foreground = design(layer, with_base=False, art_scale=0.85)
        written += write(foreground, os.path.join(folder, "ic_launcher_foreground.png"))

        monochrome = design(layer, with_base=False, art_scale=0.85, monochrome=True)
        written += write(monochrome, os.path.join(folder, "ic_launcher_monochrome.png"))

    # Play Store listing: full-bleed square, no rounding, no shadow.
    store = design(512, with_base=True, rounded=False, shadow=False)
    written += write(store, os.path.join(ASSETS, "play-store-icon-512.png"))

    print("icons written:", written, "bytes across", len(DENSITIES), "densities")


if __name__ == "__main__":
    main()
