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

private class EvidencePolicyPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<EvidencePolicyPostgreSQLContainer>(imageName)

/**
 * A version that asks for a document also states the policy the files it collects are judged by.
 *
 * The policy is held per binding, so a later version can restate how strictly the same stable
 * requirement is judged without becoming a different requirement. It belongs only to a requested
 * document, because counts, sizes, pages, issuance, coverage, and conformance describe files and
 * nothing else. Restrictions that name accepted values are a set rather than a column, so a new
 * restrictable attribute needs no further table, and substitute evidence is a flat set of
 * alternatives that cannot be chained.
 */
class InformationRequestTemplateEvidencePolicyContractTest
{
    private val policyVersion = "87"

    @Test
    fun `only a requested document carries the evidence policy its version judges files by`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                fixture.withFieldRequirementBound(connection)
                fixture.withAttestationRequirementBound(connection)

                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)
                assertEquals("1", policyValue(connection, fixture.policyId, "minimum_file_count"))
                assertEquals("NOT_PERMITTED", policyValue(connection, fixture.policyId, "waiver_policy"))
                assertEquals(
                    "CONFORMANCE_REQUIRED",
                    policyValue(connection, fixture.policyId, "conformance_policy"),
                )

                // Counts, sizes, pages, issuance, and coverage describe files, so nothing but a
                // requested document is judged by them.
                refused(connection, "an evidence policy belongs to a requested document") {
                    insertPolicy(connection, UUID.randomUUID(), fixture.bindingId, fixture.versionId)
                }
                refused(connection, "an evidence policy belongs to a requested document") {
                    insertPolicy(connection, UUID.randomUUID(), fixture.attestationBindingId, fixture.versionId)
                }
                // One document is judged by one policy, otherwise two answers to the same question
                // are both in force.
                refused(connection, "ux_request_template_evidence_policy_binding") {
                    insertPolicy(connection, UUID.randomUUID(), fixture.documentBindingId, fixture.versionId)
                }
                // The policy belongs to the version its binding is in, so one naming another
                // version reaches no binding at all.
                fixture.withDocumentOnlyVersion(connection)
                refused(connection, "request_template_evidence_policy_binding_fkey") {
                    insertPolicy(connection, UUID.randomUUID(), fixture.otherVersionBindingId, fixture.versionId)
                }
            }
        }
    }

    @Test
    fun `evidence bounds refuse an impossible or contradictory range`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)

                refused(connection, "ck_request_template_evidence_file_count") {
                    fixture.insertPolicyWith(connection, minimumFileCount = 0)
                }
                refused(connection, "ck_request_template_evidence_file_count") {
                    fixture.insertPolicyWith(connection, minimumFileCount = 3, maximumFileCount = 2)
                }
                refused(connection, "ck_request_template_evidence_file_size") {
                    fixture.insertPolicyWith(connection, maximumFileSizeBytes = 0)
                }
                // A collection cannot be allowed less room in total than one of its files.
                refused(connection, "ck_request_template_evidence_file_size") {
                    fixture.insertPolicyWith(
                        connection,
                        maximumFileSizeBytes = 4_000_000,
                        maximumTotalSizeBytes = 1_000_000,
                    )
                }
                refused(connection, "ck_request_template_evidence_page_count") {
                    fixture.insertPolicyWith(connection, minimumPageCount = 0)
                }
                refused(connection, "ck_request_template_evidence_page_count") {
                    fixture.insertPolicyWith(connection, minimumPageCount = 4, maximumPageCount = 2)
                }

                fixture.insertPolicyWith(
                    connection,
                    minimumFileCount = 2,
                    maximumFileCount = 6,
                    maximumFileSizeBytes = 1_000_000,
                    maximumTotalSizeBytes = 4_000_000,
                    minimumPageCount = 1,
                    maximumPageCount = 40,
                )
                assertEquals("6", policyValue(connection, fixture.policyId, "maximum_file_count"))
                assertEquals("4000000", policyValue(connection, fixture.policyId, "maximum_total_size_bytes"))
                assertEquals("40", policyValue(connection, fixture.policyId, "maximum_page_count"))
            }
        }
    }

    @Test
    fun `an evidence attribute draws from one vocabulary and its bounds need the attribute captured`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)

                refused(connection, "ck_request_template_evidence_attribute_requirement") {
                    fixture.insertPolicyWith(connection, issuerRequirement = "PREFERRED")
                }
                refused(connection, "ck_request_template_evidence_attribute_requirement") {
                    fixture.insertPolicyWith(connection, languageRequirement = "SOMETIMES")
                }
                refused(connection, "ck_request_template_evidence_waiver_policy") {
                    fixture.insertPolicyWith(connection, waiverPolicy = "ON_REQUEST")
                }
                refused(connection, "ck_request_template_evidence_conformance_policy") {
                    fixture.insertPolicyWith(connection, conformancePolicy = "IGNORED")
                }

                // An age is measured from a date, a remaining validity from an expiry, and a covered
                // span from a coverage period. Bounding any of them without capturing the date they
                // are measured against leaves a rule nothing can ever evaluate.
                refused(connection, "ck_request_template_evidence_freshness") {
                    fixture.insertPolicyWith(connection, maximumIssueAgeDays = 90)
                }
                refused(connection, "ck_request_template_evidence_freshness") {
                    fixture.insertPolicyWith(
                        connection,
                        issueDateRequirement = "OPTIONAL",
                        maximumIssueAgeDays = 90,
                    )
                }
                refused(connection, "ck_request_template_evidence_validity") {
                    fixture.insertPolicyWith(connection, minimumRemainingValidityDays = 30)
                }
                refused(connection, "ck_request_template_evidence_coverage_bounds") {
                    fixture.insertPolicyWith(connection, minimumCoverageDays = 90)
                }
                refused(connection, "ck_request_template_evidence_coverage_bounds") {
                    fixture.insertPolicyWith(connection, coverageContinuityRequired = true)
                }
                refused(connection, "ck_request_template_evidence_freshness") {
                    fixture.insertPolicyWith(connection, issueDateRequirement = "REQUIRED", maximumIssueAgeDays = 0)
                }

                fixture.insertPolicyWith(
                    connection,
                    issuerRequirement = "REQUIRED",
                    jurisdictionRequirement = "REQUIRED",
                    languageRequirement = "OPTIONAL",
                    issueDateRequirement = "REQUIRED",
                    expiryDateRequirement = "REQUIRED",
                    coveragePeriodRequirement = "REQUIRED",
                    certificationRequirement = "REQUIRED",
                    signatureRequirement = "OPTIONAL",
                    maximumIssueAgeDays = 90,
                    minimumRemainingValidityDays = 0,
                    minimumCoverageDays = 90,
                    coverageContinuityRequired = true,
                    waiverPolicy = "REVIEW_APPROVAL_REQUIRED",
                    conformancePolicy = "DEFICIENCY_REVIEWABLE",
                )
                assertEquals("REQUIRED", policyValue(connection, fixture.policyId, "certification_requirement"))
                assertEquals("OPTIONAL", policyValue(connection, fixture.policyId, "signature_requirement"))
                assertEquals("90", policyValue(connection, fixture.policyId, "maximum_issue_age_days"))
                assertEquals("0", policyValue(connection, fixture.policyId, "minimum_remaining_validity_days"))
                assertTrue(
                    continuityRequired(connection, fixture.policyId),
                    "The stored policy has to keep the continuity rule it was written with",
                )
                assertEquals(
                    "DEFICIENCY_REVIEWABLE",
                    policyValue(connection, fixture.policyId, "conformance_policy"),
                )
            }
        }
    }

    @Test
    fun `accepted values restrict one attribute of one policy and never repeat`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)

                listOf(
                    "CONTENT_TYPE" to "application/pdf",
                    "CONTENT_TYPE" to "image/png",
                    "ISSUER" to "primary-record-source",
                    "JURISDICTION" to "region-a",
                    "LANGUAGE" to "en",
                ).forEach { (attribute, value) ->
                    insertAcceptedValue(connection, UUID.randomUUID(), fixture.policyId, fixture.versionId, attribute, value)
                }
                assertEquals(5, acceptedValueCount(connection, fixture.policyId))
                assertEquals(2, acceptedValueCount(connection, fixture.policyId, "CONTENT_TYPE"))

                refused(connection, "ck_request_template_evidence_accepted_attribute") {
                    insertAcceptedValue(
                        connection,
                        UUID.randomUUID(),
                        fixture.policyId,
                        fixture.versionId,
                        "PAGE_COUNT",
                        "40",
                    )
                }
                refused(connection, "ck_request_template_evidence_accepted_value") {
                    insertAcceptedValue(
                        connection,
                        UUID.randomUUID(),
                        fixture.policyId,
                        fixture.versionId,
                        "LANGUAGE",
                        "   ",
                    )
                }
                refused(connection, "ux_request_template_evidence_accepted_value") {
                    insertAcceptedValue(
                        connection,
                        UUID.randomUUID(),
                        fixture.policyId,
                        fixture.versionId,
                        "LANGUAGE",
                        "en",
                    )
                }
                // The same value under another attribute is a different restriction.
                insertAcceptedValue(
                    connection,
                    UUID.randomUUID(),
                    fixture.policyId,
                    fixture.versionId,
                    "ISSUER",
                    "en",
                )
                // A restriction belongs to the version of the policy it restricts.
                fixture.withDocumentOnlyVersion(connection)
                refused(connection, "request_template_evidence_accepted_policy_fkey") {
                    insertAcceptedValue(
                        connection,
                        UUID.randomUUID(),
                        fixture.policyId,
                        fixture.otherVersionId,
                        "LANGUAGE",
                        "fr",
                    )
                }
            }
        }
    }

    @Test
    fun `substitute evidence names another requested document of the same version and never chains`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                fixture.withAlternateDocumentRequirementBound(connection)
                fixture.withFieldRequirementBound(connection)

                // Nothing stands in for itself. This is checked before anything is stored, because
                // once a document has an alternative the flatness rule below already refuses it.
                refused(connection, "ck_request_template_substitute_distinct") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.documentBindingId,
                        fixture.documentBindingId,
                        fixture.versionId,
                    )
                }

                insertSubstitute(
                    connection,
                    UUID.randomUUID(),
                    fixture.documentBindingId,
                    fixture.alternateDocumentBindingId,
                    fixture.versionId,
                )
                assertEquals(1, substituteCount(connection, fixture.documentBindingId))

                refused(connection, "ux_request_template_substitute_pair") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.documentBindingId,
                        fixture.alternateDocumentBindingId,
                        fixture.versionId,
                    )
                }
                // Substitution replaces one requested document with another, so neither end of it
                // can be something that is not a document.
                refused(connection, "only a requested document declares substitute evidence") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.bindingId,
                        fixture.alternateDocumentBindingId,
                        fixture.versionId,
                    )
                }
                refused(connection, "substitute evidence must be a requested document") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.documentBindingId,
                        fixture.bindingId,
                        fixture.versionId,
                    )
                }
                // Alternatives are a flat set. Both directions of a chain are refused, so no
                // resolution of what may stand in for what has to walk a graph or detect a cycle.
                fixture.withThirdDocumentRequirementBound(connection)
                refused(connection, "substitute evidence cannot itself declare substitutes") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.thirdDocumentBindingId,
                        fixture.documentBindingId,
                        fixture.versionId,
                    )
                }
                refused(connection, "a requirement that is itself substitute evidence cannot declare substitutes") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.alternateDocumentBindingId,
                        fixture.thirdDocumentBindingId,
                        fixture.versionId,
                    )
                }
                // One document may have several alternatives.
                insertSubstitute(
                    connection,
                    UUID.randomUUID(),
                    fixture.documentBindingId,
                    fixture.thirdDocumentBindingId,
                    fixture.versionId,
                )
                assertEquals(2, substituteCount(connection, fixture.documentBindingId))

                fixture.withDocumentOnlyVersion(connection)
                refused(connection, "request_template_substitute_alternative_fkey") {
                    insertSubstitute(
                        connection,
                        UUID.randomUUID(),
                        fixture.documentBindingId,
                        fixture.otherVersionBindingId,
                        fixture.versionId,
                    )
                }
            }
        }
    }

    @Test
    fun `publishing a version that requests a document requires a complete and coherent policy`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)

                refused(connection, "must state the evidence policy it is judged by") {
                    publish(connection, fixture.versionId, fixture.userId)
                }

                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)
                publish(connection, fixture.versionId, fixture.userId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
            }
        }
    }

    @Test
    fun `publishing refuses an unreachable restriction and an incoherent waiver rule`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)

                // Nothing states the language, so restricting which languages are accepted is a
                // rule that can never be evaluated.
                insertAcceptedValue(
                    connection,
                    UUID.randomUUID(),
                    fixture.policyId,
                    fixture.versionId,
                    "LANGUAGE",
                    "en",
                )
                refused(connection, "cannot restrict which values are accepted") {
                    publish(connection, fixture.versionId, fixture.userId)
                }
                setPolicyColumn(connection, fixture.policyId, "language_requirement", "REQUIRED")
                publish(connection, fixture.versionId, fixture.userId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
            }
        }

        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)

                // A waiver rule that no permitted answer reaches can never take effect.
                setPolicyColumn(connection, fixture.policyId, "waiver_policy", "RESPONDENT_DECLARED")
                refused(connection, "requires the requirement to permit a waived answer") {
                    publish(connection, fixture.versionId, fixture.userId)
                }

                insertDisposition(
                    connection,
                    UUID.randomUUID(),
                    fixture.documentBindingId,
                    fixture.versionId,
                    "WAIVED",
                )
                publish(connection, fixture.versionId, fixture.userId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
            }
        }

        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)

                // The permitted answer reaches a waiver the evidence policy forbids, so nothing
                // governs how it may be given.
                insertDisposition(
                    connection,
                    UUID.randomUUID(),
                    fixture.documentBindingId,
                    fixture.versionId,
                    "WAIVED",
                )
                refused(connection, "must state the evidence waiver rule that reaches it") {
                    publish(connection, fixture.versionId, fixture.userId)
                }
            }
        }
    }

    @Test
    fun `a published version freezes its evidence policy, accepted values, and substitutes`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                fixture.withAlternateDocumentRequirementBound(connection)
                insertPolicy(connection, fixture.policyId, fixture.documentBindingId, fixture.versionId)
                insertPolicy(
                    connection,
                    fixture.alternatePolicyId,
                    fixture.alternateDocumentBindingId,
                    fixture.versionId,
                )

                val acceptedValueId = UUID.randomUUID()
                val substituteId = UUID.randomUUID()
                insertAcceptedValue(
                    connection,
                    acceptedValueId,
                    fixture.policyId,
                    fixture.versionId,
                    "CONTENT_TYPE",
                    "application/pdf",
                )
                insertSubstitute(
                    connection,
                    substituteId,
                    fixture.documentBindingId,
                    fixture.alternateDocumentBindingId,
                    fixture.versionId,
                )

                publish(connection, fixture.versionId, fixture.userId)

                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "UPDATE information_request_template_evidence_policy SET minimum_file_count = 3 " +
                            "WHERE id = ?",
                        fixture.policyId,
                    )
                }
                refused(connection, "immutable") {
                    insertAcceptedValue(
                        connection,
                        UUID.randomUUID(),
                        fixture.policyId,
                        fixture.versionId,
                        "CONTENT_TYPE",
                        "image/png",
                    )
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "DELETE FROM information_request_template_evidence_accepted_value WHERE id = ?",
                        acceptedValueId,
                    )
                }
                refused(connection, "immutable") {
                    updateStatement(
                        connection,
                        "DELETE FROM information_request_template_binding_substitute WHERE id = ?",
                        substituteId,
                    )
                }

                assertEquals("1", policyValue(connection, fixture.policyId, "minimum_file_count"))
                assertEquals(1, acceptedValueCount(connection, fixture.policyId))
                assertEquals(1, substituteCount(connection, fixture.documentBindingId))
            }
        }
    }

    @Test
    fun `an upgrade carries a released published version across and applies the policy rule onwards`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = policyVersion).migrate()

            val fixture: Fixture
            postgres.createConnection("").use { connection ->
                fixture = Fixture(connection)
                fixture.withDocumentRequirementBound(connection)
                publish(connection, fixture.versionId, fixture.userId)
                fixture.withDocumentOnlyVersion(connection)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                // A version published before the rule existed keeps its published state. The rule
                // decides publications from here on, not history it was never applied to.
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
                assertEquals(0, policyCount(connection, fixture.documentBindingId))

                refused(connection, "must state the evidence policy it is judged by") {
                    publish(connection, fixture.otherVersionId, fixture.userId)
                }
                // The frozen version stays frozen, so the policy it never had cannot be added now.
                refused(connection, "immutable") {
                    insertPolicy(connection, UUID.randomUUID(), fixture.documentBindingId, fixture.versionId)
                }
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
        val fieldRequirementId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val attestationRequirementId: UUID = UUID.randomUUID()
        val documentRequirementId: UUID = UUID.randomUUID()
        val alternateDocumentRequirementId: UUID = UUID.randomUUID()
        val thirdDocumentRequirementId: UUID = UUID.randomUUID()
        val bindingId: UUID = UUID.randomUUID()
        val attestationBindingId: UUID = UUID.randomUUID()
        val documentBindingId: UUID = UUID.randomUUID()
        val alternateDocumentBindingId: UUID = UUID.randomUUID()
        val thirdDocumentBindingId: UUID = UUID.randomUUID()
        val otherVersionBindingId: UUID = UUID.randomUUID()
        val policyId: UUID = UUID.randomUUID()
        val alternatePolicyId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertUser(connection, userId)
            insertDefinition(connection, definitionId, organizationId, namespace)
            insertVersion(connection, versionId, definitionId, 1)
            insertVersion(connection, otherVersionId, definitionId, 2)
            insertSection(connection, sectionId, versionId, "collected-data", 1)
        }

        fun withDocumentRequirementBound(connection: Connection)
        {
            insertRequirement(connection, documentRequirementId, definitionId, "supporting-record", "DOCUMENT")
            insertBinding(connection, documentBindingId, versionId, documentRequirementId, sectionId, 1)
        }

        fun withAlternateDocumentRequirementBound(connection: Connection)
        {
            insertRequirement(connection, alternateDocumentRequirementId, definitionId, "alternate-record", "DOCUMENT")
            insertBinding(connection, alternateDocumentBindingId, versionId, alternateDocumentRequirementId, sectionId, 2)
        }

        fun withThirdDocumentRequirementBound(connection: Connection)
        {
            insertRequirement(connection, thirdDocumentRequirementId, definitionId, "further-record", "DOCUMENT")
            insertBinding(connection, thirdDocumentBindingId, versionId, thirdDocumentRequirementId, sectionId, 4)
        }

        fun withFieldRequirementBound(connection: Connection)
        {
            insertFieldDefinition(connection, fieldDefinitionId, namespace)
            insertRequirement(connection, fieldRequirementId, definitionId, "recorded-note", "FIELD")
            insertBinding(
                connection, bindingId, versionId, fieldRequirementId, sectionId, 5,
                collectedFieldDefinitionId = fieldDefinitionId,
            )
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
            insertBinding(connection, attestationBindingId, versionId, attestationRequirementId, sectionId, 6)
        }

        /** A second version of the same definition that asks for its own copy of the document. */
        fun withDocumentOnlyVersion(connection: Connection)
        {
            insertSection(connection, otherSectionId, otherVersionId, "supporting-data", 1)
            insertRequirement(connection, documentRequirementId, definitionId, "supporting-record", "DOCUMENT")
            insertBinding(connection, otherVersionBindingId, otherVersionId, documentRequirementId, otherSectionId, 1)
        }

        @Suppress("LongParameterList")
        fun insertPolicyWith(
            connection: Connection,
            minimumFileCount: Int = 1,
            maximumFileCount: Int? = null,
            maximumFileSizeBytes: Long? = null,
            maximumTotalSizeBytes: Long? = null,
            minimumPageCount: Int? = null,
            maximumPageCount: Int? = null,
            issuerRequirement: String = "NOT_CAPTURED",
            jurisdictionRequirement: String = "NOT_CAPTURED",
            languageRequirement: String = "NOT_CAPTURED",
            issueDateRequirement: String = "NOT_CAPTURED",
            expiryDateRequirement: String = "NOT_CAPTURED",
            coveragePeriodRequirement: String = "NOT_CAPTURED",
            certificationRequirement: String = "NOT_CAPTURED",
            signatureRequirement: String = "NOT_CAPTURED",
            maximumIssueAgeDays: Int? = null,
            minimumRemainingValidityDays: Int? = null,
            minimumCoverageDays: Int? = null,
            coverageContinuityRequired: Boolean = false,
            waiverPolicy: String = "NOT_PERMITTED",
            conformancePolicy: String = "CONFORMANCE_REQUIRED",
        )
        {
            insertPolicy(
                connection,
                policyId,
                documentBindingId,
                versionId,
                minimumFileCount,
                maximumFileCount,
                maximumFileSizeBytes,
                maximumTotalSizeBytes,
                minimumPageCount,
                maximumPageCount,
                issuerRequirement,
                jurisdictionRequirement,
                languageRequirement,
                issueDateRequirement,
                expiryDateRequirement,
                coveragePeriodRequirement,
                certificationRequirement,
                signatureRequirement,
                maximumIssueAgeDays,
                minimumRemainingValidityDays,
                minimumCoverageDays,
                coverageContinuityRequired,
                waiverPolicy,
                conformancePolicy,
            )
        }
    }

    private fun withPostgres(block: (EvidencePolicyPostgreSQLContainer) -> Unit)
    {
        val postgres = EvidencePolicyPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_evidence_policy_test")
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

    private fun flyway(postgres: EvidencePolicyPostgreSQLContainer, target: String? = null): Flyway
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
            statement.setString(3, "evidence-owner-${id.toString().take(8)}@process.test")
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

    private fun insertBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        displayOrder: Int,
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
                 contributor_role, review_policy$column)
            SELECT ?, version.id, version.template_definition_id, ?, ?, ?,
                   'Supply the supporting record', 'PROVIDE', 'REQUIRED', 'CONTRIBUTOR', 'REQUIRED'$value
            FROM information_request_template_version version
            WHERE version.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requirementId)
            statement.setObject(3, sectionId)
            statement.setInt(4, displayOrder)
            if (collectable)
            {
                statement.setObject(5, collectedFieldDefinitionId)
                statement.setObject(6, versionId)
            }
            else
            {
                statement.setObject(5, versionId)
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

    /** The stable field a typed requirement in this fixture collects. */
    private fun insertFieldDefinition(connection: Connection, id: UUID, namespace: String)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_definition
                (id, scope_kind, namespace, field_key, status, created_at, updated_at)
            VALUES (?, 'PLATFORM', ?, 'recorded-note', 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, namespace)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }

    @Suppress("LongParameterList")
    private fun insertPolicy(
        connection: Connection,
        id: UUID,
        bindingId: UUID,
        versionId: UUID,
        minimumFileCount: Int = 1,
        maximumFileCount: Int? = null,
        maximumFileSizeBytes: Long? = null,
        maximumTotalSizeBytes: Long? = null,
        minimumPageCount: Int? = null,
        maximumPageCount: Int? = null,
        issuerRequirement: String = "NOT_CAPTURED",
        jurisdictionRequirement: String = "NOT_CAPTURED",
        languageRequirement: String = "NOT_CAPTURED",
        issueDateRequirement: String = "NOT_CAPTURED",
        expiryDateRequirement: String = "NOT_CAPTURED",
        coveragePeriodRequirement: String = "NOT_CAPTURED",
        certificationRequirement: String = "NOT_CAPTURED",
        signatureRequirement: String = "NOT_CAPTURED",
        maximumIssueAgeDays: Int? = null,
        minimumRemainingValidityDays: Int? = null,
        minimumCoverageDays: Int? = null,
        coverageContinuityRequired: Boolean = false,
        waiverPolicy: String = "NOT_PERMITTED",
        conformancePolicy: String = "CONFORMANCE_REQUIRED",
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_evidence_policy
                (id, template_binding_id, template_version_id, minimum_file_count,
                 maximum_file_count, maximum_file_size_bytes, maximum_total_size_bytes,
                 minimum_page_count, maximum_page_count, issuer_requirement,
                 jurisdiction_requirement, language_requirement, issue_date_requirement,
                 expiry_date_requirement, coverage_period_requirement, certification_requirement,
                 signature_requirement, maximum_issue_age_days, minimum_remaining_validity_days,
                 minimum_coverage_days, coverage_continuity_required, waiver_policy,
                 conformance_policy)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, bindingId)
            statement.setObject(3, versionId)
            statement.setInt(4, minimumFileCount)
            statement.setObject(5, maximumFileCount)
            statement.setObject(6, maximumFileSizeBytes)
            statement.setObject(7, maximumTotalSizeBytes)
            statement.setObject(8, minimumPageCount)
            statement.setObject(9, maximumPageCount)
            statement.setString(10, issuerRequirement)
            statement.setString(11, jurisdictionRequirement)
            statement.setString(12, languageRequirement)
            statement.setString(13, issueDateRequirement)
            statement.setString(14, expiryDateRequirement)
            statement.setString(15, coveragePeriodRequirement)
            statement.setString(16, certificationRequirement)
            statement.setString(17, signatureRequirement)
            statement.setObject(18, maximumIssueAgeDays)
            statement.setObject(19, minimumRemainingValidityDays)
            statement.setObject(20, minimumCoverageDays)
            statement.setBoolean(21, coverageContinuityRequired)
            statement.setString(22, waiverPolicy)
            statement.setString(23, conformancePolicy)
            statement.executeUpdate()
        }
    }

    private fun insertAcceptedValue(
        connection: Connection,
        id: UUID,
        policyId: UUID,
        versionId: UUID,
        attribute: String,
        acceptedValue: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_evidence_accepted_value
                (id, evidence_policy_id, template_version_id, attribute, accepted_value)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, policyId)
            statement.setObject(3, versionId)
            statement.setString(4, attribute)
            statement.setString(5, acceptedValue)
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
        // A baseline that predates the rule has neither the table nor anything to record in it.
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

    private fun setPolicyColumn(connection: Connection, policyId: UUID, column: String, value: String)
    {
        connection.prepareStatement(
            "UPDATE information_request_template_evidence_policy SET $column = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, value)
            statement.setObject(2, policyId)
            statement.executeUpdate()
        }
    }

    private fun policyValue(connection: Connection, policyId: UUID, column: String): String? =
        connection.prepareStatement(
            "SELECT $column FROM information_request_template_evidence_policy WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, policyId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun continuityRequired(connection: Connection, policyId: UUID): Boolean =
        connection.prepareStatement(
            "SELECT coverage_continuity_required FROM information_request_template_evidence_policy WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, policyId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getBoolean(1)
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

    private fun policyCount(connection: Connection, bindingId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_evidence_policy WHERE template_binding_id = ?",
            bindingId,
        )

    private fun acceptedValueCount(connection: Connection, policyId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_evidence_accepted_value " +
                "WHERE evidence_policy_id = ?",
            policyId,
        )

    private fun acceptedValueCount(connection: Connection, policyId: UUID, attribute: String): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM information_request_template_evidence_accepted_value " +
                "WHERE evidence_policy_id = ? AND attribute = ?",
        ).use { statement ->
            statement.setObject(1, policyId)
            statement.setString(2, attribute)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun substituteCount(connection: Connection, bindingId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_template_binding_substitute WHERE template_binding_id = ?",
            bindingId,
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
