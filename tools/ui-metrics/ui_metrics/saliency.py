"""Saliency (predicted visual attention) metric (spec 0033, Stage C).

DeepGaze (PyTorch, validated against human eye-tracking data on graphic
design imagery) was chosen over a lightweight OpenCV spectral-residual
candidate after a visual bake-off on the 6 Stage-A screenshots (spec 0033
Допущение 9) - the product owner reviewed side-by-side saliency maps and
picked DeepGaze. The rejected spectral-residual implementation has been
removed, not left dead in this module.

`deepgaze_saliency` returns an HxW float32 array normalized to [0, 1]
(higher = more predicted attention). `saliency_score` reduces that map to
one number for the CLI table - a rough proxy for "how concentrated is
predicted attention" (the map's spatial standard deviation), not a
validated UX metric on its own. DeepGaze is trained on natural
photographs/graphic design, not chat UIs - this is an explicit
extrapolation (spec 0033 Допущение 7/12), interpret with caution.
"""
import numpy as np

_model_cache = {}


def _load_model():
    # Module-level cache: constructing DeepGazeIIE(pretrained=True) reloads
    # every backbone's weights from disk each time (~600MB total) - without
    # this, a batch run over a directory of N screenshots would pay that
    # cost N times instead of once (found during Stage C peer review).
    if "model" not in _model_cache:
        import torch
        from deepgaze_pytorch import DeepGazeIIE

        device = "cuda" if torch.cuda.is_available() else "cpu"
        _model_cache["model"] = DeepGazeIIE(pretrained=True).to(device).eval()
        _model_cache["device"] = device
    return _model_cache["model"], _model_cache["device"]


def deepgaze_saliency(image: np.ndarray) -> np.ndarray:
    """image: HxWx3 uint8 RGB array.

    Lazy-imports torch/DeepGaze so importing this module never requires
    PyTorch to be installed unless this function is actually called (spec
    0033 Допущение 11) - keeps `colorfulness`/`feature_congestion`/
    `symmetry` usable without the ~600MB of DeepGaze model weights. The
    model itself is cached after the first call (see `_load_model`).
    """
    import torch

    model, device = _load_model()

    tensor = torch.from_numpy(image).permute(2, 0, 1).unsqueeze(0).float().to(device)
    centerbias = torch.zeros(1, image.shape[0], image.shape[1]).to(device)

    with torch.no_grad():
        log_density = model(tensor, centerbias)

    density = log_density.exp().squeeze().cpu().numpy()
    density_min, density_max = density.min(), density.max()
    if density_max - density_min <= 1e-9:
        return np.zeros_like(density, dtype=np.float32)
    return ((density - density_min) / (density_max - density_min)).astype(np.float32)


def saliency_score(saliency_map: np.ndarray) -> float:
    """Rough proxy for how concentrated predicted attention is: spatial
    standard deviation of the (already 0..1 normalized) saliency map."""
    return float(saliency_map.std())
