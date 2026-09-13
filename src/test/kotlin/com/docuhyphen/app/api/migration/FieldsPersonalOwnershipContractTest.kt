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

private class PersonalOwnershipPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<PersonalOwnershipPostgreSQLContainer>(imageName)

/**
 * Reusable configuration is owned by the platform or by an organization, and there is nowhere to
 * record a person as the owner. A user with no organization therefore cannot hold a Field, a Schema,
 * or an assignment of one, even though personal ownership is first-class everywhere else.
 *
 * A column alone does not settle it. The released key indexes fold every owner into
 * `COALESCE(scope_org_id, '000...0')`, so the moment two people hold the same key their rows meet at
 * the same sentinel and the second one is refused. These contract tests hold the three facts that
 * make personal ownership safe: the owner is recorded, each scope kind admits exactly one owner, and
 * one person's key space is their own.
 */
class FieldsPersonalOwnershipContractTest
{
    private val releasedVersion = "81"
    private val sentinel = UUID.fromString("00000000-0000-0000-0000-000000000000")

    @Test
    fun `a person owns a definition, a schema, and an assignment of one`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                insertFieldDefinition(connection, fixture.fieldDefinitionId, "PERSONAL", userId = fixture.ownerId)
                insertSchemaDefinition(connection, fixture.schemaDefinitionId, "PERSONAL", userId = fixture.ownerId)
                insertSchemaVersion(connection, fixture)
                insertAssignment(connection, fixture, fixture.assignmentId, "PERSONAL", userId = fixture.ownerId)

