package com.softwarearchetypes.scoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Guards what L05.1 is about, the same way CoreIndependenceTest guards the rule core: the AST and
// the algebras must not name a business metric or import a scoring plugin. It fails the moment
// somebody puts YEARLY_PURCHASE_AMOUNT back into the general code.
// Note: archunit would be ideal here
public class ScoringCoreIndependenceTest {

    private static final List<Path> CORE = List.of(
            Path.of("src/main/java/com/softwarearchetypes/scoring/ast"),
            Path.of("src/main/java/com/softwarearchetypes/scoring/algebra"),
            Path.of("src/main/java/com/softwarearchetypes/scoring/context"));

    private static final List<String> FORBIDDEN = List.of(
            "com.softwarearchetypes.scoring.customer",
            "PURCHASE_AMOUNT",
            "COMPLAINT");

    @Test
    public void scoringCoreNamesNoBusinessMetric() throws IOException {
        List<String> leaks = CORE.stream()
                .flatMap(ScoringCoreIndependenceTest::sources)
                .flatMap(ScoringCoreIndependenceTest::forbiddenLines)
                .sorted()
                .toList();

        assertEquals(List.of(), leaks);
    }

    private static Stream<Path> sources(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> forbiddenLines(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> FORBIDDEN.stream().anyMatch(line::contains))
                    .map(line -> source.getFileName() + ": " + line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
