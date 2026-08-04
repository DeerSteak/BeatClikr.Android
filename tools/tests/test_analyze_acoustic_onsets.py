import importlib.util
import math
import tempfile
import unittest
import wave
from array import array
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "analyze_acoustic_onsets.py"
SPEC = importlib.util.spec_from_file_location("analyze_acoustic_onsets", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class AcousticOnsetAnalysisTest(unittest.TestCase):
    def test_detects_known_transient_positions(self):
        rate = 8_000
        expected = [800, 1_600, 2_400, 3_200]
        samples = array("h", [0] * 4_000)
        for frame in expected:
            samples[frame] = 24_000
            samples[frame + 1] = -12_000
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "known.wav"
            with wave.open(str(path), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(rate)
                output.writeframes(samples.tobytes())
            actual_rate, onsets = MODULE.detect_onsets(path, 0.01, 50.0, 1.5)
        self.assertEqual(rate, actual_rate)
        self.assertEqual(len(expected), len(onsets))
        offsets = [actual - known for actual, known in zip(onsets, expected)]
        self.assertEqual(1, len(set(offsets)))
        self.assertIn(offsets[0], range(13))

    def test_reports_known_interval_and_drift(self):
        metrics = MODULE.analyze([0, 100, 200, 300, 400], 1_000, 0.1, 1)
        self.assertEqual(5, metrics["detected_onsets"])
        self.assertTrue(math.isclose(600.0, metrics["fitted_bpm"], abs_tol=1e-9))
        self.assertTrue(math.isclose(0.0, metrics["fitted_endpoint_error_ms"], abs_tol=1e-9))
        self.assertTrue(math.isclose(0.0, metrics["absolute_error_max_ms"], abs_tol=1e-9))


if __name__ == "__main__":
    unittest.main()
