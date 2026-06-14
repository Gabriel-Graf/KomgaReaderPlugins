"""Smoke test for the specimen generator (deterministic, non-blank, expected size)."""
import os
import subprocess
import tempfile
from PIL import Image

# A TTF guaranteed present on this machine (used only by the test).
SYSTEM_TTF = "/usr/share/fonts/truetype/ebgaramond/EBGaramond12-Regular.ttf"
SCRIPT = os.path.join(os.path.dirname(__file__), "make_specimen.py")


def _render(out_path):
    subprocess.run(
        ["python3", SCRIPT, SYSTEM_TTF, "EB Garamond", out_path],
        check=True,
    )


def test_produces_non_blank_png_of_expected_size():
    with tempfile.TemporaryDirectory() as d:
        out = os.path.join(d, "spec.png")
        _render(out)
        assert os.path.exists(out)
        img = Image.open(out).convert("L")
        assert img.size == (1000, 420)  # WIDTH x HEIGHT from make_specimen
        # Not entirely white: at least some dark pixels were drawn (glyphs rendered).
        extrema = img.getextrema()  # (min, max)
        assert extrema[0] < 64, f"expected some dark pixels, got extrema={extrema}"


def test_is_deterministic():
    with tempfile.TemporaryDirectory() as d:
        a, b = os.path.join(d, "a.png"), os.path.join(d, "b.png")
        _render(a)
        _render(b)
        assert open(a, "rb").read() == open(b, "rb").read(), "specimen must be byte-stable"
