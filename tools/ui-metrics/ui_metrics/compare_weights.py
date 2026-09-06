"""Compare the three ai weight functions on real baseline screens
(spec 0034, Milestone 29, requirements doc 0034 open question 1).

Usage: python -m ui_metrics.compare_weights <snapshots-dir> [<out-dir>]

For every `*.geometry.json` in <snapshots-dir> (written by
SemanticsGeometryExport.kt next to the matching `*.png`), computes BM/EM/SYM
under all three weight functions (equal/area/type - see apb_formulas.py) and:
  - prints a markdown table (screen x weight set -> BM/EM/SYM)
  - writes one visualization PNG per (screen, weight set) into <out-dir>
    (default: ./out/), overlaying the weighted centroid (equilibrium) and
    the quadrant split axes used by the symmetry formula onto the original
    screenshot.

This is a one-off decision-support tool for the product owner (not a
permanent CI artifact, unlike the Roborazzi baselines themselves) - its
output directory is git-ignored.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

from ui_metrics.apb_formulas import WEIGHT_FUNCTIONS, ScreenGeometry, compute_all, load_geometry


def _matching_png(geometry_json_path: Path) -> Path | None:
    # "<FQCN>.<method>.geometry.json" -> "<FQCN>.<method>.png"
    name = geometry_json_path.name.removesuffix(".geometry.json") + ".png"
    candidate = geometry_json_path.with_name(name)
    return candidate if candidate.exists() else None


def _draw_overlay(png_path: Path, geometry: ScreenGeometry, weight_name: str, out_path: Path) -> None:
    weight_fn = WEIGHT_FUNCTIONS[weight_name]
    image = Image.open(png_path).convert("RGB")
    draw = ImageDraw.Draw(image)

    scale_x = image.width / geometry.screen_width
    scale_y = image.height / geometry.screen_height

    # Quadrant split axes (symmetry_measure) - the screen's geometric center.
    center_x_px = image.width / 2.0
    center_y_px = image.height / 2.0
    draw.line([(center_x_px, 0), (center_x_px, image.height)], fill=(0, 200, 255), width=2)
    draw.line([(0, center_y_px), (image.width, center_y_px)], fill=(0, 200, 255), width=2)

    # Weighted centroid (equilibrium_measure).
    total = sum(weight_fn(obj) for obj in geometry.objects)
    if total > 1e-9:
        cx = sum(weight_fn(obj) * obj.center_x for obj in geometry.objects) / total
        cy = sum(weight_fn(obj) * obj.center_y for obj in geometry.objects) / total
        cx_px, cy_px = cx * scale_x, cy * scale_y
        r = 8
        draw.ellipse([cx_px - r, cy_px - r, cx_px + r, cy_px + r], outline=(255, 40, 40), width=3)
        draw.line([(cx_px - r * 1.5, cy_px), (cx_px + r * 1.5, cy_px)], fill=(255, 40, 40), width=2)
        draw.line([(cx_px, cy_px - r * 1.5), (cx_px, cy_px + r * 1.5)], fill=(255, 40, 40), width=2)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    image.save(out_path)


def main(argv: list[str] | None = None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    if len(argv) not in (1, 2):
        print("usage: python -m ui_metrics.compare_weights <snapshots-dir> [<out-dir>]", file=sys.stderr)
        return 1

    snapshots_dir = Path(argv[0])
    out_dir = Path(argv[1]) if len(argv) == 2 else Path("out")

    geometry_files = sorted(snapshots_dir.glob("*.geometry.json"))
    if not geometry_files:
        print(f"no *.geometry.json files found under {snapshots_dir}", file=sys.stderr)
        return 1

    header = f"{'screen':<75} {'weights':<8} {'BM':>8} {'EM':>8} {'SYM':>8}"
    print(header)
    for geometry_path in geometry_files:
        screen_name = geometry_path.name.removesuffix(".geometry.json")
        try:
            geometry = load_geometry(geometry_path)
        except Exception as e:
            print(f"{screen_name:<75} ERROR: {e}")
            continue

        png_path = _matching_png(geometry_path)
        for weight_name, weight_fn in WEIGHT_FUNCTIONS.items():
            result = compute_all(geometry, weight_fn)
            print(
                f"{screen_name:<75} {weight_name:<8} "
                f"{result['BM']:>8.3f} {result['EM']:>8.3f} {result['SYM']:>8.3f}"
            )
            if png_path is not None:
                out_path = out_dir / f"{screen_name}.{weight_name}.png"
                _draw_overlay(png_path, geometry, weight_name, out_path)
            else:
                print(f"  (no matching .png for {screen_name} - visualization skipped)", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
