import numpy as np

from ui_metrics.saliency import deepgaze_saliency, saliency_score


def test_returns_same_size_map_in_expected_range():
    image = np.random.default_rng(0).integers(0, 256, size=(64, 64, 3), dtype=np.uint8)
    saliency_map = deepgaze_saliency(image)
    assert saliency_map.shape == (64, 64)
    assert saliency_map.min() >= 0.0
    assert saliency_map.max() <= 1.0 + 1e-6


def test_does_not_crash_on_a_flat_image():
    image = np.full((64, 64, 3), 128, dtype=np.uint8)
    saliency_map = deepgaze_saliency(image)
    assert saliency_map.shape == (64, 64)


def test_saliency_score_is_non_negative():
    image = np.random.default_rng(1).integers(0, 256, size=(64, 64, 3), dtype=np.uint8)
    saliency_map = deepgaze_saliency(image)
    assert saliency_score(saliency_map) >= 0.0
