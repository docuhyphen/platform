package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestAcceptedFactContractTest
{
    @Test
    fun `promoted facts store a policy basis and exact submitted evidence references`()
    {
        withFact { connection, _ ->
            org.junit.jupiter.api.Assertions.assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'information_request_accepted_fact' AND column_name = 'policy_basis_key' AND is_nullable = 'NO'",
                ),
            )
            org.junit.jupiter.api.Assertions.assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'information_request_accepted_fact_evidence'",
                ),
            )
        }
    }

    @Test
    fun `a promoted evidence reference is restricted to a conforming version linked to the source answer`()
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
                val facts = FactSqlFixture(connection, runtime, answers)
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
                val factId = UUID.randomUUID()
                facts.insertFact(factId)
                val referenceId = UUID.randomUUID()
                execute(
                    connection,
                    "INSERT INTO information_request_accepted_fact_evidence (id, fact_id, source_submission_evidence_id, evidence_version_id) VALUES (?, ?, ?, ?)",
                    referenceId,
                    factId,
                    submittedEvidenceId,
                    runtime.evidenceVersionId,
                )
                assertEquals(
                    1,
                    queryInt(connection, "SELECT COUNT(*) FROM information_request_accepted_fact_evidence WHERE fact_id = ?", factId),
                )
                refusedBy(connection, "promoted evidence is a conforming submitted version supporting the fact") {
                    execute(
                        connection,
                        "INSERT INTO information_request_accepted_fact_evidence (id, fact_id, source_submission_evidence_id, evidence_version_id) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        factId,
                        submittedEvidenceId,
                        UUID.randomUUID(),
                    )
                }
                refusedBy(connection, "information request history is append-only") {
                    execute(connection, "DELETE FROM information_request_accepted_fact_evidence WHERE id = ?", referenceId)
                }

                execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", runtime.requestId)
                val preservation = PreservationSqlFixture(connection, runtime)
                val scheduleId = UUID.randomUUID()
                preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
                val claimId = UUID.randomUUID()
                preservation.insertClaim(claimId, scheduleId)
                preservation.insertScope(claimId, "INFORMATION_REQUEST", runtime.requestId.toString(), direct = true)
                val objectId = preservation.insertObject(claimId, retained = false)
                execute(connection, "UPDATE record_disposal_object SET deletion_outcome = 'ABSENT', deleted_at = now() WHERE id = ?", objectId)
                preservation.advance(claimId)
                val removed = requireNotNull(queryString(connection, "SELECT record_dispose_information_request(?)", claimId))
                assertTrue(removed.contains("\"information_request_accepted_fact_evidence\": 1"), removed)
                assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM information_request_accepted_fact_evidence WHERE fact_id = ?", factId))
            }
        }
    }

    @Test
    fun `an accepted fact is promoted from the exact field answer a package froze about the request's subject`()
    {
        withFact { connection, facts ->
            refusedBy(connection, "an accepted fact is about the subject of its source request") {
                facts.insertFact(UUID.randomUUID(), subject = facts.unrelatedSubjectId)
            }
            refusedBy(connection, "an accepted fact names the exact answer its source item froze") {
                facts.insertFact(UUID.randomUUID(), responseRevision = 3)
            }
            refusedBy(connection, "ck_information_request_accepted_fact_confidence") {
                facts.insertFact(UUID.randomUUID(), confidence = "REVIEWED")
            }
            refusedBy(connection, "ck_information_request_accepted_fact_purpose") {
                facts.insertFact(UUID.randomUUID(), purpose = "Not A Purpose")
            }
            refusedBy(connection, "ck_information_request_accepted_fact_policy_basis") {
                facts.insertFact(UUID.randomUUID(), policyBasis = "Unstated Basis")
            }
            refusedBy(connection, "ck_information_request_accepted_fact_period") {
                facts.insertFact(UUID.randomUUID(), validToOffsetDays = -1)
            }
            val fact = UUID.randomUUID()
            facts.insertFact(fact)
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_accepted_fact SET purpose_key = 'profile.other' WHERE id = ?", fact)
            }
        }
    }

    @Test
    fun `supersession and conflict are stated at promotion and a revocation is recorded once`()
    {
        withFact { connection, facts ->
            val first = UUID.randomUUID()
            facts.insertFact(first)
            refusedBy(connection, "ck_information_request_accepted_fact_conflict") {
                facts.insertFact(UUID.randomUUID(), conflict = "CONFLICTING")
            }
            val conflicting = UUID.randomUUID()
            facts.insertFact(conflicting, conflict = "CONFLICTING", conflictingFactId = first)
            val second = UUID.randomUUID()
            facts.insertFact(second, supersedes = first)
            refusedBy(connection, "ux_information_request_accepted_fact_supersedes") {
                facts.insertFact(UUID.randomUUID(), supersedes = first)
            }
            facts.revoke(second)
            refusedBy(connection, "ux_information_request_accepted_fact_revocation") {
                facts.revoke(second)
            }
            refusedBy(connection, "a revoked accepted fact cannot be superseded") {
                facts.insertFact(UUID.randomUUID(), supersedes = second)
            }
        }
    }

    @Test
    fun `a business decision numbers its process chain from one and a later decision names the latest prior one`()
    {
        withFact { connection, facts ->
            val original = UUID.randomUUID()
            refusedBy(connection, "a business decision numbers its process from one without a gap") {
                facts.insertDecision(UUID.randomUUID(), "process.outcome", 2, "APPEAL", original)
            }
            refusedBy(connection, "ck_information_request_business_decision_chain") {
                facts.insertDecision(UUID.randomUUID(), "process.outcome", 1, "APPEAL", null)
            }
            facts.insertDecision(original, "process.outcome", 1, "ORIGINAL", null)
            val appeal = UUID.randomUUID()
            facts.insertDecision(appeal, "process.outcome", 2, "APPEAL", original)
            refusedBy(connection, "a later business decision names the latest decision of its own process") {
                facts.insertDecision(UUID.randomUUID(), "process.outcome", 3, "RECONSIDERATION", original)
            }
            val otherProcess = UUID.randomUUID()
            facts.insertDecision(otherProcess, "process.other", 1, "ORIGINAL", null)
            refusedBy(connection, "a later business decision names the latest decision of its own process") {
                facts.insertDecision(UUID.randomUUID(), "process.outcome", 3, "RECONSIDERATION", otherProcess)
            }
            facts.insertDecision(UUID.randomUUID(), "process.outcome", 3, "RECONSIDERATION", appeal)
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_business_decision SET outcome_code = 'changed' WHERE id = ?", original)
            }
        }
    }

    private fun withFact(block: (Connection, FactSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                lateinit var answers: FieldAnswerSqlFixture
                val runtime = SubmissionRuntimeSqlFixture(
                    connection,
                    beforePublish = { runtime -> answers = FieldAnswerSqlFixture(connection, runtime.template) },
                )
                answers.materialize(runtime)
                block(connection, FactSqlFixture(connection, runtime, answers))
            }
        }
    }
}

