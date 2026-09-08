"""Real CLI20 preference-library probe on synthetic owned folders, not an AVD test."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import owned_sdk_image as control
from test_owned_sdk_image import FakeOwned


CLI = Path("/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest")
PINS = {
    "lib/avdmanager-classpath.jar": "ffc2689864e3d6186b62a35dbed1f4bb128253d789dcbef4d8f647a31a03c996",
    "lib/common/tools.common.jar": "367a6391abb542772628bf67c1ec20ba5bd5282e3004f4db397d932ae8d6a672",
}
JAVA_SOURCE = r'''
import com.android.prefs.AndroidLocationsException;
import com.android.prefs.AndroidLocationsSingleton;
import java.nio.file.Path;

class OwnedPreferences {
    public static void main(String[] args) throws Exception {
        boolean conflict = args[1].equals("conflict");
        try {
            Path observed = AndroidLocationsSingleton.INSTANCE.getPrefsLocation();
            if (conflict) throw new AssertionError("Expected original preference conflict");
            if (!observed.equals(Path.of(args[0]))) throw new AssertionError("Unexpected preference path");
            System.out.println("OWNED_PREFERENCES_OK");
        } catch (AndroidLocationsException error) {
            if (!conflict || !error.getMessage().startsWith(
                    "Several environment variables and/or system properties contain different paths")) {
                throw error;
            }
            System.out.println("EXPECTED_CONFLICT_OBSERVED");
        }
    }
}
'''


class ActualCli20PreferencesTests(unittest.TestCase):
    def test_old_conflict_and_corrected_owned_paths_in_exact_installed_library(self):
        for relative, expected in PINS.items():
            self.assertEqual(expected, hashlib.sha256((CLI / relative).read_bytes()).hexdigest())
        java = Path(os.environ["JAVA_HOME"]) / "bin/java"
        self.assertTrue(java.is_file())
        for mode in ("conflict", "corrected", "inherited-deprecated"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(prefix="parlor-cli20-prefs-") as name:
                owned = FakeOwned(Path(name).resolve() / "owned")
                for directory in ("home", "tmp", "sdk", "android-user", "other-owned-preferences"):
                    (owned.path / directory).mkdir(mode=0o700)
                source = owned.path / "OwnedPreferences.java"
                source.write_text(JAVA_SOURCE)
                env = {
                    "JAVA_HOME": str(java.parent.parent), "PATH": "/usr/bin:/bin",
                    "HOME": str(owned.path / "home"), "TMPDIR": str(owned.path / "tmp"),
                    "ANDROID_USER_HOME": str(owned.path / "android-user"),
                    "ANDROID_SDK_HOME": str(owned.path / "home"),
                }
                if mode == "inherited-deprecated":
                    env["ANDROID_PREFS_ROOT"] = str(owned.path / "other-owned-preferences")
                argv, effective = control.cli20_invocation(
                    java, CLI / "bin/avdmanager", owned, owned.path / "sdk", env, [],
                )
                self.assertTrue(set(control.DEPRECATED_PREFS_ENV_OPTIONS).isdisjoint(effective))
                if mode == "conflict":
                    # Reconstruct the original runner's conflicting environment
                    # after command construction; do not modify installed SDKs.
                    effective["ANDROID_SDK_HOME"] = env["ANDROID_SDK_HOME"]
                self.assertEqual("com.android.sdklib.tool.AvdManagerCli", argv[-1])
                result = subprocess.run(
                    argv[:-1] + [str(source), env["ANDROID_USER_HOME"], mode],
                    cwd=owned.path, env=effective, text=True, capture_output=True, timeout=30,
                )
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                expected = "EXPECTED_CONFLICT_OBSERVED" if mode == "conflict" else "OWNED_PREFERENCES_OK"
                self.assertEqual(expected, result.stdout.strip())
                self.assertEqual("", result.stderr)
                self.assertFalse((owned.path / "home/.android").exists())
                self.assertEqual([], list((owned.path / "other-owned-preferences").iterdir()))
