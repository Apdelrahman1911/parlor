/** Campaign-only public runtime metadata; never accesses application data. */
class ArchitectureProbe {
    public static void main(String[] args) {
        if (Runtime.version().feature() != 21
                || !java.util.Set.of("x86_64", "amd64").contains(System.getProperty("os.arch"))) {
            throw new AssertionError("Expected actual x64 JDK21 runtime under Rosetta");
        }
        for (String key : java.util.List.of("java.home", "java.version", "java.vendor", "os.arch", "os.name")) {
            System.out.println(key + "=" + System.getProperty(key));
        }
    }
}
