import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

/** Campaign fixture only: verifies the actual JAR has an authenticated timestamp. */
class TimestampEvidence {
    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 21 || args.length != 2) {
            throw new IllegalArgumentException("JDK21, fixture path and expected upload hash required");
        }
        CodeSigner onlySigner = null;
        try (var jar = new JarFile(Path.of(args[0]).toFile(), true)) {
            for (String name : List.of("base/assets/synthetic.txt", "META-INF/ordinary-payload.txt")) {
                var entry = jar.getJarEntry(name);
                if (entry == null || entry.getSize() > 4096) throw new AssertionError("Fixture entry missing/large");
                try (var input = jar.getInputStream(entry)) {
                    if (input.readAllBytes().length != entry.getSize()) throw new AssertionError("Fixture EOF");
                }
                var signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1 || signers[0].getTimestamp() == null) {
                    throw new AssertionError("Expected a real timestamp on both verified fixture payloads");
                }
                if (onlySigner != null && !onlySigner.equals(signers[0])) throw new AssertionError("Signer drift");
                onlySigner = signers[0];
            }
        }
        var leaf = (X509Certificate) onlySigner.getSignerCertPath().getCertificates().getFirst();
        if (!sha256(leaf).equals(args[1])) throw new AssertionError("Unexpected upload signer");
        var timestamp = onlySigner.getTimestamp();
        var tsa = (X509Certificate) timestamp.getSignerCertPath().getCertificates().getFirst();
        System.out.println("{\"verified_payloads\":2,\"timestamp_present\":true,\"timestamp_epoch_ms\":"
                + timestamp.getTimestamp().getTime() + ",\"tsa_leaf_sha256\":\"" + sha256(tsa)
                + "\",\"upload_leaf_sha256\":\"" + sha256(leaf) + "\"}");
    }

    private static String sha256(X509Certificate certificate) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }
}
