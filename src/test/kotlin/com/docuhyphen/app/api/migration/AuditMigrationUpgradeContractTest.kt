package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

private class KotlinPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<KotlinPostgreSQLContainer>(imageName)

class AuditMigrationUpgradeContractTest
{
    private val migrationDirectory: Path = Path.of("src/main/resources/db/migration")

    @Test
    fun `audit migrations extend the previous schema in version order`()
    {
        val auditTail = Files.list(migrationDirectory).use { paths ->
            paths.map { it.fileName.toString() }
                .filter { it.matches(Regex("V(5[1-4])__.*\\.sql")) }
                .sorted()
                .toList()
        }

        assertEquals(
            listOf(
                "V51__workflow_event_outbox.sql",
                "V52__audit_export_build_lease.sql",
                "V53__remove_legacy_audit_storage.sql",
                "V54__audit_archive_format_version.sql",
            ),
            auditTail,
        )
    }

    @Test
    fun `upgrade adds export concurrency columns before removing obsolete audit storage`()
    {
        val leaseMigration = Files.readString(migrationDirectory.resolve("V52__audit_export_build_lease.sql"))
        val cleanupMigration = Files.readString(migrationDirectory.resolve("V53__remove_legacy_audit_storage.sql"))

        assertTrue(leaseMigration.contains("ADD COLUMN build_worker_id"))
        assertTrue(leaseMigration.contains("ADD COLUMN build_lease_expires_at"))
        assertTrue(leaseMigration.contains("ADD COLUMN version"))
        assertTrue(cleanupMigration.contains("DROP TABLE IF EXISTS auth_audit_event"))
        assertTrue(cleanupMigration.contains("DROP TABLE IF EXISTS audit_log"))
        assertTrue(cleanupMigration.contains("DROP TABLE IF EXISTS access_audit_log"))
        assertTrue(cleanupMigration.contains("DROP COLUMN IF EXISTS legacy_import"))
    }

    @Test
    fun `released schema upgrades through audit cleanup on PostgreSQL`()
    {
        val postgres = KotlinPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_migration_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            val releasedFlyway = flyway(postgres, target = "51")
            releasedFlyway.migrate()

            postgres.createConnection("").use { connection ->
                assertTrue(tableExists(connection, "auth_audit_event"))
                assertTrue(tableExists(connection, "audit_log"))
                assertTrue(tableExists(connection, "access_audit_log"))
                assertTrue(columnExists(connection, "audit_archive_segment", "legacy_import"))
                assertFalse(columnExists(connection, "audit_export", "build_worker_id"))
            }

            val currentFlyway = flyway(postgres)
            currentFlyway.migrate()

            postgres.createConnection("").use { connection ->
                assertFalse(tableExists(connection, "auth_audit_event"))
                assertFalse(tableExists(connection, "audit_log"))
                assertFalse(tableExists(connection, "access_audit_log"))
                assertFalse(columnExists(connection, "audit_archive_segment", "legacy_import"))
                assertTrue(columnExists(connection, "audit_export", "build_worker_id"))
                assertTrue(columnExists(connection, "audit_export", "build_lease_expires_at"))
                assertTrue(columnExists(connection, "audit_export", "version"))
                assertTrue(columnExists(connection, "audit_archive_segment", "format_version"))
            }

            assertEquals("54", currentFlyway.info().current().version.toString())
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: KotlinPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    private fun tableExists(connection: Connection, tableName: String): Boolean =
        connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL").use { statement ->
            statement.setString(1, "public.$tableName")
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean(1)
            }
        }

    private fun columnExists(connection: Connection, tableName: String, columnName: String): Boolean =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tableName)
            statement.setString(2, columnName)
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean(1)
            }
        }
}
