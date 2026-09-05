import numpy as np

from ui_metrics.colorfulness import colorfulness


def test_flat_gray_image_has_near_zero_colorfulness():
    image = np.full((32, 32, 3), 128, dtype=np.uint8)
    assert colorfulness(image) < 0.01


def test_high_variance_colors_have_clearly_positive_colorfulness():
    rng = np.random.default_rng(42)
    image = rng.integers(0, 256, size=(32, 32, 3), dtype=np.uint8)
    assert colorfulness(image) > 10.0


def test_does_not_crash_on_degenerate_one_pixel_image():
    image = np.array([[[10, 20, 30]]], dtype=np.uint8)
    result = colorfulness(image)
    assert result >= 0.0
