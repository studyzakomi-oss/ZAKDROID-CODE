#!/usr/bin/env python3
"""Generates the zakomi TECH logo exports from one geometric definition.

The interlocking Z is two identical pieces; the lower piece is the upper piece
rotated 180 degrees around the centre of a 100x100 grid. Every Android vector,
SVG and PNG export is derived from the coordinates below.

Usage: python3 branding/generate_brand_assets.py   (requires Pillow for the PNG)
"""
from pathlib import Path

NAVY = "#0A1F52"
WHITE = "#FFFFFF"
CORNER_RADIUS = 18  # ~18% of the 100-unit square

UPPER_PIECE = [
    (18.5, 17), (76, 17), (81.5, 25.5), (54.5, 67), (43, 67),
    (69.5, 26.5), (30, 26.5), (36, 35.5), (30, 44.5), (18.5, 26.5),
]
LOWER_PIECE = [(100 - x, 100 - y) for x, y in UPPER_PIECE]
PIECES = [UPPER_PIECE, LOWER_PIECE]

HERE = Path(__file__).resolve().parent


def fmt(v: float) -> str:
    return f"{v:g}"


def polygon_path(points, scale=1.0, dx=0.0, dy=0.0) -> str:
    coords = [f"{fmt(x * scale + dx)},{fmt(y * scale + dy)}" for x, y in points]
    return "M" + " L".join(coords) + " Z"


def rounded_square_path(size=100.0, r=CORNER_RADIUS, scale=1.0, dx=0.0, dy=0.0) -> str:
    s, rr = size * scale, r * scale
    return (
        f"M{fmt(dx + rr)},{fmt(dy)} H{fmt(dx + s - rr)} "
        f"A{fmt(rr)},{fmt(rr)} 0 0 1 {fmt(dx + s)},{fmt(dy + rr)} V{fmt(dy + s - rr)} "
        f"A{fmt(rr)},{fmt(rr)} 0 0 1 {fmt(dx + s - rr)},{fmt(dy + s)} H{fmt(dx + rr)} "
        f"A{fmt(rr)},{fmt(rr)} 0 0 1 {fmt(dx)},{fmt(dy + s - rr)} V{fmt(dy + rr)} "
        f"A{fmt(rr)},{fmt(rr)} 0 0 1 {fmt(dx + rr)},{fmt(dy)} Z"
    )


def z_path(scale=1.0, dx=0.0, dy=0.0) -> str:
    return " ".join(polygon_path(p, scale, dx, dy) for p in PIECES)


def write_svgs():
    (HERE / "logo.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="512" height="512">\n'
        "  <title>zakomi TECH</title>\n"
        f'  <path fill="{NAVY}" d="{rounded_square_path()}"/>\n'
        f'  <path fill="{WHITE}" d="{z_path()}"/>\n'
        "</svg>\n"
    )
    # Single-colour mark: the Z is knocked out of the square, so it works as a
    # stamp in any one colour (set via CSS `color`).
    (HERE / "logo-mono.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="512" height="512">\n'
        "  <title>zakomi TECH (mono)</title>\n"
        f'  <path fill="currentColor" fill-rule="evenodd" d="{rounded_square_path()} {z_path()}"/>\n'
        "</svg>\n"
    )


def write_png(size=512, supersample=4):
    from PIL import Image, ImageDraw

    big = size * supersample
    k = big / 100.0
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=CORNER_RADIUS * k, fill=NAVY)
    for piece in PIECES:
        draw.polygon([(x * k, y * k) for x, y in piece], fill=WHITE)
    img.resize((size, size), Image.LANCZOS).save(HERE / "logo-512.png", optimize=True)


def android_path_data():
    """Prints the pathData strings used by the Android vector drawables."""
    print("square(100):", rounded_square_path())
    print("z(100):", z_path())
    # Adaptive icon foreground: 108dp canvas, Z scaled to stay in the 66dp safe zone.
    s = 0.70
    off = 54 - 50 * s
    print("z(adaptive 108):", z_path(s, off, off))
    # Splash icon: full logo inside the 2/3 safe circle of a 108 canvas.
    s2 = 0.56
    off2 = 54 - 50 * s2
    print("square(splash 108):", rounded_square_path(scale=s2, dx=off2, dy=off2))
    print("z(splash 108):", z_path(s2, off2, off2))


if __name__ == "__main__":
    write_svgs()
    write_png()
    android_path_data()
