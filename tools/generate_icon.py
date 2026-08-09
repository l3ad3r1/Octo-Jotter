#!/usr/bin/env python3
"""
Generate the Octo Jotter launcher icon from a parametric definition.

Nothing here is drawn by hand: every point of the mark comes out of a closed-form
function of the constants below, so the icon is reproducible and re-tunable —
edit a parameter, re-run the script, and every density is regenerated.

The mark is an octopus seen head-on:

    mantle      superellipse (Lamé curve)   |x/a|^n + |y/b|^n = 1
                n = 2 is an ellipse; n > 2 squares off the dome.

    arms        r(s) = R0 + (R1 - R0) * s               radial extension
                phi(s) = PHI * s^EASE                   angular hook
                for s in [0,1], mirrored about the vertical axis.
                Cubic easing (EASE = 3) keeps the arm travelling straight out
                and concentrates the curl in the last third, which is what makes
                it read as a tentacle rather than a spiral.

    width       w(s) = W_TIP + (W0 - W_TIP) * (1 - s)^TAPER
                plus a semicircular cap of radius w(1)/2, so tips are rounded.

    fan         eight arms at evenly spaced angles across [A_FIRST, A_LAST],
                measured in screen coordinates (y grows downward, so 90 deg is
                straight down). Arms nearer the midline reach further (REACH_MIN)
                and hook harder, which fans the silhouette into a skirt.

    eyes        two circles, punched out with an even-odd fill so the adaptive
                icon's background colour shows through.

Outputs (sizes match what the project already ships):

    app/src/main/res/drawable/ic_launcher_foreground.xml     adaptive foreground (vector)
    app/src/main/res/drawable-*/splash_icon.png              splash mark
    app/src/main/res/mipmap-*/ic_launcher.png                legacy launcher
    app/src/main/res/mipmap-*/ic_launcher_round.png          legacy round launcher
    assets/ic_launcher-web-512.png                           store / README art

Usage:  python tools/generate_icon.py
Requires: Pillow
"""

from __future__ import annotations

import math
import os
from typing import List, Optional, Sequence, Tuple

from PIL import Image, ImageDraw

Point = Tuple[float, float]
Rgba = Tuple[int, int, int, int]

# --- the parameters that define the mark -----------------------------------

VIEWPORT = 108.0          # adaptive-icon design canvas (dp)
CENTER = VIEWPORT / 2
SAFE_RADIUS = 33.0        # adaptive icons may be masked down to this radius

ARMS = 8                  # "octo"
A_FIRST = 30.0            # fan start, degrees (screen coords: 90 = straight down)
A_LAST = 150.0            # fan end
R_INNER = 9.0             # arm origin radius — sits under the mantle
R_OUTER = 34.0            # arm length for the longest (central) arms
REACH_MIN = 0.80          # outermost arms are this fraction as long
PHI = 0.62                # total angular hook, radians
EASE = 3.0                # hook easing exponent — higher = later curl
W_BASE = 8.4              # arm width where it leaves the mantle
W_TIP = 1.5               # arm width at the tip (before the round cap)
TAPER = 1.0               # taper exponent

MANTLE_A = 15.4           # superellipse semi-axes
MANTLE_B = 16.0
MANTLE_N = 2.4            # 2 = ellipse, larger = squarer dome
MANTLE_DY = -10.6         # mantle centre, relative to the canvas centre
ARM_ORIGIN_DY = -0.6      # arm origin, relative to the canvas centre

EYE_R = 2.9
EYE_DX = 5.4
EYE_DY = 0.0              # relative to the mantle centre

ARM_SAMPLES = 52          # points along each arm's centre line
MANTLE_SAMPLES = 110
CIRCLE_SAMPLES = 32
CAP_SAMPLES = 12

# Brand colours (Premium Dark palette — see ui/theme/Color.kt)
INDIGO: Rgba = (79, 70, 229, 255)   # #4F46E5
WHITE: Rgba = (255, 255, 255, 255)
CLEAR: Rgba = (0, 0, 0, 0)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "app", "src", "main", "res")


# --- the functions ---------------------------------------------------------

