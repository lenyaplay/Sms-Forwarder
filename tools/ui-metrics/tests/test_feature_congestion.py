import numpy as np

from ui_metrics.feature_congestion import feature_congestion


def test_flat_image_has_near_zero_clutter():
    image = np.full((64, 64, 3), 128, dtype=np.uint8)
    assert feature_congestion(image) < 0.01


def test_random_noise_has_clearly_higher_clutter_than_flat_image():
    flat = np.full((64, 64, 3), 128, dtype=np.uint8)
    rng = np.random.default_rng(7)
    noise = rng.integers(0, 256, size=(64, 64, 3), dtype=np.uint8)

    flat_clutter = feature_congestion(flat)
    noise_clutter = feature_congestion(noise)

    assert noise_clutter > flat_clutter * 10


def test_does_not_crash_on_a_small_image():
    image = np.zeros((4, 4, 3), dtype=np.uint8)
    result = feature_congestion(image)
    assert result >= 0.0
