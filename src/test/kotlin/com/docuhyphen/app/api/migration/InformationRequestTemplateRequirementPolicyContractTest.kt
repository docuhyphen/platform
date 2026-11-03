package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestTemplateRequirementPolicyContractTest
{
    private val structureVersion = "86"

    @Test
    fun `a requirement declares one platform type and never changes it`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                insertRequirement(
                    connection,
                    fixture.fieldRequirementId,
                    fixture.definitionId,
                    "recorded-note",
                    "FIELD"
                )
                insertRequirement(
                    connection,
                    fixture.documentRequirementId,
                    fixture.definitionId,
                    "supporting-record",
                    "DOCUMENT",
                )
                insertRequirement(
                    connection,
                    fixture.attestationRequirementId,
                    fixture.definitionId,
                    "recorded-assertion",
                    "RESPONSE_ATTESTATION",
                )

                assertEquals("FIELD", requirementType(connection, fixture.fieldRequirementId))
                assertEquals("DOCUMENT", requirementType(connection, fixture.documentRequirementId))
                assertEquals("RESPONSE_ATTESTATION", requirementType(connection, fixture.attestationRequirementId))

                refused(connection, "ck_request_template_requirement_type") {
                    insertRequirement(connection, UUID.randomUUID(), fixture.definitionId, "guessed-kind", "SIGNATURE")
                }
                refused(connection, "stable identity") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_requirement SET requirement_type = 'DOCUMENT' " +
                                "WHERE id = ?",
                        fixture.fieldRequirementId,
                    )
                }
            }
        }
    }

    @Test
    fun `a binding states the respondent policy under which its version asks`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withFieldRequirementBound(connection)

                assertEquals("PROVIDE", bindingValue(connection, fixture.bindingId, "response_mode"))
                assertEquals("REQUIRED", bindingValue(connection, fixture.bindingId, "requiredness"))
                assertEquals("CONTRIBUTOR", bindingValue(connection, fixture.bindingId, "contributor_role"))
                assertEquals("REQUIRED", bindingValue(connection, fixture.bindingId, "review_policy"))

                refused(connection, "ck_request_template_binding_response_mode") {
                    fixture.insertSecondBinding(connection, responseMode = "SHOUTED")
                }
                refused(connection, "ck_request_template_binding_requiredness") {
                    fixture.insertSecondBinding(connection, requiredness = "PREFERRED")
                }
                refused(connection, "ck_request_template_binding_contributor_role") {
                    fixture.insertSecondBinding(connection, contributorRole = "REVIEWER")
                }
                refused(connection, "ck_request_template_binding_review_policy") {
                    fixture.insertSecondBinding(connection, reviewPolicy = "MAYBE")
                }
                refused(connection, "ck_request_template_binding_prompt") {
                    fixture.insertSecondBinding(connection, prompt = "   ")
                }
                refused(connection, "ck_request_template_binding_policy_keys") {
                    fixture.insertSecondBinding(connection, occurrenceAnchorKey = "  ")
                }
                refused(connection, "ck_request_template_binding_conditional_rule") {
                    fixture.insertSecondBinding(connection, requiredness = "CONDITIONAL", conditionalRuleKey = null)
                }

                fixture.insertSecondBinding(
                    connection,
                    requiredness = "CONDITIONAL",
                    conditionalRuleKey = "prior-answer-recorded",
                    occurrenceAnchorKey = "repeated-entry",
                    confidentialityCompartmentKey = "restricted-review",
                    responseMode = "NOT_DISCLOSED",
                )
                assertEquals(
                    "prior-answer-recorded",
                    bindingValue(connection, fixture.secondBindingId, "conditional_rule_key"),
                )
                assertEquals(
                    "repeated-entry",
                    bindingValue(connection, fixture.secondBindingId, "occurrence_anchor_key")
                )
                assertEquals(
                    "restricted-review",
                    bindingValue(connection, fixture.secondBindingId, "confidentiality_compartment_key"),
                )
            }
        }
    }

    @Test
    fun `a binding permits platform dispositions once each and never the absence of an answer`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withFieldRequirementBound(connection)

                refused(connection, "request_template_disposition_binding_fkey") {
                    insertDisposition(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.otherVersionId,
                        "PROVIDED",
                    )
                }

                listOf(
                    "PROVIDED",
                    "PARTIALLY_PROVIDED",
                    "NOT_APPLICABLE",
                    "UNAVAILABLE",
                    "EXCEPTION_REQUESTED",
                    "SATISFIED_BY_REFERENCE",
                    "WAIVED",
                ).forEach { disposition ->
                    insertDisposition(connection, UUID.randomUUID(), fixture.bindingId, fixture.versionId, disposition)
                }
                assertEquals(7, dispositionCount(connection, fixture.bindingId))

                refused(connection, "ux_request_template_disposition_binding") {
                    insertDisposition(connection, UUID.randomUUID(), fixture.bindingId, fixture.versionId, "PROVIDED")
                }
                refused(connection, "ck_request_template_disposition_value") {
                    insertDisposition(connection, UUID.randomUUID(), fixture.bindingId, fixture.versionId, "APPROVED")
                }
                refused(connection, "ck_request_template_disposition_value") {
                    insertDisposition(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.versionId,
                        "NOT_ANSWERED",
                    )
                }
            }
        }
    }

    @Test
    fun `supporting evidence runs from a non-document requirement to a document of the same version`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withFieldRequirementBound(connection)
                fixture.withDocumentRequirementBound(connection)

                insertEvidenceLink(
                    connection,
                    UUID.randomUUID(),
                    fixture.bindingId,
                    fixture.documentBindingId,
                    fixture.versionId,
                )
                assertEquals(1, evidenceLinkCount(connection, fixture.bindingId))

                refused(connection, "ux_request_template_evidence_link_pair") {
                    insertEvidenceLink(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.documentBindingId,
                        fixture.versionId,
                    )
                }
                refused(connection, "supporting evidence must be a requested document") {
                    insertEvidenceLink(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.bindingId,
                        fixture.versionId,
                    )
                }
                refused(connection, "does not itself declare supporting evidence") {
                    insertEvidenceLink(
                        connection,
                        UUID.randomUUID(),
                        fixture.documentBindingId,
                        fixture.bindingId,
                        fixture.versionId,
                    )
                }
                fixture.withAttestationRequirementBound(connection)
                refused(connection, "supporting evidence must be a requested document") {
                    insertEvidenceLink(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.attestationBindingId,
                        fixture.versionId,
                    )
                }
                fixture.withDocumentOnlyVersion(connection)
                refused(connection, "request_template_evidence_link_supporting_fkey") {
                    insertEvidenceLink(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.otherVersionBindingId,
                        fixture.versionId,
                    )
                }
            }
        }
    }

    @Test
    fun `publishing a version that asks for field data requires the schema version it resolves against`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withFieldRequirementBound(connection)

                refused(connection, "must name the schema version") {
                    publish(connection, fixture.versionId)
                }

                updateStatement(
                    connection,
                    "UPDATE information_request_template_version SET schema_version_id = '${fixture.schemaVersionId}' " +
                            "WHERE id = ?",
                    fixture.versionId,
                )
                publish(connection, fixture.versionId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))

                fixture.withDocumentOnlyVersion(connection)
                publish(connection, fixture.otherVersionId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.otherVersionId))
            }
        }
    }

    @Test
    fun `a published version freezes the dispositions and evidence links it holds`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withFieldRequirementBound(connection)
                fixture.withDocumentRequirementBound(connection)

                val dispositionId = UUID.randomUUID()
                val evidenceLinkId = UUID.randomUUID()
                insertDisposition(connection, dispositionId, fixture.bindingId, fixture.versionId, "PROVIDED")
                insertEvidenceLink(
                    connection,
                    evidenceLinkId,
                    fixture.bindingId,
                    fixture.documentBindingId,
                    fixture.versionId,
                )

                updateStatement(
                    connection,
                    "UPDATE information_request_template_version SET schema_version_id = '${fixture.schemaVersionId}' " +
                            "WHERE id = ?",
                    fixture.versionId,
                )
                publish(connection, fixture.versionId)

                refused(connection, "immutable") {
                    insertDisposition(connection, UUID.randomUUID(), fixture.bindingId, fixture.versionId, "WAIVED")
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "DELETE FROM information_request_template_binding_disposition WHERE id = ?",
                        dispositionId,
                    )
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "DELETE FROM information_request_template_binding_evidence_link WHERE id = ?",
                        evidenceLinkId,
                    )
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET review_policy = 'NOT_REQUIRED' " +
                                "WHERE id = ?",
                        fixture.bindingId,
                    )
                }

                assertEquals(1, dispositionCount(connection, fixture.bindingId))
                assertEquals(1, evidenceLinkCount(connection, fixture.bindingId))
            }
        }
    }

    @Test
    fun `an upgrade carries released configuration across and refuses to invent a requirement type`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = structureVersion).migrate()

            val fixture: Fixture
            postgres.createConnection("").use { connection ->
                fixture = Fixture(connection)
                insertSection(connection, fixture.sectionId, fixture.versionId, "collected-data", 1)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals(1, sectionCount(connection, fixture.versionId))
                assertEquals("DRAFT", versionStatus(connection, fixture.versionId))
                insertRequirement(connection, UUID.randomUUID(), fixture.definitionId, "recorded-note", "FIELD")
            }
        }

        withPostgres { postgres ->
            flyway(postgres, target = structureVersion).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                insertUntypedRequirement(connection, UUID.randomUUID(), fixture.definitionId, "recorded-note")
            }

            val refusal = assertThrows<Exception> { flyway(postgres).migrate() }
            assertTrue(
                generateSequence<Throwable>(refusal) { it.cause }
                    .any { it.message.orEmpty().contains("requirement_type") },
                "Expected the upgrade to stop on the untyped requirement: ${refusal.message}",
            )
        }
    }

    private inner class Fixture(private val connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val namespace: String = "process-${UUID.randomUUID().toString().take(8)}"
        val definitionId: UUID = UUID.randomUUID()
        val versionId: UUID = UUID.randomUUID()
        val otherVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val otherSectionId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val fieldContractId: UUID = UUID.randomUUID()
        val fieldRequirementId: UUID = UUID.randomUUID()
        val documentRequirementId: UUID = UUID.randomUUID()
        val attestationRequirementId: UUID = UUID.randomUUID()
        val bindingId: UUID = UUID.randomUUID()
        val secondBindingId: UUID = UUID.randomUUID()
        val documentBindingId: UUID = UUID.randomUUID()
        val attestationBindingId: UUID = UUID.randomUUID()
        val otherVersionBindingId: UUID = UUID.randomUUID()
        val documentPolicyId: UUID = UUID.randomUUID()
        val otherVersionPolicyId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertUser(connection, userId)
            insertDefinition(connection, definitionId, organizationId, namespace)
            insertVersion(connection, versionId, definitionId, 1)
            insertVersion(connection, otherVersionId, definitionId, 2)
            insertSchemaVersion(connection, schemaDefinitionId, schemaVersionId, organizationId, namespace)
            insertCollectableField(
                connection, fieldDefinitionId, fieldContractId, schemaVersionId, namespace,
            )
        }

        fun withFieldRequirementBound(connection: Connection)
        {
            insertSection(connection, sectionId, versionId, "collected-data", 1)
            insertRequirement(connection, fieldRequirementId, definitionId, "recorded-note", "FIELD")
            insertBinding(
                connection, bindingId, versionId, fieldRequirementId, sectionId, 1,
                collectedFieldDefinitionId = fieldDefinitionId,
            )
        }

        fun withDocumentRequirementBound(connection: Connection)
        {
            insertRequirement(connection, documentRequirementId, definitionId, "supporting-record", "DOCUMENT")
            insertBinding(connection, documentBindingId, versionId, documentRequirementId, sectionId, 2)
            insertEvidencePolicy(connection, documentPolicyId, documentBindingId, versionId)
        }

        fun withAttestationRequirementBound(connection: Connection)
        {
            insertRequirement(
                connection,
                attestationRequirementId,
                definitionId,
                "recorded-assertion",
                "RESPONSE_ATTESTATION",
            )
            insertBinding(connection, attestationBindingId, versionId, attestationRequirementId, sectionId, 3)
        }

        fun withDocumentOnlyVersion(connection: Connection)
        {
            insertSection(connection, otherSectionId, otherVersionId, "supporting-data", 1)
            insertRequirement(connection, documentRequirementId, definitionId, "supporting-record", "DOCUMENT")
            insertBinding(connection, otherVersionBindingId, otherVersionId, documentRequirementId, otherSectionId, 1)
            insertEvidencePolicy(connection, otherVersionPolicyId, otherVersionBindingId, otherVersionId)
        }

        fun insertSecondBinding(
            connection: Connection,
            prompt: String = "State the recorded note",
            responseMode: String = "PROVIDE",
            requiredness: String = "OPTIONAL",
            contributorRole: String = "CONTRIBUTOR",
            reviewPolicy: String = "NOT_REQUIRED",
            confidentialityCompartmentKey: String? = null,
            conditionalRuleKey: String? = null,
            occurrenceAnchorKey: String? = null,
        )
        {
            insertRequirement(
                connection,
                attestationRequirementId,
                definitionId,
                "recorded-assertion",
                "RESPONSE_ATTESTATION"
            )
            insertBinding(
                connection,
                secondBindingId,
                versionId,
                attestationRequirementId,
                sectionId,
                2,
                prompt,
                responseMode,
                requiredness,
                contributorRole,
                reviewPolicy,
                confidentialityCompartmentKey,
                conditionalRuleKey,
                occurrenceAnchorKey,
            )
        }
    }

    private fun withPostgres(block: (PostgreSQLContainer) -> Unit)
    {
        val postgres = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("docuhyphen_requirement_policy_test")
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

    private fun flyway(postgres: PostgreSQLContainer, target: String? = null): Flyway
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
            statement.setString(3, "policy-owner-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID, namespace: String)
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
            statement.setString(3, namespace)
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

    private fun insertSchemaVersion(
        connection: Connection,
        definitionId: UUID,
        versionId: UUID,
        organizationId: UUID,
        namespace: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO schema_definition
                (id, scope_kind, scope_org_id, namespace, schema_key, display_name,
                 target_resource_type, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, ?, 'collected-data', 'Collected data', 'INFORMATION_REQUEST',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, definitionId)
            statement.setObject(2, organizationId)
            statement.setString(3, namespace)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_version
                (id, schema_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.setObject(2, definitionId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
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

    private fun insertRequirement(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        requirementKey: String,
        requirementType: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (template_definition_id, requirement_key) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, requirementKey)
            statement.setString(4, requirementType)
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertUntypedRequirement(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        requirementKey: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, created_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, requirementKey)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    @Suppress("LongParameterList")
    private fun insertBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        displayOrder: Int,
        prompt: String = "State the recorded note",
        responseMode: String = "PROVIDE",
        requiredness: String = "REQUIRED",
        contributorRole: String = "CONTRIBUTOR",
        reviewPolicy: String = "REQUIRED",
        confidentialityCompartmentKey: String? = null,
        conditionalRuleKey: String? = null,
        occurrenceAnchorKey: String? = null,
        collectedFieldDefinitionId: UUID? = null,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, help_text, response_mode, requiredness,
                 contributor_role, review_policy, confidentiality_compartment_key,
                 conditional_rule_key, occurrence_anchor_key, collected_field_definition_id)
            SELECT ?, version.id, version.template_definition_id, ?, ?, ?, ?, 'Use the recorded wording',
                   ?, ?, ?, ?, ?, ?, ?, ?
            FROM information_request_template_version version
            WHERE version.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requirementId)
            statement.setObject(3, sectionId)
            statement.setInt(4, displayOrder)
            statement.setString(5, prompt)
            statement.setString(6, responseMode)
            statement.setString(7, requiredness)
            statement.setString(8, contributorRole)
            statement.setString(9, reviewPolicy)
            statement.setString(10, confidentialityCompartmentKey)
            statement.setString(11, conditionalRuleKey)
            statement.setString(12, occurrenceAnchorKey)
            statement.setObject(13, collectedFieldDefinitionId)
            statement.setObject(14, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertCollectableField(
        connection: Connection,
        fieldDefinitionId: UUID,
        fieldContractId: UUID,
        schemaVersionId: UUID,
        namespace: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_definition
                (id, scope_kind, namespace, field_key, status, created_at, updated_at)
            VALUES (?, 'PLATFORM', ?, 'recorded-note', 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fieldDefinitionId)
            statement.setString(2, namespace)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO field_contract
                (id, field_definition_id, contract_version, value_type, label, created_at)
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Recorded note', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fieldContractId)
            statement.setObject(2, fieldDefinitionId)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_field_binding
                (id, schema_version_id, field_contract_id, field_definition_id, display_order,
                 is_required, is_read_only, visibility)
            VALUES (?, ?, ?, ?, 1, FALSE, FALSE, 'INTERNAL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, schemaVersionId)
            statement.setObject(3, fieldContractId)
            statement.setObject(4, fieldDefinitionId)
            statement.executeUpdate()
        }
    }

    private fun insertEvidencePolicy(connection: Connection, id: UUID, bindingId: UUID, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_evidence_policy
                (id, template_binding_id, template_version_id, minimum_file_count,
                 issuer_requirement, jurisdiction_requirement, language_requirement,
                 issue_date_requirement, expiry_date_requirement, coverage_period_requirement,
                 certification_requirement, signature_requirement, coverage_continuity_required,
                 waiver_policy, conformance_policy)
            VALUES (?, ?, ?, 1, 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED',
                    'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', FALSE,
                    'NOT_PERMITTED', 'CONFORMANCE_REQUIRED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, bindingId)
            statement.setObject(3, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertDisposition(
        connection: Connection,
        id: UUID,
        bindingId: UUID,
        versionId: UUID,
        disposition: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_binding_disposition
                (id, template_binding_id, template_version_id, disposition)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, bindingId)
            statement.setObject(3, versionId)
            statement.setString(4, disposition)
            statement.executeUpdate()
        }
    }

    private fun insertEvidenceLink(
        connection: Connection,
        id: UUID,
        bindingId: UUID,
        supportingBindingId: UUID,
        versionId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_binding_evidence_link
                (id, template_binding_id, supporting_template_binding_id, template_version_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, bindingId)
            statement.setObject(3, supportingBindingId)
            statement.setObject(4, versionId)
            statement.executeUpdate()
        }
    }

    private fun publish(connection: Connection, versionId: UUID)
    {
        recordRequiredCapabilities(connection, versionId)
        connection.prepareStatement(
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, versionId)
            statement.executeUpdate()
        }
    }

    private fun recordRequiredCapabilities(connection: Connection, versionId: UUID)
    {
        if (!capabilityTableExists(connection))
        {
            return
        }

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

    private fun capabilityTableExists(connection: Connection): Boolean =
        connection.prepareStatement(
            "SELECT to_regclass('information_request_template_version_capability') IS NOT NULL",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getBoolean(1)
            }
        }

    private fun requirementType(connection: Connection, requirementId: UUID): String? =
        connection.prepareStatement(
            "SELECT requirement_type FROM information_request_template_requirement WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, requirementId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun bindingValue(connection: Connection, bindingId: UUID, column: String): String? =
        connection.prepareStatement(
            "SELECT $column FROM information_request_template_requirement_binding WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, bindingId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
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

    private fun dispositionCount(connection: Connection, bindingId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_binding_disposition WHERE template_binding_id = ?",
            bindingId,
        )

    private fun evidenceLinkCount(connection: Connection, bindingId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_binding_evidence_link WHERE template_binding_id = ?",
            bindingId,
        )

    private fun sectionCount(connection: Connection, versionId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_section WHERE template_version_id = ?",
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
}