def arm_outline(theta0: float, length: float, hook: float) -> List[Point]:
    """
    Closed outline of one tentacle.

    Walk the centre line r(s), phi(s) outward, offsetting by +w(s)/2 along the
    normal; cap the tip with a semicircle; walk back offsetting by -w(s)/2.
    """
    origin_y = CENTER + ARM_ORIGIN_DY
    centre: List[Point] = []
    for i in range(ARM_SAMPLES):
        s = i / (ARM_SAMPLES - 1)
        r = R_INNER + (length - R_INNER) * s
        angle = theta0 + hook * (s ** EASE)
        centre.append((CENTER + r * math.cos(angle), origin_y + r * math.sin(angle)))

    def half_width(s: float) -> float:
        return 0.5 * (W_TIP + (W_BASE - W_TIP) * (1.0 - s) ** TAPER)

    left: List[Point] = []
    right: List[Point] = []
    normals: List[Tuple[float, float, float, float]] = []
    for i, (x, y) in enumerate(centre):
        s = i / (ARM_SAMPLES - 1)
        px, py = centre[max(i - 1, 0)]
        nx, ny = centre[min(i + 1, ARM_SAMPLES - 1)]
        dx, dy = nx - px, ny - py
        length_ = math.hypot(dx, dy) or 1.0
        ox, oy = -dy / length_, dx / length_          # normal
        ux, uy = dx / length_, dy / length_           # tangent
        normals.append((ox, oy, ux, uy))
        w = half_width(s)
        left.append((x + ox * w, y + oy * w))
        right.append((x - ox * w, y - oy * w))

    tip_x, tip_y = centre[-1]
    ox, oy, ux, uy = normals[-1]
    w = half_width(1.0)
    cap = [
        (
            tip_x + (ox * math.cos(math.pi * i / CAP_SAMPLES) + ux * math.sin(math.pi * i / CAP_SAMPLES)) * w,
            tip_y + (oy * math.cos(math.pi * i / CAP_SAMPLES) + uy * math.sin(math.pi * i / CAP_SAMPLES)) * w,
        )
        for i in range(1, CAP_SAMPLES)
    ]

    return left + cap + right[::-1]


def superellipse(cx: float, cy: float, a: float, b: float, n: float) -> List[Point]:
    """|x/a|^n + |y/b|^n = 1, sampled parametrically."""
    points: List[Point] = []
    for i in range(MANTLE_SAMPLES):
        th = 2 * math.pi * i / MANTLE_SAMPLES
        c, s = math.cos(th), math.sin(th)
        points.append(
            (
                cx + a * math.copysign(abs(c) ** (2.0 / n), c),
                cy + b * math.copysign(abs(s) ** (2.0 / n), s),
            )
        )
    return points


def circle(cx: float, cy: float, r: float) -> List[Point]:
    return [
        (
            cx + r * math.cos(2 * math.pi * i / CIRCLE_SAMPLES),
            cy + r * math.sin(2 * math.pi * i / CIRCLE_SAMPLES),
        )
        for i in range(CIRCLE_SAMPLES)
    ]


def mark_shapes() -> Tuple[List[List[Point]], List[List[Point]]]:
    """(filled shapes, punched holes), in 108x108 design units."""
    mantle_y = CENTER + MANTLE_DY
    solids: List[List[Point]] = []
    for k in range(ARMS):
        fraction = k / (ARMS - 1)
        theta = math.radians(A_FIRST + fraction * (A_LAST - A_FIRST))
        # 1 for an arm pointing straight down, 0 for one pointing sideways.
        downward = abs(math.sin(theta))
        # Hook away from the midline: right-hand arms turn anticlockwise.
        direction = -1.0 if theta < math.pi / 2 else 1.0
        length = R_INNER + (R_OUTER - R_INNER) * (REACH_MIN + (1 - REACH_MIN) * downward)
        solids.append(arm_outline(theta, length, direction * PHI))

    solids.append(superellipse(CENTER, mantle_y, MANTLE_A, MANTLE_B, MANTLE_N))
    holes = [
        circle(CENTER - EYE_DX, mantle_y + EYE_DY, EYE_R),
        circle(CENTER + EYE_DX, mantle_y + EYE_DY, EYE_R),
    ]
    return solids, holes


def extent(shapes: Sequence[Sequence[Point]]) -> Tuple[float, float, float, float]:
    xs = [x for shape in shapes for x, _ in shape]
    ys = [y for shape in shapes for _, y in shape]
    return min(xs), min(ys), max(xs), max(ys)


# --- raster output ---------------------------------------------------------

SUPERSAMPLE = 4


