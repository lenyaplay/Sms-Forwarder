"""Report BM/EM/SYM (Ngo, Teo, Byrne 2003 - see apb_formulas.py) for real
baseline screens, with a visualization overlay (spec 0034, Milestone 29).

Usage: python -m ui_metrics.apb_report <snapshots-dir> [<out-dir>]

For every `*.geometry.json` in <snapshots-dir> (written by
SemanticsGeometryExport.kt next to the matching `*.png`): prints BM/EM/SYM
and writes one visualization PNG into <out-dir> (default: ./out/, git-
ignored) overlaying the weighted centroid (equilibrium) and the quadrant
split axes (symmetry) onto the original screenshot.

Formerly `compare_weights.py`: an earlier version of this tool compared
three configurable object weights (equal/area/type). That premise was
wrong - reading the paper's actual text (not available when that version
was written) showed `a_i` inside BM/EM is fixed to object area by the
formula itself, not a free parameter, and SYM uses no weight at all. This
version reports the single, unambiguous BM/EM/SYM per screen.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

from ui_metrics.apb_formulas import ScreenGeometry, compute_all, load_geometry


def _matching_png(geometry_json_path: Path) -> Path | None:
    # "<FQCN>.<method>.geometry.json" -> "<FQCN>.<method>.png"
    name = geometry_json_path.name.removesuffix(".geometry.json") + ".png"
    candidate = geometry_json_path.with_name(name)
    return candidate if candidate.exists() else None


def _draw_overlay(png_path: Path, geometry: ScreenGeometry, out_path: Path) -> None:
    image = Image.open(png_path).convert("RGB")
    draw = ImageDraw.Draw(image)

    scale_x = image.width / geometry.screen_width
    scale_y = image.height / geometry.screen_height

    # Quadrant split axes (symmetry_measure) - the screen's geometric center.
    center_x_px = image.width / 2.0
    center_y_px = image.height / 2.0
    draw.line([(center_x_px, 0), (center_x_px, image.height)], fill=(0, 200, 255), width=2)
    draw.line([(0, center_y_px), (image.width, center_y_px)], fill=(0, 200, 255), width=2)

    # Weighted (by area, per the paper's own a_i definition) centroid (equilibrium_measure).
    total_area = sum(obj.area for obj in geometry.objects)
    if total_area > 1e-9:
        cx = sum(obj.area * obj.center_x for obj in geometry.objects) / total_area
        cy = sum(obj.area * obj.center_y for obj in geometry.objects) / total_area
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
        print("usage: python -m ui_metrics.apb_report <snapshots-dir> [<out-dir>]", file=sys.stderr)
        return 1

    snapshots_dir = Path(argv[0])
    out_dir = Path(argv[1]) if len(argv) == 2 else Path("out")

    geometry_files = sorted(snapshots_dir.glob("*.geometry.json"))
    if not geometry_files:
        print(f"no *.geometry.json files found under {snapshots_dir}", file=sys.stderr)
        return 1

    header = f"{'screen':<75} {'BM':>8} {'EM':>8} {'SYM':>8}"
    print(header)
    for geometry_path in geometry_files:
        screen_name = geometry_path.name.removesuffix(".geometry.json")
        try:
            geometry = load_geometry(geometry_path)
        except Exception as e:
            print(f"{screen_name:<75} ERROR: {e}")
            continue

        result = compute_all(geometry)
        print(f"{screen_name:<75} {result['BM']:>8.3f} {result['EM']:>8.3f} {result['SYM']:>8.3f}")

        png_path = _matching_png(geometry_path)
        if png_path is not None:
            _draw_overlay(png_path, geometry, out_dir / f"{screen_name}.png")
        else:
            print(f"  (no matching .png for {screen_name} - visualization skipped)", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
