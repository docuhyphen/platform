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

private class InformationRequestRuntimePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<InformationRequestRuntimePostgreSQLContainer>(imageName)

class InformationRequestRuntimePersistenceContractTest
{
    @Test
    fun `a runtime request pins one published template version under the Exchange owner`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )

                assertEquals("DRAFT", requestState(connection, fixture.requestId))

                refused(connection, "published") {
                    insertRequest(
                        connection,
                        UUID.randomUUID(),
                        fixture.exchangeId,
                        fixture.draftVersionId,
                        fixture.organizationId,
                    )
                }
                refused(connection, "owner") {
                    insertRequest(
                        connection,
                        UUID.randomUUID(),
                        fixture.exchangeId,
                        fixture.otherOwnerVersionId,
                        fixture.organizationId,
                    )
                }
            }
        }
    }

    @Test
    fun `a party and current runtime requirement survive the round trip`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertParty(connection, fixture.partyId, fixture.requestId, "CONTRIBUTOR", fixture.userId)
                insertRuntimeRequirement(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    "root",
                )
                val revisionId = insertRequirementRevision(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    1,
                    "root",
                    null,
                    HASH_ONE,
                )
                insertCurrentRequirementRevision(connection, fixture.runtimeRequirementId, revisionId, 1)

                assertEquals(1, activePartyCount(connection, fixture.requestId))
                assertEquals(1, currentRequirementCount(connection, fixture.requestId))
                assertEquals(1, currentRequirementRevision(connection, fixture.runtimeRequirementId))
            }
        }
    }

    @Test
    fun `a response draft is scoped to one current Requirement occurrence`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertRuntimeRequirement(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    "root",
                )
                val revisionId = insertRequirementRevision(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    1,
                    "root",
                    null,
                    HASH_ONE,
                )
                insertCurrentRequirementRevision(connection, fixture.runtimeRequirementId, revisionId, 1)

                insertResponseDraft(
                    connection,
                    UUID.randomUUID(),
                    fixture.requestId,
                    fixture.runtimeRequirementId,
                    revisionId,
                    "root",
                    fixture.userId,
                )
                updateRequestResponseRevision(connection, fixture.requestId, 2)

                assertEquals(1, responseDraftCount(connection, fixture.requestId))
                assertEquals(2, requestResponseRevision(connection, fixture.requestId))
                refused(connection, "one Requirement occurrence") {
                    insertResponseDraft(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.runtimeRequirementId,
                        revisionId,
                        "other",
                        fixture.userId,
                    )
                }
            }
        }
    }

    @Test
    fun `a response draft records hidden condition state separately from active response state`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertRuntimeRequirement(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    "root",
                )
                val revisionId = insertRequirementRevision(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    1,
                    "root",
                    null,
                    HASH_ONE,
                )
                insertCurrentRequirementRevision(connection, fixture.runtimeRequirementId, revisionId, 1)
                val responseId = UUID.randomUUID()
                insertResponseDraft(
                    connection,
                    responseId,
                    fixture.requestId,
                    fixture.runtimeRequirementId,
                    revisionId,
                    "root",
                    fixture.userId,
                )

                assertEquals(true, responseActive(connection, responseId))

                updateResponseHiddenState(
                    connection,
                    responseId,
                    "when-source-applies",
                    "ARCHIVE_OUTSIDE_ACTIVE_RESPONSE",
                )

                assertEquals(false, responseActive(connection, responseId))
                assertEquals("ARCHIVE_OUTSIDE_ACTIVE_RESPONSE", responseHiddenPolicy(connection, responseId))
                refused(connection, "ck_information_request_response_hidden_data_policy") {
                    updateResponseHiddenPolicy(connection, responseId, "DELETE_ACTIVE_RESPONSE")
                }
                refused(connection, "ck_information_request_response_hidden_state") {
                    updateResponseInactiveWithoutHiddenState(connection, responseId)
                }
            }
        }
    }

    @Test
    fun `delegated authority rows are scoped to their assigned party and optional Requirement`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val otherRequestId = UUID.randomUUID()
                val otherPartyId = UUID.randomUUID()
                val otherRequirementId = UUID.randomUUID()
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertRequest(
                    connection,
                    otherRequestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertParty(connection, fixture.partyId, fixture.requestId, "CONTRIBUTOR", fixture.userId)
                insertParty(connection, otherPartyId, otherRequestId, "CONTRIBUTOR", fixture.userId)
                insertRuntimeRequirement(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    "root",
                )
                insertRuntimeRequirement(
                    connection,
                    otherRequirementId,
                    otherRequestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    "root",
                )

                val requestAuthorityId = UUID.randomUUID()
                val requirementAuthorityId = UUID.randomUUID()
                insertDelegatedAuthority(
                    connection,
                    requestAuthorityId,
                    fixture.requestId,
                    fixture.partyId,
                    fixture.userId,
                    null,
                )
                insertDelegatedAuthority(
                    connection,
                    requirementAuthorityId,
                    fixture.requestId,
                    fixture.partyId,
                    fixture.userId,
                    fixture.runtimeRequirementId,
                )

                assertEquals(2, delegatedAuthorityCount(connection, fixture.requestId))
                assertDelegatedAuthorityInstrument(
                    connection,
                    requestAuthorityId,
                    fixture.userId,
                    null,
                )
                assertDelegatedAuthorityInstrument(
                    connection,
                    requirementAuthorityId,
                    fixture.userId,
                    fixture.runtimeRequirementId,
                )
                refused(connection, "assigned party") {
                    insertDelegatedAuthority(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        otherPartyId,
                        fixture.userId,
                        null,
                    )
                }
                refused(connection, "Requirement scope") {
                    insertDelegatedAuthority(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.partyId,
                        fixture.userId,
                        otherRequirementId,
                    )
                }
                refused(connection, "ck_information_request_delegated_authority_principal") {
                    insertDelegatedAuthority(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.partyId,
                        fixture.userId,
                        null,
                        "PUBLIC_LINK",
                    )
                }
            }
        }
    }

    @Test
    fun `runtime requirement occurrences are stable while revisions append`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertRuntimeRequirement(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    "items[0]",
                )
                val firstRevisionId = insertRequirementRevision(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    1,
                    "items[0]",
                    null,
                    HASH_ONE,
                )
                insertCurrentRequirementRevision(connection, fixture.runtimeRequirementId, firstRevisionId, 1)

                refused(connection, "ux_information_request_requirement_occurrence") {
                    insertRuntimeRequirement(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.publishedVersionId,
                        fixture.templateRequirementId,
                        fixture.templateBindingId,
                        "items[0]",
                    )
                }
                refused(connection, "ux_information_request_requirement_revision_number") {
                    insertRequirementRevision(
                        connection,
                        fixture.runtimeRequirementId,
                        fixture.requestId,
                        fixture.publishedVersionId,
                        fixture.templateRequirementId,
                        fixture.templateBindingId,
                        1,
                        "items[0]",
                        null,
                        HASH_TWO,
                    )
                }
                refused(connection, "append-only") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_requirement_revision SET configuration_hash_sha256 = ? WHERE id = ?",
                        HASH_THREE,
                        firstRevisionId,
                    )
                }

                val secondRevisionId = insertRequirementRevision(
                    connection,
                    fixture.runtimeRequirementId,
                    fixture.requestId,
                    fixture.publishedVersionId,
                    fixture.templateRequirementId,
                    fixture.templateBindingId,
                    2,
                    "items[0]",
                    null,
                    HASH_TWO,
                )
                updateCurrentRequirementRevision(connection, fixture.runtimeRequirementId, secondRevisionId, 2)

                assertEquals(2, currentRequirementRevision(connection, fixture.runtimeRequirementId))
            }
        }
    }

    @Test
    fun `group occurrence display order can change while stable paths remain unique`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertGroupOccurrence(
                    connection,
                    UUID.randomUUID(),
                    fixture.requestId,
                    fixture.templateGroupId,
                    occurrenceIndex = 0,
                    occurrencePath = "items[0]",
                    removed = true,
                    actorId = fixture.userId,
                )
                insertGroupOccurrence(
                    connection,
                    UUID.randomUUID(),
                    fixture.requestId,
                    fixture.templateGroupId,
                    occurrenceIndex = 0,
                    occurrencePath = "items[1]",
                )
                insertGroupOccurrence(
                    connection,
                    UUID.randomUUID(),
                    fixture.requestId,
                    fixture.templateGroupId,
                    occurrenceIndex = 1,
                    occurrencePath = "items[2]",
                )

                assertEquals(
                    listOf("items[1]", "items[2]"),
                    activeGroupOccurrencePaths(connection, fixture.requestId),
                )
                refused(connection, "ux_request_group_occurrence_path") {
                    insertGroupOccurrence(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.templateGroupId,
                        occurrenceIndex = 1,
                        occurrencePath = "items[1]",
                    )
                }
            }
        }
    }

    @Test
    fun `transition history is append only and sequenced per request`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertRequest(
                    connection,
                    fixture.requestId,
                    fixture.exchangeId,
                    fixture.publishedVersionId,
                    fixture.organizationId,
                )
                insertTransition(
                    connection,
                    fixture.requestId,
                    1,
                    null,
                    "DRAFT",
                    "CREATE_DRAFT",
                    fixture.userId,
                )

                refused(connection, "ux_information_request_transition_sequence") {
                    insertTransition(
                        connection,
                        fixture.requestId,
                        1,
                        "DRAFT",
                        "ISSUED",
                        "ISSUE",
                        fixture.userId,
                    )
                }
                refused(connection, "append-only") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_transition SET to_state = ? WHERE information_request_id = ?",
                        "ISSUED",
                        fixture.requestId,
                    )
                }
            }
        }
    }

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val otherOrganizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val publishedDefinitionId: UUID = UUID.randomUUID()
        val publishedVersionId: UUID = UUID.randomUUID()
        val draftVersionId: UUID = UUID.randomUUID()
        val otherOwnerDefinitionId: UUID = UUID.randomUUID()
        val otherOwnerVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val templateRequirementId: UUID = UUID.randomUUID()
        val templateBindingId: UUID = UUID.randomUUID()
        val templateGroupId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()
        val partyId: UUID = UUID.randomUUID()
        val runtimeRequirementId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertOrganization(connection, otherOrganizationId, "Other Process Owner")
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertDefinition(connection, publishedDefinitionId, organizationId)
            insertVersion(connection, publishedVersionId, publishedDefinitionId, 1, "DRAFT")
            insertVersion(connection, draftVersionId, publishedDefinitionId, 2, "DRAFT")
            insertDefinition(connection, otherOwnerDefinitionId, otherOrganizationId)
            insertVersion(connection, otherOwnerVersionId, otherOwnerDefinitionId, 1, "PUBLISHED")
            insertTemplateRequirement(connection, templateRequirementId, publishedDefinitionId)
            insertTemplateSection(connection, sectionId, publishedVersionId)
            insertTemplateBinding(
                connection,
                templateBindingId,
                publishedVersionId,
                publishedDefinitionId,
                templateRequirementId,
                sectionId,
            )
            insertTemplateGroup(connection, templateGroupId, publishedVersionId)
            publishVersion(connection, publishedVersionId, userId)
        }
    }

    private fun withPostgres(block: (InformationRequestRuntimePostgreSQLContainer) -> Unit)
    {
        val postgres = InformationRequestRuntimePostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_information_request_runtime_test")
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

    private fun flyway(postgres: InformationRequestRuntimePostgreSQLContainer): Flyway =
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
            statement.setString(3, "runtime-owner-${id.toString().take(8)}@process.test")
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

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                 status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, "collection-${id.toString().take(8)}")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertVersion(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        versionNumber: Int,
        status: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setInt(3, versionNumber)
            statement.setString(4, status)
            statement.setTimestamp(5, if (status == "PUBLISHED") now else null)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateSection(connection: Connection, id: UUID, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_section
                (id, template_version_id, section_key, display_order, title)
            VALUES (?, ?, 'records', 1, 'Records')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateRequirement(connection: Connection, id: UUID, definitionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, 'recorded-assertion', 'RESPONSE_ATTESTATION', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertTemplateBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        definitionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy)
            VALUES (?, ?, ?, ?, ?, 1, 'State the response', 'PROVIDE', 'REQUIRED',
                    'CONTRIBUTOR', 'NOT_REQUIRED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setObject(3, definitionId)
            statement.setObject(4, requirementId)
            statement.setObject(5, sectionId)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateGroup(connection: Connection, id: UUID, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_group
                (id, template_version_id, group_key, min_occurrences, max_occurrences)
            VALUES (?, ?, 'items', 0, 5)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.executeUpdate()
        }
    }

    private fun publishVersion(connection: Connection, versionId: UUID, actorId: UUID)
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
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
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

    private fun insertGroupOccurrence(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        groupId: UUID,
        occurrenceIndex: Int,
        occurrencePath: String,
        removed: Boolean = false,
        actorId: UUID? = null,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_group_occurrence
                (id, information_request_id, source_template_group_id, occurrence_index,
                 occurrence_path, removed_at, removed_by_principal_kind, removed_by_principal_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, groupId)
            statement.setInt(4, occurrenceIndex)
            statement.setString(5, occurrencePath)
            statement.setTimestamp(6, if (removed) Timestamp.from(Instant.now()) else null)
            statement.setString(7, if (removed) "USER" else null)
            statement.setObject(8, if (removed) requireNotNull(actorId) else null)
            statement.executeUpdate()
        }
    }

    private fun insertParty(connection: Connection, id: UUID, requestId: UUID, roleKey: String, principalId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id,
                 active, party_revision, created_at, updated_at)
            VALUES (?, ?, ?, 'USER', ?, TRUE, 1, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setString(3, roleKey)
            statement.setObject(4, principalId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertRuntimeRequirement(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        versionId: UUID,
        requirementId: UUID,
        bindingId: UUID,
        occurrencePath: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, occurrence_path,
                 created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, versionId)
            statement.setObject(4, requirementId)
            statement.setObject(5, bindingId)
            statement.setString(6, occurrencePath)
            statement.setTimestamp(7, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertRequirementRevision(
        connection: Connection,
        requirementId: UUID,
        requestId: UUID,
        versionId: UUID,
        templateRequirementId: UUID,
        bindingId: UUID,
        revisionNumber: Int,
        occurrencePath: String,
        effectiveTo: Timestamp?,
        configurationHash: String,
    ): UUID
    {
        val revisionId = UUID.randomUUID()
        connection.prepareStatement(
            """
            INSERT INTO information_request_requirement_revision
                (id, information_request_requirement_id, information_request_id,
                 source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, revision_number, occurrence_path, effective_from,
                 effective_to, configuration_hash_sha256, optimistic_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, revisionId)
            statement.setObject(2, requirementId)
            statement.setObject(3, requestId)
            statement.setObject(4, versionId)
            statement.setObject(5, templateRequirementId)
            statement.setObject(6, bindingId)
            statement.setInt(7, revisionNumber)
            statement.setString(8, occurrencePath)
            statement.setTimestamp(9, Timestamp.from(Instant.now()))
            statement.setTimestamp(10, effectiveTo)
            statement.setString(11, configurationHash)
            statement.executeUpdate()
        }
        return revisionId
    }

    private fun insertCurrentRequirementRevision(
        connection: Connection,
        requirementId: UUID,
        revisionId: UUID,
        revisionNumber: Int,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_requirement_current
                (information_request_requirement_id, current_revision_id, current_revision_number,
                 updated_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, requirementId)
            statement.setObject(2, revisionId)
            statement.setInt(3, revisionNumber)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun updateCurrentRequirementRevision(
        connection: Connection,
        requirementId: UUID,
        revisionId: UUID,
        revisionNumber: Int,
    )
    {
        connection.prepareStatement(
            """
            UPDATE information_request_requirement_current
            SET current_revision_id = ?, current_revision_number = ?, updated_at = ?
            WHERE information_request_requirement_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, revisionId)
            statement.setInt(2, revisionNumber)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setObject(4, requirementId)
            statement.executeUpdate()
        }
    }

    private fun insertTransition(
        connection: Connection,
        requestId: UUID,
        sequenceNumber: Int,
        fromState: String?,
        toState: String,
        mutation: String,
        actorId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation,
                 actor_kind, actor_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, requestId)
            statement.setInt(3, sequenceNumber)
            statement.setString(4, fromState)
            statement.setString(5, toState)
            statement.setString(6, mutation)
            statement.setObject(7, actorId)
            statement.setTimestamp(8, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertResponseDraft(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        requirementId: UUID,
        revisionId: UUID,
        occurrencePath: String,
        actorId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_response
                (id, information_request_id, information_request_requirement_id,
                 requirement_revision_id, occurrence_path, disposition, narrative,
                 response_revision, recorded_by_principal_kind, recorded_by_principal_id,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'PROVIDED', 'Recorded response', 2, 'USER', ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, requirementId)
            statement.setObject(4, revisionId)
            statement.setString(5, occurrencePath)
            statement.setObject(6, actorId)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    private fun insertDelegatedAuthority(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        partyId: UUID,
        delegateId: UUID,
        requirementId: UUID?,
        delegateKind: String = "USER",
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_delegated_authority
                (id, information_request_id, assigned_party_id, delegate_principal_kind,
                 delegate_principal_id, requirement_id, active, grantor_principal_kind,
                 grantor_principal_id, effective_at, recorded_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, 'USER', ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, partyId)
            statement.setString(4, delegateKind)
            statement.setObject(5, delegateId)
            statement.setObject(6, requirementId)
            statement.setObject(7, delegateId)
            statement.setTimestamp(8, now)
            statement.setTimestamp(9, now)
            statement.setTimestamp(10, now)
            statement.executeUpdate()
        }
    }

    private fun assertDelegatedAuthorityInstrument(
        connection: Connection,
        authorityId: UUID,
        grantorId: UUID,
        requirementId: UUID?,
    )
    {
        connection.prepareStatement(
            """
            SELECT grantor_principal_kind, grantor_principal_id, effective_at, requirement_id
            FROM information_request_delegated_authority
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, authorityId)
            statement.executeQuery().use { result ->
                assertTrue(result.next())
                assertEquals("USER", result.getString("grantor_principal_kind"))
                assertEquals(grantorId, result.getObject("grantor_principal_id", UUID::class.java))
                assertTrue(result.getTimestamp("effective_at").toInstant() <= Instant.now())
                assertEquals(requirementId, result.getObject("requirement_id", UUID::class.java))
            }
        }
    }

    private fun updateStatement(connection: Connection, sql: String, value: String, id: UUID)
    {
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.setObject(2, id)
            statement.executeUpdate()
            }
        }

    private fun updateRequestResponseRevision(connection: Connection, requestId: UUID, revision: Long)
    {
        connection.prepareStatement(
            "UPDATE information_request SET response_revision = ? WHERE id = ?",
        ).use { statement ->
            statement.setLong(1, revision)
            statement.setObject(2, requestId)
            statement.executeUpdate()
        }
    }

    private fun updateResponseHiddenState(
        connection: Connection,
        responseId: UUID,
        ruleKey: String,
        policy: String,
    )
    {
        connection.prepareStatement(
            """
            UPDATE information_request_response
            SET active_in_response = FALSE,
                hidden_by_condition_rule_key = ?,
                hidden_data_policy = ?,
                hidden_at = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, ruleKey)
            statement.setString(2, policy)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setObject(4, responseId)
            statement.executeUpdate()
        }
    }

    private fun updateResponseHiddenPolicy(connection: Connection, responseId: UUID, policy: String)
    {
        connection.prepareStatement(
            "UPDATE information_request_response SET hidden_data_policy = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, policy)
            statement.setObject(2, responseId)
            statement.executeUpdate()
        }
    }

    private fun updateResponseInactiveWithoutHiddenState(connection: Connection, responseId: UUID)
    {
        connection.prepareStatement(
            """
            UPDATE information_request_response
            SET active_in_response = FALSE,
                hidden_by_condition_rule_key = NULL,
                hidden_data_policy = NULL,
                hidden_at = NULL
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, responseId)
            statement.executeUpdate()
        }
    }

    private fun requestState(connection: Connection, requestId: UUID): String =
        connection.prepareStatement("SELECT state FROM information_request WHERE id = ?")
            .use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
            }
        }

    private fun requestResponseRevision(connection: Connection, requestId: UUID): Long =
        connection.prepareStatement("SELECT response_revision FROM information_request WHERE id = ?")
            .use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }

    private fun responseActive(connection: Connection, responseId: UUID): Boolean =
        connection.prepareStatement("SELECT active_in_response FROM information_request_response WHERE id = ?")
            .use { statement ->
                statement.setObject(1, responseId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getBoolean(1)
                }
            }

    private fun responseHiddenPolicy(connection: Connection, responseId: UUID): String =
        connection.prepareStatement("SELECT hidden_data_policy FROM information_request_response WHERE id = ?")
            .use { statement ->
                statement.setObject(1, responseId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }

    private fun activePartyCount(connection: Connection, requestId: UUID): Int =
        countOf(connection, "SELECT COUNT(*) FROM information_request_party WHERE information_request_id = ?", requestId)

    private fun currentRequirementCount(connection: Connection, requestId: UUID): Int =
        countOf(
            connection,
            """
            SELECT COUNT(*)
            FROM information_request_requirement_current current_revision
                     JOIN information_request_requirement requirement
                          ON requirement.id = current_revision.information_request_requirement_id
            WHERE requirement.information_request_id = ?
            """.trimIndent(),
            requestId,
        )

    private fun currentRequirementRevision(connection: Connection, requirementId: UUID): Int =
        connection.prepareStatement(
            """
            SELECT current_revision_number
            FROM information_request_requirement_current
            WHERE information_request_requirement_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, requirementId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun delegatedAuthorityCount(connection: Connection, requestId: UUID): Int =
        countOf(connection, "SELECT COUNT(*) FROM information_request_delegated_authority WHERE information_request_id = ?", requestId)

    private fun responseDraftCount(connection: Connection, requestId: UUID): Int =
        countOf(connection, "SELECT COUNT(*) FROM information_request_response WHERE information_request_id = ?", requestId)

    private fun activeGroupOccurrencePaths(connection: Connection, requestId: UUID): List<String> =
        connection.prepareStatement(
            """
            SELECT occurrence_path
            FROM information_request_group_occurrence
            WHERE information_request_id = ?
              AND removed_at IS NULL
            ORDER BY occurrence_index, occurrence_path
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, requestId)
            statement.executeQuery().use { rows ->
                val paths = mutableListOf<String>()
                while (rows.next())
                    paths += rows.getString(1)
                paths
            }
        }

    private fun countOf(connection: Connection, sql: String, id: UUID): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private companion object
    {
        const val HASH_ONE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_TWO = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val HASH_THREE = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    }
}
