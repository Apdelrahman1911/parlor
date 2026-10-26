import java.lang.reflect.InvocationTargetException;
import java.lang.management.ManagementFactory;
import java.util.Set;

/** External experimental runner; never modifies/discovers CI @Ignore annotations. */
public final class RealAdapterProbe {
    private static final Set<String> ALLOWED = Set.of(
        "host_advertises_a_room_code_that_join_resolves_to",
        "peer_can_join_a_hosted_room_and_membership_appears_on_host",
        "peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back",
        "host_broadcast_reaches_every_peer"
    );

    public static void main(String[] args) throws Throwable {
        if (args.length != 1 || !ALLOWED.contains(args[0])) throw new IllegalArgumentException("Exactly one existing fixture method required");
        Class<?> fixtureType = Class.forName("com.parlor.transport.p2p.P2pKitRoomTransportLoopbackTest");
        Object fixture = fixtureType.getConstructor().newInstance();
        long start = System.nanoTime();
        Throwable failure = null;
        System.out.println("EXPERIMENT_START method=" + args[0] + " pid=" + ProcessHandle.current().pid());
        try {
            fixtureType.getMethod(args[0]).invoke(fixture);
            System.out.println("FIXTURE_PASS");
        } catch (InvocationTargetException error) {
            failure = error.getCause();
            System.out.println("FIXTURE_FAIL type=" + failure.getClass().getName());
        } finally {
            try {
                fixtureType.getMethod("teardown").invoke(fixture);
                System.out.println("TEARDOWN_PASS");
            } catch (InvocationTargetException error) {
                if (failure == null) failure = error.getCause();
                else failure.addSuppressed(error.getCause());
                System.out.println("TEARDOWN_FAIL type=" + error.getCause().getClass().getName());
            }
            System.out.println("EXPERIMENT_METRICS elapsed_ms=" + ((System.nanoTime()-start)/1_000_000)
                + " live_threads=" + ManagementFactory.getThreadMXBean().getThreadCount()
                + " peak_threads=" + ManagementFactory.getThreadMXBean().getPeakThreadCount()
                + " process_cpu_ns=" + ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime()
                + " used_heap_bytes=" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        }
        if (failure != null) {
            // Never echo assertion values, credentials, or arbitrary exception text.
            throw new IllegalStateException("Real adapter fixture failed; type=" + failure.getClass().getName());
        }
        System.out.println("EXPERIMENT_PASS awaiting_natural_jvm_exit=true");
    }
}
