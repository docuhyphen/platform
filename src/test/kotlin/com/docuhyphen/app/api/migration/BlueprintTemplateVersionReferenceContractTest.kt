package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

private class BlueprintReferencePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<BlueprintReferencePostgreSQLContainer>(imageName)

/**
 * A blueprint may name one exact Information Request Template Version for the requests its future
 * instantiations create.
 *
 * The reference is to a Version rather than to a Template, because what a request is issued against
 * has to be exactly reproducible and a Template keeps changing. Storage holds the reference to a
 * real Version and keeps holding it: which statuses a Version may be in when it is selected, and
 * when it may still be instantiated from, are decisions the owning services make, because both
 * answers change over the life of a Version that storage is not told about.
 */
class BlueprintTemplateVersionReferenceContractTest
{
    private val baselineVersion = "89"

    @Test
    fun `a blueprint names one exact template version`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                insertBlueprint(connection, fixture.blueprintId, fixture, fixture.publishedVersionId)
                assertEquals(
                    fixture.publishedVersionId,
                    referencedVersion(connection, fixture.blueprintId),
                )

                // Naming nothing stays the ordinary case: a blueprint that creates no request is
                // unaffected by this capability.
                val plainBlueprintId = UUID.randomUUID()
                insertBlueprint(connection, plainBlueprintId, fixture, null)
                assertNull(referencedVersion(connection, plainBlueprintId))

                // A Version that does not exist cannot be pinned, because a request created from it
                // could resolve no configuration at all.
                refused(connection, "blueprint_definition_request_template_version_fkey") {
                    insertBlueprint(connection, UUID.randomUUID(), fixture, UUID.randomUUID())
                }
            }
        }
    }

    @Test
    fun `a referenced version cannot be removed out from under the blueprint that names it`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertBlueprint(connection, fixture.blueprintId, fixture, fixture.draftVersionId)

                // Forgetting the reference silently would change what the blueprint creates without
                // anybody deciding to change it.
                refused(connection, "blueprint_definition_request_template_version_fkey") {
                    deleteVersion(connection, fixture.draftVersionId)
                }
            }
        }
    }

    @Test
    fun `retiring the referenced version leaves the reference in place`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertBlueprint(connection, fixture.blueprintId, fixture, fixture.publishedVersionId)

                retire(connection, fixture.publishedVersionId, fixture.userId)

                // The blueprint keeps naming it, so an author can still read what it used to create
                // and can edit everything else about the blueprint. Whether a new request may be
                // created from a retired Version is a service decision, not a stored one.
                assertEquals(
                    fixture.publishedVersionId,
                    referencedVersion(connection, fixture.blueprintId),
                )
                assertEquals("RETIRED", versionStatus(connection, fixture.publishedVersionId))
            }
        }
    }

    @Test
    fun `an upgrade adds the reference to existing blueprints without naming one for them`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = baselineVersion).migrate()

            val fixture: Fixture
            val releasedBlueprintId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                fixture = Fixture(connection)
                assertFalse(hasReferenceColumn(connection))
                insertBlueprint(connection, releasedBlueprintId, fixture, null, withReference = false)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertTrue(hasReferenceColumn(connection))
                // A blueprint released before this capability existed names no Version and keeps
                // creating exactly what it created before.
                assertNull(referencedVersion(connection, releasedBlueprintId))
            }
        }
    }

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val draftVersionId: UUID = UUID.randomUUID()
        val publishedVersionId: UUID = UUID.randomUUID()
        val blueprintId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertDefinition(connection, definitionId, organizationId)
            insertVersion(connection, draftVersionId, definitionId, 1)
            insertVersion(connection, publishedVersionId, definitionId, 2)
            publish(connection, publishedVersionId, userId)
        }
    }

    private fun withPostgres(block: (BlueprintReferencePostgreSQLContainer) -> Unit)
    {
        val postgres = BlueprintReferencePostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_blueprint_reference_test")
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

    private fun flyway(postgres: BlueprintReferencePostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
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

    private fun insertOrganization(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, 'Primary Process Owner', ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "REG-${id.toString().take(8)}")
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
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
            statement.setString(3, "blueprint-author-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name, status,
                 created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, ?, 'collection-pattern', 'Collection pattern', 'DRAFT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, "process-${id.toString().take(8)}")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertVersion(connection: Connection, id: UUID, definitionId: UUID, versionNumber: Int)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, ?, 'DRAFT', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setInt(3, versionNumber)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertBlueprint(
        connection: Connection,
        id: UUID,
        fixture: Fixture,
        templateVersionId: UUID?,
        withReference: Boolean = true,
    )
    {
        val columns = if (withReference) ", information_request_template_version_id" else ""
        val values = if (withReference) ", ?" else ""
        connection.prepareStatement(
            """
            INSERT INTO blueprint_definition
                (id, name, scope, organization_id, created_by_app_user_id, config_json,
                 created_at, updated_at$columns)
            VALUES (?, 'Collection blueprint', 'ORG', ?, ?, '{}', ?, ?$values)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, fixture.organizationId)
            statement.setObject(3, fixture.userId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            if (withReference)
            {
                statement.setObject(6, templateVersionId)
            }
            statement.executeUpdate()
        }
    }

    private fun publish(connection: Connection, versionId: UUID, actorId: UUID)
    {
        connection.prepareStatement(
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, actorId)
            statement.setObject(3, versionId)
            statement.executeUpdate()
        }
    }

    private fun retire(connection: Connection, versionId: UUID, actorId: UUID)
    {
        connection.prepareStatement(
            """
            UPDATE information_request_template_version
            SET status = 'RETIRED', retired_at = ?, retired_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, actorId)
            statement.setObject(3, versionId)
            statement.executeUpdate()
        }
    }

    private fun deleteVersion(connection: Connection, versionId: UUID)
    {
        connection.prepareStatement("DELETE FROM information_request_template_version WHERE id = ?")
            .use { statement ->
                statement.setObject(1, versionId)
                statement.executeUpdate()
            }
    }

    private fun referencedVersion(connection: Connection, blueprintId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT information_request_template_version_id FROM blueprint_definition WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, blueprintId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getObject(1) as UUID?
            }
        }

    private fun versionStatus(connection: Connection, versionId: UUID): String =
        connection.prepareStatement("SELECT status FROM information_request_template_version WHERE id = ?")
            .use { statement ->
                statement.setObject(1, versionId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }

    private fun hasReferenceColumn(connection: Connection): Boolean =
        connection.prepareStatement(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_name = 'blueprint_definition'
              AND column_name = 'information_request_template_version_id'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1) == 1
            }
        }
}

