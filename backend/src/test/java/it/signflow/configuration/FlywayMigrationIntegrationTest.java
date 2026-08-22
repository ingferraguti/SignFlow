package it.signflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywayMigrationIntegrationTest {
    private static final MigrationVersion PREVIOUS_RELEASE = MigrationVersion.fromVersion("22");
    private static final MigrationVersion MVP_RELEASE = MigrationVersion.fromVersion("23");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesAnEmptyDatabaseToTheMvpSchema() throws Exception {
        Flyway flyway = flyway("mvp_clean", MigrationVersion.LATEST);

        var result = flyway.migrate();

        assertThat(result.targetSchemaVersion).isEqualTo(MVP_RELEASE.toString());
        assertThat(result.migrationsExecuted).isEqualTo(23);
        try (Connection connection = connection("mvp_clean")) {
            assertThat(count(connection, "select count(*) from flyway_schema_history where success and version is not null"))
                    .isEqualTo(23);
            assertThat(count(connection, "select count(*) from external_delivery_operations"))
                    .isZero();
            assertThat(count(connection, "select count(*) from reports"))
                    .isEqualTo(12);
        }
    }

    @Test
    void upgradesVersion22WithoutLosingExistingData() throws Exception {
        Flyway previous = flyway("mvp_upgrade", PREVIOUS_RELEASE);
        var previousResult = previous.migrate();
        assertThat(previousResult.targetSchemaVersion).isEqualTo(PREVIOUS_RELEASE.toString());

        try (Connection connection = connection("mvp_upgrade")) {
            connection.createStatement().executeUpdate("""
                    insert into application_metadata(property_key, property_value)
                    values ('mvp.upgrade.marker', 'preserved')
                    """);
        }

        Flyway current = flyway("mvp_upgrade", MigrationVersion.LATEST);
        var upgrade = current.migrate();

        assertThat(upgrade.targetSchemaVersion).isEqualTo(MVP_RELEASE.toString());
        assertThat(upgrade.migrationsExecuted).isEqualTo(1);
        try (Connection connection = connection("mvp_upgrade")) {
            assertThat(text(connection, """
                    select property_value from application_metadata
                    where property_key='mvp.upgrade.marker'
                    """)).isEqualTo("preserved");
            assertThat(count(connection, "select count(*) from flyway_schema_history where success and version is not null"))
                    .isEqualTo(23);
            assertThat(count(connection, "select count(*) from information_schema.tables "
                    + "where table_schema='mvp_upgrade' and table_name='external_delivery_receipts'"))
                    .isEqualTo(1);
        }
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .validateOnMigrate(true)
                .load();
    }

    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String text(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
