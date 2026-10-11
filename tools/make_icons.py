#!/usr/bin/env python3
"""Draws the AI Calculator launcher icon and writes every Android variant.

    python3 tools/make_icons.py            # write the icons + the preview sheets

The design comes from the SVG the app owner supplied: a dark squircle keypad with
+, x, - and an orange "=" key, faint orange circuit traces behind it and a cyan
"AI" badge in the corner. The build environment has no SVG rasteriser (no cairo,
no rsvg, no root to install one), so the same artwork is redrawn as plain
geometry with Pillow. Every measurement below is in the SVG's own coordinates —
a 640-unit viewBox spanning -64..576 — and is scaled by `S` at draw time, with 4x
supersampling, so one shape set covers 48 px through 512 px.

Outputs, per density (mdpi 48 · hdpi 72 · xhdpi 96 · xxhdpi 144 · xxxhdpi 192):

    ic_launcher.png              the rounded tile (pre-Android 8)
    ic_launcher_round.png        the same art, circle-clipped
    ic_launcher_background.png   adaptive background layer (108dp canvas)
    ic_launcher_foreground.png   adaptive foreground layer (108dp canvas)
    ic_launcher_monochrome.png   themed-icon glyph (Android 13+)

plus assets/play-store-icon-512.png and assets/app-icon-preview.png.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4                      # supersampling factor
VIEW = 640.0                # the SVG's viewBox side
ORIGIN = -64.0              # the SVG's viewBox origin (min-x / min-y)

# --- palette ----------------------------------------------------------------- #
BG_TOP = (36, 38, 41)       # #242629
BG_BOTTOM = (20, 21, 23)    # #141517
KEY_TOP = (85, 88, 94)      # #55585E
KEY_BOTTOM = (53, 55, 59)   # #35373B
ACCENT_TOP = (255, 122, 0)  # #FF7A00
ACCENT_BOTTOM = (230, 81, 0)  # #E65100
SYMBOL = (241, 243, 245)    # #F1F3F5
TRACE = (255, 138, 0)       # #FF8A00
BADGE_FILL = (23, 26, 33)   # #171A21
BADGE_EDGE = (0, 229, 255)  # #00E5FF
BADGE_TEXT = (230, 252, 255)  # #E6FCFF

# --- how much the art shrinks inside the 108dp adaptive layers ---------------- #
# Android guarantees a 36dp radius stays visible whatever mask the launcher uses;
# the full keypad+badge cluster reaches 43.5dp at 1.0, so it is scaled to fit.
FOREGROUND_SCALE = 0.80
MONO_SCALE = 0.90

# --- geometry, in SVG units --------------------------------------------------- #
TILE = (32.0, 32.0, 480.0, 480.0)   # x0, y0, x1, y1 of the squircle
TILE_RADIUS = 108.0
KEY = 144.0
KEY_RADIUS = 46.0
KEYS = {
    "plus": (96.0, 96.0),
    "times": (272.0, 96.0),
    "minus": (96.0, 272.0),
    "equals": (272.0, 272.0),
}
STROKE = 18.0               # symbol stroke width
TRACE_WIDTH = 2.5
BADGE = (336.0, 66.0, 430.0, 116.0)  # x0, y0, x1, y1 (the "AI" pill)
BADGE_RADIUS = 25.0
SPARK_CENTER = (408.0, 91.0)
SPARK_RADIUS = 10.0

TRACES = [
    [(330, 265), (330, 235), (310, 235)],
    [(370, 265), (370, 225), (400, 225)],
    [(270, 330), (240, 330), (240, 310)],
    [(270, 370), (230, 370), (230, 400)],
    [(330, 435), (330, 455), (310, 455)],
    [(370, 435), (370, 465), (395, 465)],
]
TRACE_DOTS = [(310, 235), (400, 225), (240, 310), (230, 400), (310, 455), (395, 465)]

SYMBOLS = {
    "plus": [((168, 138), (168, 198)), ((138, 168), (198, 168))],
    "times": [((323, 147), (365, 189)), ((365, 147), (323, 189))],
    "minus": [((138, 344), (198, 344))],
    "equals": [((320, 330), (368, 330)), ((320, 358), (368, 358))],
}


def font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    for folder in ("/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/dejavu"):
        path = Path(folder) / name
        if path.exists():
            return ImageFont.truetype(str(path), max(1, int(round(size))))
    sys.exit("DejaVu fonts are missing; install fonts-dejavu-core.")


class Design:
    """Maps SVG coordinates onto a pixel canvas."""

    def __init__(self, size: int):
        self.size = size
        self.k = size / VIEW            # pixels per SVG unit

    def px(self, value: float) -> float:
        return (value - ORIGIN) * self.k

    def length(self, value: float) -> float:
        return value * self.k

    def point(self, pt) -> tuple[float, float]:
        return (self.px(pt[0]), self.px(pt[1]))


def vertical_gradient(canvas: Image.Image, rect, top, bottom) -> None:
    """Paints a vertical gradient inside `rect` (SVG units) on `canvas`."""
    d = Design(canvas.width)
    x0, y0, x1, y1 = rect
    left, top_px = d.px(x0), d.px(y0)
    right, bottom_px = d.px(x1), d.px(y1)
    height = max(1, int(round(bottom_px - top_px)))
    strip = Image.new("RGB", (1, height))
    for i in range(height):
        t = i / max(1, height - 1)
        strip.putpixel((0, i), tuple(int(top[c] + (bottom[c] - top[c]) * t) for c in range(3)))
    strip = strip.resize((max(1, int(round(right - left))), height), Image.NEAREST)
    canvas.paste(strip, (int(round(left)), int(round(top_px))))


def rounded_mask(canvas_size: int, rect, radius: float) -> Image.Image:
    d = Design(canvas_size)
    mask = Image.new("L", (canvas_size, canvas_size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (d.px(rect[0]), d.px(rect[1]), d.px(rect[2]), d.px(rect[3])),
        radius=d.length(radius),
        fill=255,
    )
    return mask


def drop_shadow(mask: Image.Image, colour, offset: float, blur: float, alpha: int) -> Image.Image:
    """A coloured, blurred copy of `mask`, ready to be pasted under the shape."""
    layer = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    shifted = Image.new("L", mask.size, 0)
    shifted.paste(mask, (0, int(round(offset))))
    shadow = shifted.filter(ImageFilter.GaussianBlur(max(0.5, blur)))
    shadow = shadow.point(lambda v: int(v * alpha / 255))
    layer.paste((*colour, 255), (0, 0), shadow)
    return layer


def draw_keys(canvas: Image.Image, *, shadows: bool) -> None:
    d = Design(canvas.width)
    for name, (x, y) in KEYS.items():
        rect = (x, y, x + KEY, y + KEY)
        mask = rounded_mask(canvas.width, rect, KEY_RADIUS)
        if shadows:
            canvas.alpha_composite(
                drop_shadow(mask, (0, 0, 0), d.length(10), d.length(10), 150)
            )
        key = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        accent = name == "equals"
        vertical_gradient(key, rect, ACCENT_TOP if accent else KEY_TOP,
                          ACCENT_BOTTOM if accent else KEY_BOTTOM)
        key.putalpha(mask)
        canvas.alpha_composite(key)

        # inner highlight: a hairline just inside the top of the key
        inset = 2.0
        inner = rounded_mask(
            canvas.width, (x + inset, y + inset, x + KEY - inset, y + KEY - inset), KEY_RADIUS - 2
        )
        edge = Image.new("L", canvas.size, 0)
        ImageDraw.Draw(edge).rounded_rectangle(
            (d.px(x + inset), d.px(y + inset), d.px(x + KEY - inset), d.px(y + KEY - inset)),
            radius=d.length(KEY_RADIUS - 2),
            outline=255,
            width=max(1, int(round(d.length(2)))),
        )
        edge = Image.composite(edge, Image.new("L", canvas.size, 0), inner)
        highlight = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        highlight.paste((255, 255, 255, 63 if not accent else 30), (0, 0), edge)
        canvas.alpha_composite(highlight)

        # the symbol
        pen = ImageDraw.Draw(canvas)
        line_colour = (255, 255, 255) if accent else SYMBOL
        for (a, b) in SYMBOLS[name]:
            pen.line([d.point(a), d.point(b)], fill=(*line_colour, 255), width=max(1, int(round(d.length(STROKE)))))
            radius = d.length(STROKE) / 2
            for pt in (a, b):
                cx, cy = d.point(pt)
                pen.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(*line_colour, 255))


def draw_traces(canvas: Image.Image) -> None:
    d = Design(canvas.width)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    pen = ImageDraw.Draw(layer)
    width = max(1, int(round(d.length(TRACE_WIDTH))))
    radius = d.length(TRACE_WIDTH) / 2
    for path in TRACES:
        pen.line([d.point(p) for p in path], fill=(*TRACE, 255), width=width)
        for end in (path[0], path[-1]):
            cx, cy = d.point(end)
            pen.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(*TRACE, 255))
    for dot in TRACE_DOTS:
        cx, cy = d.point(dot)
        r = d.length(3)
        pen.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(*TRACE, 255))
    layer.putalpha(layer.getchannel("A").point(lambda v: int(v * 0.30)))
    canvas.alpha_composite(layer)


def draw_badge(canvas: Image.Image) -> None:
    d = Design(canvas.width)
    mask = rounded_mask(canvas.width, BADGE, BADGE_RADIUS)
    canvas.alpha_composite(drop_shadow(mask, BADGE_EDGE, d.length(6), d.length(8), 90))

    fill = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    fill.paste((*BADGE_FILL, 255), (0, 0), mask)
    canvas.alpha_composite(fill)

    pen = ImageDraw.Draw(canvas)
    pen.rounded_rectangle(
        (d.px(BADGE[0]), d.px(BADGE[1]), d.px(BADGE[2]), d.px(BADGE[3])),
        radius=d.length(BADGE_RADIUS),
        outline=(*BADGE_EDGE, 255),
        width=max(1, int(round(d.length(2.5)))),
    )

    size = d.length(24)
    text_font = font(round(size))
    left, baseline = d.point((366, 101))
    pen.text((left, baseline), "AI", font=text_font, fill=(*BADGE_TEXT, 255), anchor="ls")

    cx, cy = d.point(SPARK_CENTER)
    r = d.length(SPARK_RADIUS)
    inner = r * 0.32
    pen.polygon(
        [
            (cx, cy - r), (cx + inner, cy - inner), (cx + r, cy),
            (cx + inner, cy + inner), (cx, cy + r), (cx - inner, cy + inner),
            (cx - r, cy), (cx - inner, cy - inner),
        ],
        fill=(*BADGE_EDGE, 255),
    )


def draw_content(canvas: Image.Image, *, keys_only: bool = False) -> None:
    """Keys, traces and the badge — everything except the tile background."""
    if keys_only:
        draw_keys(canvas, shadows=False)
        return
    draw_traces(canvas)
    draw_keys(canvas, shadows=True)
    draw_badge(canvas)


def content_layer(size: int, art_scale: float, *, keys_only: bool = False) -> Image.Image:
    """The content on a transparent canvas, shrunk to `art_scale`.

    The viewBox is centred on the tile, so scaling the whole canvas about its
    centre keeps the keypad where it belongs. Keeping the content inside a
    circle of 36dp (Android's guaranteed visible area) is what the two scales
    in `main()` are for — the badge would otherwise be cropped by round masks.
    """
    big = size * SS
    art = int(round(big * art_scale))
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    art_canvas = Image.new("RGBA", (art, art), (0, 0, 0, 0))
    draw_content(art_canvas, keys_only=keys_only)
    offset = (big - art) // 2
    layer.alpha_composite(art_canvas, (offset, offset))
    return layer.resize((size, size), Image.LANCZOS)


def tile(size: int) -> Image.Image:
    """The shipping icon: the dark squircle with everything drawn on it."""
    big = size * SS
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    plate = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    vertical_gradient(plate, TILE, BG_TOP, BG_BOTTOM)
    plate.putalpha(rounded_mask(big, TILE, TILE_RADIUS))
    canvas.alpha_composite(plate)
    draw_content(canvas)
    return canvas.resize((size, size), Image.LANCZOS)


def round_tile(size: int) -> Image.Image:
    img = tile(size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    img.putalpha(mask)
    return img


def adaptive_background(size: int) -> Image.Image:
    """Full-bleed dark gradient; the launcher supplies the shape."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    vertical_gradient(img, (-ORIGIN, -ORIGIN, -ORIGIN + VIEW, -ORIGIN + VIEW), BG_TOP, BG_BOTTOM)
    return img


def adaptive_foreground(size: int) -> Image.Image:
    """Keys, traces and badge only: the background layer paints the tile."""
    return content_layer(size, FOREGROUND_SCALE)


def monochrome(size: int) -> Image.Image:
    """One flat colour, tinted by the system: the four keys with their symbols cut out."""
    big = size * SS
    art = int(round(big * MONO_SCALE))
    d = Design(art)
    glyph = Image.new("L", (art, art), 0)
    pen = ImageDraw.Draw(glyph)
    for name, (x, y) in KEYS.items():
        pen.rounded_rectangle(
            (d.px(x), d.px(y), d.px(x + KEY), d.px(y + KEY)),
            radius=d.length(KEY_RADIUS),
            fill=255,
        )
        for (a, b) in SYMBOLS[name]:
            pen.line([d.point(a), d.point(b)], fill=0,
                     width=max(1, int(round(d.length(STROKE)))))
            radius = d.length(STROKE) / 2
            for pt in (a, b):
                cx, cy = d.point(pt)
                pen.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=0)

    canvas = Image.new("L", (big, big), 0)
    offset = (big - art) // 2
    canvas.paste(glyph, (offset, offset))
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(canvas.resize((size, size), Image.LANCZOS))
    return out