internal class FactSqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
)
{
    val subjectId: UUID = UUID.randomUUID()
    val unrelatedSubjectId: UUID = UUID.randomUUID()
    val packageId: UUID = UUID.randomUUID()
    val itemId: UUID = UUID.randomUUID()

    init
    {
        runtime.insertSubject(subjectId, UUID.randomUUID())
        runtime.insertSubject(unrelatedSubjectId)
        runtime.insertPackage(packageId, 1)
        answers.insertFieldItem(runtime, itemId, packageId)
    }

    @Suppress("LongParameterList")
    fun insertFact(
        id: UUID,
        subject: UUID = subjectId,
        responseRevision: Long = 2,
        confidence: String = "DECLARED",
        purpose: String = "profile.reuse",
        policyBasis: String = "policy.reuse",
        validToOffsetDays: Int = 30,
        conflict: String = "NONE",
        conflictingFactId: UUID? = null,
        supersedes: UUID? = null,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_accepted_fact
                (id, owner_type, owner_organization_id, subject_identity_ref_id, purpose_key, policy_basis_key, field_definition_id, value_type,
                 canonical_value, source_information_request_id, source_package_id, source_submission_item_id,
                 source_requirement_id, source_response_id, source_response_revision, source_field_value_revision_id,
                 visibility, confidence, valid_from, valid_to, supersedes_fact_id, conflict_state, conflicting_fact_id,
                 promoted_by_principal_kind, promoted_by_principal_id, promoted_at)
            VALUES (?, 'ORGANIZATION', ?, ?, ?, ?, ?, 'SHORT_TEXT', '"Recorded answer"', ?, ?, ?, ?, ?, ?, ?,
                    'RESPONDING_PARTIES', ?, now(), now() + (? * INTERVAL '1 day'), ?, ?, ?, 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.template.organizationId,
            subject,
            purpose,
            policyBasis,
            answers.fieldDefinitionId,
            runtime.requestId,
            packageId,
            itemId,
            answers.requirementId,
            answers.responseId,
            responseRevision,
            answers.fieldValueRevisionId,
            confidence,
            validToOffsetDays,
            supersedes,
            conflict,
            conflictingFactId,
            runtime.template.userId,
        )
    }

    fun revoke(factId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_accepted_fact_revocation
                (id, fact_id, reason_code, revoked_by_principal_kind, revoked_by_principal_id, revoked_at)
            VALUES (?, ?, 'no-longer-accurate', 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            factId,
            runtime.template.userId,
        )
    }

    fun insertDecision(id: UUID, process: String, revision: Int, kind: String, prior: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_business_decision
                (id, information_request_id, owning_process_key, outcome_code, reason_reference, kind, prior_decision_id,
                 decision_revision, decided_at, recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, ?, 'outcome.recorded', 'reference-1', ?, ?, ?, now(), 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.requestId,
            process,
            kind,
            prior,
            revision,
            runtime.template.userId,
        )
    }
}
