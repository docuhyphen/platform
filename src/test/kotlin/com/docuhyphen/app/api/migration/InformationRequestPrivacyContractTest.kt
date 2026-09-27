package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestPrivacyContractTest
{
    @Test
    fun `a privacy request states its kind, keys, and outcome and is immutable once finished`()
    {
        withPrivacy { connection, privacy ->
            refusedBy(connection, "ck_information_request_privacy_request_kind") {
                privacy.insertRequest(UUID.randomUUID(), kind = "ERASE_EVERYTHING")
            }
            refusedBy(connection, "ck_information_request_privacy_request_keys") {
                privacy.insertRequest(UUID.randomUUID(), purpose = "Not A Key")
            }
            refusedBy(connection, "ck_information_request_privacy_request_state") {
                privacy.insertRequest(UUID.randomUUID(), state = "REFUSED")
            }
            val correctionRequestId = UUID.randomUUID()
            privacy.insertRequest(correctionRequestId, kind = "CORRECTION")
            refusedBy(connection, "ck_information_request_privacy_request_export") {
                execute(
                    connection,
                    "UPDATE information_request_privacy_request SET record_export_id = ?, request_revision = request_revision + 1 WHERE id = ?",
                    UUID.randomUUID(),
                    correctionRequestId,
                )
            }
            val requestId = UUID.randomUUID()
            privacy.insertRequest(requestId)
            refusedBy(connection, "a privacy request keeps the identity it was recorded with") {
                execute(connection, "UPDATE information_request_privacy_request SET purpose_key = 'other', request_revision = request_revision + 1 WHERE id = ?", requestId)
            }
            execute(
                connection,
                "UPDATE information_request_privacy_request SET state = 'COMPLETED', completed_at = now(), request_revision = request_revision + 1 WHERE id = ?",
                requestId,
            )
            refusedBy(connection, "a finished privacy request is immutable") {
                execute(connection, "UPDATE information_request_privacy_request SET refusal_detail = 'x', request_revision = request_revision + 1 WHERE id = ?", requestId)
            }
            refusedBy(connection, "a privacy request is never deleted") {
                execute(connection, "DELETE FROM information_request_privacy_request WHERE id = ?", requestId)
            }
            refusedBy(connection, "ck_information_request_privacy_target_reason") {
                privacy.insertTarget(requestId, "REFUSED", reason = null)
            }
            privacy.insertTarget(requestId, "RESTRICTED")
            refusedBy(connection, "record preservation history is append-only") {
                execute(connection, "DELETE FROM information_request_privacy_target WHERE privacy_request_id = ?", requestId)
            }
        }
    }

    @Test
    fun `a subject has at most one active restriction and a lifted restriction is immutable`()
    {
        withPrivacy { connection, privacy ->
            val privacyRequestId = UUID.randomUUID()
            privacy.insertRequest(privacyRequestId, kind = "RESTRICTION")
            val restrictionId = UUID.randomUUID()
            privacy.insertRestriction(restrictionId, privacyRequestId)
            refusedBy(connection, "ux_information_request_subject_restriction_active") {
                privacy.insertRestriction(UUID.randomUUID(), privacyRequestId)
            }
            refusedBy(connection, "ck_information_request_subject_restriction_lift") {
                execute(connection, "UPDATE information_request_subject_restriction SET lifted_at = now() WHERE id = ?", restrictionId)
            }
            execute(
                connection,
                """
                UPDATE information_request_subject_restriction
                SET lifted_at = now(), lifted_by_principal_kind = 'USER', lifted_by_principal_id = ?, lift_reason_code = 'resolved'
                WHERE id = ?
                """.trimIndent(),
                privacy.runtime.template.userId,
                restrictionId,
            )
            refusedBy(connection, "a lifted subject restriction is immutable") {
                execute(connection, "UPDATE information_request_subject_restriction SET lift_reason_code = 'changed' WHERE id = ?", restrictionId)
            }
            privacy.insertRestriction(UUID.randomUUID(), privacyRequestId)
            assertEquals(2, queryInt(connection, "SELECT count(*) FROM information_request_subject_restriction WHERE subject_identity_ref_id = ?", privacy.subjectId))
        }
    }

    @Test
    fun `a correction names an item of its request's package, is append-only, and goes with its request on disposal`()
    {
        withPrivacy { connection, privacy ->
            val runtime = privacy.runtime
            val packageId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            runtime.insertPackage(packageId, 1)
            runtime.insertItem(itemId, packageId, runtime.documentRequirementId, runtime.documentRevisionId, runtime.documentBindingId, "DOCUMENT")
            refusedBy(connection, "ck_information_request_item_correction_content") {
                privacy.insertCorrection(UUID.randomUUID(), packageId, itemId, value = null, narrative = null)
            }
            refusedBy(connection, "a correction names an item of a package of its request") {
                privacy.insertCorrection(UUID.randomUUID(), UUID.randomUUID(), itemId)
            }
            val correctionId = UUID.randomUUID()
            privacy.insertCorrection(correctionId, packageId, itemId)
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_item_correction SET reason_code = 'other' WHERE id = ?", correctionId)
            }
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", runtime.requestId)
            val preservation = PreservationSqlFixture(connection, runtime)
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            val claimId = UUID.randomUUID()
            preservation.insertClaim(claimId, scheduleId)
            preservation.advance(claimId)
            queryString(connection, "SELECT record_dispose_information_request(?)", claimId)
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_item_correction WHERE id = ?", correctionId))
        }
    }

    private fun withPrivacy(block: (Connection, PrivacySqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                block(connection, PrivacySqlFixture(connection, SubmissionRuntimeSqlFixture(connection)))
            }
        }
    }
}

internal class PrivacySqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
)
{
    val subjectId: UUID = UUID.randomUUID()

    init
    {
        runtime.insertSubject(subjectId, UUID.randomUUID())
    }

    fun insertRequest(
        id: UUID,
        kind: String = "ACCESS",
        purpose: String = "subject.request",
        state: String = "RECORDED",
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_privacy_request
                (id, owner_kind, owner_id, subject_identity_ref_id, request_kind, purpose_key, policy_basis_key, state,
                 recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, 'ORGANIZATION', ?, ?, ?, ?, 'policy.basis', ?, 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.template.organizationId,
            subjectId,
            kind,
            purpose,
            state,
            runtime.template.userId,
        )
    }

    fun insertTarget(privacyRequestId: UUID, outcome: String, reason: String? = null)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_privacy_target (privacy_request_id, information_request_id, outcome, reason_code)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            privacyRequestId,
            runtime.requestId,
            outcome,
            reason,
        )
    }

    fun insertRestriction(id: UUID, privacyRequestId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_subject_restriction
                (id, owner_kind, owner_id, subject_identity_ref_id, privacy_request_id, restricted_at)
            VALUES (?, 'ORGANIZATION', ?, ?, ?, now())
            """.trimIndent(),
            id,
            runtime.template.organizationId,
            subjectId,
            privacyRequestId,
        )
    }

    fun insertCorrection(id: UUID, packageId: UUID, itemId: UUID, value: String? = "\"corrected\"", narrative: String? = null)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_item_correction
                (id, information_request_id, package_id, submission_item_id, corrected_value_json, corrected_narrative,
                 reason_code, recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, 'subject.correction', 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.requestId,
            packageId,
            itemId,
            value,
            narrative,
            runtime.template.userId,
        )
    }
}
