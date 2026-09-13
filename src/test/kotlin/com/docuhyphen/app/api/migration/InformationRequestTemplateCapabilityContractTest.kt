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

private class CapabilityPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<CapabilityPostgreSQLContainer>(imageName)

/**
 * A version states which runtime capabilities the configuration it froze needs somebody to supply.
 *
 * Which ones those are is not a free choice: they follow from what the version actually configures,
 * so the derivation is held in one place and publication refuses a recorded set that disagrees with
 * it in either direction. Which contract version of each is needed is a separate fact recorded
 * beside the key, because whether an installed runtime can serve it is answered outside the
 * database, at issuance rather than at publication.
 */
class InformationRequestTemplateCapabilityContractTest
{
    private val baselineVersion = "88"

    private val everyCapability = setOf(
        "STRUCTURED_RESPONSE",
        "DOCUMENT_EVIDENCE",
        "RESPONSE_ATTESTATION",
        "CONDITIONAL_REQUIREMENT",
        "REPEATABLE_OCCURRENCE",
        "RESPONSE_REVIEW",
        "CONFIDENTIALITY_COMPARTMENT",
        "EVIDENCE_WAIVER",
        "SUBSTITUTE_EVIDENCE",
        "SUPPORTING_EVIDENCE",
        "RESPONSE_SUBMISSION",
    )

