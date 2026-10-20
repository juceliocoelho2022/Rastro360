package br.com.rastro360;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Creates an H2-consistent online ZIP backup after every development startup.
 * Failures are logged; no existing backup is ever overwritten.
 */
@Component
@Profile("dev")
public class DevelopmentDatabaseBackup {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentDatabaseBackup.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final JdbcTemplate jdbc;
    private final Path backupDirectory;

    public DevelopmentDatabaseBackup(
            JdbcTemplate jdbc,
            @Value("${RASTRO360_BACKUP_DIR:${user.home}/.rastro360/backups}") String backupDirectory) {
        this.jdbc = jdbc;
        this.backupDirectory = Path.of(backupDirectory).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createStartupBackup() {
        try {
            Files.createDirectories(backupDirectory);
            String name = "rastro360-" + LocalDateTime.now().format(TIMESTAMP)
                    + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
            Path file = backupDirectory.resolve(name);
            String sqlPath = file.toString().replace('\\', '/').replace("'", "''");
            jdbc.execute("BACKUP TO '" + sqlPath + "'");
            LOGGER.info("Rastro360: backup H2 de inicialização criado: {}", file);
        } catch (Exception error) {
            LOGGER.error("Rastro360: FALHA AO CRIAR BACKUP do banco H2. Verifique permissões e espaço disponível.", error);
        }
    }
}
