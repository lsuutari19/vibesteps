"""One-shot helper to refresh the plugin nav icons.

- Trims fully-transparent padding from the source icon.
- Upscales to a 64x64 RGBA canvas so the icon fills more of the nav strip.
- Writes the result back over icon.png.
- Generates a blue-tinted teammates-icon.png next to it.
"""

import sys
from pathlib import Path
from PIL import Image, ImageOps, ImageChops

ICON_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "com" / "gimprogresstracker"
SOURCE = ICON_DIR / "icon.png"
TEAMMATES = ICON_DIR / "teammates-icon.png"
TARGET_SIZE = 64


def trim_transparent(img: Image.Image) -> Image.Image:
    """Crop fully-transparent borders so the content fills the canvas."""
    img = img.convert("RGBA")
    alpha = img.split()[-1]
    bbox = alpha.getbbox()
    if bbox:
        return img.crop(bbox)
    return img


def pad_to_square(img: Image.Image) -> Image.Image:
    """Pad with transparency so the image is square (keeps aspect ratio)."""
    w, h = img.size
    side = max(w, h)
    if w == h:
        return img
    bg = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    bg.paste(img, ((side - w) // 2, (side - h) // 2), img)
    return bg


def upscale(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.LANCZOS)


def tint(img: Image.Image, color: tuple) -> Image.Image:
    """Apply a color tint while preserving the original luminance and alpha."""
    img = img.convert("RGBA")
    r, g, b, a = img.split()
    grey = ImageOps.grayscale(img)
    tinted = ImageOps.colorize(grey, black=(0, 0, 0), white=color, mid=None)
    tinted = tinted.convert("RGBA")
    tinted.putalpha(a)
    return tinted


def main() -> int:
    if not SOURCE.exists():
        print(f"missing: {SOURCE}", file=sys.stderr)
        return 1

    src = Image.open(SOURCE)
    trimmed = trim_transparent(src)
    squared = pad_to_square(trimmed)
    big = upscale(squared, TARGET_SIZE)
    big.save(SOURCE, "PNG", optimize=True)
    print(f"wrote {SOURCE} {big.size}")

    blue = tint(big, (90, 150, 235))
    blue.save(TEAMMATES, "PNG", optimize=True)
    print(f"wrote {TEAMMATES} {blue.size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
