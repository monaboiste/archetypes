package com.softwarearchetypes.rules.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Guards the separation the refactoring is about: the core must not import a plugin type or a
// domain value object. Cheaper than a full architecture-test dependency, and it fails the moment
// somebody re-couples the core to the discount problem.
public class CoreIndependenceTest {

    private static final List<String> FORBIDDEN = List.of(
            "com.softwarearchetypes.rules.discounting",
            "com.softwarearchetypes.quantity",
            "com.softwarearchetypes.scoring");

    @Test
    public void coreDoesNotDependOnAnyDomainPlugin() throws IOException {
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java/com/softwarearchetypes/rules/core"))) {
            List<String> leaks = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(CoreIndependenceTest::forbiddenImports)
                    .sorted()
                    .toList();

            assertEquals(List.of(), leaks);
        }
    }

    private static Stream<String> forbiddenImports(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> FORBIDDEN.stream().anyMatch(line::contains))
                    .map(line -> source.getFileName() + ": " + line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
