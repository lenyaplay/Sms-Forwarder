# ui-metrics

Pixel-based UI-metrics analysis for `sms_forwarder` (spec [0033](../../docs/specs/0033-ui-metrics-tooling.md), Stage B). Consumes the Roborazzi baseline PNGs produced by `android_gateway`'s Stage A snapshot tests (`android_gateway/app/src/test/snapshots/`).

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

Prints `colorfulness` and `feature_congestion` for every `.png` under the given directory (or a single file if a file path is given).

## Scope (Stage B only)

Implemented: `colorfulness` (Hasler-Süsstrunk), `feature_congestion` (simplified clutter proxy — **not** the published Rosenholtz algorithm, see the module docstring in `ui_metrics/feature_congestion.py` for why and what the simplification actually computes).

Not implemented yet: saliency, symmetry/balance (Stage C — deferred, model choice not yet decided per spec 0033).

## Tests

```
pytest tests/
```
Tests check the harness (image loading, no crashes, plausible relative ordering) — not "correctness" of the metrics against external ground truth, since neither metric has one available in this project.
