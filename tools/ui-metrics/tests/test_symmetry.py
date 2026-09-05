import numpy as np

from ui_metrics.symmetry import mirror_symmetry, visual_balance


def test_symmetric_image_has_near_one_mirror_symmetry():
    image = np.zeros((32, 32, 3), dtype=np.uint8)
    image[:, :16] = 200
    image[:, 16:] = 200
    assert mirror_symmetry(image) > 0.99


def test_asymmetric_image_has_clearly_lower_mirror_symmetry():
    image = np.zeros((32, 32, 3), dtype=np.uint8)
    image[:, :16] = 255
    image[:, 16:] = 0
    assert mirror_symmetry(image) < 0.5


def test_does_not_crash_on_a_small_image():
    image = np.zeros((4, 4, 3), dtype=np.uint8)
    result = mirror_symmetry(image)
    assert 0.0 <= result <= 1.0


def test_centered_visual_weight_has_near_zero_balance_offset():
    image = np.zeros((64, 64, 3), dtype=np.uint8)
    image[24:40, 24:40] = [0, 200, 200]  # saturated, bright square at center
    assert visual_balance(image) < 0.1


def test_corner_concentrated_weight_has_clearly_higher_balance_offset():
    image = np.zeros((64, 64, 3), dtype=np.uint8)
    image[0:16, 0:16] = [0, 200, 200]  # saturated, bright square in a corner
    assert visual_balance(image) > 0.3


def test_flat_image_has_zero_balance_offset():
    image = np.full((16, 16, 3), 100, dtype=np.uint8)
    assert visual_balance(image) == 0.0


def test_visual_balance_does_not_crash_on_a_small_image():
    image = np.zeros((4, 4, 3), dtype=np.uint8)
    result = visual_balance(image)
    assert result >= 0.0
