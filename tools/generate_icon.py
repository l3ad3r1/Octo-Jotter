#!/usr/bin/env python3
"""
Generate the Octo Jotter launcher icon from the source artwork.

The mark is imported line art (`assets/icon-source.png`), not drawn by formula
— an earlier version of this script built the octopus from parametric curves
instead; see git history if that's ever worth reviving. What this script
automates is everything downstream of the artwork: isolating it from its
reference mockup's border, keying white to transparent, and emitting every
size Android needs — so re-running it after swapping the source image
regenerates all of them consistently, the same "edit and re-run" workflow
the parametric version had.

Pipeline:

    1. Load `assets/icon-source.png` — a square mockup: white background, a
       rounded-square border stroke, teal line art of the octopus/pen/pad.
    2. Label connected ink components (luminance < INK_THRESHOLD). The one
       component touching all four image edges is the mockup's own border —
       discard it entirely (rather than thresholding it away, which would
       leave its anti-aliased fringe behind).
    3. Crop to the bounding box of what's left; that's the artwork.
    4. Turn white into alpha (how far a pixel is from white becomes its
       opacity) and letterbox the result onto a square canvas.

Every output — the adaptive foreground and monochrome layers, the legacy
launcher (square + round), the splash mark, and the web/store icon — is
resized or recomposited from that one square canvas.

Outputs:

    app/src/main/res/mipmap-*/ic_launcher_foreground.png   adaptive foreground
    app/src/main/res/mipmap-*/ic_launcher_monochrome.png   adaptive monochrome (themed icons)
    app/src/main/res/mipmap-*/ic_launcher.png              legacy launcher
    app/src/main/res/mipmap-*/ic_launcher_round.png         legacy round launcher
    app/src/main/res/drawable-*/splash_icon.png             splash mark
    assets/ic_launcher-web-512.png                          store / README art

Usage:  python tools/generate_icon.py
Requires: Pillow, numpy, scipy
"""

from __future__ import annotations

import os

import numpy as np
from PIL import Image
from scipy.ndimage import label

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "app", "src", "main", "res")
SOURCE = os.path.join(REPO, "assets", "icon-source.png")

INK_THRESHOLD = 200      # luminance below this counts as "ink" when labelling components
ALPHA_GAIN = 1.6         # alpha = clip((255 - luminance) * ALPHA_GAIN, 0, 255)
CANVAS_PAD = 0.16        # fraction of the square canvas left empty around the artwork

WHITE = (255, 255, 255, 255)

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ADAPTIVE_DP = 108        # adaptive-icon foreground/monochrome working canvas
LEGACY_DP = 48           # legacy launcher canvas
LEGACY_SCALE = 0.80      # fraction of the legacy canvas the artwork fills
SPLASH_DP = 288          # windowSplashScreenAnimatedIcon canvas
SPLASH_SCALE = 0.62      # fraction of the splash canvas the artwork fills
WEB_PX = 512


# --- import the artwork -----------------------------------------------------

def load_artwork() -> Image.Image:
    """
    Return the source's line art as a tightly-cropped RGBA image: the mockup's
    border stroke removed, white keyed to transparent.
    """
    img = Image.open(SOURCE).convert("RGB")
    arr = np.array(img)
    lum = arr.mean(axis=2)
    ink = lum < INK_THRESHOLD

    labels, _ = label(ink, structure=np.ones((3, 3), dtype=int))
    border_labels = set(labels[0, :]) | set(labels[-1, :]) | set(labels[:, 0]) | set(labels[:, -1])
    border_labels.discard(0)
    artwork_ink = ink.copy()
    for lbl in border_labels:
        artwork_ink[labels == lbl] = False

    ys, xs = np.where(artwork_ink)
    if not len(xs):
        raise RuntimeError("no ink found outside the mockup border — check INK_THRESHOLD")
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()

    alpha = np.clip((255.0 - lum) * ALPHA_GAIN, 0, 255).astype(np.uint8)
    rgba = np.dstack([arr, alpha])

    # Zero out anything that was part of a border component, even outside the
    # crop, so no fringe survives if the crop box ever tightens.
    for lbl in border_labels:
        rgba[labels == lbl, 3] = 0

    return Image.fromarray(rgba[y0 : y1 + 1, x0 : x1 + 1], "RGBA")


def letterbox(art: Image.Image, pad: float) -> Image.Image:
    """Centre `art` on a transparent square canvas, `pad` fraction of empty margin."""
    side = round(max(art.size) / (1 - 2 * pad))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(art, ((side - art.width) // 2, (side - art.height) // 2), art)
    return canvas


# --- derived outputs ---------------------------------------------------------

def tinted(art: Image.Image, rgb: tuple) -> Image.Image:
    """Recolour every pixel to `rgb`, keeping the original alpha (for monochrome)."""
    out = Image.new("RGBA", art.size, rgb + (0,))
    out.putalpha(art.split()[3])
    return out


def composite_on(art: Image.Image, size: int, background) -> Image.Image:
    """Resize `art` to fill `size`x`size` and flatten it over `background` (or None)."""
    fitted = art.resize((size, size), Image.LANCZOS)
    if background is None:
        return fitted
    base = Image.new("RGBA", (size, size), background)
    base.alpha_composite(fitted)
    return base


def circular_mask(img: Image.Image) -> Image.Image:
    """Clip `img` to the inscribed circle, transparent outside it."""
    size = img.size[0]
    ys, xs = np.mgrid[0:size, 0:size]
    r = size / 2
    inside = (xs - r + 0.5) ** 2 + (ys - r + 0.5) ** 2 <= r * r
    arr = np.array(img)
    arr[..., 3] = np.where(inside, arr[..., 3], 0)
    return Image.fromarray(arr, "RGBA")


def write_image(path: str, image: Image.Image) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print(f"   {os.path.relpath(path, REPO)}  {image.size[0]}x{image.size[1]}")


def main() -> None:
    print("Octo Jotter icon — importing", os.path.relpath(SOURCE, REPO))
    art = load_artwork()
    print(f"   artwork extent {art.size[0]}x{art.size[1]} (border discarded)")

    adaptive_canvas = letterbox(art, CANVAS_PAD)
    legacy_canvas = letterbox(art, (1 - LEGACY_SCALE) / 2)
    splash_canvas = letterbox(art, (1 - SPLASH_SCALE) / 2)
    monochrome_canvas = tinted(adaptive_canvas, (255, 255, 255))

    for density, factor in DENSITIES.items():
        adaptive_px = round(ADAPTIVE_DP * factor)
        legacy_px = round(LEGACY_DP * factor)
        splash_px = round(SPLASH_DP * factor)

        write_image(
            os.path.join(RES, f"mipmap-{density}", "ic_launcher_foreground.png"),
            composite_on(adaptive_canvas, adaptive_px, None),
        )
        write_image(
            os.path.join(RES, f"mipmap-{density}", "ic_launcher_monochrome.png"),
            composite_on(monochrome_canvas, adaptive_px, None),
        )
        write_image(
            os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"),
            composite_on(legacy_canvas, legacy_px, WHITE),
        )
        write_image(
            os.path.join(RES, f"mipmap-{density}", "ic_launcher_round.png"),
            circular_mask(composite_on(legacy_canvas, legacy_px, WHITE)),
        )
        write_image(
            os.path.join(RES, f"drawable-{density}", "splash_icon.png"),
            composite_on(splash_canvas, splash_px, None),
        )

    write_image(
        os.path.join(REPO, "assets", "ic_launcher-web-512.png"),
        composite_on(legacy_canvas, WEB_PX, WHITE),
    )
    print("done")


if __name__ == "__main__":
    main()
