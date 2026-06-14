#!/usr/bin/env python3
"""Render a deterministic font specimen PNG: the font name + an English pangram,
in the font's own face, black on white (E-Ink friendly, maximum contrast).

Usage: make_specimen.py <ttf-path> <display-name> <out-png>

Deterministic: no date/random; identical TTF + name -> byte-stable PNG.
The specimen text is English Basic-Latin only (A-Z a-z 0-9) so every bundled
font renders it without .notdef/tofu.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1000, 420
MARGIN = 48
BG, FG = 255, 0  # white bg, black text (grayscale 'L')
PANGRAM = "The quick brown fox jumps over the lazy dog"
ALPHA = "ABCDEFGHIJKLM NOPQRSTUVWXYZ"
ALPHA2 = "abcdefghijklm nopqrstuvwxyz  0123456789"


def render(ttf_path: str, display_name: str, out_png: str) -> None:
    img = Image.new("L", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)
    name_font = ImageFont.truetype(ttf_path, 72)
    body_font = ImageFont.truetype(ttf_path, 40)
    small_font = ImageFont.truetype(ttf_path, 34)
    y = MARGIN
    draw.text((MARGIN, y), display_name, font=name_font, fill=FG)
    y += 96
    draw.text((MARGIN, y), PANGRAM, font=body_font, fill=FG)
    y += 64
    draw.text((MARGIN, y), ALPHA, font=small_font, fill=FG)
    y += 50
    draw.text((MARGIN, y), ALPHA2, font=small_font, fill=FG)
    img.save(out_png, "PNG", optimize=True)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("usage: make_specimen.py <ttf-path> <display-name> <out-png>")
    render(sys.argv[1], sys.argv[2], sys.argv[3])
