#!/usr/bin/env python3
"""Draws the Play listing's feature graphic (1024 x 500) from the app's own icon and typeface.

Usage: python3 tools/store/feature_graphic.py  (writes docs/assets/feature-graphic.png)

Paper, a faint 9x9 grid running off the right edge, the icon, and the name set in Fraunces at
the weights the app uses. No screenshots and no claims: Play shows the graphic above them.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
PAPER = (244, 239, 229)
GRID = (217, 207, 191)
BOX = (176, 165, 147)
INK = (31, 28, 24)
PEN = (45, 85, 119)
MUTED = (97, 89, 78)
W, H = 1024, 500


def font(name: str, size: int, wght: int, opsz: int) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(ROOT / "app/src/main/res/font" / name), size)
    axes = [a["name"].decode() if isinstance(a["name"], bytes) else a["name"] for a in f.get_variation_axes()]
    f.set_variation_by_axes([opsz if "optical" in a.lower() else wght for a in axes])
    return f


def main() -> None:
    image = Image.new("RGB", (W, H), PAPER)
    draw = ImageDraw.Draw(image)
    # A page of the notebook running off the right edge: cell lines, box lines every third.
    cell, left, top = 62, 700, -26
    for i in range(10):
        colour, width = (BOX, 3) if i % 3 == 0 else (GRID, 1)
        draw.line([(left + i * cell, top), (left + i * cell, top + 9 * cell)], fill=colour, width=width)
        draw.line([(left, top + i * cell), (left + 9 * cell, top + i * cell)], fill=colour, width=width)
    digits = font("fraunces.ttf", 40, 560, 36)
    pen = font("fraunces_italic.ttf", 40, 420, 36)
    for (r, c, d, f, colour) in [(1, 1, "5", digits, INK), (2, 4, "3", digits, INK), (4, 2, "8", pen, PEN), (5, 5, "1", digits, INK), (6, 3, "9", pen, PEN), (7, 6, "4", digits, INK)]:
        x, y = left + c * cell + cell / 2, top + r * cell + cell / 2
        draw.text((x, y), d, font=f, fill=colour, anchor="mm")
    # Fade the grid into the paper towards the text.
    fade = Image.new("L", (W, H), 0)
    fd = ImageDraw.Draw(fade)
    for x in range(680, 820):
        fd.line([(x, 0), (x, H)], fill=int(255 * (820 - x) / 140))
    fd.rectangle([0, 0, 680, H], fill=255)
    image.paste(Image.new("RGB", (W, H), PAPER), (0, 0), fade)

    icon = Image.open(ROOT / "docs/assets/icon-512.png").convert("RGBA").resize((168, 168), Image.LANCZOS)
    image.paste(icon, (72, 166), icon)
    title = font("fraunces.ttf", 72, 560, 96)
    draw.text((272, 214), "Paper Sudoku", font=title, fill=INK, anchor="ls")
    tagline = font("fraunces_italic.ttf", 30, 420, 36)
    draw.text((274, 268), "A notebook of daily pages", font=tagline, fill=MUTED, anchor="ls")
    out = ROOT / "docs/assets/feature-graphic.png"
    image.save(out, optimize=True)
    print(f"Wrote {out} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
