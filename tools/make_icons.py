#!/usr/bin/env python3
"""Draws the AI Calculator launcher icon and writes every Android variant.

    python3 tools/make_icons.py                     # the shipping style: "card"
    python3 tools/make_icons.py --style outline     # card | outline | glyph
    python3 tools/make_icons.py --concepts          # side-by-side sheet of all three

Pillow only — the build environment has no SVG rasteriser, so the icon is drawn
as plain geometry instead of being traced from artwork. Everything is defined in
a 512-unit design space and rendered with 4× supersampling, so the same shapes
come out crisp from 48 px up to 512 px.

Outputs, per density (mdpi 48 · hdpi 72 · xhdpi 96 · xxhdpi 144 · xxxhdpi 192):

    ic_launcher.png              square icon with rounded corners (pre-Android 8)
    ic_launcher_round.png        circular icon (pre-Android 8)
    ic_launcher_background.png   adaptive background layer (108dp canvas)
    ic_launcher_foreground.png   adaptive foreground layer (108dp canvas)
    ic_launcher_monochrome.png   themed-icon glyph (Android 13+)

plus assets/play-store-icon-512.png and assets/app-icon-preview.png.
"""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4                     # supersampling factor
D = 512.0                  # design space
TILE_RADIUS = 0.235        # corner radius of a launcher tile, × side
VISIBLE = 2 / 3            # part of the 108dp canvas a launcher keeps visible
STYLES = ("card", "outline", "glyph")

# --- the palette ----------------------------------------------------------- #
TOP = (128, 94, 255)       # violet, top-left of the gradient
MID = (98, 63, 220)
BOTTOM = (66, 32, 178)     # deep violet, bottom-right
GLOW = (168, 141, 255)     # soft highlight in the top-left corner
INK = (255, 255, 255)      # the calculator itself
SCREEN = (70, 39, 189)     # display glass (card style)
KEY = (223, 217, 250)      # light lavender keys
ACCENT = (99, 63, 240)     # the highlighted "=" key
SHADOW = (24, 8, 76)       # drop shadow under the calculator

# --- the calculator, in design units --------------------------------------- #
BODY = (156.0, 126.0, 356.0, 382.0)
BODY_RADIUS = 38.0
BODY_STROKE = 16.0         # outline style
SCREEN_RECT = (180.0, 150.0, 332.0, 198.0)
SCREEN_RADIUS = 15.0
KEY_MARGIN = 25.0
KEY_GAP = 15.0
KEY_COLUMNS = 3
KEY_ROWS = 3
KEY_RADIUS = 12.0
ACCENT_KEY = (KEY_ROWS - 1, KEY_COLUMNS - 1)   # the "=" key, bottom-right


# --------------------------------------------------------------------------- #
# small helpers
# --------------------------------------------------------------------------- #
def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    for folder in ("/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/dejavu"):
        path = Path(folder) / name
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def blank(size: int, value: int = 0) -> Image.Image:
    return Image.new("L", (size, size), value)


def scaled(rect, scale: float):
    return tuple(v * scale for v in rect)


def key_rects():
    """(row, column, rect) for every key, laid out inside the calculator body."""
    left = BODY[0] + KEY_MARGIN
    right = BODY[2] - KEY_MARGIN
    top = SCREEN_RECT[3] + 20.0
    bottom = BODY[3] - KEY_MARGIN
    width = (right - left - KEY_GAP * (KEY_COLUMNS - 1)) / KEY_COLUMNS
    height = (bottom - top - KEY_GAP * (KEY_ROWS - 1)) / KEY_ROWS
    rects = []
    for row in range(KEY_ROWS):
        for col in range(KEY_COLUMNS):
            x = left + col * (width + KEY_GAP)
            y = top + row * (height + KEY_GAP)
            rects.append((row, col, (x, y, x + width, y + height)))
    return rects


# --------------------------------------------------------------------------- #
# the calculator
# --------------------------------------------------------------------------- #
def glyph_masks(size: int, style: str):
    """Masks for the shell, the display and every key."""
    scale = size / D

    shell = blank(size)
    pen = ImageDraw.Draw(shell)
    if style == "outline":
        pen.rounded_rectangle(
            scaled(BODY, scale), radius=BODY_RADIUS * scale, outline=255,
            width=max(1, round(BODY_STROKE * scale)),
        )
    elif style == "card":
        pen.rounded_rectangle(scaled(BODY, scale), radius=BODY_RADIUS * scale, fill=255)

    screen = blank(size)
    ImageDraw.Draw(screen).rounded_rectangle(
        scaled(SCREEN_RECT, scale), radius=SCREEN_RADIUS * scale, fill=255
    )

    keys = []
    for row, col, rect in key_rects():
        mask = blank(size)
        ImageDraw.Draw(mask).rounded_rectangle(
            scaled(rect, scale), radius=KEY_RADIUS * scale, fill=255
        )
        keys.append((row, col, mask))

    if style == "card":
        # the white shell is the body with the display and the keys punched out
        for _, _, mask in keys:
            shell.paste(0, (0, 0), mask)
        shell.paste(0, (0, 0), screen)
    return shell, screen, keys


