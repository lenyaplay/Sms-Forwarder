from ui_metrics.apb_formulas import (
    GeometryObject,
    ScreenGeometry,
    area_weight,
    balance_measure,
    equal_weight,
    equilibrium_measure,
    symmetry_measure,
    type_weight,
)


def obj(x, y, w, h, textual=False, interactive=False) -> GeometryObject:
    return GeometryObject(x=x, y=y, width=w, height=h, is_textual=textual, is_interactive=interactive)


def test_two_mirrored_equal_objects_are_perfectly_balanced():
    # 100x100 screen, one 10x10 object at each side, equidistant from center.
    geometry = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(obj(10, 45, 10, 10), obj(80, 45, 10, 10)),
    )
    assert balance_measure(geometry, equal_weight) > 0.99


def test_one_object_per_quadrant_mirrored_both_ways_is_perfectly_symmetric():
    # One equal-size object centered in each quadrant, mirrored across both axes.
    geometry = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(
            obj(20, 20, 10, 10),  # top-left
            obj(70, 20, 10, 10),  # top-right
            obj(20, 70, 10, 10),  # bottom-left
            obj(70, 70, 10, 10),  # bottom-right
        ),
    )
    assert symmetry_measure(geometry, equal_weight) > 0.99


def test_single_object_at_geometric_center_has_perfect_equilibrium():
    geometry = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(obj(45, 45, 10, 10),),
    )
    assert equilibrium_measure(geometry, equal_weight) > 0.99


def test_single_object_in_corner_has_low_equilibrium_and_balance():
    geometry = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(obj(0, 0, 10, 10),),
    )
    assert equilibrium_measure(geometry, equal_weight) < 0.5
    # A lone object left/top of both axes maximally imbalances both axes.
    assert balance_measure(geometry, equal_weight) < 0.5


def test_single_object_in_corner_has_lower_symmetry_than_quadrant_mirrored_layout():
    # A lone object occupies exactly one of the four quadrant-pair
    # comparisons the formula makes (see symmetry_measure docstring) while
    # its three empty counterpart quadrants trivially "match" each other -
    # this is a known limitation of the quadrant-mass approach with very
    # few objects, not treated as a bug: real screens have many objects.
    lone_corner = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(obj(0, 0, 10, 10),),
    )
    mirrored_quadrants = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(
            obj(20, 20, 10, 10),
            obj(70, 20, 10, 10),
            obj(20, 70, 10, 10),
            obj(70, 70, 10, 10),
        ),
    )
    assert symmetry_measure(lone_corner, equal_weight) < symmetry_measure(mirrored_quadrants, equal_weight)


def test_no_objects_is_treated_as_perfectly_balanced():
    geometry = ScreenGeometry(screen_width=100.0, screen_height=100.0, objects=())
    assert balance_measure(geometry, equal_weight) == 1.0
    assert equilibrium_measure(geometry, equal_weight) == 1.0
    assert symmetry_measure(geometry, equal_weight) == 1.0


def test_area_weight_lets_one_large_object_outweigh_a_mirrored_small_pair():
    # A big object on the left, a tiny one on the right at the mirrored spot -
    # equal weighting would see two "matching" object counts per quadrant,
    # but area weighting must catch the real mass imbalance.
    geometry = ScreenGeometry(
        screen_width=100.0,
        screen_height=100.0,
        objects=(obj(0, 40, 40, 20), obj(90, 45, 5, 5)),
    )
    assert area_weight(geometry.objects[0]) > area_weight(geometry.objects[1])
    assert balance_measure(geometry, area_weight) < balance_measure(geometry, equal_weight)


def test_type_weight_favors_textual_and_interactive_objects():
    decorative = obj(0, 0, 10, 10)
    textual = obj(0, 0, 10, 10, textual=True)
    interactive = obj(0, 0, 10, 10, interactive=True)
    assert type_weight(textual) > type_weight(decorative)
    assert type_weight(interactive) > type_weight(decorative)
