"""Hasler-Süsstrunk colorfulness metric (spec 0033, Stage B).

M. Hasler and S. Süsstrunk, "Measuring colorfulness in natural images", 2003.
"""
import numpy as np


def colorfulness(image: np.ndarray) -> float:
    """image: HxWx3 uint8 RGB array. Returns a non-negative colorfulness score."""
    rgb = image.astype(np.float64)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]

    rg = r - g
    yb = 0.5 * (r + g) - b

    std_root = np.sqrt(rg.std() ** 2 + yb.std() ** 2)
    mean_root = np.sqrt(rg.mean() ** 2 + yb.mean() ** 2)

    return float(std_root + 0.3 * mean_root)
