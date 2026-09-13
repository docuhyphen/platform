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

private class InformationRequestTemplatePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<InformationRequestTemplatePostgreSQLContainer>(imageName)

/**
 * Reusable request configuration is a stable identity, an immutable published version of that
 * identity, the ordered sections of that version, the stable requirements of the identity, and the
 * bindings that place those requirements into one version.
 *
 * The rules that matter here cannot be recovered later: a key belongs to one owner, a version
 * number belongs to one definition, a binding may only reach the sections of its own version and
 * the requirements of its own definition, a stable requirement key is never rewritten, and a
 * published version and everything it holds stop changing.
 */
class InformationRequestTemplateContractTest
{
    @Test
    fun `platform organization and personal owners each hold their own template key space`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                insertDefinition(connection, fixture.platformDefinitionId, "PLATFORM", null, null, fixture.namespace)
                insertDefinition(
                    connection,
                    fixture.organizationDefinitionId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    null,
                    fixture.namespace,
                )
                insertDefinition(
                    connection,
                    fixture.personalDefinitionId,
                    "PERSONAL",
                    null,
                    fixture.userId,
                    fixture.namespace,
                )

                assertEquals(3, definitionCount(connection, fixture.namespace))
                refused(connection, "ux_request_template_definition_key") {
                    insertDefinition(connection, UUID.randomUUID(), "PERSONAL", null, fixture.userId, fixture.namespace)
                }
            }
        }
    }

    @Test
    fun `a template definition names exactly one existing owner`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                refused(connection, "ck_request_template_definition_scope_owner") {
                    insertDefinition(
                        connection,
                        UUID.randomUUID(),
                        "PERSONAL",
                        fixture.organizationId,
                        fixture.userId,
                        fixture.namespace,
                    )
                }
                refused(connection, "ck_request_template_definition_scope_owner") {
                    insertDefinition(connection, UUID.randomUUID(), "ORGANIZATION", null, null, fixture.namespace)
                }
                refused(connection, "ck_request_template_definition_scope_kind") {
                    insertDefinition(connection, UUID.randomUUID(), "TEAM", null, null, fixture.namespace)
                }
                refused(connection, "information_request_template_definition_scope_user_id_fkey") {
                    insertDefinition(
                        connection,
                        UUID.randomUUID(),
                        "PERSONAL",
                        null,
                        UUID.randomUUID(),
                        fixture.namespace,
                    )
                }
            }
        }
    }

    @Test
    fun `a definition numbers each version once and records how it was published`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertDefinition(
                    connection,
                    fixture.organizationDefinitionId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    null,
                    fixture.namespace,
                )

                insertVersion(connection, fixture.draftVersionId, fixture.organizationDefinitionId, 1)
                publish(connection, fixture.draftVersionId, fixture.userId)
                insertVersion(connection, fixture.secondVersionId, fixture.organizationDefinitionId, 2)

                assertEquals("PUBLISHED", versionStatus(connection, fixture.draftVersionId))
                assertEquals(fixture.userId, publishedBy(connection, fixture.draftVersionId))
                refused(connection, "ux_request_template_version_number") {
                    insertVersion(connection, UUID.randomUUID(), fixture.organizationDefinitionId, 2)
                }
                refused(connection, "ck_request_template_version_publication") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_version SET status = 'PUBLISHED' WHERE id = ?",
                        fixture.secondVersionId,
                    )
                }
            }
        }
    }

    @Test
    fun `a binding reaches only the sections of its version and the requirements of its definition`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertDefinition(
                    connection,
                    fixture.organizationDefinitionId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    null,
                    fixture.namespace,
                )
                insertDefinition(
                    connection,
                    fixture.personalDefinitionId,
                    "PERSONAL",
                    null,
                    fixture.userId,
                    fixture.namespace,
                )
                insertVersion(connection, fixture.draftVersionId, fixture.organizationDefinitionId, 1)
                insertVersion(connection, fixture.otherOwnerVersionId, fixture.personalDefinitionId, 1)
                insertSection(connection, fixture.sectionId, fixture.draftVersionId, "collected-data", 1)
                insertSection(connection, fixture.otherOwnerSectionId, fixture.otherOwnerVersionId, "collected-data", 1)
                insertRequirement(connection, fixture.requirementId, fixture.organizationDefinitionId, "recorded-note")
                insertRequirement(connection, fixture.secondRequirementId, fixture.organizationDefinitionId, "supporting-note")
                insertRequirement(connection, fixture.otherOwnerRequirementId, fixture.personalDefinitionId, "recorded-note")

                insertBinding(
                    connection,
                    UUID.randomUUID(),
                    fixture.draftVersionId,
                    fixture.organizationDefinitionId,
                    fixture.requirementId,
                    fixture.sectionId,
                    1,
                )

                assertEquals(1, bindingCount(connection, fixture.draftVersionId))
                refused(connection, "request_template_binding_section_fkey") {
                    insertBinding(
                        connection,
                        UUID.randomUUID(),
                        fixture.draftVersionId,
                        fixture.organizationDefinitionId,
                        fixture.secondRequirementId,
                        fixture.otherOwnerSectionId,
                        2,
                    )
                }
                refused(connection, "request_template_binding_requirement_fkey") {
                    insertBinding(
                        connection,
                        UUID.randomUUID(),
                        fixture.draftVersionId,
                        fixture.organizationDefinitionId,
                        fixture.otherOwnerRequirementId,
                        fixture.sectionId,
                        2,
                    )
                }
                refused(connection, "ux_request_template_binding_requirement") {
                    insertBinding(
                        connection,
                        UUID.randomUUID(),
                        fixture.draftVersionId,
                        fixture.organizationDefinitionId,
                        fixture.requirementId,
                        fixture.sectionId,
                        2,
                    )
                }
                refused(connection, "ux_request_template_section_order") {
                    insertSection(connection, UUID.randomUUID(), fixture.draftVersionId, "supporting-data", 1)
                }
            }
        }
    }

    @Test
    fun `a stable requirement key is unique per definition and is never rewritten`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertDefinition(
                    connection,
                    fixture.organizationDefinitionId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    null,
                    fixture.namespace,
                )
                insertDefinition(
                    connection,
                    fixture.personalDefinitionId,
                    "PERSONAL",
                    null,
                    fixture.userId,
                    fixture.namespace,
                )
                insertRequirement(connection, fixture.requirementId, fixture.organizationDefinitionId, "recorded-note")
                insertRequirement(connection, fixture.otherOwnerRequirementId, fixture.personalDefinitionId, "recorded-note")

                assertEquals(2, requirementCount(connection, "recorded-note"))
                refused(connection, "ux_request_template_requirement_key") {
                    insertRequirement(connection, UUID.randomUUID(), fixture.organizationDefinitionId, "recorded-note")
                }
                refused(connection, "stable identity") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_requirement SET requirement_key = 'renamed-note' " +
                            "WHERE id = ?",
                        fixture.requirementId,
                    )
                }
            }
        }
    }

    @Test
    fun `a published version and the configuration it holds stop changing`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertDefinition(
                    connection,
                    fixture.organizationDefinitionId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    null,
                    fixture.namespace,
                )
                insertVersion(connection, fixture.draftVersionId, fixture.organizationDefinitionId, 1)
                insertSection(connection, fixture.sectionId, fixture.draftVersionId, "collected-data", 1)
                insertRequirement(connection, fixture.requirementId, fixture.organizationDefinitionId, "recorded-note")
                insertBinding(
                    connection,
                    fixture.bindingId,
                    fixture.draftVersionId,
                    fixture.organizationDefinitionId,
                    fixture.requirementId,
                    fixture.sectionId,
                    1,
                )
                publish(connection, fixture.draftVersionId, fixture.userId)

                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_version SET version_number = 7 WHERE id = ?",
                        fixture.draftVersionId,
                    )
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_section SET title = 'Changed' WHERE id = ?",
                        fixture.sectionId,
                    )
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "DELETE FROM information_request_template_requirement_binding WHERE id = ?",
                        fixture.bindingId,
                    )
                }
                refused(connection, "immutable") {
                    insertSection(connection, UUID.randomUUID(), fixture.draftVersionId, "supporting-data", 2)
                }

                retire(connection, fixture.draftVersionId, fixture.userId)
                assertEquals("RETIRED", versionStatus(connection, fixture.draftVersionId))
                assertEquals(1, bindingCount(connection, fixture.draftVersionId))
            }
        }
    }

    @Test
    fun `ad hoc template definitions are request private and excluded from reusable listings`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertDefinition(
                    connection,
                    fixture.organizationDefinitionId,
                    "ORGANIZATION",
                    fixture.organizationId,
                    null,
                    fixture.namespace,
                )
                insertPrivateDefinitionWithRequest(
                    connection,
                    fixture.personalDefinitionId,
                    fixture.organizationId,
                    fixture.userId,
                    fixture.exchangeId,
                    UUID.randomUUID(),
                    fixture.namespace,
                )

                assertEquals(1, reusableDefinitionCount(connection, fixture.organizationId, fixture.namespace))
                assertEquals(1, privateDefinitionCount(connection, fixture.organizationId, fixture.namespace))
                refused(connection, "ck_request_template_definition_origin") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_definition SET origin_kind = 'AD_HOC_REQUEST' " +
                            "WHERE id = ?",
                        fixture.organizationDefinitionId,
                    )
                }
            }
        }
    }

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val namespace: String = "process-${UUID.randomUUID().toString().take(8)}"
        val platformDefinitionId: UUID = UUID.randomUUID()
        val organizationDefinitionId: UUID = UUID.randomUUID()
        val personalDefinitionId: UUID = UUID.randomUUID()
        val draftVersionId: UUID = UUID.randomUUID()
        val secondVersionId: UUID = UUID.randomUUID()
        val otherOwnerVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val otherOwnerSectionId: UUID = UUID.randomUUID()
        val requirementId: UUID = UUID.randomUUID()
        val secondRequirementId: UUID = UUID.randomUUID()
        val otherOwnerRequirementId: UUID = UUID.randomUUID()
        val bindingId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
        }
    }

    private fun withPostgres(block: (InformationRequestTemplatePostgreSQLContainer) -> Unit)
    {
        val postgres = InformationRequestTemplatePostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_request_template_test")
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

    private fun flyway(postgres: InformationRequestTemplatePostgreSQLContainer): Flyway =
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
            "Expected $expected to refuse this statement: ${refusal.message}",
        )
    }

    private fun updateStatement(connection: Connection, sql: String, id: UUID)
    {
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeUpdate()
        }
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
            statement.setString(3, "template-owner-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, userId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                    'Process collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setObject(3, userId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(
        connection: Connection,
        id: UUID,
        scopeKind: String,
        organizationId: UUID?,
        userId: UUID?,
        namespace: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, scope_user_id, namespace, template_key, display_name,
                 status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'collection-pattern', 'Collection pattern', 'DRAFT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setString(2, scopeKind)
            statement.setObject(3, organizationId)
            statement.setObject(4, userId)
            statement.setString(5, namespace)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, now)
            statement.executeUpdate()
        }
    }

    private fun insertPrivateDefinitionWithRequest(
        connection: Connection,
        definitionId: UUID,
        organizationId: UUID,
        userId: UUID,
        exchangeId: UUID,
        requestId: UUID,
        namespace: String,
    )
    {
        val versionId = UUID.randomUUID()
        val requirementId = UUID.randomUUID()
        val sectionId = UUID.randomUUID()
        val bindingId = UUID.randomUUID()

        connection.autoCommit = false
        try
        {
            connection.createStatement().use { it.execute("SET CONSTRAINTS ALL DEFERRED") }
            insertDefinition(
                connection,
                definitionId,
                "ORGANIZATION",
                organizationId,
                null,
                namespace,
                originKind = "AD_HOC_REQUEST",
                originRequestId = requestId,
            )
            insertVersion(connection, versionId, definitionId, 1)
            insertSection(connection, sectionId, versionId, "collected-data", 1)
            insertRequirement(connection, requirementId, definitionId, "recorded-note")
            insertBinding(connection, bindingId, versionId, definitionId, requirementId, sectionId, 1)
            publish(connection, versionId, userId)
            insertRequest(connection, requestId, exchangeId, versionId, organizationId)
            connection.commit()
        }
        catch (exception: Exception)
        {
            connection.rollback()
            throw exception
        }
        finally
        {
            connection.autoCommit = true
        }
    }

    private fun insertDefinition(
        connection: Connection,
        id: UUID,
        scopeKind: String,
        organizationId: UUID?,
        userId: UUID?,
        namespace: String,
        originKind: String,
        originRequestId: UUID?,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, scope_user_id, namespace, template_key, display_name,
                 status, created_at, updated_at, origin_kind, origin_request_id)
            VALUES (?, ?, ?, ?, ?, ?, 'Collection pattern', 'DRAFT', ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setString(2, scopeKind)
            statement.setObject(3, organizationId)
            statement.setObject(4, userId)
            statement.setString(5, namespace)
            statement.setString(6, "collection-${id.toString().take(8)}")
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.setString(9, originKind)
            statement.setObject(10, originRequestId)
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

    private fun insertSection(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        sectionKey: String,
        displayOrder: Int,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_section
                (id, template_version_id, section_key, display_order, title)
            VALUES (?, ?, ?, ?, 'Collected data')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setString(3, sectionKey)
            statement.setInt(4, displayOrder)
            statement.executeUpdate()
        }
    }

    /**
     * The requirement type is immaterial to the structural rules under test here, and a requested
     * document needs no typed-data contract, so these versions stay publishable without one.
     */
    /**
     * These tests are about structure, ownership, and immutability rather than about what is being
     * asked for, so they use the one requirement type that owes publication nothing further: a typed
     * answer needs the Schema Version it resolves against, and a requested document needs the
     * evidence policy it is judged by.
     */
    private fun insertRequirement(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        requirementKey: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, ?, 'RESPONSE_ATTESTATION', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, requirementKey)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        definitionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        displayOrder: Int,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy)
            VALUES (?, ?, ?, ?, ?, ?, 'Provide the supporting record', 'PROVIDE', 'REQUIRED',
                    'CONTRIBUTOR', 'NOT_REQUIRED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setObject(3, definitionId)
            statement.setObject(4, requirementId)
            statement.setObject(5, sectionId)
            statement.setInt(6, displayOrder)
            statement.executeUpdate()
        }
    }

    /**
     * Publication requires the version to have recorded the runtime capabilities its configuration
     * needs, which is not what these tests are about, so the set is derived and recorded here.
     * Nothing is recorded once the version has left draft, so a refused publication still refuses
     * for its own reason.
     */
    private fun publish(connection: Connection, versionId: UUID, actorId: UUID)
    {
        recordRequiredCapabilities(connection, versionId)
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

    private fun recordRequiredCapabilities(connection: Connection, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            SELECT gen_random_uuid(), version.id, required.capability_key, 1
            FROM information_request_template_version version
                     CROSS JOIN request_template_required_capabilities(version.id) required
            WHERE version.id = ?
              AND version.status = 'DRAFT'
              AND NOT EXISTS (SELECT 1
                              FROM information_request_template_version_capability existing
                              WHERE existing.template_version_id = version.id
                                AND existing.capability_key = required.capability_key)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
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

    private fun insertRequest(connection: Connection, id: UUID, exchangeId: UUID, versionId: UUID, ownerId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                 state, gates_exchange_closure, aggregate_revision, party_revision,
                 created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, exchangeId)
            statement.setObject(3, versionId)
            statement.setObject(4, ownerId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun definitionCount(connection: Connection, namespace: String): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_definition WHERE namespace = ?",
            namespace,
        )

    private fun reusableDefinitionCount(connection: Connection, organizationId: UUID, namespace: String): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_definition " +
                "WHERE scope_org_id = ? AND namespace = '$namespace' AND origin_kind = 'REUSABLE'",
            organizationId,
        )

    private fun privateDefinitionCount(connection: Connection, organizationId: UUID, namespace: String): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_definition " +
                "WHERE scope_org_id = ? AND namespace = '$namespace' AND origin_kind = 'AD_HOC_REQUEST'",
            organizationId,
        )

    private fun requirementCount(connection: Connection, requirementKey: String): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_requirement WHERE requirement_key = ?",
            requirementKey,
        )

    private fun bindingCount(connection: Connection, versionId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_requirement_binding " +
                "WHERE template_version_id = ?",
            versionId,
        )

    private fun countOf(connection: Connection, sql: String, parameter: Any): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, parameter)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
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

    private fun publishedBy(connection: Connection, versionId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT published_by_app_user_id FROM information_request_template_version WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getObject(1) as UUID?
            }
        }
}
