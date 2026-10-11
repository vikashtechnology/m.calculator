"""Generates the M Calculator launcher icons.

Run from the repository root:

    python3 tools/make_icons.py

It writes legacy square/round mipmaps plus the two layers of the adaptive icon
(API 26+) for every density, using only Pillow and DejaVu Sans Bold.
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Palette (matches ui/theme/Theme.kt)
INK_TOP = (17, 19, 42)
INK_BOTTOM = (46, 34, 96)
VIOLET = (124, 108, 255)
CYAN = (53, 194, 240)
WHITE = (255, 255, 255)


def gradient(size, top, bottom):
    img = Image.new("RGB", (size, size), top)
    draw = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line([(0, y), (size, y)], fill=color)
    return img


def add_glow(img, size):
    """Soft violet glow in the upper-left, like the app's keypad accents."""
    glow = Image.new("L", (size, size), 0)
    gd = ImageDraw.Draw(glow)
    radius = int(size * 0.62)
    gd.ellipse(
        [-radius // 2, -radius // 2, radius, radius],
        fill=110,
    )
    glow = glow.filter(ImageFilter_GaussianBlur(size * 0.12))
    overlay = Image.new("RGB", (size, size), VIOLET)
    return Image.composite(overlay, img, glow)


def ImageFilter_GaussianBlur(radius):
    from PIL import ImageFilter
    return ImageFilter.GaussianBlur(radius)


def rounded_mask(size, radius_ratio=0.22):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def draw_mark(img, size, scale=1.0):
    """Draws the white M with a violet→cyan accent bar underneath."""
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT_PATH, int(size * 0.56 * scale))
    text = "M"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - tw) / 2 - bbox[0]
    y = (size - th) / 2 - bbox[1] - int(size * 0.045)
    # Soft drop shadow for depth.
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.text((x, y + int(size * 0.012)), text, font=font, fill=(0, 0, 0, 110))
    shadow = shadow.filter(ImageFilter_GaussianBlur(size * 0.02))
    img.alpha_composite(shadow) if img.mode == "RGBA" else img.paste(
        Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB"), (0, 0)
    )
    draw.text((x, y), text, font=font, fill=WHITE)

    # Accent bar with a violet → cyan gradient, centred under the M.
    bar_w = int(size * 0.42)
    bar_h = max(2, int(size * 0.045))
    bar_y = int(size * 0.70)
    bar_x = (size - bar_w) // 2
    for i in range(bar_w):
        t = i / max(1, bar_w - 1)
        color = tuple(int(VIOLET[c] + (CYAN[c] - VIOLET[c]) * t) for c in range(3))
        draw.rectangle([bar_x + i, bar_y, bar_x + i + 1, bar_y + bar_h], fill=color)


def build_legacy(size, round_icon=False):
    base = add_glow(gradient(size, INK_TOP, INK_BOTTOM), size)
    img = base.convert("RGBA")
    draw_mark(img, size)
    mask = circle_mask(size) if round_icon else rounded_mask(size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def build_background(size):
    return add_glow(gradient(size, INK_TOP, INK_BOTTOM), size).convert("RGBA")


def build_foreground(size):
    """Adaptive foreground: the mark inside the 66/108 safe zone."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_mark(img, size, scale=0.72)
    return img


def main():
    for density, factor in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)

        legacy = int(48 * factor)
        size = int(108 * factor)

        build_legacy(legacy).save(os.path.join(folder, "ic_launcher.png"))
        build_legacy(legacy, round_icon=True).save(os.path.join(folder, "ic_launcher_round.png"))
        build_background(size).save(os.path.join(folder, "ic_launcher_background.png"))
        build_foreground(size).save(os.path.join(folder, "ic_launcher_foreground.png"))
        print(f"{density}: legacy {legacy}px, adaptive {size}px")

    # Adaptive icon descriptors.
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    with open(os.path.join(anydpi, "ic_launcher.xml"), "w") as f:
        f.write(adaptive)
    with open(os.path.join(anydpi, "ic_launcher_round.xml"), "w") as f:
        f.write(adaptive)

    # Play Store sized icon for later publishing.
    assets = os.path.join(ROOT, "assets")
    os.makedirs(assets, exist_ok=True)
    store = build_legacy(512)
    store.save(os.path.join(assets, "play-store-icon-512.png"))
    print("wrote assets/play-store-icon-512.png")


if __name__ == "__main__":
    main()
