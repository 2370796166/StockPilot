package com.stockpilot.shared.migration;

import org.flywaydb.core.Flyway;

/**
 * Deployment-only entry point; credentials are provided as environment variables, never CLI
 * arguments.
 */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) {
        if (args.length != 0)
            throw new IllegalArgumentException("Migration accepts environment variables only");
        String url = required("DB_URL"),
                username = required("MIGRATION_DB_USERNAME"),
                password = required("MIGRATION_DB_PASSWORD");
        if (!url.startsWith("jdbc:mysql://"))
            throw new IllegalArgumentException("MySQL database URL required");
        var result =
                Flyway.configure()
                        .dataSource(url, username, password)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(false)
                        .cleanDisabled(true)
                        .load()
                        .migrate();
        System.out.println("Database migrations completed: " + result.migrationsExecuted);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing environment variable: " + key);
        return value;
    }
}
