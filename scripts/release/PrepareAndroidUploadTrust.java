import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSigner;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import sun.security.provider.certpath.AlgorithmChecker;
import sun.security.validator.Validator;

/** Validation only: no private keys, signing, downloads, or newly trusted CA chains. */
class PrepareAndroidUploadTrust {
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;

    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 21 || args.length != 3) {
            throw new IllegalArgumentException("Upload validation requires JDK 21 and three arguments");
        }
        Path archive = Path.of(args[0]);
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                || Files.size(archive) > MAX_ARCHIVE_BYTES || !args[1].matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid upload validation input");
        }
        byte[] approved = HexFormat.of().parseHex(args[1]);
        Set<CodeSigner> signers = verifiedSigners(archive, approved);
        X509Certificate approvedSelfSigned = null;
        for (CodeSigner signer : signers) {
            X509Certificate leaf = leaf(signer);
            if (isSelfSigned(leaf)) {
                validateSelfSignedLeaf(leaf, signer);
                approvedSelfSigned = leaf;
            }
        }
        if (approvedSelfSigned != null) {
            KeyStore publicOnly = KeyStore.getInstance("PKCS12");
            publicOnly.load(null, null);
            publicOnly.setCertificateEntry("approved-upload", approvedSelfSigned);
            // CREATE_NEW refuses stale output or symlinks. The caller owns and
            // removes this temporary directory on success, failure and signals.
            try (var output = Files.newOutputStream(Path.of(args[2]),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                publicOnly.store(output, "parlor-public-only".toCharArray());
            }
        }
        // Non-self-signed certificates keep the original strict CA-path policy;
        // do not import an unapproved issuer or turn a pinned leaf into a CA.
    }

    private static Set<CodeSigner> verifiedSigners(Path archive, byte[] approved) throws Exception {
        Set<CodeSigner> signers = new HashSet<>();
        try (JarFile jar = new JarFile(archive.toFile(), true)) {
            // Bound and de-duplicate before JarFile initializes signature parsing.
            Set<String> names = new HashSet<>();
            long declaredBytes = 0;
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!names.add(entry.getName()) || names.size() > MAX_ENTRIES || entry.getSize() < 0) {
                    throw new SecurityException("Invalid or duplicate AAB entry");
                }
                declaredBytes = boundedAdd(declaredBytes, entry.getSize());
            }
            byte[] buffer = new byte[64 * 1024];
            long readBytes = 0;
            entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        readBytes = boundedAdd(readBytes, count);
                    }
                }
                // JarFile verifies digests/signature blocks on EOF, but neither
                // trusts the certificate nor rejects an unsigned entry itself.
                CodeSigner[] entrySigners = entry.getCodeSigners();
                if (entrySigners == null || entrySigners.length == 0) {
                    if (!signatureMetadata(entry.getName())) {
                        throw new SecurityException("Unsigned AAB payload");
                    }
                    continue;
                }
                for (CodeSigner signer : entrySigners) {
                    byte[] actual = MessageDigest.getInstance("SHA-256").digest(leaf(signer).getEncoded());
                    if (!MessageDigest.isEqual(approved, actual)) {
                        throw new SecurityException("Android upload certificate mismatch");
                    }
                    signers.add(signer);
                }
            }
        }
        if (signers.isEmpty()) throw new SecurityException("AAB contains no approved signed payload");
        return signers;
    }

    private static X509Certificate leaf(CodeSigner signer) {
        return (X509Certificate) signer.getSignerCertPath().getCertificates().getFirst();
    }

    private static boolean isSelfSigned(X509Certificate leaf) {
        if (!leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal())) return false;
        try {
            leaf.verify(leaf.getPublicKey());
            return true;
        } catch (GeneralSecurityException notSelfSigned) {
            // Self-issued CA rollover certificates can have the same names but
            // a different issuer key. Do not trust them; retain strict CA-path
            // verification instead. Invalid certificates still fail that gate.
            return false;
        }
    }

    private static long boundedAdd(long current, long addition) {
        if (addition < 0 || addition > MAX_EXPANDED_BYTES - current) {
            throw new SecurityException("AAB expands beyond the reviewed 1 GiB bound");
        }
        return current + addition;
    }

    private static void validateSelfSignedLeaf(X509Certificate leaf, CodeSigner signer) throws Exception {
        if (signer.getTimestamp() != null) {
            // The later jarsigner invocation also sees the upload trust store.
            // It must NOT turn that upload certificate into a trusted TSA. Check
            // timestamps separately against the JDK's original public CA roots,
            // with its actual TSA variant (including usage/validity checks),
            // before using the timestamp date for upload-certificate validation.
            KeyStore publicRoots = KeyStore.getInstance(
                    Path.of(System.getProperty("java.home"), "lib", "security", "cacerts").toFile(),
                    (char[]) null);
            X509Certificate[] chain = signer.getTimestamp().getSignerCertPath().getCertificates()
                    .stream().map(X509Certificate.class::cast).toArray(X509Certificate[]::new);
            Validator.getInstance(Validator.TYPE_PKIX, Validator.VAR_TSA_SERVER, publicRoots).validate(chain);
        }
        Date date = signer.getTimestamp() == null ? new Date() : signer.getTimestamp().getTimestamp();
        TrustAnchor anchor = new TrustAnchor(leaf.getSubjectX500Principal(), leaf.getPublicKey(), null);
        PKIXParameters parameters = new PKIXParameters(Set.of(anchor));
        parameters.setDate(date);
        parameters.setRevocationEnabled(false); // Original jarsigner does not opt into -revCheck.
        // Generic PKIX omits usage SignedJAR restrictions. Use the pinned JDK21
        // checker's code-signing variant, exactly as jarsigner does. Missing API
        // or a different JDK fails closed; no security-property override/fallback.
        parameters.addCertPathChecker(new AlgorithmChecker(anchor, null, date, "code signing"));
        // Include the leaf as a path element, not a trusted-certificate shortcut:
        // importing it alone would skip expiry, certificate algorithms and more.
        CertPathValidator.getInstance("PKIX").validate(
                CertificateFactory.getInstance("X.509").generateCertPath(List.of(leaf)), parameters);
        // The subsequent mandatory strict jarsigner still checks key usage/EKU,
        // all JAR algorithms, and any timestamp's TSA path/signature.
    }

    private static boolean signatureMetadata(String name) {
        // Only JAR-spec signing metadata is exempt, NOT arbitrary META-INF data.
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) return false;
        String file = upper.substring("META-INF/".length());
        if (file.contains("/")) return false;
        if (file.equals("MANIFEST.MF") || file.endsWith(".SF") || file.endsWith(".RSA")
                || file.endsWith(".DSA") || file.endsWith(".EC")) return true;
        if (!file.startsWith("SIG-")) return false;
        int dot = file.lastIndexOf('.');
        return dot < 0 || file.substring(dot + 1).matches("[A-Z0-9]{1,3}");
    }
}
