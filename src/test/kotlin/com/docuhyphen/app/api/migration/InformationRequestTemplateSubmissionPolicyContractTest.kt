package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestTemplateSubmissionPolicyContractTest
{
    @Test
    fun `a version states its submission mode and stage ordering and keeps them once published`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                assertEquals("WHOLE_PACKAGE", versionValue(connection, fixture.versionId, "submission_mode"))
                assertEquals("ANY_ORDER", versionValue(connection, fixture.versionId, "submission_stage_ordering"))

                refusedBy(connection, "ck_request_template_version_submission_mode") {
                    setVersion(connection, fixture.versionId, "submission_mode", "PARTIAL")
                }
                refusedBy(connection, "ck_request_template_version_stage_ordering") {
                    setVersion(connection, fixture.versionId, "submission_stage_ordering", "SEQUENTIAL")
                }

                val attestationId = UUID.randomUUID()
                val bindingId = UUID.randomUUID()
                fixture.insertRequirement(attestationId, "recorded-assertion", "RESPONSE_ATTESTATION")
                fixture.insertBinding(bindingId, fixture.versionId, attestationId, fixture.sectionId, 1, "ATTESTOR")
                fixture.recordDerivedCapabilities(fixture.versionId)
                fixture.publish(fixture.versionId)

                refusedBy(connection, "immutable except for retirement") {
                    setVersion(connection, fixture.versionId, "submission_mode", "STAGED")
                }

                // An assertion frozen without a stated policy freezes with the default one.
                assertEquals(
                    "ANY_ORDER|1|VERIFIED_CONTACT|NOT_ACCEPTED|ATTESTOR|1",
                    statedPolicy(connection, bindingId),
                )
            }
        }
    }

    @Test
    fun `a staged version names a stage on every section and a whole-package version names none`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                val secondSectionId = UUID.randomUUID()
                fixture.insertSection(secondSectionId, fixture.versionId, "confirmations", 2, "confirmation-stage")
                val attestationId = UUID.randomUUID()
                fixture.insertRequirement(attestationId, "recorded-assertion", "RESPONSE_ATTESTATION")
                fixture.insertBinding(UUID.randomUUID(), fixture.versionId, attestationId, secondSectionId, 1, "ATTESTOR")
                fixture.recordDerivedCapabilities(fixture.versionId)

                refusedBy(connection, "ck_request_template_section_stage_key") {
                    execute(
                        connection,
                        "UPDATE information_request_template_section SET submission_stage_key = '  ' WHERE id = ?",
                        fixture.sectionId,
                    )
                }
                refusedBy(connection, "a whole-package version cannot name a submission stage") {
                    fixture.publish(fixture.versionId)
                }

                setVersion(connection, fixture.versionId, "submission_mode", "STAGED")
                setVersion(connection, fixture.versionId, "submission_stage_ordering", "SEQUENTIAL")
                refusedBy(connection, "a staged version must name the submission stage of every section") {
                    fixture.publish(fixture.versionId)
                }

                execute(
                    connection,
                    "UPDATE information_request_template_section SET submission_stage_key = 'record-stage' WHERE id = ?",
                    fixture.sectionId,
                )
                fixture.publish(fixture.versionId)
                assertEquals("PUBLISHED", versionValue(connection, fixture.versionId, "status"))
                refusedBy(connection, "immutable") {
                    execute(
                        connection,
                        "UPDATE information_request_template_section SET submission_stage_key = 'other-stage' WHERE id = ?",
                        fixture.sectionId,
                    )
                }
            }
        }
    }

    @Test
    fun `an attestation policy belongs to an assertion and states the roles that must make it`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                val attestationId = UUID.randomUUID()
                val attestationBindingId = UUID.randomUUID()
                val documentId = UUID.randomUUID()
                val documentBindingId = UUID.randomUUID()
                fixture.insertRequirement(attestationId, "recorded-assertion", "RESPONSE_ATTESTATION")
                fixture.insertBinding(attestationBindingId, fixture.versionId, attestationId, fixture.sectionId, 1, "ATTESTOR")
                fixture.insertRequirement(documentId, "supporting-record", "DOCUMENT")
                fixture.insertBinding(documentBindingId, fixture.versionId, documentId, fixture.sectionId, 2)
                fixture.insertEvidencePolicy(UUID.randomUUID(), documentBindingId, fixture.versionId)

                refusedBy(connection, "only an assertion states an attestation policy") {
                    fixture.insertAttestationPolicy(UUID.randomUUID(), documentBindingId, fixture.versionId)
                }
                refusedBy(connection, "ck_request_template_attestation_policy_assents") {
                    fixture.insertAttestationPolicy(UUID.randomUUID(), attestationBindingId, fixture.versionId, minimumAssents = 0)
                }
                refusedBy(connection, "ck_request_template_attestation_policy_strength") {
                    fixture.insertAttestationPolicy(
                        UUID.randomUUID(),
                        attestationBindingId,
                        fixture.versionId,
                        strength = "UNSTATED",
                    )
                }
                refusedBy(connection, "ck_request_template_attestation_policy_validity") {
                    fixture.insertAttestationPolicy(
                        UUID.randomUUID(),
                        attestationBindingId,
                        fixture.versionId,
                        validityHours = 0,
                    )
                }
                refusedBy(connection, "ck_request_template_attestation_policy_signature") {
                    fixture.insertAttestationPolicy(
                        UUID.randomUUID(),
                        attestationBindingId,
                        fixture.versionId,
                        signatureReference = "QUALIFIED",
                    )
                }

                val policyId = UUID.randomUUID()
                fixture.insertAttestationPolicy(
                    policyId,
                    attestationBindingId,
                    fixture.versionId,
                    ordering = "ROLE_SEQUENCE",
                    minimumAssents = 3,
                    strength = "ACCOUNT_SIGN_IN",
                    validityHours = 72,
                    signatureReference = "OPTIONAL",
                )
                refusedBy(connection, "ux_request_template_attestation_policy_binding") {
                    fixture.insertAttestationPolicy(UUID.randomUUID(), attestationBindingId, fixture.versionId)
                }
                refusedBy(connection, "ck_request_template_attestation_role_key") {
                    fixture.insertAttestationRole(policyId, fixture.versionId, "REVIEWER", 1)
                }
                fixture.recordDerivedCapabilities(fixture.versionId)
                refusedBy(connection, "an attestation policy must name at least one role") {
                    fixture.publish(fixture.versionId)
                }

                fixture.insertAttestationRole(policyId, fixture.versionId, "CONTRIBUTOR", 1)
                refusedBy(connection, "ux_request_template_attestation_role_key") {
                    fixture.insertAttestationRole(policyId, fixture.versionId, "CONTRIBUTOR", 2)
                }
                refusedBy(connection, "ux_request_template_attestation_role_position") {
                    fixture.insertAttestationRole(policyId, fixture.versionId, "ATTESTOR", 1)
                }
                fixture.insertAttestationRole(policyId, fixture.versionId, "ATTESTOR", 2)
                refusedBy(connection, "must need at least one assent from each role it names") {
                    execute(
                        connection,
                        "UPDATE information_request_template_attestation_policy SET minimum_assent_count = 1 WHERE id = ?",
                        policyId,
                    )
                    fixture.publish(fixture.versionId)
                }
                refusedBy(connection, "role positions must run from one without a gap") {
                    execute(
                        connection,
                        "UPDATE information_request_template_attestation_role SET position = 3 WHERE attestation_policy_id = ? AND role_key = 'ATTESTOR'",
                        policyId,
                    )
                    fixture.publish(fixture.versionId)
                }

                fixture.publish(fixture.versionId)
                assertEquals("PUBLISHED", versionValue(connection, fixture.versionId, "status"))
                refusedBy(connection, "immutable") {
                    execute(
                        connection,
                        "UPDATE information_request_template_attestation_policy SET minimum_assent_count = 2 WHERE id = ?",
                        policyId,
                    )
                }
                refusedBy(connection, "immutable") {
                    fixture.insertAttestationRole(policyId, fixture.versionId, "PREPARER", 3)
                }
                refusedBy(connection, "immutable") {
                    execute(
                        connection,
                        "DELETE FROM information_request_template_attestation_role WHERE attestation_policy_id = ?",
                        policyId,
                    )
                }
            }
        }
    }

    @Test
    fun `a version that can route evidence to a reviewer needs the review capability`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                val documentId = UUID.randomUUID()
                val documentBindingId = UUID.randomUUID()
                val policyId = UUID.randomUUID()
                fixture.insertRequirement(documentId, "supporting-record", "DOCUMENT")
                fixture.insertBinding(documentBindingId, fixture.versionId, documentId, fixture.sectionId, 1)
                fixture.insertEvidencePolicy(policyId, documentBindingId, fixture.versionId, waiverPolicy = "RESPONDENT_DECLARED")
                fixture.insertDisposition(documentBindingId, fixture.versionId, "WAIVED")
                assertEquals(
                    setOf("DOCUMENT_EVIDENCE", "EVIDENCE_WAIVER", "RESPONSE_SUBMISSION"),
                    fixture.derivedCapabilities(fixture.versionId),
                )

                execute(
                    connection,
                    "UPDATE information_request_template_evidence_policy SET waiver_policy = 'REVIEW_APPROVAL_REQUIRED' WHERE id = ?",
                    policyId,
                )
                assertTrue("RESPONSE_REVIEW" in fixture.derivedCapabilities(fixture.versionId))

                execute(
                    connection,
                    """
                    UPDATE information_request_template_evidence_policy
                    SET waiver_policy = 'RESPONDENT_DECLARED', conformance_policy = 'DEFICIENCY_REVIEWABLE'
                    WHERE id = ?
                    """.trimIndent(),
                    policyId,
                )
                assertTrue("RESPONSE_REVIEW" in fixture.derivedCapabilities(fixture.versionId))
            }
        }
    }

    @Test
    fun `an upgrade records the review capability of a published version that routes evidence to a reviewer`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, target = "131").migrate()
            val attestationBindingId = UUID.randomUUID()
            val fixtureVersionId = postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                val documentId = UUID.randomUUID()
                val documentBindingId = UUID.randomUUID()
                val attestationId = UUID.randomUUID()
                fixture.insertRequirement(attestationId, "recorded-assertion", "RESPONSE_ATTESTATION")
                fixture.insertBinding(attestationBindingId, fixture.versionId, attestationId, fixture.sectionId, 2, "PREPARER")
                fixture.insertRequirement(documentId, "supporting-record", "DOCUMENT")
                fixture.insertBinding(documentBindingId, fixture.versionId, documentId, fixture.sectionId, 1)
                fixture.insertEvidencePolicy(
                    UUID.randomUUID(),
                    documentBindingId,
                    fixture.versionId,
                    conformancePolicy = "DEFICIENCY_REVIEWABLE",
                )
                fixture.recordDerivedCapabilities(fixture.versionId)
                fixture.publish(fixture.versionId)
                assertEquals(
                    setOf("DOCUMENT_EVIDENCE", "RESPONSE_ATTESTATION", "RESPONSE_SUBMISSION"),
                    fixture.recordedCapabilities(fixture.versionId),
                )
                fixture.versionId
            }

            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                assertEquals(
                    setOf("DOCUMENT_EVIDENCE", "RESPONSE_ATTESTATION", "RESPONSE_SUBMISSION", "RESPONSE_REVIEW"),
                    queryStrings(
                        connection,
                        "SELECT capability_key FROM information_request_template_version_capability WHERE template_version_id = ?",
                        fixtureVersionId,
                    ),
                )
                assertEquals("PUBLISHED", versionValue(connection, fixtureVersionId, "status"))
                assertEquals("WHOLE_PACKAGE", versionValue(connection, fixtureVersionId, "submission_mode"))
                assertEquals(
                    "ANY_ORDER|1|VERIFIED_CONTACT|NOT_ACCEPTED|PREPARER|1",
                    statedPolicy(connection, attestationBindingId),
                )
            }
        }
    }

    private fun statedPolicy(connection: java.sql.Connection, bindingId: UUID): String? =
        queryString(
            connection,
            """
            SELECT policy.ordering || '|' || policy.minimum_assent_count || '|' ||
                   policy.minimum_authentication_strength || '|' || policy.external_signature_reference || '|' ||
                   role.role_key || '|' || role.position
            FROM information_request_template_attestation_policy policy
                     JOIN information_request_template_attestation_role role ON role.attestation_policy_id = policy.id
            WHERE policy.template_binding_id = ?
            """.trimIndent(),
            bindingId,
        )

    private fun versionValue(connection: java.sql.Connection, versionId: UUID, column: String): String? =
        queryString(connection, "SELECT $column FROM information_request_template_version WHERE id = ?", versionId)

    private fun setVersion(connection: java.sql.Connection, versionId: UUID, column: String, value: String)
    {
        execute(connection, "UPDATE information_request_template_version SET $column = ? WHERE id = ?", value, versionId)
    }
}