def render_glyph(px: int, style: str) -> Image.Image:
    """The calculator alone on a transparent background, shadow included."""
    big = px
    shell, screen, keys = glyph_masks(big, style)
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    silhouette = shell.copy()
    silhouette.paste(255, (0, 0), screen)
    for _, _, mask in keys:
        silhouette.paste(255, (0, 0), mask)
    shadow = silhouette.filter(ImageFilter.GaussianBlur(big * 0.03))
    shadow = shadow.point(lambda v: int(v * 0.5))
    img.paste((*SHADOW, 255), (0, int(big * 0.013)), shadow)

    img.paste((*INK, 255), (0, 0), shell)
    img.paste((*INK, 255), (0, 0), screen)
    for row, col, mask in keys:
        img.paste((*INK, 255), (0, 0), mask)

    if style == "card":
        img.paste((*SCREEN, 255), (0, 0), screen)
        for row, col, mask in keys:
            colour = ACCENT if (row, col) == ACCENT_KEY else KEY
            img.paste((*colour, 255), (0, 0), mask)
    elif style == "outline":
        # the display stays white, the "=" key reads solid so there is one focus
        for row, col, mask in keys:
            if (row, col) == ACCENT_KEY:
                continue
            img.paste((*SCREEN, 255), (0, 0), mask)
    return img


def glyph(size: int, style: str) -> Image.Image:
    return render_glyph(size * SS, style).resize((size, size), Image.LANCZOS)


def gradient(size: int) -> Image.Image:
    """The tile background: a soft violet diagonal gradient with a corner glow."""
    small = Image.new("RGB", (8, 8))
    for y in range(8):
        for x in range(8):
            t = (x + y) / 14.0
            if t < 0.5:
                a, b, f = TOP, MID, t * 2
            else:
                a, b, f = MID, BOTTOM, (t - 0.5) * 2
            small.putpixel((x, y), tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3)))
    img = small.resize((size, size), Image.BICUBIC)
    glow = blank(size)
    ImageDraw.Draw(glow).ellipse((-size * 0.4, -size * 0.6, size * 0.8, size * 0.5), fill=88)
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.12))
    return Image.composite(Image.new("RGB", (size, size), GLOW), img, glow)


def tile(size: int, style: str) -> Image.Image:
    """A full launcher tile: gradient background plus the calculator."""
    big = size * SS
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    mask = blank(big)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1),
                                           radius=int(big * TILE_RADIUS), fill=255)
    canvas.paste(gradient(big), (0, 0), mask)
    canvas.alpha_composite(render_glyph(big, style))
    return canvas.resize((size, size), Image.LANCZOS)


def round_tile(size: int, style: str) -> Image.Image:
    img = tile(size, style)
    mask = blank(size)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    img.putalpha(mask)
    return img


def adaptive_background(size: int) -> Image.Image:
    """Fills the whole 108dp canvas; the launcher masks it to its own shape."""
    return gradient(size * 2).resize((size, size), Image.LANCZOS).convert("RGBA")


