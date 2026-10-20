package br.com.rastro360;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevelopmentPersistenceTest {
    @TempDir Path temporaryDirectory;

    private static String baseUrl(Path path) {
        return "jdbc:h2:file:" + path.toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    }

    @Test
    void missingDatabaseMustNotBeCreatedSilently() {
        Path path = temporaryDirectory.resolve("missing");
        assertThatThrownBy(() -> DriverManager.getConnection(
                baseUrl(path) + ";IFEXISTS=TRUE", "sa", ""))
                .isInstanceOf(SQLException.class);
        assertThat(Files.exists(Path.of(path + ".mv.db"))).isFalse();
    }

    @Test
    void dataPersistsAfterDisconnectAndReconnect() throws Exception {
        Path path = temporaryDirectory.resolve("persistent");
        try (var connection = DriverManager.getConnection(baseUrl(path), "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE persisted_volume (code VARCHAR(64) PRIMARY KEY)");
                statement.executeUpdate("INSERT INTO persisted_volume (code) VALUES ('RT-TEST-PERSISTENCE')");
            }
        }
        try (var connection = DriverManager.getConnection(
                baseUrl(path) + ";IFEXISTS=TRUE", "sa", "")) {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT code FROM persisted_volume")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("RT-TEST-PERSISTENCE");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void onlineBackupProducesNonemptyZipWithoutChangingData() throws Exception {
        Path path = temporaryDirectory.resolve("volume");
        Path backup = temporaryDirectory.resolve("backup.zip");

        try (var connection = DriverManager.getConnection(baseUrl(path), "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE product_backup (code VARCHAR(32) PRIMARY KEY)");
                statement.executeUpdate("INSERT INTO product_backup (code) VALUES ('LEITE-001')");
                String backupPath = backup.toString().replace('\\', '/').replace("'", "''");
                statement.execute("BACKUP TO '" + backupPath + "'");
            }
        }
        assertThat(Files.size(backup)).isGreaterThan(0);
        try (var zip = new ZipFile(backup.toFile())) {
            assertThat(zip.size()).isGreaterThan(0);
        }
        try (var connection = DriverManager.getConnection(
                baseUrl(path) + ";IFEXISTS=TRUE", "sa", "")) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM product_backup")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void devConfigurationUsesExternalGuardedDatabase() throws Exception {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("application-dev.yml")) {
            assertThat(input).isNotNull();
            String config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(config).contains("RASTRO360_DB_PATH");
            assertThat(config).contains("IFEXISTS=TRUE");
            assertThat(config).doesNotContain("jdbc:h2:file:./data/rastro360");
        }
    }
}
