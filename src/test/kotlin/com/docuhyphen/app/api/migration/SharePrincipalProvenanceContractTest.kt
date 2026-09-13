package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class SharePrincipalProvenancePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<SharePrincipalProvenancePostgreSQLContainer>(imageName)

class SharePrincipalProvenanceContractTest
{
    private val releasedVersion = "92"
    private val principalKinds = listOf(
        "USER", "PARTICIPANT", "PRINCIPAL_GROUP", "ORGANIZATION", "APPLICATION",
        "SERVICE_ACCOUNT", "PUBLIC_LINK",
    )

    @Test
    fun `legacy Share grantor and revoker are carried into canonical principal columns`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val shareId = UUID.randomUUID()
            val grantorId = UUID.randomUUID()
            val revokerId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                insertAppUser(connection, grantorId, "share-grantor@process.example")
                insertAppUser(connection, revokerId, "share-revoker@process.example")
                insertShare(
                    connection = connection,
                    shareId = shareId,
                    grantedByAppUserId = grantorId,
                    revokedByAppUserId = revokerId,
                )
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("USER", stringColumn(connection, "granted_by_principal_kind", shareId))
                assertEquals(grantorId, uuidColumn(connection, "granted_by_principal_id", shareId))
                assertEquals(grantorId, uuidColumn(connection, "granted_by_app_user_id", shareId))
                assertEquals("USER", stringColumn(connection, "revoked_by_principal_kind", shareId))
                assertEquals(revokerId, uuidColumn(connection, "revoked_by_principal_id", shareId))
                assertEquals(revokerId, uuidColumn(connection, "revoked_by_app_user_id", shareId))
            }
        }
    }

    @Test
    fun `unrecorded Share provenance stays unrecorded across expansion`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val shareId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                insertShare(connection = connection, shareId = shareId)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertNull(stringColumn(connection, "granted_by_principal_kind", shareId))
                assertNull(uuidColumn(connection, "granted_by_principal_id", shareId))
                assertNull(stringColumn(connection, "revoked_by_principal_kind", shareId))
                assertNull(uuidColumn(connection, "revoked_by_principal_id", shareId))
            }
        }
    }

    @Test
    fun `a clean Share table accepts every canonical principal kind for provenance`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val userId = UUID.randomUUID()
                insertAppUser(connection, userId, "share-canonical-user@process.example")

                principalKinds.forEach { kind ->
                    val principalId = if (kind == "USER") userId else UUID.randomUUID()
                    insertShare(
                        connection = connection,
                        shareId = UUID.randomUUID(),
                        grantedByAppUserId = if (kind == "USER") userId else null,
                        grantedByPrincipalKind = kind,
                        grantedByPrincipalId = principalId,
                        revokedByPrincipalKind = kind,
                        revokedByPrincipalId = principalId,
                    )
                }
            }
        }
    }

    @Test
    fun `canonical Share provenance refuses half principals and legacy drift`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val userId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()
                insertAppUser(connection, userId, "share-user@process.example")
                insertAppUser(connection, otherUserId, "share-other-user@process.example")

                refused(connection, "ck_share_grantor_principal_pair") {
                    insertShare(
                        connection = connection,
                        shareId = UUID.randomUUID(),
                        grantedByPrincipalKind = "PARTICIPANT",
                        grantedByPrincipalId = null,
                    )
                }
                refused(connection, "ck_share_grantor_principal_legacy") {
                    insertShare(
                        connection = connection,
                        shareId = UUID.randomUUID(),
                        grantedByAppUserId = userId,
                        grantedByPrincipalKind = "USER",
                        grantedByPrincipalId = otherUserId,
                    )
                }
                refused(connection, "ck_share_revoker_principal_legacy") {
                    insertShare(
                        connection = connection,
                        shareId = UUID.randomUUID(),
                        revokedByAppUserId = userId,
                        revokedByPrincipalKind = "PARTICIPANT",
                        revokedByPrincipalId = userId,
                    )
                }
            }
        }
    }

    private fun insertAppUser(connection: Connection, id: UUID, email: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user (id, email, created_date, is_active, is_temporary,
                                  sign_in_attempts, exchange_version,
                                  multifactor_authentication_type)
            VALUES (?, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, email)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertShare(
        connection: Connection,
        shareId: UUID,
        grantedByAppUserId: UUID? = null,
        revokedByAppUserId: UUID? = null,
        grantedByPrincipalKind: String? = null,
        grantedByPrincipalId: UUID? = null,
        revokedByPrincipalKind: String? = null,
        revokedByPrincipalId: UUID? = null,
    )
    {
        val canonicalColumns = listOf(
            "granted_by_principal_kind" to grantedByPrincipalKind,
            "granted_by_principal_id" to grantedByPrincipalId,
            "revoked_by_principal_kind" to revokedByPrincipalKind,
            "revoked_by_principal_id" to revokedByPrincipalId,
        ).filter { it.second != null }
        val columnSql = canonicalColumns.joinToString("") { ", ${it.first}" }
        val placeholderSql = canonicalColumns.joinToString("") { ", ?" }

        connection.prepareStatement(
            """
            INSERT INTO share
                (id, resource_type, resource_id, principal_kind, principal_id, role_name,
                 source, status, granted_by_app_user_id, granted_at, revoked_at,
                 revoked_by_app_user_id$columnSql)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'VIEWER', 'DIRECT', 'ACTIVE', ?, ?, ?, ?$placeholderSql)
            """.trimIndent(),
        ).use { statement ->
            var index = 1
            statement.setObject(index++, shareId)
            statement.setObject(index++, UUID.randomUUID())
            statement.setObject(index++, UUID.randomUUID())
            statement.setObject(index++, grantedByAppUserId)
            statement.setTimestamp(index++, Timestamp.from(Instant.now()))
            statement.setTimestamp(index++, revokedByAppUserId?.let { Timestamp.from(Instant.now()) })
            statement.setObject(index++, revokedByAppUserId)
            canonicalColumns.forEach { (_, value) ->
                statement.setObject(index++, value)
            }
            statement.executeUpdate()
        }
    }

    private fun stringColumn(connection: Connection, column: String, shareId: UUID): String? =
        connection.prepareStatement("SELECT $column FROM share WHERE id = ?")
            .use { statement ->
                statement.setObject(1, shareId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }

    private fun uuidColumn(connection: Connection, column: String, shareId: UUID): UUID? =
        connection.prepareStatement("SELECT $column FROM share WHERE id = ?")
            .use { statement ->
                statement.setObject(1, shareId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getObject(1, UUID::class.java)
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

    private fun withPostgres(block: (SharePrincipalProvenancePostgreSQLContainer) -> Unit)
    {
        val postgres = SharePrincipalProvenancePostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_share_provenance_test")
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

    private fun flyway(postgres: SharePrincipalProvenancePostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }
}
