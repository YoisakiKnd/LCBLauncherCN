import unittest
from apk_metadata import sdk_levels


class ApkMetadataTest(unittest.TestCase):
    def test_current_aapt2_uses_full_namespace_and_decimal_integers(self):
        text = """
        A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=26
        A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=36
        """
        self.assertEqual((26, 36), sdk_levels(text))

    def test_legacy_typed_hex_is_also_read(self):
        text = """
        A: android:minSdkVersion(0x0101020c)=(type 0x10)0x1a
        A: android:targetSdkVersion(0x01010270)=(type 0x10)0x24
        """
        self.assertEqual((26, 36), sdk_levels(text))

    def test_actual_values_are_preserved_for_the_verifier(self):
        text = """
        A: android:minSdkVersion(0x0101020c)=23
        A: android:targetSdkVersion(0x01010270)=35
        """
        self.assertEqual((23, 35), sdk_levels(text))

    def test_missing_or_duplicate_attributes_are_rejected(self):
        line = "A: android:minSdkVersion(0x0101020c)=26"
        for text in ("", line, line + "\n" + line):
            with self.assertRaises(ValueError):
                sdk_levels(text)
