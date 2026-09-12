// Audit-only compiled metadata inspection. No test instance or method is executed.
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

class AuditTestMethodScanner {
    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    public static void main(String[] arguments) throws Exception {
        int annotated = 0;
        int nonVoid = 0;
        for (String argument : arguments) {
            Path root = Path.of(argument);
            if (!Files.isDirectory(root)) continue;
            List<Path> files;
            try (var paths = Files.walk(root)) {
                files = paths.filter(p -> p.toString().endsWith(".class")).sorted().toList();
            }
            for (Path file : files) {
                String className = root.relativize(file).toString()
                    .replace(java.io.File.separatorChar, '.').replaceFirst("\\.class$", "");
                if (className.endsWith("module-info")) continue;
                try {
                    Class<?> type = Class.forName(className, false, AuditTestMethodScanner.class.getClassLoader());
                    for (Method method : type.getDeclaredMethods()) {
                        List<String> annotations = Arrays.stream(method.getDeclaredAnnotations())
                            .map(a -> a.annotationType().getName()).sorted().toList();
                        boolean test = annotations.contains("org.junit.jupiter.api.Test")
                            || annotations.contains("org.junit.Test")
                            || annotations.contains("org.junit.jupiter.params.ParameterizedTest")
                            || annotations.contains("kotlin.test.Test");
                        if (!test) continue;
                        annotated++;
                        if (method.getReturnType() != void.class) nonVoid++;
                        System.out.println("AUDIT_TEST_METHOD {\"class\":" + quoted(className)
                            + ",\"method\":" + quoted(method.getName())
                            + ",\"return_type\":" + quoted(method.getGenericReturnType().getTypeName())
                            + ",\"parameter_count\":" + method.getParameterCount()
                            + ",\"modifiers\":" + quoted(Modifier.toString(method.getModifiers()))
                            + ",\"annotations\":" + annotations.stream().map(AuditTestMethodScanner::quoted).toList()
                            + "}");
                    }
                } catch (LinkageError | ReflectiveOperationException exception) {
                    System.out.println("AUDIT_CLASS_INSPECTION_ERROR " + quoted(className)
                        + " " + exception.getClass().getName());
                }
            }
        }
        System.out.println("AUDIT_TEST_METHOD_SUMMARY annotated=" + annotated + " nonVoid=" + nonVoid);
    }
}
