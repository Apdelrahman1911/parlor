package com.parlor.transport.p2p

import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Check compiled signatures: Kotlin expression bodies can silently return an assertion value. */
class JupiterDiscoveryContractTest {
    @Test
    fun annotated_tests_including_disabled_tests_are_discoverable(): Unit {
        val classesRoot = Path.of(javaClass.protectionDomain.codeSource.location.toURI())
        assertTrue(Files.isDirectory(classesRoot), "Expected compiled test classes at $classesRoot")
        val annotatedMethods = Files.walk(classesRoot).use { paths ->
            paths.filter { it.toString().endsWith(".class") }
                .map { path ->
                    val name = classesRoot.relativize(path).toString()
                        .removeSuffix(".class").replace(java.io.File.separatorChar, '.')
                    Class.forName(name, false, javaClass.classLoader)
                }
                .toList()
                .flatMap { it.declaredMethods.toList() }
                .filter { method ->
                    method.declaredAnnotations.any {
                        it.annotationClass.java.name == "org.junit.jupiter.api.Test"
                    }
                }
        }
        assertTrue(annotatedMethods.isNotEmpty(), "No Jupiter annotations were inspected")
        val invalid = annotatedMethods.filter {
            it.returnType != Void.TYPE || Modifier.isPrivate(it.modifiers) || Modifier.isStatic(it.modifiers)
        }
        assertTrue(
            invalid.isEmpty(),
            "Jupiter silently omits invalid @Test methods (including @Ignore):\n" +
                invalid.joinToString("\n") { it.toGenericString() },
        )
    }
}
