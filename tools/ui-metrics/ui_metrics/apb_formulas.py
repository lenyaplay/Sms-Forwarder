"""Ngo, Teo, Byrne (2003) Balance/Equilibrium/Symmetry formulas
(spec 0034, Milestone 29; corrected 2026-09-06 against the primary source).

Ngo, D.C.L., Teo, L.S., Byrne, J.G. (2003), "Modelling interface
aesthetics", Information Sciences 152, 25-46, DOI 10.1016/S0020-0255(02)00404-8.

Works over a list of discrete screen *objects* (bounding boxes exported
from a real Compose semantics tree - see
`android_gateway/.../ui/tooling/SemanticsGeometryExport.kt`), matching how
the paper itself defines BM/EM/SYM - no pixel data involved.

IMPORTANT correction (2026-09-06): an earlier version of this module used
an `ai` weight that was a *configurable* parameter (equal/area/type), on
the belief that the paper leaves per-object weighting open. Reading the
actual paper (the product owner supplied the PDF) showed this was wrong:
`a_i` inside BM (eq. 4) and EM (eq. 6-7) is explicitly defined as "the area
of the object" - a fixed part of the formula, not a free parameter. The
paper's own open weighting question (`alpha_i`, eq. 59) is a *different*
weight, for combining all 14 aesthetic measures (BM, EM, SYM, Sequence,
Cohesion, ...) into one overall Order Measure - out of scope here, since
this module only computes BM/EM/SYM individually. SYM (eq. 8-17) does not
use any per-object weight at all. The product owner's earlier decision "all
three weight sets remain equally valid, no default" is therefore void - it
answered a question the paper does not actually leave open. This version
follows the paper literally: no weight parameter anywhere.

Formula numbers below refer to the paper's own numbering.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

_EPS = 1e-9


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


def geometry_from_objects(screen_width: float, screen_height: float, objects) -> ScreenGeometry:
    """Builds a ScreenGeometry from (x, y, width, height) tuples - used by
    tests to reproduce the paper's own Table 1 layouts without a JSON file.
    """
    return ScreenGeometry(
        screen_width=screen_width,
        screen_height=screen_height,
        objects=tuple(
            GeometryObject(x=x, y=y, width=w, height=h, is_textual=False, is_interactive=False)
            for (x, y, w, h) in objects
        ),
    )


def balance_measure(geometry: ScreenGeometry) -> float:
    """BM, eq. (1)-(4). a_ij = area of object i (paper's own definition,
    not configurable). d_ij = distance from the object's center to the
    frame's center line on the split axis.
    """
    x_axis = geometry.screen_width / 2.0
    y_axis = geometry.screen_height / 2.0

    w_left = w_right = w_top = w_bottom = 0.0
    for obj in geometry.objects:
        a = obj.area
        dx = abs(obj.center_x - x_axis)
        dy = abs(obj.center_y - y_axis)
        if obj.center_x < x_axis:
            w_left += a * dx
        else:
            w_right += a * dx
        if obj.center_y < y_axis:
            w_top += a * dy
        else:
            w_bottom += a * dy

    def signed_ratio(a: float, b: float) -> float:
        denom = max(abs(a), abs(b))
        if denom <= _EPS:
            return 0.0
        return (a - b) / denom

    bm_vertical = signed_ratio(w_left, w_right)
    bm_horizontal = signed_ratio(w_top, w_bottom)
    return 1.0 - (abs(bm_vertical) + abs(bm_horizontal)) / 2.0


def equilibrium_measure(geometry: ScreenGeometry) -> float:
    """EM, eq. (5)-(7). a_i = area of object i. Note the paper's own
    normalization divides by n (object count) as well as frame
    width/height - equilibrium's sensitivity shrinks as more objects are
    present, not just as they move further from center.
    """
    n = len(geometry.objects)
    total_area = sum(obj.area for obj in geometry.objects)
    if n == 0 or total_area <= _EPS:
        return 1.0

    x_c = geometry.screen_width / 2.0
    y_c = geometry.screen_height / 2.0

    sum_x = sum(obj.area * (obj.center_x - x_c) for obj in geometry.objects)
    sum_y = sum(obj.area * (obj.center_y - y_c) for obj in geometry.objects)

    em_x = 2.0 * sum_x / (n * geometry.screen_width * total_area)
    em_y = 2.0 * sum_y / (n * geometry.screen_height * total_area)
    return 1.0 - (abs(em_x) + abs(em_y)) / 2.0


_QUADRANTS = ("UL", "UR", "LL", "LR")


def _quadrant(obj: GeometryObject, x_c: float, y_c: float) -> str:
    left = obj.center_x < x_c
    top = obj.center_y < y_c
    if top and left:
        return "UL"
    if top and not left:
        return "UR"
    if not top and left:
        return "LL"
    return "LR"


def _raw_quadrant_sums(geometry: ScreenGeometry) -> dict[str, dict[str, float]]:
    """eq. (12)-(17): X, Y, H, B, Theta, R raw sums per quadrant."""
    x_c = geometry.screen_width / 2.0
    y_c = geometry.screen_height / 2.0
    sums = {q: {"X": 0.0, "Y": 0.0, "H": 0.0, "B": 0.0, "Theta": 0.0, "R": 0.0} for q in _QUADRANTS}

    for obj in geometry.objects:
        q = _quadrant(obj, x_c, y_c)
        dx = obj.center_x - x_c
        dy = obj.center_y - y_c
        sums[q]["X"] += abs(dx)
        sums[q]["Y"] += abs(dy)
        sums[q]["H"] += obj.height
        sums[q]["B"] += obj.width
        # eq. (16): Theta_j = sum |y-yc| / |x-xc| - undefined when the
        # object's center sits exactly on the vertical axis; the paper
        # does not address this case explicitly, so such objects are
        # skipped from Theta only (documented project assumption).
        if abs(dx) > _EPS:
            sums[q]["Theta"] += abs(dy) / abs(dx)
        sums[q]["R"] += (dx * dx + dy * dy) ** 0.5

    return sums


def _normalize_quadrant_values(raw: dict[str, dict[str, float]]) -> dict[str, dict[str, float]]:
    """Produces the "primed" (normalised) X', Y', H', B', Theta', R' values.

    The paper states these are "the normalised values of" the raw sums
    (eq. 12-17) but the copy of the text available to this project does not
    give the normalization formula explicitly. Project assumption
    (documented, not from the paper): normalize each quantity by its own
    max across the four quadrants, so each primed value lands in [0, 1] -
    the same "divide by the max" pattern the paper uses elsewhere (e.g. BM,
    eq. 2-3). If both are 0, the primed value is 0 for that quadrant.
    """
    keys = ("X", "Y", "H", "B", "Theta", "R")
    normalized = {q: {} for q in _QUADRANTS}
    for key in keys:
        values = {q: raw[q][key] for q in _QUADRANTS}
        max_value = max(values.values())
        for q in _QUADRANTS:
            normalized[q][key] = values[q] / max_value if max_value > _EPS else 0.0
    return normalized


def symmetry_measure(geometry: ScreenGeometry) -> float:
    """SYM, eq. (8)-(17). No per-object weight in the paper's own formula -
    purely geometric (position/size), not area-weighted.
    """
    if not geometry.objects:
        return 1.0

    raw = _raw_quadrant_sums(geometry)
    primed = _normalize_quadrant_values(raw)

    def term(quadrant_a: str, quadrant_b: str) -> float:
        keys = ("X", "Y", "H", "B", "Theta", "R")
        return sum(abs(primed[quadrant_a][k] - primed[quadrant_b][k]) for k in keys)

    # eq. (9): vertical symmetry - compares UL<->UR and LL<->LR (mirrored
    # across the vertical axis).
    sym_vertical = (term("UL", "UR") + term("LL", "LR")) / 12.0
    # eq. (10): horizontal symmetry - UL<->LL and UR<->LR.
    sym_horizontal = (term("UL", "LL") + term("UR", "LR")) / 12.0
    # eq. (11): radial symmetry - UL<->LR and UR<->LL.
    sym_radial = (term("UL", "LR") + term("UR", "LL")) / 12.0

    return 1.0 - (abs(sym_vertical) + abs(sym_horizontal) + abs(sym_radial)) / 3.0


def compute_all(geometry: ScreenGeometry) -> dict[str, float]:
    return {
        "BM": balance_measure(geometry),
        "EM": equilibrium_measure(geometry),
        "SYM": symmetry_measure(geometry),
    }
