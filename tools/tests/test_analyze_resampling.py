import importlib.util
import math
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "analyze_resampling.py"
SPEC = importlib.util.spec_from_file_location("analyze_resampling", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ResamplingAnalysisTest(unittest.TestCase):
    def test_linear_resampling_preserves_constant_signal_and_duration(self):
        source = [0.25] * 441
        converted = MODULE.linear_resample(source, 44_100, 48_000)
        self.assertEqual(480, len(converted))
        self.assertTrue(all(math.isclose(0.25, value) for value in converted))

    def test_onset_uses_first_material_transient_not_later_peak(self):
        samples = [0.0] * 20
        samples[5] = 0.2
        samples[12] = 1.0
        self.assertEqual(5, MODULE.onset_frame(samples))

    def test_identical_signals_have_zero_normalized_error(self):
        values = [0.0, 0.5, -0.5, 0.0]
        self.assertEqual(0.0, MODULE.normalized_rmse(values, values))


if __name__ == "__main__":
    unittest.main()
