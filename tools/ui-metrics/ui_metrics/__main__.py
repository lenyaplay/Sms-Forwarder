"""CLI: python -m ui_metrics <path-to-png-or-directory>

Spec 0033, Stages B+C. Prints colorfulness, feature_congestion, and saliency
score for each PNG. Balance/symmetry moved to exact Ngo/Teo/Byrne (2003)
formulas over Compose semantics geometry (spec 0034, Milestone 29) - see
`python -m ui_metrics.apb_report <snapshots-dir>` instead; the old
pixel-based `symmetry.py` heuristic was deleted as no longer useful once the
exact formulas were available.
"""
import sys
from pathlib import Path

import cv2

from ui_metrics.colorfulness import colorfulness
from ui_metrics.feature_congestion import feature_congestion
from ui_metrics.saliency import deepgaze_saliency, saliency_score


def _load_rgb(path: Path):
    bgr = cv2.imread(str(path))
    if bgr is None:
        raise ValueError(f"could not read image: {path}")
    return cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)


def _iter_pngs(path: Path):
    if path.is_dir():
        yield from sorted(path.glob("*.png"))
    else:
        yield path


def main(argv=None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    if len(argv) != 1:
        print("usage: python -m ui_metrics <path-to-png-or-directory>", file=sys.stderr)
        return 1

    target = Path(argv[0])
    if not target.exists():
        print(f"path does not exist: {target}", file=sys.stderr)
        return 1

    files = list(_iter_pngs(target))
    if not files:
        print(f"no .png files found under {target}", file=sys.stderr)
        return 1

    header = (
        f"{'file':<70} {'colorfulness':>14} {'feature_congestion':>20} "
        f"{'saliency':>10}"
    )
    print(header)
    for file_path in files:
        # Report-only tool (same philosophy as the JVM contrast/touch-target
        # report, spec 0033 Допущение 4): one unreadable file must not abort
        # the rest of the batch - report it inline and keep going.
        try:
            image = _load_rgb(file_path)
            cf = colorfulness(image)
            fc = feature_congestion(image)
            sal = saliency_score(deepgaze_saliency(image))
            print(
                f"{file_path.name:<70} {cf:>14.3f} {fc:>20.3f} "
                f"{sal:>10.3f}"
            )
        except Exception as e:
            print(f"{file_path.name:<70} ERROR: {e}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
