import unittest
from release_version import next_version


class ReleaseVersionTest(unittest.TestCase):
    def test_main_push_increases_previous_release(self):
        self.assertEqual((10012, "13.7.0.12"), next_version(12, 10011))

    def test_rerun_or_manual_higher_version_cannot_downgrade(self):
        self.assertEqual((15001, "13.7.0.5001"), next_version(12, 15000))
        self.assertEqual((10013, "13.7.0.13"), next_version(12, 10012))

    def test_android_version_limit_is_enforced(self):
        with self.assertRaises(ValueError):
            next_version(12, 2100000000)


if __name__ == "__main__":
    unittest.main()
