package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class RecordPreservationDisposalContractTest
{
    @Test
    fun `an existing hold gains its owner, canonical attribution, scope, and a lifecycle history`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, target = "141").migrate()
            val organizationId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val activeId = UUID.randomUUID()
            val releasedId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                execute(
                    connection,
                    """
                    INSERT INTO audit_legal_hold (id, organization_id, resource_type, resource_id, reason, status,
                                                  placed_by_user_id, placed_at, created_at, updated_at)
                    VALUES (?, ?, 'EXCHANGE', 'record-1', 'preserve records', 'ACTIVE', ?, now(), now(), now())
                    """.trimIndent(),
                    activeId,
                    organizationId,
                    userId,
                )
                execute(
                    connection,
                    """
                    INSERT INTO audit_legal_hold (id, organization_id, resource_type, resource_id, reason, status,
                                                  placed_by_user_id, placed_at, released_by_user_id, released_at,
                                                  created_at, updated_at)
                    VALUES (?, NULL, 'DOCUMENT', 'record-2', 'preserve records', 'RELEASED', ?, now(), ?, now(), now(), now())
                    """.trimIndent(),
                    releasedId,
                    userId,
                    userId,
                )
            }
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                assertEquals(
                    "ORGANIZATION|$organizationId|RESOURCE|USER|$userId|1",
                    queryString(
                        connection,
                        "SELECT owner_kind || '|' || owner_id || '|' || scope || '|' || placed_by_principal_kind || '|' || placed_by_principal_id || '|' || hold_revision FROM audit_legal_hold WHERE id = ?",
                        activeId,
                    ),
                )
                assertEquals(
                    "PLATFORM|USER|2",
                    queryString(
                        connection,
                        "SELECT owner_kind || '|' || released_by_principal_kind || '|' || hold_revision FROM audit_legal_hold WHERE id = ? AND owner_id IS NULL",
                        releasedId,
                    ),
                )
                assertEquals(1, queryInt(connection, "SELECT count(*) FROM audit_legal_hold_event WHERE hold_id = ?", activeId))
                assertEquals(
                    "PLACED,RELEASED",
                    queryString(
                        connection,
                        "SELECT string_agg(event_kind, ',' ORDER BY event_number) FROM audit_legal_hold_event WHERE hold_id = ?",
                        releasedId,
                    ),
                )
                refusedBy(connection, "organization_id") {
                    execute(connection, "SELECT organization_id FROM audit_legal_hold")
                }
            }
        }
    }

    @Test
    fun `a hold keeps its identity, changes scope only by revision, is never deleted, and is immutable once released`()
    {
        withPreservation { connection, preservation ->
            val holdId = UUID.randomUUID()
            refusedBy(connection, "ck_audit_legal_hold_scope") {
                preservation.insertHold(UUID.randomUUID(), "EXCHANGE", preservation.runtime.exchangeId.toString(), scope = "EVERYTHING")
            }
            refusedBy(connection, "ck_audit_legal_hold_owner") {
                preservation.insertHold(UUID.randomUUID(), "EXCHANGE", preservation.runtime.exchangeId.toString(), ownerKind = "PLATFORM")
            }
            preservation.insertHold(holdId, "EXCHANGE", preservation.runtime.exchangeId.toString())
            refusedBy(connection, "a record preservation hold revision advances by one") {
                execute(connection, "UPDATE audit_legal_hold SET scope = 'DESCENDANTS_AND_REFERENCES' WHERE id = ?", holdId)
            }
            refusedBy(connection, "keeps the identity it was placed with") {
                execute(connection, "UPDATE audit_legal_hold SET resource_id = 'other', hold_revision = hold_revision + 1 WHERE id = ?", holdId)
            }
            execute(
                connection,
                "UPDATE audit_legal_hold SET scope = 'DESCENDANTS_AND_REFERENCES', hold_revision = hold_revision + 1 WHERE id = ?",
                holdId,
            )
            refusedBy(connection, "a record preservation hold is never deleted") {
                execute(connection, "DELETE FROM audit_legal_hold WHERE id = ?", holdId)
            }
            execute(
                connection,
                """
                UPDATE audit_legal_hold
                SET status = 'RELEASED', released_at = now(), released_by_principal_kind = 'USER',
                    released_by_principal_id = ?, release_reason = 'matter closed', hold_revision = hold_revision + 1
                WHERE id = ?
                """.trimIndent(),
                preservation.runtime.template.userId,
                holdId,
            )
            refusedBy(connection, "a released record preservation hold is immutable") {
                execute(connection, "UPDATE audit_legal_hold SET release_reason = 'changed', hold_revision = hold_revision + 1 WHERE id = ?", holdId)
            }
            preservation.insertHoldEvent(holdId, 1, "PLACED")
            refusedBy(connection, "record preservation history is append-only") {
                execute(connection, "DELETE FROM audit_legal_hold_event WHERE hold_id = ?", holdId)
            }
        }
    }

    @Test
    fun `a retention schedule is an append-only version whose disposal age never precedes its minimum retention`()
    {
        withPreservation { connection, preservation ->
            refusedBy(connection, "ck_record_retention_schedule_days") {
                preservation.insertSchedule(UUID.randomUUID(), 1, minimumDays = 30, disposalDays = 10)
            }
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 30, disposalDays = 60)
            refusedBy(connection, "ux_record_retention_schedule_version") {
                preservation.insertSchedule(UUID.randomUUID(), 1, minimumDays = 10, disposalDays = null)
            }
            refusedBy(connection, "record preservation history is append-only") {
                execute(connection, "UPDATE record_retention_schedule SET minimum_retention_days = 1 WHERE id = ?", scheduleId)
            }
        }
    }

    @Test
    fun `a held record cannot be claimed for disposal and a record under disposal cannot be placed on hold`()
    {
        withPreservation { connection, preservation ->
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            val requestId = preservation.runtime.requestId.toString()
            val exchangeId = preservation.runtime.exchangeId.toString()
            preservation.insertHold(UUID.randomUUID(), "EXCHANGE", exchangeId)
            val claimId = UUID.randomUUID()
            preservation.insertClaim(claimId, scheduleId)
            preservation.insertScope(claimId, "EXCHANGE", exchangeId, direct = false)
            preservation.insertScope(claimId, "INFORMATION_REQUEST", requestId, direct = true)
            refusedBy(connection, "a record under disposal cannot be placed on hold") {
                preservation.insertHold(UUID.randomUUID(), "INFORMATION_REQUEST", requestId)
            }
            refusedBy(connection, "a record under disposal cannot be placed on hold") {
                preservation.insertHold(UUID.randomUUID(), "EXCHANGE", exchangeId, scope = "DESCENDANTS_AND_REFERENCES")
            }
            preservation.insertHold(
                UUID.randomUUID(), "EXCHANGE", exchangeId, scope = "DESCENDANTS_AND_REFERENCES",
                ownerKind = "USER", ownerId = preservation.runtime.template.userId,
            )

            val other = SubmissionRuntimeSqlFixture(connection)
            val otherPreservation = PreservationSqlFixture(connection, other)
            val otherSchedule = UUID.randomUUID()
            otherPreservation.insertSchedule(otherSchedule, 1, minimumDays = 0, disposalDays = 0)
            otherPreservation.insertHold(UUID.randomUUID(), "EXCHANGE", other.exchangeId.toString(), scope = "DESCENDANTS_AND_REFERENCES")
            val heldClaim = UUID.randomUUID()
            otherPreservation.insertClaim(heldClaim, otherSchedule)
            refusedBy(connection, "a held record cannot be claimed for disposal") {
                otherPreservation.insertScope(heldClaim, "EXCHANGE", other.exchangeId.toString(), direct = false)
            }
            otherPreservation.insertHold(UUID.randomUUID(), "INFORMATION_REQUEST", other.requestId.toString(), ownerKind = "PLATFORM", ownerId = null)
            refusedBy(connection, "a held record cannot be claimed for disposal") {
                otherPreservation.insertScope(heldClaim, "INFORMATION_REQUEST", other.requestId.toString(), direct = true)
            }
        }
    }

    @Test
    fun `a claim only moves forward and a record under disposal gains no new reference`()
    {
        withPreservation { connection, preservation ->
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            val claimId = UUID.randomUUID()
            refusedBy(connection, "ck_record_disposal_claim_basis") {
                preservation.insertClaim(UUID.randomUUID(), scheduleId = null)
            }
            preservation.insertClaim(claimId, scheduleId)
            refusedBy(connection, "ux_record_disposal_claim_resource") {
                preservation.insertClaim(UUID.randomUUID(), scheduleId)
            }
            preservation.insertObject(claimId, retained = false)
            refusedBy(connection, "a record under disposal cannot gain a reference") {
                execute(connection, "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)", preservation.runtime.exchangeId, preservation.runtime.documentId)
            }
            refusedBy(connection, "a record under disposal cannot gain a reference") {
                preservation.insertLineageFrom(preservation.runtime.requestId)
            }
            refusedBy(connection, "a disposal claim moves from CLAIMED to FINALIZED only forward") {
                execute(
                    connection,
                    "UPDATE record_disposal_claim SET state = 'FINALIZED', objects_deleted_at = now(), finalized_at = now(), claim_revision = claim_revision + 1 WHERE id = ?",
                    claimId,
                )
            }
            refusedBy(connection, "a disposal claim is never deleted") {
                execute(connection, "DELETE FROM record_disposal_claim WHERE id = ?", claimId)
            }
            refusedBy(connection, "a disposal object records its deletion once and nothing else") {
                execute(connection, "UPDATE record_disposal_object SET retained = TRUE, retained_reason = 'SHARED_REFERENCE' WHERE claim_id = ?", claimId)
            }
        }
    }

    @Test
    fun `the disposal function removes the request only after its objects are deleted, keeps shared bytes, and leaves a tombstone`()
    {
        withPreservation { connection, preservation ->
            val runtime = preservation.runtime
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", runtime.requestId)
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "DELETE FROM information_request_evidence_version WHERE information_request_id = ?", runtime.requestId)
            }
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            val claimId = UUID.randomUUID()
            preservation.insertClaim(claimId, scheduleId)
            preservation.insertScope(claimId, "INFORMATION_REQUEST", runtime.requestId.toString(), direct = true)
            val objectId = preservation.insertObject(claimId, retained = false)
            refusedBy(connection, "a record is finalized only after its claimed objects are deleted") {
                queryString(connection, "SELECT record_dispose_information_request(?)", claimId)
            }
            execute(connection, "UPDATE record_disposal_object SET deletion_outcome = 'ABSENT', deleted_at = now() WHERE id = ?", objectId)
            preservation.advance(claimId)

            val removed = requireNotNull(queryString(connection, "SELECT record_dispose_information_request(?)", claimId))

            assertTrue(removed.contains("\"information_request\": 1"), removed)
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", runtime.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_party WHERE information_request_id = ?", runtime.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_evidence_version WHERE information_request_id = ?", runtime.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", runtime.documentVersionId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM document WHERE id = ?", runtime.documentId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM information_request_template_definition WHERE id = ?", runtime.template.definitionId))
            assertEquals("FINALIZED", queryString(connection, "SELECT state FROM record_disposal_claim WHERE id = ?", claimId))
            assertEquals(1, queryInt(connection, "SELECT deleted_object_count FROM record_disposal_tombstone WHERE claim_id = ?", claimId))
            refusedBy(connection, "a finalized disposal claim is immutable") {
                execute(connection, "UPDATE record_disposal_claim SET attempt_count = attempt_count + 1, claim_revision = claim_revision + 1 WHERE id = ?", claimId)
            }

            val shared = SubmissionRuntimeSqlFixture(connection)
            val sharedPreservation = PreservationSqlFixture(connection, shared)
            execute(connection, "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)", shared.exchangeId, shared.documentId)
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", shared.requestId)
            execute(
                connection,
                "UPDATE information_request_template_definition SET origin_kind = 'AD_HOC_REQUEST', origin_request_id = ? WHERE id = ?",
                shared.requestId,
                shared.template.definitionId,
            )
            val sharedSchedule = UUID.randomUUID()
            sharedPreservation.insertSchedule(sharedSchedule, 1, minimumDays = 0, disposalDays = 0)
            val sharedClaim = UUID.randomUUID()
            sharedPreservation.insertClaim(sharedClaim, sharedSchedule)
            sharedPreservation.insertObject(sharedClaim, retained = true)
            sharedPreservation.advance(sharedClaim)

            queryString(connection, "SELECT record_dispose_information_request(?)", sharedClaim)

            assertEquals(1, queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", shared.documentVersionId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM exchange_document WHERE documents_id = ?", shared.documentId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_template_definition WHERE id = ?", shared.template.definitionId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request_template_version WHERE id = ?", shared.template.versionId))
            assertEquals(1, queryInt(connection, "SELECT retained_object_count FROM record_disposal_tombstone WHERE claim_id = ?", sharedClaim))
        }
    }

    @Test
    fun `a record export states its hash, location, and transfer decision and is never rewritten`()
    {
        withPreservation { connection, preservation ->
            refusedBy(connection, "ck_information_request_record_export_hash") {
                preservation.insertExport(UUID.randomUUID(), hash = "not-a-hash")
            }
            refusedBy(connection, "ck_information_request_record_export_transfer") {
                preservation.insertExport(UUID.randomUUID(), transferDecision = "PERMITTED", transferRegion = null)
            }
            refusedBy(connection, "ck_information_request_record_export_kind") {
                preservation.insertExport(UUID.randomUUID(), kind = "SUBJECT_RECORD")
            }
            val exportId = UUID.randomUUID()
            preservation.insertExport(exportId)
            execute(
                connection,
                "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (?, ?)",
                exportId,
                preservation.runtime.requestId,
            )
            refusedBy(connection, "record preservation history is append-only") {
                execute(connection, "UPDATE information_request_record_export SET content_json = '{}' WHERE id = ?", exportId)
            }
        }
    }

    private fun withPreservation(block: (Connection, PreservationSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                block(connection, PreservationSqlFixture(connection, SubmissionRuntimeSqlFixture(connection)))
            }
        }
    }
}