def write(img: Image.Image, path: Path) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG", optimize=True)
    return path.stat().st_size


# --------------------------------------------------------------------------- #
# preview sheet
# --------------------------------------------------------------------------- #
def phone_mock(size=(340, 700)) -> Image.Image:
    width, height = size
    phone = Image.new("RGBA", size, (0, 0, 0, 0))
    backdrop = Image.new("RGB", size)
    for y in range(height):
        t = y / height
        backdrop.paste((int(28 + 24 * t), int(21 + 19 * t), int(60 + 36 * t)), (0, y, width, y + 1))
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1),
                                           radius=int(width * 0.13), fill=255)
    phone.paste(backdrop, (0, 0), mask)

    draw = ImageDraw.Draw(phone)
    icon = tile(int(width * 0.24))
    label = font(int(width * 0.042), bold=False)
    cell = int(width * 0.3)
    x0, y0 = int(width * 0.1), int(height * 0.13)
    for row in range(3):
        for col in range(3):
            x = x0 + col * cell
            y = y0 + row * cell
            if (row, col) == (0, 1):
                phone.paste(icon, (x, y), icon)
                draw.text((x + 4, y + icon.height + int(height * 0.012)), "AI Calc",
                          font=label, fill=(238, 236, 250))
            else:
                placeholder = Image.new("RGBA", (int(width * 0.19), int(width * 0.19)),
                                        (255, 255, 255, 24))
                phone.paste(placeholder, (x, y), placeholder)
    return phone


