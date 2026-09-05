"""Feature-congestion-inspired visual clutter metric (spec 0033, Stage B).

IMPORTANT: this is a deliberately simplified approximation, NOT the published
Rosenholtz feature-congestion algorithm (which uses a multi-scale DOG pyramid
+ per-pixel local covariance + Mahalanobis distance across a joint
color/orientation feature space). That is research-grade complexity
disproportionate to this tool's advisory, report-only role (spec 0033
explicitly defers numeric acceptance thresholds - see Допущение 7).

This approximation instead sums normalized local standard deviation of L/a/b
(CIE Lab) and local gradient-orientation variance across a few box-filter
scales into one per-pixel "clutter" map, then averages it into a single
number. Treat the result as a rough, relative signal - not a validated
reproduction of Rosenholtz's metric.
"""
import cv2
import numpy as np

DEFAULT_SCALES = (3, 9, 27)


def _local_std(channel: np.ndarray, ksize: int) -> np.ndarray:
    channel = channel.astype(np.float64)
    mean = cv2.blur(channel, (ksize, ksize))
    mean_sq = cv2.blur(channel * channel, (ksize, ksize))
    variance = np.maximum(mean_sq - mean * mean, 0.0)
    return np.sqrt(variance)


def feature_congestion(image: np.ndarray, scales=DEFAULT_SCALES) -> float:
    """image: HxWx3 uint8 RGB array. Returns a non-negative clutter score."""
    lab = cv2.cvtColor(image, cv2.COLOR_RGB2LAB).astype(np.float64)
    l_channel, a_channel, b_channel = lab[..., 0], lab[..., 1], lab[..., 2]

    grad_x = cv2.Sobel(l_channel, cv2.CV_64F, 1, 0, ksize=3)
    grad_y = cv2.Sobel(l_channel, cv2.CV_64F, 0, 1, ksize=3)
    orientation = np.arctan2(grad_y, grad_x)

    clutter = np.zeros(l_channel.shape, dtype=np.float64)
    for ksize in scales:
        for channel in (l_channel, a_channel, b_channel, orientation):
            local_std = _local_std(channel, ksize)
            max_val = local_std.max()
            # Threshold well above float-precision noise (~1e-6 observed from
            # box-filtering a perfectly flat image with a large kernel) so a
            # uniform image doesn't get normalized noise/noise -> 1.0 added in.
            if max_val > 1e-3:
                clutter += local_std / max_val

    return float(clutter.mean())
