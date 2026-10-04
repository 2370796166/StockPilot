package com.stockpilot.acceptance;

import java.net.URI;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * Test-only configuration; never inherit consumers or bootstrap accounts from the developer's .env.
 */
public final class IntegrationTestInfrastructure {
    private IntegrationTestInfrastructure() {}

    public static String databaseName(String prefix) {
        if (!prefix.matches("stockpilot_[a-z0-9_]+_it"))
            throw new IllegalArgumentException("Not a test schema");
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String databaseUrl(String adminUrl, String database) {
        if (!database.matches("stockpilot_[a-z0-9_]+_it_[a-f0-9]{12}"))
            throw new IllegalArgumentException("Not a dedicated test schema");
        if (!adminUrl.startsWith("jdbc:mysql://"))
            throw new IllegalArgumentException("MySQL URL required");
        URI uri = URI.create(adminUrl.substring(5));
        if (uri.getUserInfo() != null || uri.getHost() == null)
            throw new IllegalArgumentException(
                    "Use separate credentials and an explicit MySQL host");
        return "jdbc:mysql://"
                + uri.getRawAuthority()
                + "/"
                + database
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    public static void isolate(ConfigurableApplicationContext context, String database) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "stockpilot.cache.enabled=false",
                "stockpilot.cache.key-prefix=" + database,
                "stockpilot.messaging.enabled=false",
                "stockpilot.security.bootstrap-admin-username=",
                "stockpilot.security.bootstrap-admin-password=");
    }

    public static String[] rabbitProperties(String database) {
        return new String[] {
            "spring.rabbitmq.host="
                    + System.getenv().getOrDefault("STOCKPILOT_IT_RABBIT_HOST", "localhost"),
            "spring.rabbitmq.port="
                    + System.getenv().getOrDefault("STOCKPILOT_IT_RABBIT_PORT", "5673"),
            "spring.rabbitmq.username="
                    + System.getenv().getOrDefault("STOCKPILOT_IT_RABBIT_USER", "stockpilot"),
            "spring.rabbitmq.password="
                    + System.getenv()
                            .getOrDefault("STOCKPILOT_IT_RABBIT_PASSWORD", "stockpilot_dev"),
            "spring.rabbitmq.virtual-host="
                    + System.getenv().getOrDefault("STOCKPILOT_IT_RABBIT_VHOST", "/stockpilot-it"),
            "stockpilot.messaging.exchange=" + database + ".business",
            "stockpilot.messaging.completion-queue=" + database + ".completion",
            "stockpilot.messaging.dead-letter-exchange=" + database + ".dlx",
            "stockpilot.messaging.dead-letter-queue=" + database + ".dlq",
            "stockpilot.messaging.dead-letter-routing-key=" + database + ".dead"
        };
    }
}