def preview() -> Image.Image:
    art = tile(512)
    W, H = 1620, 1120
    sheet = Image.new("RGB", (W, H), (13, 11, 20))
    draw = ImageDraw.Draw(sheet)
    draw.text((60, 44), "AI Calculator — launcher icon", font=font(48), fill=(242, 240, 252))
    draw.text((60, 108),
              "dark keypad · orange equals key · circuit traces · cyan AI badge",
              font=font(23, bold=False), fill=(156, 150, 186))

    hero = Image.new("RGBA", (600, 560), (0, 0, 0, 0))
    big = art.resize((430, 430), Image.LANCZOS)
    dark = Image.new("RGBA", big.size, (0, 0, 0, 190))
    dark.putalpha(big.getchannel("A").point(lambda v: int(v * 0.72)))
    drop = Image.new("RGBA", hero.size, (0, 0, 0, 0))
    drop.alpha_composite(dark, (85, 85))
    hero.alpha_composite(drop.filter(ImageFilter.GaussianBlur(30)))
    hero.alpha_composite(big, (85, 65))
    sheet.paste(hero, (30, 165), hero)

    def adaptive(mask: str, px: int = 360) -> Image.Image:
        layer = Image.alpha_composite(adaptive_background(px), adaptive_foreground(px))
        shape = Image.new("L", (px, px), 0)
        if mask == "circle":
            ImageDraw.Draw(shape).ellipse((0, 0, px - 1, px - 1), fill=255)
        else:
            ImageDraw.Draw(shape).rounded_rectangle((0, 0, px - 1, px - 1),
                                                    radius=int(px * 0.235), fill=255)
        layer.putalpha(shape)
        return layer

    x = 620
    for label, img in (("adaptive · circle", adaptive("circle")),
                       ("adaptive · squircle", adaptive("squircle")),
                       ("store · 512px", art)):
        thumb = img.resize((172, 172), Image.LANCZOS)
        sheet.paste(thumb, (x, 195), thumb)
        draw.text((x, 385), label, font=font(20, bold=False), fill=(158, 152, 188))
        x += 200

    draw.text((620, 450), "sizes", font=font(24), fill=(214, 210, 238))
    x = 620
    for size in (192, 144, 96, 48):
        icon = tile(size)
        sheet.paste(icon, (x, 500 + (192 - size) // 2), icon)
        draw.text((x + 4, 720), f"{size}px", font=font(19, bold=False), fill=(140, 134, 172))
        x += 150

    draw.text((620, 790), "themed · Android 13+", font=font(24), fill=(214, 210, 238))
    themed = Image.new("RGBA", (176, 176), (23, 25, 33, 255))
    mono = monochrome(150)
    tinted = Image.new("RGBA", mono.size, (198, 202, 216, 255))
    tinted.putalpha(mono.getchannel("A"))
    themed.paste(tinted, (13, 13), tinted)
    sheet.paste(themed, (620, 840), themed)
    draw.text((624, 1032), "tinted by the system", font=font(19, bold=False), fill=(150, 145, 180))

    mock = phone_mock()
    sheet.paste(mock, (1250, 175), mock)
    draw.text((1256, 895), "home screen", font=font(21, bold=False), fill=(160, 154, 190))
    return sheet


def layers_sheet() -> Image.Image:
    """The three Android icon shapes side by side, for a quick sanity check."""
    cell = 300
    W, H = 60 + 5 * (cell + 40), 460
    sheet = Image.new("RGB", (W, H), (13, 11, 20))
    draw = ImageDraw.Draw(sheet)
    draw.text((60, 40), "What each layer looks like", font=font(32), fill=(242, 240, 252))
    entries = [
        ("ic_launcher.png", tile(cell)),
        ("ic_launcher_round.png", round_tile(cell)),
        ("background", adaptive_background(cell)),
        ("foreground", adaptive_foreground(cell)),
        ("monochrome", monochrome(cell)),
    ]
    for i, (label, img) in enumerate(entries):
        x = 60 + i * (cell + 40)
        sheet.paste(img, (x, 120), img)
        draw.text((x, 430), label, font=font(19, bold=False), fill=(158, 152, 188))
    return sheet


# --------------------------------------------------------------------------- #
# Google Play store graphics
# --------------------------------------------------------------------------- #
def feature_graphic() -> Image.Image:
    """The 1024x500 banner for the Play listing: icon, name, what it does."""
    W, H = 1024, 500
    sheet = Image.new("RGB", (W, H), BG_TOP)
    strip = Image.new("RGB", (1, H))
    for y in range(H):
        t = y / (H - 1)
        strip.putpixel((0, y), tuple(int(BG_TOP[c] + (BG_BOTTOM[c] - BG_TOP[c]) * t) for c in range(3)))
    sheet.paste(strip.resize((W, H), Image.NEAREST), (0, 0))

    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse((-W * 0.15, -H * 0.7, W * 0.55, H * 0.75), fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    sheet = Image.composite(Image.new("RGB", (W, H), (44, 46, 52)), sheet, glow)

    icon = tile(260)
    shadow = Image.new("RGBA", (340, 340), (0, 0, 0, 0))
    dark = Image.new("RGBA", icon.size, (0, 0, 0, 200))
    dark.putalpha(icon.getchannel("A").point(lambda v: int(v * 0.7)))
    shadow.alpha_composite(dark, (40, 46))
    shadow = shadow.filter(ImageFilter.GaussianBlur(26))
    sheet.paste(shadow, (58, 78), shadow)
    sheet.paste(icon, (98, 108), icon)

    draw = ImageDraw.Draw(sheet)
    draw.text((410, 168), "AI Calculator", font=font(62), fill=(246, 245, 252))
    draw.text((412, 244), "Graphing · matrices · calculus · units", font=font(27, bold=False),
              fill=(196, 199, 214))
    draw.text((412, 288), "everyday tools · Ask AI", font=font(27, bold=False),
              fill=(196, 199, 214))
    draw.rounded_rectangle((412, 344, 560, 352), radius=4, fill=(255, 122, 0))
    return sheet


CARDS = [
    {
        "title": "AI Calculator",
        "subtitle": "Calculus · matrices · graphs · units",
        "bullets": [
            "Solve, differentiate and integrate",
            "Plot up to five functions",
            "Matrices, complex numbers, 250+ units",
            "GST, EMI, BMI and other everyday tools",
            "Ask AI: type, photograph or attach",
            "Offline maths — no sign-up required",
        ],
        "accent": ACCENT_TOP,
    },
    {
        "title": "Solve anything",
        "subtitle": "from algebra to calculus",
        "bullets": [
            "Solve equations for x",
            "Derivatives and definite integrals",
            "Σ and Π series, limits at a point",
            "Fraction and exact-form results",
            "History of every calculation",
            "Works without a connection",
        ],
        "accent": (94, 220, 255),
    },
    {
        "title": "Ask AI",
        "subtitle": "your own Gemini key, no account",
        "bullets": [
            "Type the question, or",
            "Photograph it with the camera",
            "Attach a picture or a PDF",
            "Step-by-step answers",
            "No chat history is kept",
            "Everyday tools built in",
        ],
        "accent": (255, 138, 0),
    },
]


def marketing_shot(spec: dict) -> Image.Image:
    """One 1080x1920 listing card, drawn in the app's own palette.

    Play wants at least two phone screenshots. These are generated so they can be
    refreshed whenever the app changes; screenshots taken on a real device are
    even better and can be uploaded instead.
    """
    W, H = 1080, 1920
    sheet = Image.new("RGB", (W, H), BG_TOP)
    strip = Image.new("RGB", (1, H))
    for y in range(H):
        t = y / (H - 1)
        strip.putpixel((0, y), tuple(int(BG_TOP[c] + (BG_BOTTOM[c] - BG_TOP[c]) * t) for c in range(3)))
    sheet.paste(strip.resize((W, H), Image.NEAREST), (0, 0))

    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse((-W * 0.4, -H * 0.25, W * 1.1, H * 0.45), fill=64)
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    sheet = Image.composite(Image.new("RGB", (W, H), (46, 48, 55)), sheet, glow)

    icon = tile(420)
    shadow = Image.new("RGBA", (540, 540), (0, 0, 0, 0))
    dark = Image.new("RGBA", icon.size, (0, 0, 0, 205))
    dark.putalpha(icon.getchannel("A").point(lambda v: int(v * 0.72)))
    shadow.alpha_composite(dark, (60, 66))
    blurred = shadow.filter(ImageFilter.GaussianBlur(34))
    sheet.paste(blurred, (270, 250), blurred)
    sheet.paste(icon, (330, 310), icon)

    draw = ImageDraw.Draw(sheet)
    title_font = font(72)
    width = draw.textlength(spec["title"], font=title_font)
    draw.text(((W - width) / 2, 880), spec["title"], font=title_font, fill=(248, 247, 253))

    sub_font = font(34, bold=False)
    width = draw.textlength(spec["subtitle"], font=sub_font)
    draw.text(((W - width) / 2, 976), spec["subtitle"], font=sub_font, fill=(198, 201, 216))

    # Bullets shrink to fit the card instead of running off its edge.
    left = 250
    limit = W - 110
    y = 1130
    for line in spec["bullets"]:
        size = 34
        while size > 22 and left + 64 + draw.textlength(line, font=font(size, bold=False)) > limit:
            size -= 1
        bullet_font = font(size, bold=False)
        dot = size * 0.4
        draw.ellipse((left, y + size * 0.42, left + dot, y + size * 0.42 + dot), fill=spec["accent"])
        draw.text((left + 64, y), line, font=bullet_font, fill=(226, 228, 238))
        y += int(size * 2.35)

    draw.rounded_rectangle((478, 1720, 602, 1732), radius=6, fill=spec["accent"])
    return sheet


def main() -> int:
    total = 0
    for density, size in DENSITIES.items():
        folder = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
        canvas = int(round(size * 1.5))
        total += write(tile(size), folder / "ic_launcher.png")
        total += write(round_tile(size), folder / "ic_launcher_round.png")
        total += write(adaptive_background(canvas), folder / "ic_launcher_background.png")
        total += write(adaptive_foreground(canvas), folder / "ic_launcher_foreground.png")
        total += write(monochrome(canvas), folder / "ic_launcher_monochrome.png")
        print(f"  mipmap-{density:<9} {size}px tile + 108dp layers")
    total += write(tile(512), ASSETS / "play-store-icon-512.png")
    total += write(preview(), ASSETS / "app-icon-preview.png")
    total += write(layers_sheet(), ASSETS / "icon-layers.png")
    total += write(feature_graphic(), ASSETS / "play-feature-graphic-1024x500.png")
    for index, spec in enumerate(CARDS, start=1):
        total += write(marketing_shot(spec), ASSETS / f"play-screenshot-{index}-1080x1920.png")
    print("  assets/play-store-icon-512.png, assets/app-icon-preview.png,")
    print("  assets/icon-layers.png, assets/play-feature-graphic-1024x500.png,")
    print(f"  assets/play-screenshot-1..{len(CARDS)}-1080x1920.png")
    print(f"done — {total:,} bytes of icons")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
