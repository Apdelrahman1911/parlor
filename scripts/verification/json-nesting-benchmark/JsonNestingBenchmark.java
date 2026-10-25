import com.parlor.core.serialization.JsonNestingLimitKt;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import kotlinx.serialization.json.Json;

/** Single-process synthetic parser microbenchmark, not gameplay/network/mobile qualification. */
public final class JsonNestingBenchmark {
    private static volatile Object blackhole;

    private record Fixture(String json, byte[] bytes, int depth) {}

    private static Fixture fixture(String name) {
        String value;
        int depth = 0;
        switch (name) {
            case "command" -> value = "{\"type\":\"vote\",\"revision\":42,\"seat\":3,\"choice\":1}";
            case "snapshot1k", "snapshot8k", "snapshot32k" -> {
                int target = switch (name) {
                    case "snapshot1k" -> 1024;
                    case "snapshot8k" -> 8192;
                    default -> 32768;
                };
                String prefix = "{\"revision\":42,\"public\":{\"seats\":[0,1,2,3]},\"synthetic\":\"";
                value = prefix + "x".repeat(target - prefix.length() - 2) + "\"}";
            }
            case "depth64", "depth65", "depth4096" -> {
                depth = Integer.parseInt(name.substring(5));
                value = "[".repeat(depth) + "0" + "]".repeat(depth);
            }
            default -> throw new IllegalArgumentException("Unknown fixture");
        }
        return new Fixture(value, value.getBytes(StandardCharsets.UTF_8), depth);
    }

    private static int operation(Fixture fixture, String mode) {
        try {
            if (!mode.equals("parse")) {
                JsonNestingLimitKt.requireBoundedJsonNesting(fixture.bytes());
            }
            if (!mode.equals("guard")) {
                // Include UTF-8 materialization in both parser paths, as game decoders do.
                blackhole = Json.Default.parseToJsonElement(new String(fixture.bytes(), StandardCharsets.UTF_8));
            }
            return 0;
        } catch (IllegalArgumentException rejected) {
            if (fixture.depth() <= 64 || mode.equals("parse")) throw rejected;
            return 1;
        } catch (StackOverflowError unsafeParser) {
            if (fixture.depth() != 4096 || !mode.equals("parse")) throw unsafeParser;
            return 2;
        }
    }

    private static long rssBytes() throws Exception {
        Path status = Path.of("/proc/self/status");
        if (!Files.isRegularFile(status)) return -1;
        for (String line : Files.readAllLines(status)) {
            if (line.startsWith("VmRSS:")) return Long.parseLong(line.trim().split("\\s+")[1]) * 1024L;
        }
        return -1;
    }

    private static long nativeThreads() throws Exception {
        Path task = Path.of("/proc/self/task");
        if (!Files.isDirectory(task)) return -1;
        try (var entries = Files.list(task)) { return entries.count(); }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        String name = args[0];
        String mode = args[1];
        if (!mode.equals("parse") && !mode.equals("guard") && !mode.equals("guardedParse")) {
            throw new IllegalArgumentException("Unknown mode");
        }
        Fixture fixture = fixture(name);
        boolean overflowProbe = name.equals("depth4096") && mode.equals("parse");
        int warmup = overflowProbe ? 2 : 3000;
        int iterations = overflowProbe ? 10 : 10000;
        var bean = ManagementFactory.getThreadMXBean();
        if (bean.isThreadCpuTimeSupported()) bean.setThreadCpuTimeEnabled(true);
        var allocation = bean instanceof com.sun.management.ThreadMXBean extended ? extended : null;
        if (allocation != null && allocation.isThreadAllocatedMemorySupported()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        for (int index = 0; index < warmup; index++) operation(fixture, mode);
        long[] samples = new long[iterations];
        int[] outcomes = new int[3];
        long threadsBefore = nativeThreads();
        long rssBefore = rssBytes();
        long allocatedBefore = allocation == null || !allocation.isThreadAllocatedMemoryEnabled()
            ? -1 : allocation.getThreadAllocatedBytes(threadId);
        long cpuBefore = bean.isThreadCpuTimeEnabled() ? bean.getCurrentThreadCpuTime() : -1;
        long started = System.nanoTime();
        for (int index = 0; index < iterations; index++) {
            long before = System.nanoTime();
            int outcome = operation(fixture, mode);
            samples[index] = System.nanoTime() - before;
            outcomes[outcome]++;
        }
        long wallNanos = System.nanoTime() - started;
        long cpuNanos = cpuBefore < 0 ? -1 : bean.getCurrentThreadCpuTime() - cpuBefore;
        long allocatedBytes = allocatedBefore < 0 ? -1 : allocation.getThreadAllocatedBytes(threadId) - allocatedBefore;
        long rssAfter = rssBytes();
        long threadsAfter = nativeThreads();
        Arrays.sort(samples);
        System.out.printf(
            "{\"fixture\":\"%s\",\"mode\":\"%s\",\"bytes\":%d,\"warmup\":%d," +
            "\"iterations\":%d,\"p50_ns\":%d,\"p95_ns\":%d,\"wall_ns\":%d," +
            "\"thread_cpu_ns\":%d,\"thread_allocated_bytes\":%d," +
            "\"native_threads_before\":%d,\"native_threads_after\":%d," +
            "\"rss_before_bytes\":%d,\"rss_after_bytes\":%d," +
            "\"accepted\":%d,\"rejected\":%d,\"stack_overflow\":%d}%n",
            name, mode, fixture.bytes().length, warmup, iterations,
            samples[iterations / 2], samples[(iterations * 95 / 100) - 1], wallNanos,
            cpuNanos, allocatedBytes, threadsBefore, threadsAfter, rssBefore, rssAfter,
            outcomes[0], outcomes[1], outcomes[2]
        );
        if (!mode.equals("parse") && fixture.depth() > 64 && outcomes[1] != iterations) {
            throw new IllegalStateException("Guard failed to reject every excessive-depth fixture");
        }
    }
}
