package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.model.entity.ResourceType
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class ShareRolePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<ShareRolePostgreSQLContainer>(imageName)

class ShareResourceScopedRoleContractTest
{
    private val baselineVersion = "91"
    private val shareBearingTypes = setOf(
        ResourceType.EXCHANGE,
        ResourceType.DOCUMENT,
        ResourceType.PRINCIPAL_GROUP,
        ResourceType.INFORMATION_REQUEST,
    )

    @Test
    fun `the expand migration preserves existing Exchange Share rows`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = baselineVersion).migrate()

            val shareId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                insertShare(connection, shareId, ResourceType.EXCHANGE.name, "VIEWER")
                flyway(postgres).migrate()

                assertEquals("VIEWER", roleName(connection, shareId))
            }
        }
    }

    @Test
    fun `Information Request party Share role keys are accepted after expansion`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "CONTRIBUTOR")
                insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "PREPARER")
                insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "ATTESTOR")
                insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "REVIEWER")
                insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "DECISION_MAKER")
                insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "SUBJECT")
            }
        }
    }

    @Test
    fun `Share resource type check admits only Share bearing resources`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                shareBearingTypes.forEach { resourceType ->
                    insertShare(connection, UUID.randomUUID(), resourceType.name, acceptedRoleFor(resourceType))
                }
                ResourceType.entries
                    .filterNot { it in shareBearingTypes }
                    .forEach { resourceType ->
                        refused(connection, "share_resource_type_check") {
                            insertShare(connection, UUID.randomUUID(), resourceType.name, "VIEWER")
                        }
                    }
            }
        }
    }

    @Test
    fun `Share role name check is resource aware`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                refused(connection, "share_role_name_check") {
                    insertShare(connection, UUID.randomUUID(), ResourceType.EXCHANGE.name, "CONTRIBUTOR")
                }
                refused(connection, "share_role_name_check") {
                    insertShare(connection, UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, "VIEWER")
                }
            }
        }
    }

    private fun acceptedRoleFor(resourceType: ResourceType): String =
        when (resourceType)
        {
            ResourceType.INFORMATION_REQUEST -> "CONTRIBUTOR"
            else -> "VIEWER"
        }

    private fun insertShare(connection: Connection, id: UUID, resourceType: String, roleName: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO share
                (id, resource_type, resource_id, principal_kind, principal_id, role_name,
                 source, status, granted_at)
            VALUES (?, ?, ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, resourceType)
            statement.setObject(3, UUID.randomUUID())
            statement.setObject(4, UUID.randomUUID())
            statement.setString(5, roleName)
            statement.setTimestamp(6, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun roleName(connection: Connection, shareId: UUID): String =
        connection.prepareStatement("SELECT role_name FROM share WHERE id = ?")
            .use { statement ->
                statement.setObject(1, shareId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }

    private fun refused(connection: Connection, expected: String, block: () -> Unit)
    {
        connection.autoCommit = false
        val refusal = try
        {
            assertThrows<SQLException>(block)
        }
        finally
        {
            connection.rollback()
            connection.autoCommit = true
        }

        assertTrue(
            refusal.message.orEmpty().contains(expected),
            "Expected $expected to refuse this statement: ${refusal.message}",
        )
    }

    private fun withPostgres(block: (ShareRolePostgreSQLContainer) -> Unit)
    {
        val postgres = ShareRolePostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_share_role_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            block(postgres)
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: ShareRolePostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }
}
