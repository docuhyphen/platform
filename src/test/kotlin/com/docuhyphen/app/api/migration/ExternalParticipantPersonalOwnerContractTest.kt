package com.docuhyphen.app.api.migration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
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
private class ExternalParticipantOwnerPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<ExternalParticipantOwnerPostgreSQLContainer>(imageName)
class ExternalParticipantPersonalOwnerContractTest
{
    @Test
    fun `external participant email uniqueness is scoped separately for org and personal owners`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val organizationId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                insertOrganization(connection, organizationId)
                insertUser(connection, userId)
                insertParticipant(connection, UUID.randomUUID(), organizationId, null, "actor@example.test")
                insertParticipant(connection, UUID.randomUUID(), null, userId, "actor@example.test")
                assertEquals(2, participantCount(connection, "actor@example.test"))
                refused(connection, "uq_external_participant_owner_org_email") {
                    insertParticipant(connection, UUID.randomUUID(), organizationId, null, "actor@example.test")
                }
                refused(connection, "uq_external_participant_owner_user_email") {
                    insertParticipant(connection, UUID.randomUUID(), null, userId, "actor@example.test")
                }
                refused(connection, "ck_external_participant_owner") {
                    insertParticipant(connection, UUID.randomUUID(), null, null, "missing-owner@example.test")
                }
                refused(connection, "ck_external_participant_owner") {
                    insertParticipant(connection, UUID.randomUUID(), organizationId, userId, "both@example.test")
                }
            }
        }
    }
    @Test
    fun `participants created before ownership existed are adopted by the owner of their exchange`()
    {
        withPostgres { postgres ->
            flyway(postgres, MigrationVersion.fromVersion("96")).migrate()
            val organizationId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val exchangeId = UUID.randomUUID()
            val sharedParticipantId = UUID.randomUUID()
            val unreferencedParticipantId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                insertOrganization(connection, organizationId)
                insertUser(connection, userId)
                insertExchange(connection, exchangeId, organizationId, userId)
                insertOwnerlessParticipant(connection, sharedParticipantId, "shared@example.test")
                insertOwnerlessParticipant(connection, unreferencedParticipantId, "unreferenced@example.test")
                insertParticipantShare(connection, exchangeId, sharedParticipantId, userId)
            }
            flyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                assertEquals(organizationId, ownerOrganizationOf(connection, sharedParticipantId))
                assertEquals(1, shareCountForPrincipal(connection, sharedParticipantId))
                assertEquals(0, participantCountById(connection, unreferencedParticipantId))
            }
        }
    }
    private fun withPostgres(block: (ExternalParticipantOwnerPostgreSQLContainer) -> Unit)
    {
        val postgres = ExternalParticipantOwnerPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_external_participant_owner_test")
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
    private fun flyway(
        postgres: ExternalParticipantOwnerPostgreSQLContainer,
        target: MigrationVersion = MigrationVersion.LATEST,
    ): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .target(target)
            .load()
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
    private fun insertOrganization(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "Process Owner ${id.toString().take(8)}")
            statement.setString(3, "REG-${id.toString().take(8)}")
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }
    private fun insertUser(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.setString(3, "participant-owner-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }
    private fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, initiatorId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO exchange
                (id, name, description, initial_share_message, status, is_deleted,
                 require_recipient_sign_in, created_date, last_activity, initiator_id,
                 owner_organization_id)
            VALUES (?, ?, '', '', 'INITIATED', FALSE, TRUE, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setString(2, "Process ${id.toString().take(8)}")
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.setObject(5, initiatorId)
            statement.setObject(6, organizationId)
            statement.executeUpdate()
        }
    }
    private fun insertParticipant(
        connection: Connection,
        id: UUID,
        organizationId: UUID?,
        userId: UUID?,
        email: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO external_participant
                (id, owner_organization_id, owner_app_user_id, email, email_lower, is_active, created_date)
            VALUES (?, ?, ?, ?, LOWER(?), TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setObject(3, userId)
            statement.setString(4, email)
            statement.setString(5, email)
            statement.setTimestamp(6, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }
    private fun insertOwnerlessParticipant(connection: Connection, id: UUID, email: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO external_participant
                (id, owner_organization_id, email, email_lower, is_active, created_date)
            VALUES (?, NULL, ?, LOWER(?), TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, email)
            statement.setString(3, email)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }
    private fun insertParticipantShare(
        connection: Connection,
        exchangeId: UUID,
        participantId: UUID,
        grantorId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO share
                (id, resource_type, resource_id, principal_kind, principal_id, role_name, source,
                 status, granted_at, granted_by_app_user_id, granted_by_principal_kind,
                 granted_by_principal_id)
            VALUES (?, 'EXCHANGE', ?, 'PARTICIPANT', ?, 'EDITOR', 'DIRECT', 'ACTIVE', ?, ?, 'USER', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, exchangeId)
            statement.setObject(3, participantId)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.setObject(5, grantorId)
            statement.setObject(6, grantorId)
            statement.executeUpdate()
        }
    }
    private fun participantCount(connection: Connection, email: String): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM external_participant WHERE email_lower = LOWER(?)",
        ).use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
    private fun participantCountById(connection: Connection, participantId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM external_participant WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, participantId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
    private fun ownerOrganizationOf(connection: Connection, participantId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT owner_organization_id FROM external_participant WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, participantId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getObject(1, UUID::class.java)
            }
        }
    private fun shareCountForPrincipal(connection: Connection, participantId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM share WHERE principal_kind = 'PARTICIPANT' AND principal_id = ?",
        ).use { statement ->
            statement.setObject(1, participantId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
}
