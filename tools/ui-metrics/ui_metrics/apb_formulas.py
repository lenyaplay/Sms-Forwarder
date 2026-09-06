"""Exact Ngo, Teo, Byrne (2003) Balance/Equilibrium/Symmetry formulas
(spec 0034, Milestone 29).

Ngo, D.C.L., Teo, L.S., Byrne, J.G. (2003), "Modelling interface
aesthetics", Information Sciences 152, 25-46, DOI 10.1016/S0020-0255(02)00404-8.

Unlike the old `symmetry.py` (spec 0033, Stage C - a deliberately simplified,
explicitly-not-APB pixel heuristic, deleted once this module made it
redundant), this module works over a list of discrete screen *objects*
(bounding boxes exported from a real Compose semantics tree - see
`android_gateway/.../ui/tooling/SemanticsGeometryExport.kt`), matching how
the paper itself defines BM/EM/SYM - no pixel data involved.

The transcription below follows the paper's formulas as closely as this
project's research could verify (Milestone 27/29). Every object i has a
position/size and a weight `a_i`; the paper leaves the choice of `a_i`
explicitly open (its own worked examples use `a_i=1`, i.e. count-based
weighting) - see `WeightFn` below for the three variants this project
compares (spec 0034, requirements doc 0034).

All three metrics are returned normalized to [0, 1] where 1 = perfectly
balanced/in equilibrium/symmetric and 0 = maximally not (this project's
own "higher = better" convention), not necessarily the paper's own
scale/sign convention for every formula.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


@dataclass(frozen=True)
class GeometryObject:
    x: float
    y: float
    width: float
    height: float
    is_textual: bool
    is_interactive: bool

    @property
    def area(self) -> float:
        return self.width * self.height

    @property
    def center_x(self) -> float:
        return self.x + self.width / 2.0

    @property
    def center_y(self) -> float:
        return self.y + self.height / 2.0


@dataclass(frozen=True)
class ScreenGeometry:
    screen_width: float
    screen_height: float
    objects: tuple[GeometryObject, ...]


def load_geometry(path: str | Path) -> ScreenGeometry:
    """Loads a geometry JSON written by SemanticsGeometryExport.kt."""
    data = json.loads(Path(path).read_text())
    objects = tuple(
        GeometryObject(
            x=obj["x"],
            y=obj["y"],
            width=obj["width"],
            height=obj["height"],
            is_textual=obj["isTextual"],
            is_interactive=obj["isInteractive"],
        )
        for obj in data["objects"]
    )
    return ScreenGeometry(
        screen_width=data["screenWidth"],
        screen_height=data["screenHeight"],
        objects=objects,
    )


# A weight function maps one object to its "importance" a_i in the BM/EM/SYM
# formulas. `equal_weight` is the paper's own default; `area_weight` and
# `type_weight` are this project's candidate alternatives, compared on real
# screens before the product owner picks a default (spec 0034 open question 1).
WeightFn = Callable[[GeometryObject], float]


def equal_weight(_obj: GeometryObject) -> float:
    """a_i = 1 for every object - the paper's own default (Ngo/Teo/Byrne 2003)."""
    return 1.0


def area_weight(obj: GeometryObject) -> float:
    """a_i = bounding-box area - larger elements pull balance harder."""
    return max(obj.area, 1e-6)


# Not from the paper - a project-specific heuristic (requirements doc 0034,
# open question 1): text/interactive elements are what a chat UI's users
# actually attend to, so they're weighted above purely decorative/layout
# nodes (dividers, background containers, spacer boxes).
TYPE_WEIGHT_TEXT_OR_INTERACTIVE = 2.0
TYPE_WEIGHT_DECORATIVE = 1.0


def type_weight(obj: GeometryObject) -> float:
    if obj.is_textual or obj.is_interactive:
        return TYPE_WEIGHT_TEXT_OR_INTERACTIVE
    return TYPE_WEIGHT_DECORATIVE


WEIGHT_FUNCTIONS: dict[str, WeightFn] = {
    "equal": equal_weight,
    "area": area_weight,
    "type": type_weight,
}