internal class PreservationSqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
)
{
    @Suppress("LongParameterList")
    fun insertHold(
        id: UUID,
        resourceType: String,
        resourceId: String,
        scope: String = "RESOURCE",
        ownerKind: String = "ORGANIZATION",
        ownerId: UUID? = runtime.template.organizationId,
    )
    {
        execute(
            connection,
            """
            INSERT INTO audit_legal_hold (id, owner_kind, owner_id, resource_type, resource_id, reason, status, scope,
                                          effective_from, placed_by_principal_kind, placed_by_principal_id, placed_at,
                                          created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'preserve records', 'ACTIVE', ?, now(), 'USER', ?, now(), now(), now())
            """.trimIndent(),
            id,
            ownerKind,
            ownerId,
            resourceType,
            resourceId,
            scope,
            runtime.template.userId,
        )
    }

    fun insertHoldEvent(holdId: UUID, number: Int, kind: String)
    {
        execute(
            connection,
            """
            INSERT INTO audit_legal_hold_event (id, hold_id, event_number, event_kind, scope, reason, principal_kind,
                                                principal_id, occurred_at)
            VALUES (?, ?, ?, ?, 'RESOURCE', 'preserve records', 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            holdId,
            number,
            kind,
            runtime.template.userId,
        )
    }

    fun insertSchedule(id: UUID, version: Int, minimumDays: Int, disposalDays: Int?)
    {
        execute(
            connection,
            """
            INSERT INTO record_retention_schedule (id, owner_kind, owner_id, resource_type, version_number,
                                                   minimum_retention_days, disposal_after_days,
                                                   recorded_by_principal_kind, recorded_by_principal_id)
            VALUES (?, 'ORGANIZATION', ?, 'INFORMATION_REQUEST', ?, ?, ?, 'USER', ?)
            """.trimIndent(),
            id,
            runtime.template.organizationId,
            version,
            minimumDays,
            disposalDays,
            runtime.template.userId,
        )
    }

    fun insertClaim(id: UUID, scheduleId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO record_disposal_claim (id, resource_type, resource_id, owner_kind, owner_id, basis,
                                               retention_schedule_id, state, claimed_by_principal_kind,
                                               claimed_by_principal_id, claimed_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'ORGANIZATION', ?, 'RETENTION_SCHEDULE', ?, 'CLAIMED', 'SERVICE_ACCOUNT',
                    '00000000-0000-0000-0000-000000000000', now())
            """.trimIndent(),
            id,
            runtime.requestId,
            runtime.template.organizationId,
            scheduleId,
        )
    }

    fun insertScope(claimId: UUID, resourceType: String, resourceId: String, direct: Boolean)
    {
        execute(
            connection,
            "INSERT INTO record_disposal_claim_scope (claim_id, resource_type, resource_id, direct) VALUES (?, ?, ?, ?)",
            claimId,
            resourceType,
            resourceId,
            direct,
        )
    }

    fun insertObject(claimId: UUID, retained: Boolean): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO record_disposal_object (id, claim_id, object_kind, document_id, document_version_id,
                                                storage_provider, storage_locator_kind, storage_locator, retained,
                                                retained_reason)
            VALUES (?, ?, 'DOCUMENT_VERSION', ?, ?, 'OBJECT_STORE', 'OBJECT_KEY', ?, ?, ?)
            """.trimIndent(),
            id,
            claimId,
            runtime.documentId,
            runtime.documentVersionId,
            "document-versions/${runtime.documentVersionId}/process-record.pdf",
            retained,
            if (retained) "SHARED_REFERENCE" else null,
        )
        return id
    }

    fun advance(claimId: UUID)
    {
        execute(
            connection,
            "UPDATE record_disposal_claim SET state = 'OBJECTS_DELETED', objects_deleted_at = now(), claim_revision = claim_revision + 1 WHERE id = ?",
            claimId,
        )
    }

    fun insertLineageFrom(sourceRequestId: UUID)
    {
        val successorId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
            """.trimIndent(),
            successorId,
            runtime.exchangeId,
            runtime.template.versionId,
            runtime.template.organizationId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_lineage (id, successor_request_id, source_request_id, lineage_kind,
                                                     created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, 'SUPPLEMENT', 'USER', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            successorId,
            sourceRequestId,
            runtime.template.userId,
        )
    }

    @Suppress("LongParameterList")
    fun insertExport(
        id: UUID,
        kind: String = "REQUEST_RECORD",
        hash: String = "a".repeat(64),
        transferDecision: String = "NOT_REQUESTED",
        transferRegion: String? = null,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_record_export
                (id, export_kind, information_request_id, owner_kind, owner_id, schema_version, content_json,
                 content_hash_algorithm, content_hash, content_length, storage_location, transfer_region,
                 transfer_decision, requested_by_principal_kind, requested_by_principal_id, requested_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 1, '{}', 'SHA_256', ?, 2, 'primary', ?, ?, 'USER', ?, ?)
            """.trimIndent(),
            id,
            kind,
            runtime.requestId,
            runtime.template.organizationId,
            hash,
            transferRegion,
            transferDecision,
            runtime.template.userId,
            Timestamp.from(Instant.now()),
        )
    }
}
