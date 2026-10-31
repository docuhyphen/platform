package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class RecordPreservationRequestRecordsContractTest {
    @Test
    fun `the records of a request are its submissions, evidence artifacts and versions, notices, and record exports`() {
        withPreservation { connection, preservation ->
            val runtime = preservation.runtime
            val other = SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = runtime.template.organizationId)
            val packageId = UUID.randomUUID()
            runtime.insertPackage(packageId, 1)
            val noticeId = insertNotice(connection, runtime)
            val requestExportId = UUID.randomUUID()
            preservation.insertExport(requestExportId)
            val subjectExportId = insertSubjectExport(connection, runtime, listOf(runtime.requestId, other.requestId))

            assertEquals(
                setOf(
                    "INFORMATION_REQUEST_SUBMISSION_PACKAGE:$packageId",
                    "INFORMATION_REQUEST_EVIDENCE_ARTIFACT:${runtime.artifactId}",
                    "INFORMATION_REQUEST_EVIDENCE_VERSION:${runtime.evidenceVersionId}",
                    "INFORMATION_REQUEST_OUTBOUND_NOTICE:$noticeId",
                    "INFORMATION_REQUEST_RECORD_EXPORT:$requestExportId",
                    "INFORMATION_REQUEST_RECORD_EXPORT:$subjectExportId",
                ),
                records(connection, runtime.requestId),
            )
            assertEquals(
                setOf(
                    "INFORMATION_REQUEST_EVIDENCE_ARTIFACT:${other.artifactId}",
                    "INFORMATION_REQUEST_EVIDENCE_VERSION:${other.evidenceVersionId}",
                    "INFORMATION_REQUEST_RECORD_EXPORT:$subjectExportId",
                ),
                records(connection, other.requestId),
            )
        }
    }

    @Test
    fun `a record of a request an open claim names cannot be placed on hold, including one the request gains after the claim`() {
        withPreservation { connection, preservation ->
            val runtime = preservation.runtime
            val sibling =
                SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = runtime.template.organizationId)
            val unrelated =
                SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = runtime.template.organizationId)
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            val claimId = UUID.randomUUID()
            preservation.insertClaim(claimId, scheduleId)
            preservation.insertScope(claimId, "INFORMATION_REQUEST", runtime.requestId.toString(), direct = true)
            preservation.insertScope(claimId, "INFORMATION_REQUEST", sibling.requestId.toString(), direct = true)
            val laterNoticeId = insertNotice(connection, runtime)
            val laterExportId = UUID.randomUUID()
            PreservationSqlFixture(connection, sibling).insertExport(laterExportId)

            refusedBy(connection, "a record under disposal cannot be placed on hold") {
                preservation.insertHold(
                    UUID.randomUUID(),
                    "INFORMATION_REQUEST_OUTBOUND_NOTICE",
                    laterNoticeId.toString()
                )
            }
            refusedBy(connection, "a record under disposal cannot be placed on hold") {
                preservation.insertHold(
                    UUID.randomUUID(), "INFORMATION_REQUEST_RECORD_EXPORT", laterExportId.toString(),
                    scope = "DESCENDANTS_AND_REFERENCES",
                )
            }
            refusedBy(connection, "a record under disposal cannot be placed on hold") {
                preservation.insertHold(
                    UUID.randomUUID(), "INFORMATION_REQUEST_EVIDENCE_ARTIFACT", sibling.artifactId.toString(),
                    ownerKind = "PLATFORM", ownerId = null,
                )
            }
            preservation.insertHold(
                UUID.randomUUID(),
                "INFORMATION_REQUEST_EVIDENCE_ARTIFACT",
                unrelated.artifactId.toString()
            )
            preservation.insertHold(
                UUID.randomUUID(), "INFORMATION_REQUEST_OUTBOUND_NOTICE", laterNoticeId.toString(),
                ownerKind = "USER", ownerId = runtime.template.userId,
            )
            assertEquals(2, queryInt(connection, "SELECT count(*) FROM audit_legal_hold WHERE status = 'ACTIVE'"))
        }
    }

    @Test
    fun `a claim cannot name a request while a record of that request is held`() {
        withPreservation { connection, preservation ->
            val runtime = preservation.runtime
            val sibling =
                SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = runtime.template.organizationId)
            val otherOwners =
                SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = runtime.template.organizationId)
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            preservation.insertHold(
                UUID.randomUUID(),
                "INFORMATION_REQUEST_OUTBOUND_NOTICE",
                insertNotice(connection, runtime).toString()
            )
            preservation.insertHold(
                UUID.randomUUID(), "INFORMATION_REQUEST_EVIDENCE_VERSION", sibling.evidenceVersionId.toString(),
                scope = "DESCENDANTS_AND_REFERENCES",
            )
            preservation.insertHold(
                UUID.randomUUID(), "INFORMATION_REQUEST_EVIDENCE_ARTIFACT", otherOwners.artifactId.toString(),
                ownerKind = "USER", ownerId = runtime.template.userId,
            )
            val claimId = UUID.randomUUID()
            preservation.insertClaim(claimId, scheduleId)

            refusedBy(connection, "a held record cannot be claimed for disposal") {
                preservation.insertScope(claimId, "INFORMATION_REQUEST", runtime.requestId.toString(), direct = true)
            }
            refusedBy(connection, "a held record cannot be claimed for disposal") {
                preservation.insertScope(claimId, "INFORMATION_REQUEST", sibling.requestId.toString(), direct = true)
            }
            preservation.insertScope(claimId, "INFORMATION_REQUEST", otherOwners.requestId.toString(), direct = true)
            preservation.insertScope(claimId, "EXCHANGE", runtime.exchangeId.toString(), direct = false)
            assertEquals(
                2,
                queryInt(connection, "SELECT count(*) FROM record_disposal_claim_scope WHERE claim_id = ?", claimId)
            )
        }
    }

    @Test
    fun `an upgraded database refuses a hold on a record of a request its open claim already names`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "175").migrate()
            postgres.createConnection("").use { connection ->
                val preservation = PreservationSqlFixture(connection, SubmissionRuntimeSqlFixture(connection))
                val runtime = preservation.runtime
                val scheduleId = UUID.randomUUID()
                preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
                val claimId = UUID.randomUUID()
                preservation.insertClaim(claimId, scheduleId)
                preservation.insertScope(claimId, "INFORMATION_REQUEST", runtime.requestId.toString(), direct = true)

                submissionFlyway(postgres).migrate()

                refusedBy(connection, "a record under disposal cannot be placed on hold") {
                    preservation.insertHold(
                        UUID.randomUUID(),
                        "INFORMATION_REQUEST_EVIDENCE_ARTIFACT",
                        runtime.artifactId.toString()
                    )
                }
                assertEquals(
                    1,
                    queryInt(connection, "SELECT count(*) FROM record_disposal_claim_scope WHERE claim_id = ?", claimId)
                )
                assertEquals(0, queryInt(connection, "SELECT count(*) FROM audit_legal_hold"))
            }
        }
    }

    private fun withPreservation(block: (Connection, PreservationSqlFixture) -> Unit) {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                block(connection, PreservationSqlFixture(connection, SubmissionRuntimeSqlFixture(connection)))
            }
        }
    }

    private fun records(connection: Connection, requestId: UUID): Set<String> =
        queryStrings(
            connection,
            "SELECT resource_type || ':' || resource_id FROM record_preservation_information_request_records(?)",
            requestId,
        )

    private fun insertNotice(connection: Connection, runtime: SubmissionRuntimeSqlFixture): UUID {
        val notices = NoticeSqlFixture(connection, ClockSqlFixture(connection, runtime))
        val intentId = UUID.randomUUID()
        notices.insertClockIntent(intentId, "RESPONSE_REMINDER", notices.clockEventId)
        notices.claim(intentId)
        val noticeId = UUID.randomUUID()
        notices.insertNotice(noticeId, intentId)
        return noticeId
    }

    private fun insertSubjectExport(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        requestIds: List<UUID>
    ): UUID {
        val subjectId = UUID.randomUUID()
        runtime.insertSubject(subjectId)
        val exportId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_record_export
                (id, export_kind, subject_identity_ref_id, owner_kind, owner_id, schema_version, content_json,
                 content_hash_algorithm, content_hash, content_length, storage_location, transfer_decision,
                 requested_by_principal_kind, requested_by_principal_id, requested_at)
            VALUES (?, 'SUBJECT_RECORD', ?, 'ORGANIZATION', ?, 1, '{}', 'SHA_256', ?, 2, 'primary', 'NOT_REQUESTED',
                    'USER', ?, now())
            """.trimIndent(),
            exportId,
            subjectId,
            runtime.template.organizationId,
            "d".repeat(64),
            runtime.template.userId,
        )
        requestIds.forEach { requestId ->
            execute(
                connection,
                "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (?, ?)",
                exportId,
                requestId,
            )
        }
        return exportId
    }
}
