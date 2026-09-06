# ui-metrics

Pixel-based UI-metrics analysis for `sms_forwarder` (spec [0033](../../docs/specs/0033-ui-metrics-tooling.md)) plus exact Ngo/Teo/Byrne (2003) balance/equilibrium/symmetry formulas over Compose semantics geometry (spec [0034](../../docs/specs/0034-exact-apb-formulas.md)). Consumes the Roborazzi baseline PNGs (and, for `apb_formulas`/`compare_weights`, the geometry JSON sidecars) produced by `android_gateway`'s Stage A snapshot tests (`android_gateway/app/src/test/snapshots/`).

Standalone Python package, not part of the Gradle build. Run manually or wire into a Gradle `exec {}` task later if needed.

## Setup

```
cd tools/ui-metrics
python -m venv .venv
.venv\Scripts\activate   # Windows; source .venv/bin/activate on Unix
pip install -r requirements.txt
```

## Usage

```
python -m ui_metrics ../../android_gateway/app/src/test/snapshots
```

Prints `colorfulness`, `feature_congestion`, and `saliency` for every `.png` under the given directory (or a single file if a file path is given).

```
python -m ui_metrics.compare_weights ../../android_gateway/app/src/test/snapshots [out-dir]
```

Prints BM/EM/SYM (exact Ngo/Teo/Byrne 2003 formulas, spec 0034) for every `*.geometry.json` sidecar under the given directory, under all three weight functions (`equal`/`area`/`type` - see `ui_metrics/apb_formulas.py`), and writes one visualization PNG per (screen, weight set) into `out-dir` (default `./out/`, git-ignored) overlaying the weighted centroid and quadrant axes on the matching screenshot.

## Scope

Implemented: `colorfulness` (Hasler-Süsstrunk), `feature_congestion` (simplified clutter proxy — **not** the published Rosenholtz algorithm, see the module docstring in `ui_metrics/feature_congestion.py` for why and what the simplification actually computes), `saliency` (DeepGaze), `apb_formulas` (exact Ngo/Teo/Byrne 2003 BM/EM/SYM over Compose semantics geometry, spec 0034 — replaces the old pixel-based `symmetry.py` heuristic from spec 0033 Stage C, deleted as no longer useful once the exact formulas were available).

## Tests

```
pytest tests/
```
Tests check the harness (image loading, no crashes, plausible relative ordering) — not "correctness" of the metrics against external ground truth, since neither metric has one available in this project.
