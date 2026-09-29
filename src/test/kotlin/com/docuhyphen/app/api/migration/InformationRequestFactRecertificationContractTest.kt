package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestFactRecertificationContractTest
{
    @Test
    fun `a recertification copies the exact provenance of a current fact and stays append-only`()
    {
        withRecertification { connection, fixture ->
            val recertificationId = fixture.insertRecertification()
            fixture.insertRecertificationEvidence(recertificationId, fixture.runtime.evidenceVersionId)
            assertEquals(1, fixture.count("information_request_fact_recertification"))
            assertEquals(1, fixture.count("information_request_fact_recertification_evidence"))

            refusedBy(connection, "recertified evidence is evidence the reused fact names") {
                fixture.insertRecertificationEvidence(recertificationId, UUID.randomUUID())
            }
            refusedBy(connection, "a recertification copies the exact provenance of a current reusable fact") {
                fixture.insertRecertification(canonicalValue = "\"Different answer\"")
            }
            refusedBy(connection, "a recertification names the response revision it wrote") {
                fixture.insertRecertification(responseRevision = 9)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "DELETE FROM information_request_fact_recertification WHERE id = ?", recertificationId)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(
                    connection,
                    "UPDATE information_request_fact_recertification SET canonical_value = '\"Changed\"' WHERE id = ?",
                    recertificationId,
                )
            }
            execute(
                connection,
                """
                INSERT INTO information_request_accepted_fact_revocation
                    (id, fact_id, reason_code, revoked_by_principal_kind, revoked_by_principal_id, revoked_at)
                VALUES (?, ?, 'no-longer-accurate', 'USER', ?, now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.factId,
                fixture.runtime.template.userId,
            )
            refusedBy(connection, "a recertification copies the exact provenance of a current reusable fact") {
                fixture.insertRecertification()
            }
            execute(
                connection,
                """
                INSERT INTO information_request_transition
                    (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at)
                VALUES (?, ?, 1, 'ISSUED', 'ISSUED', 'RECERTIFY_FACT', 'USER', ?, now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.targetRequestId,
                fixture.runtime.contributorUserId,
            )
        }
    }

    @Test
    fun `disposal removes a request's own recertifications and leaves those another request made from its facts`()
    {
        withRecertification { connection, fixture ->
            val recertificationId = fixture.insertRecertification()
            fixture.insertRecertificationEvidence(recertificationId, fixture.runtime.evidenceVersionId)
            val preservation = PreservationSqlFixture(connection, fixture.runtime)
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)

            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", fixture.runtime.requestId)
            val sourceClaim = UUID.randomUUID()
            preservation.insertClaim(sourceClaim, scheduleId)
            preservation.insertScope(sourceClaim, "INFORMATION_REQUEST", fixture.runtime.requestId.toString(), direct = true)
            val objectId = preservation.insertObject(sourceClaim, retained = false)
            execute(connection, "UPDATE record_disposal_object SET deletion_outcome = 'ABSENT', deleted_at = now() WHERE id = ?", objectId)
            preservation.advance(sourceClaim)
            queryString(connection, "SELECT record_dispose_information_request(?)", sourceClaim)
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM information_request_accepted_fact WHERE id = ?", fixture.factId))
            assertEquals(1, fixture.count("information_request_fact_recertification"))
            assertEquals(1, fixture.count("information_request_fact_recertification_evidence"))

            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", fixture.targetRequestId)
            val targetClaim = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO record_disposal_claim (id, resource_type, resource_id, owner_kind, owner_id, basis,
                                                   retention_schedule_id, state, claimed_by_principal_kind,
                                                   claimed_by_principal_id, claimed_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'ORGANIZATION', ?, 'RETENTION_SCHEDULE', ?, 'CLAIMED', 'SERVICE_ACCOUNT',
                        '00000000-0000-0000-0000-000000000000', now())
                """.trimIndent(),
                targetClaim,
                fixture.targetRequestId,
                fixture.runtime.template.organizationId,
                scheduleId,
            )
            preservation.insertScope(targetClaim, "INFORMATION_REQUEST", fixture.targetRequestId.toString(), direct = true)
            preservation.advance(targetClaim)
            val removed = requireNotNull(queryString(connection, "SELECT record_dispose_information_request(?)", targetClaim))
            assertTrue(removed.contains("\"information_request_fact_recertification\": 1"), removed)
            assertTrue(removed.contains("\"information_request_fact_recertification_evidence\": 1"), removed)
            assertEquals(0, fixture.count("information_request_fact_recertification"))
            assertEquals(0, fixture.count("information_request_fact_recertification_evidence"))
        }
    }

    private fun withRecertification(block: (Connection, RecertificationSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                lateinit var answers: FieldAnswerSqlFixture
                val templateLinkId = UUID.randomUUID()
                val runtime = SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                    answers = FieldAnswerSqlFixture(connection, configured.template)
                    execute(
                        connection,
                        "INSERT INTO information_request_template_binding_evidence_link (id, template_binding_id, supporting_template_binding_id, template_version_id) VALUES (?, ?, ?, ?)",
                        templateLinkId,
                        answers.bindingId,
                        configured.documentBindingId,
                        configured.template.versionId,
                    )
                })
                answers.materialize(runtime)
                block(connection, RecertificationSqlFixture(connection, runtime, answers, templateLinkId))
            }
        }
    }
}

internal class RecertificationSqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
    private val answers: FieldAnswerSqlFixture,
    templateLinkId: UUID,
)
{
    private val facts = FactSqlFixture(connection, runtime, answers)
    val factId: UUID = UUID.randomUUID()
    val targetRequestId: UUID = UUID.randomUUID()
    private val targetRequirementId = UUID.randomUUID()
    private val targetResponseId = UUID.randomUUID()

    init
    {
        val documentItemId = UUID.randomUUID()
        val submittedEvidenceId = UUID.randomUUID()
        val requestLinkId = UUID.randomUUID()
        runtime.insertItem(
            documentItemId,
            facts.packageId,
            runtime.documentRequirementId,
            runtime.documentRevisionId,
            runtime.documentBindingId,
            "DOCUMENT",
            runtime.documentResponseId,
        )
        runtime.insertEvidenceMember(submittedEvidenceId, facts.packageId, documentItemId)
        execute(
            connection,
            "INSERT INTO information_request_supporting_evidence_link (id, information_request_id, supported_requirement_id, supporting_requirement_id, template_evidence_link_id) VALUES (?, ?, ?, ?, ?)",
            requestLinkId,
            runtime.requestId,
            answers.requirementId,
            runtime.documentRequirementId,
            templateLinkId,
        )
        execute(
            connection,
            "INSERT INTO information_request_submission_supporting_link (id, package_id, information_request_id, supporting_evidence_link_id, supported_requirement_id, supporting_requirement_id) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            facts.packageId,
            runtime.requestId,
            requestLinkId,
            answers.requirementId,
            runtime.documentRequirementId,
        )
        facts.insertFact(factId)
        execute(
            connection,
            "INSERT INTO information_request_accepted_fact_evidence (id, fact_id, source_submission_evidence_id, evidence_version_id) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            factId,
            submittedEvidenceId,
            runtime.evidenceVersionId,
        )
        runtime.insertSiblingRequest(targetRequestId, facts.subjectId)
        val revisionId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path, created_at)
            VALUES (?, ?, ?, ?, ?, 'root', now())
            """.trimIndent(),
            targetRequirementId,
            targetRequestId,
            runtime.template.versionId,
            answers.templateRequirementId,
            answers.bindingId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_revision
                (id, information_request_requirement_id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                 effective_from, configuration_hash_sha256, optimistic_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, 'root', now(), ?, 1, now())
            """.trimIndent(),
            revisionId,
            targetRequirementId,
            targetRequestId,
            runtime.template.versionId,
            answers.templateRequirementId,
            answers.bindingId,
            "0".repeat(64),
        )
        execute(
            connection,
            """
            INSERT INTO information_request_response
                (id, information_request_id, information_request_requirement_id, requirement_revision_id, occurrence_path,
                 disposition, response_revision, recorded_by_principal_kind, recorded_by_principal_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'root', 'PROVIDED', 3, 'USER', ?, now(), now())
            """.trimIndent(),
            targetResponseId,
            targetRequestId,
            targetRequirementId,
            revisionId,
            runtime.contributorUserId,
        )
    }

    fun insertRecertification(canonicalValue: String = "\"Recorded answer\"", responseRevision: Long = 3): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_fact_recertification
                (id, information_request_id, information_request_requirement_id, response_id, response_revision, fact_id,
                 purpose_key, policy_basis_key, value_type, canonical_value, source_information_request_id,
                 source_package_id, source_submission_item_id, source_requirement_id, source_field_value_revision_id,
                 assented_by_principal_kind, assented_by_principal_id, assented_by_session_ref, assented_at)
            VALUES (?, ?, ?, ?, ?, ?, 'profile.reuse', 'policy.reuse', 'SHORT_TEXT', ?, ?, ?, ?, ?, ?, 'USER', ?, 'session', now())
            """.trimIndent(),
            id,
            targetRequestId,
            targetRequirementId,
            targetResponseId,
            responseRevision,
            factId,
            canonicalValue,
            runtime.requestId,
            facts.packageId,
            facts.itemId,
            answers.requirementId,
            answers.fieldValueRevisionId,
            runtime.contributorUserId,
        )
        return id
    }

    fun insertRecertificationEvidence(recertificationId: UUID, evidenceVersionId: UUID)
    {
        execute(
            connection,
            "INSERT INTO information_request_fact_recertification_evidence (id, recertification_id, evidence_version_id) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            recertificationId,
            evidenceVersionId,
        )
    }

    fun count(table: String): Int = queryInt(connection, "SELECT COUNT(*) FROM $table")
}
