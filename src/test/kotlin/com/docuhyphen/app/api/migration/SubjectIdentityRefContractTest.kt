package com.docuhyphen.app.api.migration

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

private class SubjectIdentityPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<SubjectIdentityPostgreSQLContainer>(imageName)

class SubjectIdentityRefContractTest
{
    @Test
    fun `organization and personal tenants can own opaque subject identities`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertSubject(connection, fixture.organizationSubjectId, "ORGANIZATION", fixture.organizationId, null, "ASSET")
                insertSubject(connection, fixture.personalSubjectId, "USER", null, fixture.userId, "RECORD")

                assertEquals("ASSET", subjectKind(connection, fixture.organizationSubjectId))
                assertEquals("RECORD", subjectKind(connection, fixture.personalSubjectId))
            }
        }
    }

    @Test
    fun `a subject tenant names exactly one existing owner`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                refused(connection, "ck_subject_identity_ref_owner") {
                    insertSubject(connection, UUID.randomUUID(), "USER", fixture.organizationId, fixture.userId, "PERSON")
                }
                refused(connection, "subject_identity_ref_owner_user_id_fkey") {
                    insertSubject(connection, UUID.randomUUID(), "USER", null, UUID.randomUUID(), "PERSON")
                }
                refused(connection, "ck_subject_identity_ref_subject_kind") {
                    insertSubject(connection, UUID.randomUUID(), "ORGANIZATION", fixture.organizationId, null, "ACCOUNT")
                }
            }
        }
    }

    @Test
    fun `external identifiers are optional authorized aliases scoped to one tenant`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertSubject(connection, fixture.organizationSubjectId, "ORGANIZATION", fixture.organizationId, null, "ASSET")
                insertSubject(connection, fixture.otherOrganizationSubjectId, "ORGANIZATION", fixture.otherOrganizationId, null, "ASSET")

                insertExternalIdentifier(
                    connection,
                    fixture.organizationSubjectId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    "source-system",
                    "record-key",
                    "asset-42",
                    fixture.userId,
                )

                assertEquals(1, externalIdentifierCount(connection, fixture.organizationSubjectId))
                refused(connection, "subject_identity_external_identifier_subject_fkey") {
                    insertExternalIdentifier(
                        connection,
                        fixture.otherOrganizationSubjectId,
                        "ORGANIZATION",
                        fixture.organizationId,
                        "source-system",
                        "record-key",
                        "asset-99",
                        fixture.userId,
                    )
                }
            }
        }
    }

    @Test
    fun `merge and supersession history stays in one tenant and cannot fork`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertSubject(connection, fixture.organizationSubjectId, "ORGANIZATION", fixture.organizationId, null, "PERSON")
                insertSubject(connection, fixture.successorSubjectId, "ORGANIZATION", fixture.organizationId, null, "PERSON")
                insertSubject(connection, fixture.otherOrganizationSubjectId, "ORGANIZATION", fixture.otherOrganizationId, null, "PERSON")

                insertTransition(
                    connection,
                    fixture.organizationSubjectId,
                    fixture.successorSubjectId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    "MERGED",
                    fixture.userId,
                )

                assertEquals("MERGED", transitionKind(connection, fixture.organizationSubjectId))
                refused(connection, "ux_subject_identity_transition_source") {
                    insertTransition(
                        connection,
                        fixture.organizationSubjectId,
                        fixture.otherOrganizationSubjectId,
                        "ORGANIZATION",
                        fixture.organizationId,
                        "SUPERSEDED",
                        fixture.userId,
                    )
                }
            }
        }
    }

    @Test
    fun `subject history refuses cross tenant targets cycles and mutation`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertSubject(connection, fixture.organizationSubjectId, "ORGANIZATION", fixture.organizationId, null, "PERSON")
                insertSubject(connection, fixture.successorSubjectId, "ORGANIZATION", fixture.organizationId, null, "PERSON")
                insertSubject(connection, fixture.otherOrganizationSubjectId, "ORGANIZATION", fixture.otherOrganizationId, null, "PERSON")

                refused(connection, "subject_identity_transition_target_fkey") {
                    insertTransition(
                        connection,
                        fixture.organizationSubjectId,
                        fixture.otherOrganizationSubjectId,
                        "ORGANIZATION",
                        fixture.organizationId,
                        "SUPERSEDED",
                        fixture.userId,
                    )
                }

                insertTransition(
                    connection,
                    fixture.organizationSubjectId,
                    fixture.successorSubjectId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    "SUPERSEDED",
                    fixture.userId,
                )

                refused(connection, "subject identity transition cycle") {
                    insertTransition(
                        connection,
                        fixture.successorSubjectId,
                        fixture.organizationSubjectId,
                        "ORGANIZATION",
                        fixture.organizationId,
                        "MERGED",
                        fixture.userId,
                    )
                }
                refused(connection, "subject_identity_transition is append-only") {
                    connection.createStatement().use { statement ->
                        statement.executeUpdate(
                            "UPDATE subject_identity_transition SET reason = 'changed' " +
                                "WHERE source_subject_identity_id = '${fixture.organizationSubjectId}'",
                        )
                    }
                }
            }
        }
    }

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val otherOrganizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val organizationSubjectId: UUID = UUID.randomUUID()
        val personalSubjectId: UUID = UUID.randomUUID()
        val successorSubjectId: UUID = UUID.randomUUID()
        val otherOrganizationSubjectId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertOrganization(connection, otherOrganizationId, "Other Process Owner")
            insertUser(connection, userId)
        }
    }

    private fun withPostgres(block: (SubjectIdentityPostgreSQLContainer) -> Unit)
    {
        val postgres = SubjectIdentityPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_subject_identity_test")
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

    private fun flyway(postgres: SubjectIdentityPostgreSQLContainer): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
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
            "Expected $expected to refuse this row: ${refusal.message}",
        )
    }

    private fun insertOrganization(connection: Connection, id: UUID, name: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, name)
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
            statement.setString(3, "subject-owner-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertSubject(
        connection: Connection,
        id: UUID,
        ownerType: String,
        organizationId: UUID?,
        userId: UUID?,
        subjectKind: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO subject_identity_ref
                (id, owner_type, owner_organization_id, owner_user_id, subject_kind, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, ownerType)
            statement.setObject(3, organizationId)
            statement.setObject(4, userId)
            statement.setString(5, subjectKind)
            statement.setTimestamp(6, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertExternalIdentifier(
        connection: Connection,
        subjectId: UUID,
        ownerType: String,
        ownerId: UUID,
        authority: String,
        identifierType: String,
        identifierValue: String,
        actorId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO subject_identity_external_identifier
                (id, subject_identity_ref_id, owner_type, owner_id, authority, identifier_type,
                 identifier_value, authorized_by_principal_kind, authorized_by_principal_id,
                 authorized_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, subjectId)
            statement.setString(3, ownerType)
            statement.setObject(4, ownerId)
            statement.setString(5, authority)
            statement.setString(6, identifierType)
            statement.setString(7, identifierValue)
            statement.setObject(8, actorId)
            statement.setTimestamp(9, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertTransition(
        connection: Connection,
        sourceId: UUID,
        targetId: UUID,
        ownerType: String,
        ownerId: UUID,
        transitionKind: String,
        actorId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO subject_identity_transition
                (id, source_subject_identity_id, target_subject_identity_id, owner_type, owner_id,
                 transition_kind, reason, recorded_by_principal_kind, recorded_by_principal_id,
                 recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, 'Identity resolution', 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, sourceId)
            statement.setObject(3, targetId)
            statement.setString(4, ownerType)
            statement.setObject(5, ownerId)
            statement.setString(6, transitionKind)
            statement.setObject(7, actorId)
            statement.setTimestamp(8, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun subjectKind(connection: Connection, subjectId: UUID): String =
        connection.prepareStatement("SELECT subject_kind FROM subject_identity_ref WHERE id = ?").use { statement ->
            statement.setObject(1, subjectId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }

    private fun externalIdentifierCount(connection: Connection, subjectId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM subject_identity_external_identifier WHERE subject_identity_ref_id = ?",
        ).use { statement ->
            statement.setObject(1, subjectId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun transitionKind(connection: Connection, sourceId: UUID): String =
        connection.prepareStatement(
            "SELECT transition_kind FROM subject_identity_transition WHERE source_subject_identity_id = ?",
        ).use { statement ->
            statement.setObject(1, sourceId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }
}
