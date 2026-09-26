package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestAcceptedFactContractTest
{
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
                (id, owner_type, owner_organization_id, subject_identity_ref_id, purpose_key, field_definition_id, value_type,
                 canonical_value, source_information_request_id, source_package_id, source_submission_item_id,
                 source_requirement_id, source_response_id, source_response_revision, source_field_value_revision_id,
                 visibility, confidence, valid_from, valid_to, supersedes_fact_id, conflict_state, conflicting_fact_id,
                 promoted_by_principal_kind, promoted_by_principal_id, promoted_at)
            VALUES (?, 'ORGANIZATION', ?, ?, ?, ?, 'SHORT_TEXT', '"Recorded answer"', ?, ?, ?, ?, ?, ?, ?,
                    'RESPONDING_PARTIES', ?, now(), now() + (? * INTERVAL '1 day'), ?, ?, ?, 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.template.organizationId,
            subject,
            purpose,
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