def render(size: int, scale: float, background: Optional[Rgba], circular: bool = False) -> Image.Image:
    """
    Draw the mark into a `size` x `size` RGBA image. `scale` is the fraction of
    the canvas the 108-unit design square maps onto (1.0 = edge to edge).
    """
    big = size * SUPERSAMPLE
    img = Image.new("RGBA", (big, big), CLEAR)
    draw = ImageDraw.Draw(img)

    if background is not None:
        if circular:
            draw.ellipse([0, 0, big - 1, big - 1], fill=background)
        else:
            draw.rectangle([0, 0, big, big], fill=background)

    unit = big * scale / VIEWPORT
    offset = (big - VIEWPORT * unit) / 2

    def place(points: Sequence[Point]) -> List[Point]:
        return [(offset + x * unit, offset + y * unit) for x, y in points]

    solids, holes = mark_shapes()
    for shape in solids:
        draw.polygon(place(shape), fill=WHITE)
    # Eyes punch through to whatever is behind: the background, or transparency.
    for shape in holes:
        draw.polygon(place(shape), fill=background if background is not None else CLEAR)

    return img.resize((size, size), Image.LANCZOS)


# --- vector output ---------------------------------------------------------

def path_data(shapes: Sequence[Sequence[Point]]) -> str:
    parts = []
    for shape in shapes:
        head = f"M{shape[0][0]:.2f},{shape[0][1]:.2f}"
        body = "".join(f"L{x:.2f},{y:.2f}" for x, y in shape[1:])
        parts.append(head + body + "Z")
    return "".join(parts)


def vector_drawable() -> str:
    solids, holes = mark_shapes()
    # evenOdd so the eye circles cut holes rather than filling.
    data = path_data(list(solids) + list(holes))
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  Octo Jotter mark — GENERATED by tools/generate_icon.py.
  Do not hand-edit: change the parameters in that script and re-run it.

  mantle  superellipse |x/{MANTLE_A}|^{MANTLE_N} + |y/{MANTLE_B}|^{MANTLE_N} = 1
  arms    r(s) = {R_INNER} + (R - {R_INNER})s,  phi(s) = {PHI} * s^{EASE},  s in [0,1]
  width   w(s) = {W_TIP} + ({W_BASE} - {W_TIP})(1 - s)^{TAPER}, round tip cap
  fan     {ARMS} arms across [{A_FIRST}deg, {A_LAST}deg], mirrored about the vertical axis
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{data}" />
</vector>
"""


# --- entry point -----------------------------------------------------------

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def write_image(path: str, image: Image.Image) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print(f"   {os.path.relpath(path, REPO)}  {image.size[0]}x{image.size[1]}")


def main() -> None:
    solids, holes = mark_shapes()
    x0, y0, x1, y1 = extent(list(solids) + list(holes))
    print("Octo Jotter icon — generating")
    print(f"   mark extent x {x0:.1f}..{x1:.1f}  y {y0:.1f}..{y1:.1f}")
    inset = CENTER - SAFE_RADIUS
    if x0 < inset or y0 < inset or x1 > VIEWPORT - inset or y1 > VIEWPORT - inset:
        print(f"   WARNING: mark leaves the {SAFE_RADIUS}dp adaptive-icon safe zone")

    vector_path = os.path.join(RES, "drawable", "ic_launcher_foreground.xml")
    os.makedirs(os.path.dirname(vector_path), exist_ok=True)
    with open(vector_path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(vector_drawable())
    print(f"   {os.path.relpath(vector_path, REPO)}")

    for density, factor in DENSITIES.items():
        # Splash mark: 288dp canvas, art at 55% so it clears the splash mask.
        write_image(
            os.path.join(RES, f"drawable-{density}", "splash_icon.png"),
            render(int(288 * factor), scale=0.55, background=None),
        )
        # Legacy launcher icons, composited over the brand colour.
        write_image(
            os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"),
            render(int(48 * factor), scale=0.86, background=INDIGO),
        )
        write_image(
            os.path.join(RES, f"mipmap-{density}", "ic_launcher_round.png"),
            render(int(48 * factor), scale=0.80, background=INDIGO, circular=True),
        )

    write_image(
        os.path.join(REPO, "assets", "ic_launcher-web-512.png"),
        render(512, scale=0.86, background=INDIGO),
    )
    print("done")


if __name__ == "__main__":
    main()