                assertEquals(fixture.ownerId, ownerOf(connection, "field_definition", fixture.fieldDefinitionId))
                assertEquals(fixture.ownerId, ownerOf(connection, "schema_definition", fixture.schemaDefinitionId))
                assertEquals(fixture.ownerId, ownerOf(connection, "schema_assignment", fixture.assignmentId))
            }
        }
    }

    @Test
    fun `each scope kind admits exactly one owner`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                // A person's row names the person and nobody else.
                refused(connection, "ck_field_def_scope_owner") {
                    insertFieldDefinition(
                        connection, UUID.randomUUID(), "PERSONAL",
                        orgId = fixture.organizationId, userId = fixture.ownerId, fieldKey = "co-owned",
                    )
                }
                refused(connection, "ck_field_def_scope_owner") {
                    insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", fieldKey = "unowned")
                }

                // An organization's row and the platform's row still name nobody personally.
                refused(connection, "ck_field_def_scope_owner") {
                    insertFieldDefinition(
                        connection, UUID.randomUUID(), "ORGANIZATION",
                        orgId = fixture.organizationId, userId = fixture.ownerId, fieldKey = "org-and-person",
                    )
                }
                refused(connection, "ck_field_def_scope_owner") {
                    insertFieldDefinition(
                        connection, UUID.randomUUID(), "PLATFORM",
                        userId = fixture.ownerId, fieldKey = "platform-and-person",
                    )
                }

                refused(connection, "ck_schema_def_scope_owner") {
                    insertSchemaDefinition(connection, UUID.randomUUID(), "PERSONAL", schemaKey = "unowned")
                }

                // The assignment carries the owner of the schema it applies, and the released schema
                // never checked that it carries a coherent one.
                insertSchemaDefinition(connection, fixture.schemaDefinitionId, "PERSONAL", userId = fixture.ownerId)
                insertSchemaVersion(connection, fixture)
                refused(connection, "ck_assignment_scope_owner") {
                    insertAssignment(connection, fixture, UUID.randomUUID(), "PERSONAL", resourceId = UUID.randomUUID())
                }
            }
        }
    }

    @Test
    fun `one person's key space is their own`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.ownerId)
                insertSchemaDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.ownerId)

                // Somebody else holding the same key is not a conflict; it is a different owner.
                insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.otherOwnerId)
                insertSchemaDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.otherOwnerId)

                // The same owner holding it twice still is.
                refused(connection, "ux_field_def_key") {
                    insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.ownerId)
                }
                refused(connection, "ux_schema_def_key") {
                    insertSchemaDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.ownerId)
                }
            }
        }
    }

    @Test
    fun `a personal key does not meet the platform or organization key at a sentinel`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                // The sentinel the released index folds every ownerless row onto, held by a real
                // person, must not stand in for the platform's own key.
                insertOwner(connection, sentinel, "sentinel-owner@process.test")

                insertFieldDefinition(connection, UUID.randomUUID(), "PLATFORM")
                insertFieldDefinition(connection, UUID.randomUUID(), "ORGANIZATION", orgId = fixture.organizationId)
                insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = fixture.ownerId)
                insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = sentinel)

                assertEquals(4, countFieldDefinitions(connection, "recorded-note"))
            }
        }
    }

    @Test
    fun `rows already stored keep the owner they had`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)
                insertFieldDefinition(connection, fixture.fieldDefinitionId, "PLATFORM")
                insertSchemaDefinition(
                    connection, fixture.schemaDefinitionId, "ORGANIZATION", orgId = fixture.organizationId,
                )
                insertSchemaVersion(connection, fixture)
                insertAssignment(
                    connection, fixture, fixture.assignmentId, "ORGANIZATION", orgId = fixture.organizationId,
                )
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertNull(ownerOf(connection, "field_definition", fixture.fieldDefinitionId))
                assertNull(ownerOf(connection, "schema_definition", fixture.schemaDefinitionId))
                assertNull(ownerOf(connection, "schema_assignment", fixture.assignmentId))

                assertEquals(
                    fixture.organizationId,
                    organizationOf(connection, "schema_definition", fixture.schemaDefinitionId),
                    "An organization's schema is still that organization's",
                )

                // The keys those rows hold are still theirs alone.
                refused(connection, "ux_field_def_key") {
                    insertFieldDefinition(connection, UUID.randomUUID(), "PLATFORM")
                }
            }
        }
    }

    // ── Fixture ─────────────────────────────────────────────────────────────────

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val ownerId: UUID = UUID.randomUUID()
        val otherOwnerId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()
        val resourceId: UUID = UUID.randomUUID()
    }

    private fun withPostgres(block: (PersonalOwnershipPostgreSQLContainer) -> Unit)
    {
        val postgres = PersonalOwnershipPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_personal_ownership_test")
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

    private fun flyway(postgres: PersonalOwnershipPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    /**
     * A refused statement aborts the transaction it ran in, so each one is run in its own and rolled
     * back. Everything inserted before it was committed as it went and survives.
     */
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

    private fun insertOwners(connection: Connection, fixture: Fixture)
    {
        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete,
                                      created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.organizationId)
            statement.setString(2, "Process Owner Org")
            statement.setString(3, "REG-PROCESS-PERSONAL-OWNERSHIP")
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        insertOwner(connection, fixture.ownerId, "first-owner@process.test")
        insertOwner(connection, fixture.otherOwnerId, "second-owner@process.test")
    }

    private fun insertOwner(connection: Connection, userId: UUID, email: String)
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
            statement.setObject(1, userId)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.setString(3, email)
            statement.executeUpdate()
        }
    }

    private fun insertFieldDefinition(
        connection: Connection,
        id: UUID,
        scopeKind: String,
        orgId: UUID? = null,
        userId: UUID? = null,
        fieldKey: String = "recorded-note",
    )
    {
        val now = Timestamp.from(Instant.now())
        val ownerColumn = if (userId == null) "" else ", scope_user_id"
        val ownerValue = if (userId == null) "" else ", ?"

        connection.prepareStatement(
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status,
                                          created_at, updated_at$ownerColumn)
            VALUES (?, ?, ?, 'process', ?, 'PUBLISHED', ?, ?$ownerValue)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, scopeKind)
            statement.setObject(3, orgId)
            statement.setString(4, fieldKey)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            userId?.let { statement.setObject(7, it) }
            statement.executeUpdate()
        }
    }

    private fun insertSchemaDefinition(
        connection: Connection,
        id: UUID,
        scopeKind: String,
        orgId: UUID? = null,
        userId: UUID? = null,
        schemaKey: String = "process-data",
    )
    {
        val now = Timestamp.from(Instant.now())
        val ownerColumn = if (userId == null) "" else ", scope_user_id"
        val ownerValue = if (userId == null) "" else ", ?"

        connection.prepareStatement(
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key,
                                           display_name, target_resource_type, status, created_at,
                                           updated_at$ownerColumn)
            VALUES (?, ?, ?, 'process', ?, 'Process data', 'EXCHANGE', 'PUBLISHED', ?, ?$ownerValue)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, scopeKind)
            statement.setObject(3, orgId)
            statement.setString(4, schemaKey)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            userId?.let { statement.setObject(7, it) }
            statement.executeUpdate()
        }
    }

    private fun insertSchemaVersion(connection: Connection, fixture: Fixture)
    {
        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at,
                                        created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.schemaVersionId)
            statement.setObject(2, fixture.schemaDefinitionId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }

    private fun insertAssignment(
        connection: Connection,
        fixture: Fixture,
        id: UUID,
        scopeKind: String,
        orgId: UUID? = null,
        userId: UUID? = null,
        resourceId: UUID = fixture.resourceId,
    )
    {
        val ownerColumn = if (userId == null) "" else ", scope_user_id"
        val ownerValue = if (userId == null) "" else ", ?"

        connection.prepareStatement(
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind,
                                           scope_org_id, assignment_source, assigned_at$ownerColumn)
            VALUES (?, 'EXCHANGE', ?, ?, ?, ?, 'MANUAL', ?$ownerValue)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, resourceId)
            statement.setObject(3, fixture.schemaVersionId)
            statement.setString(4, scopeKind)
            statement.setObject(5, orgId)
            statement.setTimestamp(6, Timestamp.from(Instant.now()))
            userId?.let { statement.setObject(7, it) }
            statement.executeUpdate()
        }
    }

    private fun ownerOf(connection: Connection, table: String, id: UUID): UUID? =
        singleColumn(connection, "SELECT scope_user_id FROM $table WHERE id = ?", id)

    private fun organizationOf(connection: Connection, table: String, id: UUID): UUID? =
        singleColumn(connection, "SELECT scope_org_id FROM $table WHERE id = ?", id)

    private fun singleColumn(connection: Connection, sql: String, id: UUID): UUID? =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Expected a row for $id" }
                rows.getObject(1) as UUID?
            }
        }

    private fun countFieldDefinitions(connection: Connection, fieldKey: String): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM field_definition WHERE field_key = ?").use { statement ->
            statement.setString(1, fieldKey)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
}
