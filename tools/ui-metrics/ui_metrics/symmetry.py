"""Symmetry/balance metrics (spec 0033, Stage C).

IMPORTANT: this is a deliberately simplified pair of heuristics, NOT a
reproduction of published Aesthetic Perceived Balance (APB) research
(Levkowitz/Meyer-style weighting of region hue/saturation/area against a
fitted visual-weight model). No ready-made Python package for APB exists;
building the full published model is disproportionate to this tool's
advisory, report-only role (spec 0033 explicitly defers numeric acceptance
thresholds - see Допущение 12). Treat both numbers below as rough, relative
signals - not a validated reproduction of APB.
"""
import cv2
import numpy as np
from skimage.metrics import structural_similarity


def mirror_symmetry(image: np.ndarray) -> float:
    """image: HxWx3 uint8 RGB array.

    Returns SSIM (0..1, higher = more symmetric) between the image and its
    horizontal mirror flip, computed on grayscale.
    """
    gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
    flipped = np.fliplr(gray)

    # skimage's default win_size (7) needs both dimensions >= 7; degenerate
    # tiny inputs (unit test fixtures, corrupt crops) fall back to the
    # largest odd window that fits.
    smallest_side = min(gray.shape)
    if smallest_side < 7:
        win_size = smallest_side if smallest_side % 2 == 1 else smallest_side - 1
        if win_size < 1:
            return 1.0
        return float(structural_similarity(gray, flipped, win_size=win_size))

    return float(structural_similarity(gray, flipped))


def visual_balance(image: np.ndarray) -> float:
    """image: HxWx3 uint8 RGB array.

    Per-pixel visual weight = value * saturation (HSV). Returns the distance
    between the weight-weighted centroid and the geometric center, normalized
    by half the image diagonal (0 = perfectly balanced, up to ~1 = weight
    concentrated at a corner).
    """
    hsv = cv2.cvtColor(image, cv2.COLOR_RGB2HSV).astype(np.float64)
    saturation, value = hsv[..., 1], hsv[..., 2]
    weight = saturation * value

    height, width = weight.shape
    total = weight.sum()
    if total <= 1e-9:
        return 0.0

    ys, xs = np.mgrid[0:height, 0:width]
    centroid_y = (ys * weight).sum() / total
    centroid_x = (xs * weight).sum() / total

    center_y, center_x = (height - 1) / 2.0, (width - 1) / 2.0
    offset = np.hypot(centroid_y - center_y, centroid_x - center_x)
    half_diagonal = np.hypot(center_y, center_x)
    if half_diagonal <= 1e-9:
        return 0.0

    return float(offset / half_diagonal)
