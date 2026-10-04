package com.stockpilot.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PackageBoundaryTest {
    private static final Path MAIN = Path.of("src/main/java/com/stockpilot");
    private static final Pattern IMPORT =
            Pattern.compile("^import com\\.stockpilot\\.([^.]+)\\.(.+);$");

    @Test
    void modulesDoNotImportAnotherModulesMapper() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Dependency dependency : dependencies()) {
            if (!dependency.sourceModule().equals(dependency.targetModule())
                    && (dependency.importTail().startsWith("mapper.")
                            || dependency.importTail().contains(".mapper."))) {
                violations.add(
                        dependency.source() + " imports mapper from " + dependency.targetModule());
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void sharedPackageDoesNotDependOnBusinessModules() throws IOException {
        List<String> violations =
                dependencies().stream()
                        .filter(dependency -> dependency.sourceModule().equals("shared"))
                        .filter(dependency -> !dependency.targetModule().equals("shared"))
                        .map(
                                dependency ->
                                        dependency.source()
                                                + " imports "
                                                + dependency.targetModule())
                        .toList();
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void inventoryWorkflowModulesDoNotDependOnSecurityImplementation() throws IOException {
        Set<String> workflowModules = Set.of("inventory", "purchase", "sales", "transfer");
        List<String> violations =
                dependencies().stream()
                        .filter(dependency -> workflowModules.contains(dependency.sourceModule()))
                        .filter(dependency -> dependency.targetModule().equals("security"))
                        .map(
                                dependency ->
                                        dependency.source()
                                                + " imports security implementation "
                                                + dependency.importTail())
                        .toList();
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void topLevelBusinessModulesHaveNoDependencyCycle() throws IOException {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Dependency dependency : dependencies()) {
            graph.computeIfAbsent(dependency.sourceModule(), ignored -> new HashSet<>());
            graph.computeIfAbsent(dependency.targetModule(), ignored -> new HashSet<>());
            if (!dependency.sourceModule().equals(dependency.targetModule())) {
                graph.get(dependency.sourceModule()).add(dependency.targetModule());
            }
        }

        Map<String, Integer> indegrees = new HashMap<>();
        graph.keySet().forEach(module -> indegrees.put(module, 0));
        graph.values()
                .forEach(
                        targets ->
                                targets.forEach(
                                        target -> indegrees.merge(target, 1, Integer::sum)));
        ArrayDeque<String> ready = new ArrayDeque<>();
        indegrees.forEach(
                (module, indegree) -> {
                    if (indegree == 0) ready.add(module);
                });

        int visited = 0;
        while (!ready.isEmpty()) {
            String module = ready.removeFirst();
            visited++;
            for (String target : graph.getOrDefault(module, Set.of())) {
                int remaining = indegrees.merge(target, -1, Integer::sum);
                if (remaining == 0) ready.add(target);
            }
        }
        assertEquals(graph.size(), visited, () -> "Top-level package dependency cycle: " + graph);
    }

    @Test
    void masterDataUsesOneConsistentLayerLayout() throws IOException {
        Set<String> allowedLayers =
                Set.of(
                        "api",
                        "controller",
                        "domain",
                        "infrastructure",
                        "mapper",
                        "request",
                        "service",
                        "vo");
        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN.resolve("masterdata"))) {
            violations =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .map(path -> MAIN.resolve("masterdata").relativize(path))
                            .filter(path -> path.getNameCount() < 2)
                            .map(Path::toString)
                            .toList();
        }
        assertTrue(violations.isEmpty(), () -> "Files outside a layer: " + violations);

        try (Stream<Path> directories = Files.list(MAIN.resolve("masterdata"))) {
            List<String> unexpected =
                    directories
                            .filter(Files::isDirectory)
                            .map(path -> path.getFileName().toString())
                            .filter(name -> !allowedLayers.contains(name))
                            .toList();
            assertTrue(unexpected.isEmpty(), () -> "Unexpected masterdata package: " + unexpected);
        }
    }

    private List<Dependency> dependencies() throws IOException {
        List<Dependency> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Path relative = MAIN.relativize(file);
                if (relative.getNameCount() < 2) continue;
                String sourceModule = relative.getName(0).toString();
                for (String line : Files.readAllLines(file)) {
                    Matcher matcher = IMPORT.matcher(line);
                    if (matcher.matches()) {
                        result.add(
                                new Dependency(
                                        relative.toString(),
                                        sourceModule,
                                        matcher.group(1),
                                        matcher.group(2)));
                    }
                }
            }
        }
        return result;
    }

    private record Dependency(
            String source, String sourceModule, String targetModule, String importTail) {}
}