    @Test
    fun `a version records one contract version of each runtime capability it needs`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withAttestationRequirementBound(connection)

                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "RESPONSE_SUBMISSION", 1)
                assertEquals(1, capabilityCount(connection, fixture.versionId))
                assertEquals(
                    1,
                    recordedContractVersion(connection, fixture.versionId, "RESPONSE_SUBMISSION"),
                )

                // A capability nobody declared cannot be required, because nothing could ever be
                // installed to answer it.
                refused(connection, "ck_request_template_capability_key") {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "UNDECLARED_CAPABILITY",
                        1,
                    )
                }
                // One capability is needed at one contract version. Two would both be required and
                // an installed runtime could satisfy one while failing the other.
                refused(connection, "ux_request_template_capability_version_key") {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "RESPONSE_SUBMISSION",
                        2,
                    )
                }
                refused(connection, "ck_request_template_capability_contract_version") {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "RESPONSE_ATTESTATION",
                        0,
                    )
                }
                // A version that does not exist is not a draft either, so the freeze guard turns it
                // away before the foreign key behind it is ever consulted.
                refused(connection, "immutable") {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RESPONSE_SUBMISSION",
                        1,
                    )
                }
            }
        }
    }

    @Test
    fun `what a version needs follows from what it configures rather than from what an author claims`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                // Nothing configured needs nothing served.
                assertEquals(emptySet<String>(), derivedCapabilities(connection, fixture.versionId))

                // The smallest thing a version can ask for: one assertion, collected under no
                // further policy. It needs the runtime that asks for one and the runtime that
                // submits the result, and nothing else.
                fixture.withAttestationRequirementBound(connection)
                assertEquals(
                    setOf("RESPONSE_ATTESTATION", "RESPONSE_SUBMISSION"),
                    derivedCapabilities(connection, fixture.versionId),
                )

                // A materially different pattern: typed data and documents, asked conditionally,
                // once per occurrence, in a named compartment, reviewed, waivable, with substitutes
                // and supporting evidence. Every one of those is a capability somebody has to
                // supply at runtime, and each is derived from the configured fact that needs it.
                fixture.withFieldRequirementBound(connection)
                assertEquals(
                    setOf(
                        "STRUCTURED_RESPONSE",
                        "RESPONSE_ATTESTATION",
                        "CONDITIONAL_REQUIREMENT",
                        "REPEATABLE_OCCURRENCE",
                        "CONFIDENTIALITY_COMPARTMENT",
                        "RESPONSE_REVIEW",
                        "RESPONSE_SUBMISSION",
                    ),
                    derivedCapabilities(connection, fixture.versionId),
                )

                fixture.withDocumentRequirementBound(connection)
                fixture.withAlternateDocumentRequirementBound(connection)
                fixture.withWaivableEvidencePolicy(connection)
                fixture.withSubstituteEvidence(connection)
                fixture.withSupportingEvidence(connection)
                assertEquals(everyCapability, derivedCapabilities(connection, fixture.versionId))

                // A second version of the same definition derives its own set, so the derivation
                // reads one version's configuration rather than the definition's.
                fixture.withAttestationOnlyVersion(connection)
                assertEquals(
                    setOf("RESPONSE_ATTESTATION", "RESPONSE_SUBMISSION"),
                    derivedCapabilities(connection, fixture.otherVersionId),
                )
            }
        }
    }

    @Test
    fun `a version cannot freeze until it has recorded every capability it needs`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withAttestationRequirementBound(connection)
                fixture.withDocumentRequirementBound(connection)
                fixture.withEvidencePolicy(connection)

                // Publication is the last point at which the answer can still be supplied, and an
                // unrecorded capability is indistinguishable from one nobody thought about.
                refused(connection, "must record every runtime capability its configuration requires") {
                    publish(connection, fixture.versionId, fixture.userId)
                }

                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "DOCUMENT_EVIDENCE", 1)
                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "RESPONSE_SUBMISSION", 1)
                refused(connection, "must record every runtime capability its configuration requires") {
                    publish(connection, fixture.versionId, fixture.userId)
                }

                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "RESPONSE_ATTESTATION", 1)
                publish(connection, fixture.versionId, fixture.userId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
                assertEquals(3, capabilityCount(connection, fixture.versionId))
            }
        }
    }

    @Test
    fun `a version cannot require a capability its configuration never asked for`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withAttestationRequirementBound(connection)

                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "RESPONSE_ATTESTATION", 1)
                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "RESPONSE_SUBMISSION", 1)
                // A requirement nothing configured would refuse issuance for a reason no author
                // could act on, since there is no configuration to remove.
                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "DOCUMENT_EVIDENCE", 1)

                refused(connection, "cannot require a runtime capability its configuration does not use") {
                    publish(connection, fixture.versionId, fixture.userId)
                }
            }
        }
    }

    @Test
    fun `recording and freezing in one transaction is how a publishing service has to do it`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withAttestationRequirementBound(connection)

                // Nothing may be recorded once the version has left draft, so a service cannot
                // publish first and complete afterwards. It has to do both in one transaction, and
                // the completeness check has to see rows its own transaction has not committed yet.
                connection.autoCommit = false
                try
                {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "RESPONSE_ATTESTATION",
                        1,
                    )
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "RESPONSE_SUBMISSION",
                        1,
                    )
                    publish(connection, fixture.versionId, fixture.userId)
                    connection.commit()
                }
                finally
                {
                    connection.autoCommit = true
                }

                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
                assertEquals(2, capabilityCount(connection, fixture.versionId))
            }
        }
    }

    @Test
    fun `recorded capabilities freeze with the version that recorded them`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withAttestationRequirementBound(connection)

                val attestationId = UUID.randomUUID()
                insertCapability(connection, attestationId, fixture.versionId, "RESPONSE_ATTESTATION", 1)
                insertCapability(connection, UUID.randomUUID(), fixture.versionId, "RESPONSE_SUBMISSION", 1)
                publish(connection, fixture.versionId, fixture.userId)

                // What a request was issued against has to keep resolving to the same answer, so
                // the set a published version recorded stops changing with it.
                refused(connection, "immutable") {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "DOCUMENT_EVIDENCE",
                        1,
                    )
                }
                refused(connection, "immutable") {
                    updateContractVersion(connection, attestationId, 2)
                }
                refused(connection, "immutable") {
                    deleteCapability(connection, attestationId)
                }
                assertEquals(2, capabilityCount(connection, fixture.versionId))
            }
        }
    }

    @Test
    fun `an upgrade carries a released published version across and applies the rule onwards`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = baselineVersion).migrate()

            val fixture: Fixture
            postgres.createConnection("").use { connection ->
                fixture = Fixture(connection)
                fixture.withAttestationRequirementBound(connection)
                publish(connection, fixture.versionId, fixture.userId)
                fixture.withAttestationOnlyVersion(connection)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                // A version published before the rule existed keeps its published state and its
                // empty set. The rule decides publications from here on, not history it was never
                // applied to.
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
                assertEquals(0, capabilityCount(connection, fixture.versionId))

                refused(connection, "must record every runtime capability its configuration requires") {
                    publish(connection, fixture.otherVersionId, fixture.userId)
                }
                // The frozen version stays frozen, so the set it never recorded cannot be recorded
                // now.
                refused(connection, "immutable") {
                    insertCapability(
                        connection,
                        UUID.randomUUID(),
                        fixture.versionId,
                        "RESPONSE_SUBMISSION",
                        1,
                    )
                }

                // Retirement is still the one fact recordable about it.
                retire(connection, fixture.versionId, fixture.userId)
                assertEquals("RETIRED", versionStatus(connection, fixture.versionId))
            }
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
        val attestationRequirementId: UUID = UUID.randomUUID()
        val fieldRequirementId: UUID = UUID.randomUUID()
        val documentRequirementId: UUID = UUID.randomUUID()
        val alternateDocumentRequirementId: UUID = UUID.randomUUID()
        val attestationBindingId: UUID = UUID.randomUUID()
        val fieldBindingId: UUID = UUID.randomUUID()
        val documentBindingId: UUID = UUID.randomUUID()
        val alternateDocumentBindingId: UUID = UUID.randomUUID()
        val otherVersionBindingId: UUID = UUID.randomUUID()
        val policyId: UUID = UUID.randomUUID()
        val alternatePolicyId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val fieldContractId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertUser(connection, userId)
            insertSchemaVersion(connection, schemaDefinitionId, schemaVersionId, organizationId, namespace)
            insertCollectableField(connection, fieldDefinitionId, fieldContractId, schemaVersionId, namespace)
            insertDefinition(connection, definitionId, organizationId, namespace)
            insertVersion(connection, versionId, definitionId, 1)
            insertVersion(connection, otherVersionId, definitionId, 2)
            insertSection(connection, sectionId, versionId, "collected-data", 1)
        }

        /** An assertion asked for plainly: no condition, no occurrence, no compartment, no review. */
        fun withAttestationRequirementBound(connection: Connection)
        {
            insertRequirement(
                connection,
                attestationRequirementId,
                definitionId,
                "recorded-assertion",
                "RESPONSE_ATTESTATION",
            )
            insertBinding(
                connection,
                attestationBindingId,
                versionId,
                attestationRequirementId,
                sectionId,
                1,
                reviewPolicy = "NOT_REQUIRED",
            )
        }

        /** Typed data asked for under every optional policy a binding can carry. */
        fun withFieldRequirementBound(connection: Connection)
        {
            insertRequirement(connection, fieldRequirementId, definitionId, "recorded-note", "FIELD")
            insertBinding(
                connection,
                fieldBindingId,
                versionId,
                fieldRequirementId,
                sectionId,
                2,
                requiredness = "CONDITIONAL",
                reviewPolicy = "REQUIRED",
                confidentialityCompartmentKey = "restricted-set",
                conditionalRuleKey = "when-note-applies",
                occurrenceAnchorKey = "per-recorded-item",
                collectedFieldDefinitionId = fieldDefinitionId,
            )
            setVersionSchemaVersion(connection, versionId, schemaVersionId)
        }

        fun withDocumentRequirementBound(connection: Connection)
        {
            insertRequirement(connection, documentRequirementId, definitionId, "supporting-record", "DOCUMENT")
            insertBinding(
                connection,
                documentBindingId,
                versionId,
                documentRequirementId,
                sectionId,
                3,
                reviewPolicy = "NOT_REQUIRED",
            )
        }

        fun withAlternateDocumentRequirementBound(connection: Connection)
        {
            insertRequirement(
                connection,
                alternateDocumentRequirementId,
                definitionId,
                "alternate-record",
                "DOCUMENT",
            )
            insertBinding(
                connection,
                alternateDocumentBindingId,
                versionId,
                alternateDocumentRequirementId,
                sectionId,
                4,
                reviewPolicy = "NOT_REQUIRED",
            )
        }

        /** A document judged by a policy that restricts nothing and permits no waiver. */
        fun withEvidencePolicy(connection: Connection)
        {
            insertPolicy(connection, policyId, documentBindingId, versionId)
        }

        /** The same document, resolvable without the evidence once a reviewer approves. */
        fun withWaivableEvidencePolicy(connection: Connection)
        {
            insertPolicy(
                connection,
                policyId,
                documentBindingId,
                versionId,
                waiverPolicy = "REVIEW_APPROVAL_REQUIRED",
            )
            insertDisposition(connection, UUID.randomUUID(), documentBindingId, versionId, "WAIVED")
            insertPolicy(connection, alternatePolicyId, alternateDocumentBindingId, versionId)
        }

        fun withSubstituteEvidence(connection: Connection)
        {
            insertSubstitute(
                connection,
                UUID.randomUUID(),
                documentBindingId,
                alternateDocumentBindingId,
                versionId,
            )
        }

        fun withSupportingEvidence(connection: Connection)
        {
            insertEvidenceLink(
                connection,
                UUID.randomUUID(),
                attestationBindingId,
                documentBindingId,
                versionId,
            )
        }

        /** A second version of the same definition, asking only for the assertion. */
        fun withAttestationOnlyVersion(connection: Connection)
        {
            insertSection(connection, otherSectionId, otherVersionId, "supporting-data", 1)
            insertRequirement(
                connection,
                attestationRequirementId,
                definitionId,
                "recorded-assertion",
                "RESPONSE_ATTESTATION",
            )
            insertBinding(
                connection,
                otherVersionBindingId,
                otherVersionId,
                attestationRequirementId,
                otherSectionId,
                1,
                reviewPolicy = "NOT_REQUIRED",
            )
        }
    }

    private fun withPostgres(block: (CapabilityPostgreSQLContainer) -> Unit)
    {
        val postgres = CapabilityPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_capability_test")
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

    private fun flyway(postgres: CapabilityPostgreSQLContainer, target: String? = null): Flyway
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
            statement.setString(3, "capability-owner-${id.toString().take(8)}@process.test")
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

    /** Typed data resolves against a schema version, which publication requires it to name. */
    private fun setVersionSchemaVersion(connection: Connection, versionId: UUID, schemaVersionId: UUID)
    {
        connection.prepareStatement(
            "UPDATE information_request_template_version SET schema_version_id = ? WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, schemaVersionId)
            statement.setObject(2, versionId)
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

    @Suppress("LongParameterList")
    private fun insertBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        displayOrder: Int,
        requiredness: String = "REQUIRED",
        reviewPolicy: String = "NOT_REQUIRED",
        confidentialityCompartmentKey: String? = null,
        conditionalRuleKey: String? = null,
        occurrenceAnchorKey: String? = null,
        collectedFieldDefinitionId: UUID? = null,
    )
    {
        // The collected field arrived after this baseline, so a version of the schema that predates
        // it inserts the same row without naming one.
        val collectable = collectedFieldColumnExists(connection)
        val column = if (collectable) ", collected_field_definition_id" else ""
        val value = if (collectable) ", ?" else ""
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy, confidentiality_compartment_key,
                 conditional_rule_key, occurrence_anchor_key$column)
            SELECT ?, version.id, version.template_definition_id, ?, ?, ?,
                   'Supply the supporting record', 'PROVIDE', ?, 'CONTRIBUTOR', ?, ?, ?, ?$value
            FROM information_request_template_version version
            WHERE version.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requirementId)
            statement.setObject(3, sectionId)
            statement.setInt(4, displayOrder)
            statement.setString(5, requiredness)
            statement.setString(6, reviewPolicy)
            statement.setString(7, confidentialityCompartmentKey)
            statement.setString(8, conditionalRuleKey)
            statement.setString(9, occurrenceAnchorKey)
            if (collectable)
            {
                statement.setObject(10, collectedFieldDefinitionId)
                statement.setObject(11, versionId)
            }
            else
            {
                statement.setObject(10, versionId)
            }
            statement.executeUpdate()
        }
    }

    private fun collectedFieldColumnExists(connection: Connection): Boolean =
        connection.prepareStatement(
            """
            SELECT EXISTS (SELECT 1
                           FROM information_schema.columns
                           WHERE table_name = 'information_request_template_requirement_binding'
                             AND column_name = 'collected_field_definition_id')
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getBoolean(1)
            }
        }

    /**
     * One stable field, its contract, and the schema version binding that carries it. A typed
     * requirement names a field it collects, and publication asks whether the version's own contract
     * carries that field.
     */
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

    private fun insertPolicy(
        connection: Connection,
        id: UUID,
        bindingId: UUID,
        versionId: UUID,
        waiverPolicy: String = "NOT_PERMITTED",
    )
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
                    'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', FALSE, ?,
                    'CONFORMANCE_REQUIRED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, bindingId)
            statement.setObject(3, versionId)
            statement.setString(4, waiverPolicy)
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

    private fun insertSubstitute(
        connection: Connection,
        id: UUID,
        bindingId: UUID,
        substituteBindingId: UUID,
        versionId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_binding_substitute
                (id, template_binding_id, substitute_template_binding_id, template_version_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, bindingId)
            statement.setObject(3, substituteBindingId)
            statement.setObject(4, versionId)
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

    private fun insertCapability(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        capabilityKey: String,
        requiredContractVersion: Int,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setString(3, capabilityKey)
            statement.setInt(4, requiredContractVersion)
            statement.executeUpdate()
        }
    }

    private fun updateContractVersion(connection: Connection, id: UUID, requiredContractVersion: Int)
    {
        connection.prepareStatement(
            "UPDATE information_request_template_version_capability SET required_contract_version = ? WHERE id = ?",
        ).use { statement ->
            statement.setInt(1, requiredContractVersion)
            statement.setObject(2, id)
            statement.executeUpdate()
        }
    }

    private fun deleteCapability(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            "DELETE FROM information_request_template_version_capability WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, id)
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

    private fun derivedCapabilities(connection: Connection, versionId: UUID): Set<String> =
        connection.prepareStatement(
            "SELECT capability_key FROM request_template_required_capabilities(?)",
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                val derived = mutableSetOf<String>()
                while (rows.next())
                {
                    derived.add(rows.getString(1))
                }
                derived
            }
        }

    private fun capabilityCount(connection: Connection, versionId: UUID): Int =
        connection.prepareStatement(
            """
            SELECT COUNT(*) FROM information_request_template_version_capability
            WHERE template_version_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun recordedContractVersion(connection: Connection, versionId: UUID, capabilityKey: String): Int =
        connection.prepareStatement(
            """
            SELECT required_contract_version FROM information_request_template_version_capability
            WHERE template_version_id = ? AND capability_key = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.setString(2, capabilityKey)
            statement.executeQuery().use { rows ->
                check(rows.next())
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
}