def _weighted_centroid(geometry: ScreenGeometry, weight_fn: WeightFn) -> tuple[float, float]:
    total = sum(weight_fn(obj) for obj in geometry.objects)
    if total <= 1e-9:
        cx = geometry.screen_width / 2.0
        cy = geometry.screen_height / 2.0
        return cx, cy
    cx = sum(weight_fn(obj) * obj.center_x for obj in geometry.objects) / total
    cy = sum(weight_fn(obj) * obj.center_y for obj in geometry.objects) / total
    return cx, cy


def balance_measure(geometry: ScreenGeometry, weight_fn: WeightFn = equal_weight) -> float:
    """BM (paper's formulas 1-4): imbalance between left/right and
    top/bottom halves of the screen, each half's "moment" being the sum of
    `a_i * distance-from-axis` for objects centered in that half.

    Returns 1.0 when both halves carry equal moment on both axes (perfect
    balance), decreasing toward 0.0 as one side dominates.
    """
    x_axis = geometry.screen_width / 2.0
    y_axis = geometry.screen_height / 2.0

    left = right = top = bottom = 0.0
    for obj in geometry.objects:
        a = weight_fn(obj)
        dx = abs(obj.center_x - x_axis)
        dy = abs(obj.center_y - y_axis)
        if obj.center_x < x_axis:
            left += a * dx
        else:
            right += a * dx
        if obj.center_y < y_axis:
            top += a * dy
        else:
            bottom += a * dy

    def imbalance(a: float, b: float) -> float:
        total = a + b
        if total <= 1e-9:
            return 0.0
        return abs(a - b) / total

    horizontal_imbalance = imbalance(left, right)
    vertical_imbalance = imbalance(top, bottom)
    return 1.0 - (horizontal_imbalance + vertical_imbalance) / 2.0


def equilibrium_measure(geometry: ScreenGeometry, weight_fn: WeightFn = equal_weight) -> float:
    """EM (paper's formulas 5-6): how close the overall weighted centroid
    of all objects is to the screen's geometric center, normalized by half
    the screen diagonal. 1.0 = centroid exactly at the geometric center.
    """
    cx, cy = _weighted_centroid(geometry, weight_fn)
    center_x = geometry.screen_width / 2.0
    center_y = geometry.screen_height / 2.0

    offset = ((cx - center_x) ** 2 + (cy - center_y) ** 2) ** 0.5
    half_diagonal = ((center_x) ** 2 + (center_y) ** 2) ** 0.5
    if half_diagonal <= 1e-9:
        return 1.0
    return max(0.0, 1.0 - offset / half_diagonal)


def symmetry_measure(geometry: ScreenGeometry, weight_fn: WeightFn = equal_weight) -> float:
    """SYM (paper's formulas 7-17, quadrant-based): splits the screen into
    four quadrants around its geometric center and compares the weighted
    "mass" (sum of a_i) between the two quadrant-pairs that are mirror
    images of each other across the vertical axis (top-left vs top-right,
    bottom-left vs bottom-right) and across the horizontal axis (top-left
    vs bottom-left, top-right vs bottom-right). 1.0 = perfectly symmetric
    both ways.
    """
    x_axis = geometry.screen_width / 2.0
    y_axis = geometry.screen_height / 2.0

    top_left = top_right = bottom_left = bottom_right = 0.0
    for obj in geometry.objects:
        a = weight_fn(obj)
        is_left = obj.center_x < x_axis
        is_top = obj.center_y < y_axis
        if is_top and is_left:
            top_left += a
        elif is_top and not is_left:
            top_right += a
        elif not is_top and is_left:
            bottom_left += a
        else:
            bottom_right += a

    def mirror_similarity(a: float, b: float) -> float:
        total = a + b
        if total <= 1e-9:
            return 1.0
        return 1.0 - abs(a - b) / total

    vertical_axis_symmetry = (
        mirror_similarity(top_left, top_right) + mirror_similarity(bottom_left, bottom_right)
    ) / 2.0
    horizontal_axis_symmetry = (
        mirror_similarity(top_left, bottom_left) + mirror_similarity(top_right, bottom_right)
    ) / 2.0
    return (vertical_axis_symmetry + horizontal_axis_symmetry) / 2.0


def compute_all(geometry: ScreenGeometry, weight_fn: WeightFn = equal_weight) -> dict[str, float]:
    return {
        "BM": balance_measure(geometry, weight_fn),
        "EM": equilibrium_measure(geometry, weight_fn),
        "SYM": symmetry_measure(geometry, weight_fn),
    }
