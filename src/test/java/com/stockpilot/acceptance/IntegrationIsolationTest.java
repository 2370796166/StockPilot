package com.stockpilot.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IntegrationIsolationTest {
    @Test
    void databaseUrlPreservesTheIsolatedServerAndRejectsDevelopmentSchema() {
        String name = IntegrationTestInfrastructure.databaseName("stockpilot_core_e2e_it");
        assertEquals(
                "jdbc:mysql://localhost:13307/" + name + "?useSSL=false",
                IntegrationTestInfrastructure.databaseUrl(
                        "jdbc:mysql://localhost:13307/?useSSL=false", name));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        IntegrationTestInfrastructure.databaseUrl(
                                "jdbc:mysql://localhost:3307/", "stockpilot"));
        assertNotEquals(name, IntegrationTestInfrastructure.databaseName("stockpilot_core_e2e_it"));
    }

    @Test
    void everyIntegrationTestMustIsolateInheritedDevelopmentConfiguration() throws Exception {
        try (var paths = Files.walk(Path.of("src/test/java/com/stockpilot"))) {
            for (var path : paths.filter(p -> p.toString().endsWith("IT.java")).toList()) {
                String source = Files.readString(path);
                assertTrue(
                        source.contains("IntegrationTestInfrastructure.isolate(context, DATABASE)"),
                        path + " must override development cache, broker and bootstrap settings");
                assertFalse(
                        source.contains("spring.datasource.url=jdbc:mysql://localhost:3307/"),
                        path + " must connect to the same server used to create the test schema");
            }
        }
    }

    @Test
    void rabbitIntegrationTestMustUseDedicatedTopology() throws Exception {
        String source =
                Files.readString(
                        Path.of("src/test/java/com/stockpilot/messaging/MessagingRabbitIT.java"));
        assertTrue(
                source.contains("IntegrationTestInfrastructure.rabbitProperties(DATABASE)"),
                "purge and DLX failure injection must never target development topology");
    }
}
