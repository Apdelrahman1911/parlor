from __future__ import annotations

import hashlib
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
VALIDATOR = ROOT / "scripts/release/validate_android_artifact.sh"


def run_owned(command, *, environment, timeout=60):
    """Keep the Bash fragment and Java child in one disposable process group."""
    process = subprocess.Popen(command, env=environment, text=True, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, start_new_session=True)
    try:
        stdout, stderr = process.communicate(timeout=timeout)
    except BaseException:
        # communicate(timeout) does not kill children. Finalize the entire group
        # before TemporaryDirectory removes signing fixtures on failure/cancel.
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            pass
        finally:
            # The shell may exit on TERM while a redirected descendant ignores
            # it. Reaping the shell alone does not mean its owned group exited.
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.communicate()
        raise
    return subprocess.CompletedProcess(command, process.returncode, stdout, stderr)


class OwnedSignatureProcessTest(unittest.TestCase):
    def test_timeout_stops_term_resistant_redirected_descendant(self) -> None:
        with tempfile.TemporaryDirectory(prefix="parlor-signature-process-") as directory:
            marker = Path(directory) / "owned-child.txt"
            child = (
                "import os, signal, sys, time\n"
                "from pathlib import Path\n"
                "signal.signal(signal.SIGTERM, signal.SIG_IGN)\n"
                "Path(sys.argv[1]).write_text(f'{os.getpid()} {os.getpgrp()}')\n"
                "while True: time.sleep(1)\n"
            )
            parent = (
                "import subprocess, sys, time\n"
                "subprocess.Popen([sys.executable, '-B', '-c', sys.argv[1], sys.argv[2]], "
                "stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)\n"
                "while True: time.sleep(1)\n"
            )
            try:
                with self.assertRaises(subprocess.TimeoutExpired):
                    run_owned([sys.executable, "-B", "-c", parent, child, str(marker)],
                              environment=os.environ.copy(), timeout=5)
                self.assertTrue(marker.is_file(), "The TERM-resistant child must actually start")
                pid, group = map(int, marker.read_text().split())
                for _ in range(50):
                    status = subprocess.run(["ps", "-o", "stat=", "-p", str(pid)],
                                            text=True, capture_output=True, check=False).stdout.strip()
                    if not status or status.startswith("Z"):
                        break
                    time.sleep(0.1)
                else:
                    self.fail("The redirected child survived the owned process-group finalizer")
            finally:
                # Also clean the witness if run against the broken finalizer.
                # Only the child attested by this private fixture may be killed.
                if marker.is_file():
                    pid, group = map(int, marker.read_text().split())
                    try:
                        if os.getpgid(pid) == group:
                            os.kill(pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass


class AndroidUploadSignatureTest(unittest.TestCase):
    """Real JDK21 signature verification, synthetic disposable keys only.

    Runs the exact signing-validation fragment of the artifact script. Bundle
    structure/dex/Store validation are separate gates, not claimed by this test.
    """

    @classmethod
    def setUpClass(cls) -> None:
        temporary = tempfile.TemporaryDirectory(prefix="parlor-upload-cert-fixture-")
        cls.addClassCleanup(temporary.cleanup)
        cls.root = Path(temporary.name)
        cls.environment = {**os.environ, "JAVA_TOOL_OPTIONS": f"-Duser.home={cls.root}"}
        cls.password = "disposable-test-only"
        cls.key, cls.fingerprint = cls.create_key("approved")
        cls.original = cls.root / "signed.aab"
        cls.write_bundle(cls.original)
        cls.sign(cls.original, cls.key)

    @classmethod
    def tool(cls, command, environment=None):
        completed = run_owned(command, environment=environment or cls.environment)
        if completed.returncode:
            raise AssertionError(f"Synthetic fixture command failed: {command[0]}\n{completed.stdout}{completed.stderr}")
        return completed

    @classmethod
    def create_key(cls, name, *, size=2048, algorithm="SHA256withRSA", extra=()):
        key = cls.root / f"{name}.p12"
        cls.tool([
            "keytool", "-genkeypair", "-alias", "fixture", "-keystore", str(key),
            "-storetype", "PKCS12", "-storepass", cls.password, "-keypass", cls.password,
            "-dname", f"CN=Disposable Parlor Test {name}", "-keyalg", "RSA", "-keysize", str(size),
            "-sigalg", algorithm, "-validity", "3650", *extra,
        ])
        public = cls.root / f"{name}.der"
        cls.tool(["keytool", "-exportcert", "-keystore", str(key), "-storepass", cls.password,
                  "-alias", "fixture", "-file", str(public)])
        return key, hashlib.sha256(public.read_bytes()).hexdigest()

    @classmethod
    def write_bundle(cls, path):
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("base/assets/synthetic.txt", "synthetic signed payload")
            archive.writestr("META-INF/ordinary-payload.txt", "also requires an intact signature")

    @classmethod
    def sign(cls, bundle, key, *, algorithm="SHA256withRSA", digest="SHA-256", sigfile="UPLOAD"):
        cls.tool([
            "jarsigner", "-keystore", str(key), "-storepass", cls.password,
            "-keypass", cls.password, "-sigalg", algorithm, "-digestalg", digest,
            "-sigfile", sigfile, str(bundle), "fixture",
        ])

    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory(prefix="validation-", dir=self.root)
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.bundle = self.directory / "synthetic bundle.aab"
        shutil.copyfile(self.original, self.bundle)
        self.output = self.directory / "public-only"
        self.output.mkdir()
        self.expected = self.fingerprint

    def verify(self, *, environment=None):
        script = VALIDATOR.read_text()
        begin = 'unzip -tqq "$aab"'
        end = 'java -jar "$bundletool" dump manifest'
        self.assertEqual(script.count(begin), 1)
        self.assertEqual(script.count(end), 1)
        fragment = script[script.index(begin):script.index(end)]
        phase = (
            'set -euo pipefail\naab=$1\ntemporary_dir=$2\nexpected_certificate=$3\nrepo_root=$4\n'
            + fragment + '\nprintf "%s\\n" "$actual_certificate"\n'
        )
        result = run_owned(
            ["bash", "-c", phase, "signature-fragment", str(self.bundle), str(self.output), self.expected, str(ROOT)],
            environment=environment or self.environment,
        )
        if result.returncode:
            # These contain only synthetic public-certificate fixtures; preserve
            # diagnostics in an assertion before the owned temporary files go.
            for name in ("upload-trust.txt", "jarsigner.txt"):
                log = self.output / name
                if log.exists():
                    result.stderr += f"\n{name}:\n" + log.read_text()
        return result

    def use_certificate(self, name, **options):
        key, self.expected = self.create_key(name, **options)
        self.write_bundle(self.bundle)
        self.sign(self.bundle, key)
        # Verification never needs the signing key; remove this owned fixture.
        key.unlink()

    def test_registered_self_signed_upload_certificate_passes_strict_verification(self) -> None:
        result = self.verify()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout.strip(), self.expected)
        store = self.output / "upload-trust.p12"
        listing = self.tool(["keytool", "-list", "-v", "-keystore", str(store),
                             "-storepass", "parlor-public-only"])
        self.assertIn("trustedCertEntry", listing.stdout)
        self.assertNotIn("PrivateKeyEntry", listing.stdout)

    def test_wrong_approved_fingerprint_is_rejected(self) -> None:
        self.expected = "f" * 64
        self.assertNotEqual(self.verify().returncode, 0)

    def test_tampered_signed_payload_is_rejected(self) -> None:
        with zipfile.ZipFile(self.bundle) as original:
            entries = [(info, original.read(info)) for info in original.infolist()]
        with zipfile.ZipFile(self.bundle, "w") as changed:
            for info, data in entries:
                changed.writestr(info, b"tampered" if info.filename.endswith("synthetic.txt") else data)
        self.assertNotEqual(self.verify().returncode, 0)

    def test_unsigned_extra_payload_including_meta_inf_is_rejected(self) -> None:
        for path in ("base/assets/unsigned.txt", "META-INF/unsigned.txt"):
            with self.subTest(path=path):
                shutil.copyfile(self.original, self.bundle)
                with zipfile.ZipFile(self.bundle, "a") as archive:
                    archive.writestr(path, "unsigned payload")
                result = self.verify()
                self.assertNotEqual(result.returncode, 0)
                # A fresh invocation must not rely on another test's trust file.
                for generated in self.output.iterdir():
                    generated.unlink()

    def test_entirely_unsigned_bundle_is_rejected(self) -> None:
        self.write_bundle(self.bundle)
        self.assertNotEqual(self.verify().returncode, 0)

    def test_additional_unapproved_signer_is_rejected(self) -> None:
        other, _ = self.create_key("additional")
        self.sign(self.bundle, other, sigfile="OTHER")
        other.unlink()
        self.assertNotEqual(self.verify().returncode, 0)

    def test_corrupt_signature_block_is_rejected(self) -> None:
        with zipfile.ZipFile(self.bundle) as original:
            entries = [(info, original.read(info)) for info in original.infolist()]
        with zipfile.ZipFile(self.bundle, "w") as changed:
            for info, data in entries:
                changed.writestr(info, b"not a signature" if info.filename.endswith(".RSA") else data)
        self.assertNotEqual(self.verify().returncode, 0)

    def test_untrusted_ca_path_does_not_become_a_new_trust_anchor(self) -> None:
        authority, _ = self.create_key("untrusted-authority", extra=("-ext", "BC=ca:true", "-ext", "KU=keyCertSign"))
        leaf, _ = self.create_key("ca-leaf")
        request = self.directory / "request.csr"
        certificate = self.directory / "issued.der"
        self.tool(["keytool", "-certreq", "-alias", "fixture", "-keystore", str(leaf),
                   "-storepass", self.password, "-file", str(request)])
        self.tool(["keytool", "-gencert", "-alias", "fixture", "-keystore", str(authority),
                   "-storepass", self.password, "-infile", str(request), "-outfile", str(certificate),
                   "-ext", "KU=digitalSignature", "-ext", "EKU=codeSigning"])
        self.tool(["keytool", "-importcert", "-noprompt", "-alias", "authority", "-keystore", str(leaf),
                   "-storepass", self.password, "-file", str(self.root / "untrusted-authority.der")])
        self.tool(["keytool", "-importcert", "-alias", "fixture", "-keystore", str(leaf),
                   "-storepass", self.password, "-file", str(certificate)])
        self.expected = hashlib.sha256(certificate.read_bytes()).hexdigest()
        self.write_bundle(self.bundle)
        self.sign(self.bundle, leaf)
        authority.unlink()
        leaf.unlink()
        result = self.verify()
        self.assertEqual(result.returncode, 4, result.stdout + result.stderr)
        self.assertFalse((self.output / "upload-trust.p12").exists())
        self.assertTrue((self.output / "jarsigner.txt").is_file(), "Strict CA verification must actually execute")

    def test_stale_trust_store_is_not_overwritten_or_used_after_preparation_failure(self) -> None:
        stale = self.output / "upload-trust.p12"
        stale.write_bytes(b"stale synthetic public output")
        self.assertNotEqual(self.verify().returncode, 0)
        self.assertEqual(stale.read_bytes(), b"stale synthetic public output")
        self.assertFalse((self.output / "jarsigner.txt").exists())

    def test_upload_trust_cannot_be_reused_as_timestamp_authority_trust(self) -> None:
        # Unit test of the real post-JarFile certificate-policy boundary. A
        # synthetic CodeSigner supplies the same upload cert as TSA. This does
        # not claim an RFC3161 service or full timestamped-JAR integration test.
        harness = self.directory / "UploadTimestampPolicyTest.java"
        harness.write_text('''
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.Timestamp;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

class UploadTimestampPolicyTest {
    public static void main(String[] args) throws Exception {
        var factory = CertificateFactory.getInstance("X.509");
        X509Certificate leaf;
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            leaf = (X509Certificate) factory.generateCertificate(input);
        }
        var path = factory.generateCertPath(List.of(leaf));
        var signer = new CodeSigner(path, new Timestamp(new Date(), path));
        var validation = PrepareAndroidUploadTrust.class.getDeclaredMethod(
                "validateSelfSignedLeaf", X509Certificate.class, CodeSigner.class);
        validation.setAccessible(true);
        try {
            validation.invoke(null, leaf, signer);
        } catch (InvocationTargetException rejected) {
            if (!(rejected.getCause() instanceof CertificateException)) throw rejected;
            System.out.println("UNTRUSTED_TSA_REJECTED");
            return;
        }
        throw new AssertionError("Upload trust was incorrectly reused as TSA trust");
    }
}
''')
        exports = ["--add-exports=java.base/sun.security.provider.certpath=ALL-UNNAMED",
                   "--add-exports=java.base/sun.security.validator=ALL-UNNAMED"]
        self.tool(["javac", *exports, "-d", str(self.directory),
                   str(ROOT / "scripts/release/PrepareAndroidUploadTrust.java"), str(harness)])
        result = self.tool(["java", *exports, "-cp", str(self.directory),
                            "UploadTimestampPolicyTest", str(self.root / "approved.der")])
        self.assertEqual(result.stdout.strip(), "UNTRUSTED_TSA_REJECTED")

    def test_expired_self_signed_certificate_is_rejected(self) -> None:
        self.use_certificate("expired", extra=("-startdate", "-3d", "-validity", "1"))
        self.assertNotEqual(self.verify().returncode, 0)

    def test_not_yet_valid_self_signed_certificate_is_rejected(self) -> None:
        self.use_certificate("future", extra=("-startdate", "+3d"))
        self.assertNotEqual(self.verify().returncode, 0)

    def test_certificate_usage_must_allow_code_signing(self) -> None:
        self.use_certificate("wrong-ku", extra=("-ext", "KU=keyEncipherment"))
        self.assertNotEqual(self.verify().returncode, 0)

    def test_extended_usage_must_allow_code_signing(self) -> None:
        self.use_certificate("wrong-eku", extra=("-ext", "EKU=serverAuth"))
        self.assertNotEqual(self.verify().returncode, 0)

    def test_unknown_critical_certificate_extension_is_rejected(self) -> None:
        self.use_certificate("critical", extra=("-ext", "1.2.3.4:critical=0101FF"))
        self.assertNotEqual(self.verify().returncode, 0)

    def test_disabled_rsa_key_size_is_rejected(self) -> None:
        self.use_certificate("weak-key", size=512)
        self.assertNotEqual(self.verify().returncode, 0)

    def test_disabled_certificate_algorithm_is_rejected_with_modern_jar_signature(self) -> None:
        self.use_certificate("sha1-cert", algorithm="SHA1withRSA")
        self.assertNotEqual(self.verify().returncode, 0)

    def test_disabled_jar_algorithm_is_rejected_with_modern_certificate(self) -> None:
        self.write_bundle(self.bundle)
        self.sign(self.bundle, self.key, algorithm="SHA1withRSA", digest="SHA1")
        self.assertNotEqual(self.verify().returncode, 0)

    def test_signedjar_specific_certificate_policy_cannot_be_bypassed_by_trusting_leaf(self) -> None:
        # Append a stricter rule to the actual JDK policy, never replace it with
        # a weaker test policy. It must apply specifically to SignedJAR usage.
        java = Path(shutil.which("java")).resolve()
        properties = (java.parent.parent / "conf/security/java.security").read_text()
        match = re.search(r"^jdk.certpath.disabledAlgorithms=(.*(?:\\\n.*)*)", properties, re.MULTILINE)
        self.assertIsNotNone(match)
        policy = match.group(1).replace("\\\n", "")
        stricter = self.directory / "stricter.security"
        stricter.write_text("jdk.certpath.disabledAlgorithms=" + policy + ", SHA256 usage SignedJAR\n")
        environment = {**self.environment, "JAVA_TOOL_OPTIONS": self.environment["JAVA_TOOL_OPTIONS"] +
                       f" -Djava.security.properties={stricter}"}
        self.assertNotEqual(self.verify(environment=environment).returncode, 0)

    def test_failed_java_preparation_cannot_be_masked_by_later_verification(self) -> None:
        bin_dir = self.directory / "bin"
        bin_dir.mkdir()
        java = bin_dir / "java"
        java.write_text("#!/bin/sh\nexit 77\n")
        java.chmod(0o755)
        environment = {**self.environment, "PATH": str(bin_dir) + os.pathsep + os.environ["PATH"]}
        self.assertEqual(self.verify(environment=environment).returncode, 77)
