import com.parlor.content.schema.CaseEnvelope;
import com.parlor.games.whodunit.content.WhodunitContentIdentity;
import com.parlor.games.whodunit.content.WhodunitContentIdentityKt;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import kotlinx.serialization.json.Json;

/** Campaign-only JVM corroboration. Calls shipping serializers/canonicalizer unchanged. */
public final class VerifyContentIdentity {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || Runtime.version().feature() != 21) {
            throw new IllegalArgumentException("Requires JDK 21 and one manifest path");
        }
        if (Json.Default.getConfiguration().getIgnoreUnknownKeys()
                || Json.Default.getConfiguration().isLenient()) {
            throw new IllegalStateException("Expected strict default JSON decoding");
        }
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String[] item = line.split("\t", -1);
            if (item.length != 6 || !seen.add(item[0] + "/" + item[1])) {
                throw new IllegalArgumentException("Malformed/duplicate manifest record");
            }
            Path path = Path.of(item[2]);
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)
                    || Files.size(path) > 1024 * 1024) {
                throw new IllegalArgumentException("Not a bounded regular resource");
            }
            byte[] bytes = Files.readAllBytes(path);
            String byteHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!byteHash.equals(item[3])) throw new IllegalStateException("Source bytes changed");
            CaseEnvelope envelope = Json.Default.decodeFromString(
                    CaseEnvelope.Companion.serializer(), new String(bytes, StandardCharsets.UTF_8));
            if (!envelope.getCaseId().equals(item[1])) throw new IllegalStateException("Case ID mismatch");
            WhodunitContentIdentity identity = WhodunitContentIdentityKt.contentIdentity(envelope);
            if (!identity.getVersion().equals(item[4])) throw new IllegalStateException("Version mismatch");
            if (!item[5].equals("-") && !identity.getDigest().equals(item[5])) {
                throw new IllegalStateException("Canonical identity mismatch for " + item[0] + "/" + item[1]);
            }
            System.out.println(item[0] + "\t" + item[1] + "\t" + byteHash + "\t"
                    + identity.getVersion() + "\t" + identity.getDigest());
            count++;
        }
        if (count != 20) throw new IllegalStateException("Expected exactly 13 historical + 7 current resources");
    }
}