def adaptive_foreground(size: int, style: str) -> Image.Image:
    """The calculator alone, sized to survive circular and squircle masks."""
    big = size * SS
    art = int(round(big * VISIBLE))
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    layer.alpha_composite(render_glyph(art, style), ((big - art) // 2, (big - art) // 2))
    return layer.resize((size, size), Image.LANCZOS)


def monochrome(size: int) -> Image.Image:
    """One flat colour; Android tints it with the wallpaper palette."""
    shell, screen, keys = glyph_masks(size, "outline")
    mask = shell.copy()
    mask.paste(255, (0, 0), screen)
    for row, col, key in keys:
        mask.paste(255, (0, 0), key)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(mask)
    return out


def write(img: Image.Image, path: Path) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG", optimize=True)
    return path.stat().st_size


# --------------------------------------------------------------------------- #
# preview sheets
# --------------------------------------------------------------------------- #
def phone_mock(style: str, size=(340, 700)) -> Image.Image:
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
    icon = tile(int(width * 0.24), style)
    label = font(int(width * 0.042))
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


def preview(style: str) -> Image.Image:
    """One sheet: the icon big, the launcher masks, the sizes, and a home screen."""
    art = tile(512, style)
    W, H = 1620, 1120
    sheet = Image.new("RGB", (W, H), (13, 11, 20))
    draw = ImageDraw.Draw(sheet)
    draw.text((60, 44), "AI Calculator — launcher icon", font=font(48, bold=True),
              fill=(242, 240, 252))
    draw.text((60, 108),
              f"style: {style}   ·   drawn by tools/make_icons.py   ·   crisp from 48 px to 512 px",
              font=font(23), fill=(156, 150, 186))

    # the icon, big, with a soft shadow
    hero = Image.new("RGBA", (600, 560), (0, 0, 0, 0))
    big = art.resize((430, 430), Image.LANCZOS)
    dark = Image.new("RGBA", big.size, (0, 0, 0, 190))
    dark.putalpha(big.getchannel("A").point(lambda v: int(v * 0.72)))
    drop = Image.new("RGBA", hero.size, (0, 0, 0, 0))
    drop.alpha_composite(dark, (85, 85))
    hero.alpha_composite(drop.filter(ImageFilter.GaussianBlur(30)))
    hero.alpha_composite(big, (85, 65))
    sheet.paste(hero, (30, 165), hero)

    # the shapes a launcher may use
    x = 620
    for label, img in (("adaptive · circle", round_tile(360, style)),
                       ("adaptive · squircle", tile(360, style)),
                       ("store · 512px", art)):
        thumb = img.resize((172, 172), Image.LANCZOS)
        sheet.paste(thumb, (x, 195), thumb)
        draw.text((x, 385), label, font=font(20), fill=(158, 152, 188))
        x += 200

    # the sizes the phone actually uses
    draw.text((620, 450), "sizes", font=font(24, bold=True), fill=(214, 210, 238))
    x = 620
    for size in (192, 144, 96, 48):
        icon = tile(size, style)
        sheet.paste(icon, (x, 500 + (192 - size) // 2), icon)
        draw.text((x + 4, 720), f"{size}px", font=font(19), fill=(140, 134, 172))
        x += 150

    # the themed (monochrome) icon on a dark wallpaper
    draw.text((620, 790), "themed · Android 13+", font=font(24, bold=True), fill=(214, 210, 238))
    themed = Image.new("RGBA", (176, 176), (23, 25, 33, 255))
    mono = monochrome(150)
    themed.paste((198, 202, 216, 255), (13, 13), mono)
    sheet.paste(themed, (620, 840), themed)
    draw.text((624, 1032), "tinted by the system", font=font(19), fill=(150, 145, 180))

    mock = phone_mock(style)
    sheet.paste(mock, (1250, 175), mock)
    draw.text((1256, 895), "home screen", font=font(21), fill=(160, 154, 190))
    return sheet


def concepts(styles=STYLES) -> Image.Image:
    cell = 300
    W, H = 60 + len(styles) * (cell + 40), 560
    sheet = Image.new("RGB", (W, H), (13, 11, 20))
    draw = ImageDraw.Draw(sheet)
    draw.text((60, 40), "Icon styles — keep the shipping one, or ask for another",
              font=font(32, bold=True), fill=(242, 240, 252))
    notes = {
        "card": "solid white body, lit keys",
        "outline": "line-art body, white display",
        "glyph": "no body — display and keys only",
    }
    for i, style in enumerate(styles):
        x = 60 + i * (cell + 40)
        icon = tile(cell, style)
        sheet.paste(icon, (x, 120), icon)
        tag = "  ← shipping" if style == styles[0] else ""
        draw.text((x, 120 + cell + 30), style + tag, font=font(26, bold=True), fill=(226, 222, 246))
        draw.text((x, 120 + cell + 70), notes.get(style, ""), font=font(20), fill=(148, 143, 178))
    return sheet


# --------------------------------------------------------------------------- #
def main() -> int:
    parser = argparse.ArgumentParser(description="Draw the AI Calculator launcher icon.")
    parser.add_argument("--style", choices=STYLES, default="card")
    parser.add_argument("--concepts", action="store_true",
                        help="write assets/icon-styles.png instead of the app icons")
    args = parser.parse_args()

    if args.concepts:
        out = ASSETS / "icon-styles.png"
        write(concepts(), out)
        print(f"wrote {out.relative_to(ROOT)}")
        return 0

    total = 0
    for density, size in DENSITIES.items():
        folder = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
        canvas = int(round(size * 1.5))
        total += write(tile(size, args.style), folder / "ic_launcher.png")
        total += write(round_tile(size, args.style), folder / "ic_launcher_round.png")
        total += write(adaptive_background(canvas), folder / "ic_launcher_background.png")
        total += write(adaptive_foreground(canvas, args.style), folder / "ic_launcher_foreground.png")
        total += write(monochrome(canvas // 2), folder / "ic_launcher_monochrome.png")
        print(f"  mipmap-{density:<9} {size}px tile + 108dp layers")
    total += write(tile(512, args.style), ASSETS / "play-store-icon-512.png")
    total += write(preview(args.style), ASSETS / "app-icon-preview.png")
    print("  assets/play-store-icon-512.png, assets/app-icon-preview.png")
    print(f"style '{args.style}' — {total:,} bytes of icons")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
