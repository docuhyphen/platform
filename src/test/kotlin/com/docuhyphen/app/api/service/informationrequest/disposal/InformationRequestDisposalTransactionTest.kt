package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalDeletionOutcome
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.disposal.model.SharedSubjectExport
import com.docuhyphen.app.api.service.informationrequest.disposal.model.SubmissionFixtureRef
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAudit
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.SQLException
import java.time.Clock
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestDisposalTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var schedules: RecordRetentionScheduleService
    @Inject lateinit var holds: RecordPreservationHoldService
    @Inject lateinit var disposals: RecordDisposalService
    @Inject lateinit var disposalService: InformationRequestDisposalService
    @Inject lateinit var worker: InformationRequestDisposalWorker

    @Inject
    lateinit var subscriptions: com.docuhyphen.app.api.service.subscription.SubscriptionAccessService

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var gate: InformationRequestMutationGate

    @Inject
    lateinit var eligibility: InformationRequestDisposalEligibility

    @Inject
    lateinit var content: DocumentVersionContentService

    @Inject
    lateinit var preservationAudit: RecordPreservationAudit

    @Inject
    lateinit var accountLinks: com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository

    @ConfigProperty(name = "document.version.storage.local.root-directory")
    lateinit var storageRoot: String

    @Test
    fun `a finished request past its disposal age loses its stored bytes and rows and leaves a tombstone and audit trail`()
    {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 1, disposalDays = 5)
        val shareId = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at) VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())",
                shareId,
                fixture.requestId,
                fixture.principal.id
            )
            execute(
                connection,
                "INSERT INTO share_link (id, share_id, token_hash, status, created_at, link_mode, verification_strength) VALUES (?, ?, ?, 'ACTIVE', now(), 'VERIFICATION_BOOTSTRAP', 'EMAIL_OTP')",
                linkId,
                shareId,
                "hash-$linkId"
            )
            execute(
                connection,
                "INSERT INTO request_access_session (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength, issued_at, expires_at, credential_hash) VALUES (?, ?, 'USER', ?, 'EMAIL_OTP', now(), now() + interval '1 hour', ?)",
                sessionId,
                linkId,
                fixture.principal.id,
                sessionId.toString().replace("-", "").repeat(2)
            )
        }
        val stored = storedFile(fixture)
        assertTrue(stored.isFile)

        val run = worker.run()

        assertTrue(run.finalized >= 1)
        assertFalse(stored.exists())
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", fixture.documentVersionId))
            assertEquals("DELETED", queryString(connection, "SELECT deletion_outcome FROM record_disposal_object WHERE document_version_id = ?", fixture.documentVersionId))
            assertAll(
                {
                    assertEquals(
                        0,
                        queryInt(
                            connection,
                            "SELECT count(*) FROM record_disposal_object WHERE document_version_id = ? AND storage_locator IS NOT NULL",
                            fixture.documentVersionId
                        )
                    )
                },
                {
                    assertEquals(
                        0,
                        queryInt(
                            connection,
                            "SELECT count(*) FROM share WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
                            fixture.requestId
                        )
                    )
                },
                { assertEquals(0, queryInt(connection, "SELECT count(*) FROM share_link WHERE id = ?", linkId)) },
                {
                    assertEquals(
                        0,
                        queryInt(connection, "SELECT count(*) FROM request_access_session WHERE id = ?", sessionId)
                    )
                },
            )
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM record_disposal_tombstone WHERE resource_id = ?", fixture.requestId))
            listOf("record.disposal.claimed", "record.disposal.object_deleted", "record.disposal.finalized").forEach { key ->
                assertEquals(1, queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE event_type_key = ? AND target_id = ?", key, fixture.requestId.toString()), key)
            }
        }
    }

    @Test
    fun `an open request, a request inside its retention, and a request without a disposal age are refused with stable reasons`()
    {
        val open = SubmissionFixtureRef.of(dataSource.connection.use { SubmissionRuntimeSqlFixture(it) })
        schedule(open, minimumDays = 30, disposalDays = null)
        assertRefused(InformationRequestErrorCatalog.RECORD_NOT_FINISHED, open, RecordDisposalBasis.PRIVACY_DELETION)

        val recent = finishedFixture(daysAgo = 1)
        schedule(recent, minimumDays = 30, disposalDays = null)
        assertRefused(InformationRequestErrorCatalog.RETENTION_REQUIRED, recent, RecordDisposalBasis.PRIVACY_DELETION)

        val unscheduled = finishedFixture(daysAgo = 400)
        assertRefused(InformationRequestErrorCatalog.RETENTION_REQUIRED, unscheduled, RecordDisposalBasis.RETENTION_SCHEDULE)
        dataSource.connection.use { connection ->
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'record.disposal.denied' AND target_id = ?", recent.requestId.toString()))
        }
    }

    @Test
    fun `a held request is never claimed and a claimed request cannot be placed on hold`()
    {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val hold = QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    owner = fixture.owner,
                    resourceType = "EXCHANGE",
                    resourceId = fixture.exchangeId.toString(),
                    scope = RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    reason = "preserve the Exchange",
                    caseReference = "matter-1",
                    effectiveFrom = null,
                    principal = fixture.principal,
                ),
            )
        }
        assertRefused(InformationRequestErrorCatalog.RECORD_HELD, fixture, RecordDisposalBasis.RETENTION_SCHEDULE)

        QuarkusTransaction.requiringNew().run {
            holds.release(ReleaseRecordPreservationHoldCommand(hold.hold.id, fixture.owner, "matter closed", fixture.principal))
        }
        val claimed = disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)
        assertTrue(claimed is InformationRequestDisposalOutcome.Claimed)

        val refusal = assertThrows(RecordPreservationException::class.java) {
            QuarkusTransaction.requiringNew().call {
                holds.place(
                    PlaceRecordPreservationHoldCommand(
                        fixture.owner, "INFORMATION_REQUEST", fixture.requestId.toString(), RecordPreservationScope.RESOURCE,
                        "preserve the request", null, null, fixture.principal,
                    ),
                )
            }
        }
        assertEquals(RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS, refusal.reasonCode)
        dataSource.connection.use { connection ->
            assertEquals(
                "PLACED,RELEASED",
                queryString(connection, "SELECT string_agg(event_kind, ',' ORDER BY event_number) FROM audit_legal_hold_event WHERE hold_id = ?", hold.hold.id),
            )
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "INFORMATION_REQUEST_SUBMISSION_PACKAGE",
            "INFORMATION_REQUEST_EVIDENCE_ARTIFACT",
            "INFORMATION_REQUEST_EVIDENCE_VERSION",
            "INFORMATION_REQUEST_OUTBOUND_NOTICE",
            "INFORMATION_REQUEST_RECORD_EXPORT",
        ],
    )
    fun `a hold placed directly on a record the request owns stops the request's disposal`(resourceType: String) {
        val (fixture, records) = finishedFixtureWithRecords(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.owner,
                    resourceType,
                    records.getValue(resourceType).toString(),
                    RecordPreservationScope.RESOURCE,
                    "preserve one record",
                    null,
                    null,
                    fixture.principal,
                ),
            )
        }

        val outcome =
            disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)

        assertEquals(
            InformationRequestErrorCatalog.RECORD_HELD,
            (outcome as? InformationRequestDisposalOutcome.Refused)?.reasonCode
        )
    }

    @Test
    fun `a failed object deletion stays retryable, a missing object is idempotent success, and a retry after deletion finalizes`()
    {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val blocking = storedFile(fixture, asDirectory = true)
        val claimId = claimOf(fixture)

        assertEquals(RecordDisposalState.CLAIMED, disposalService.process(claimId))
        val failed = QuarkusTransaction.requiringNew().call { disposals.view(claimId) }
        assertEquals(1, failed.claim.attemptCount)
        assertEquals("DirectoryNotEmptyException", failed.claim.lastErrorCode)
        val failure = attemptFailures(fixture.requestId).single()
        assertEquals(claimId.toString(), failure.getValue("claimId"))
        assertEquals("OBJECT_DELETION", failure.getValue("stage"))
        assertEquals("DirectoryNotEmptyException", failure.getValue("errorCode"))
        assertEquals("1", failure.getValue("attemptCount"))

        blocking.deleteRecursively()
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimId))
        dataSource.connection.use { connection ->
            assertEquals("ABSENT", queryString(connection, "SELECT deletion_outcome FROM record_disposal_object WHERE claim_id = ?", claimId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId))
        }

        val interrupted = finishedFixture(daysAgo = 10)
        schedule(interrupted, minimumDays = 0, disposalDays = 0)
        val interruptedClaim = claimOf(interrupted)
        QuarkusTransaction.requiringNew().run {
            disposals.view(interruptedClaim).objects.forEach { disposals.recordObjectDeleted(it.id, RecordDisposalDeletionOutcome.DELETED) }
            disposals.markObjectsDeleted(interruptedClaim)
        }

        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(interruptedClaim))
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(interruptedClaim))
        dataSource.connection.use { connection ->
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", interrupted.requestId))
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM record_disposal_tombstone WHERE claim_id = ?", interruptedClaim))
        }
    }

    @Test
    fun `shared bytes are retained and a request another live record follows is refused`()
    {
        val shared = finishedFixture(daysAgo = 10)
        schedule(shared, minimumDays = 0, disposalDays = 0)
        dataSource.connection.use { connection ->
            execute(connection, "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)", shared.exchangeId, shared.documentId)
        }
        val stored = storedFile(shared)
        val outcome = disposalService.claimAndProcess(shared.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, shared.principal)
        assertTrue(outcome is InformationRequestDisposalOutcome.Claimed)
        assertTrue(stored.isFile)
        dataSource.connection.use { connection ->
            assertEquals(1, queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", shared.documentVersionId))
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", shared.requestId))
            assertEquals(1, queryInt(connection, "SELECT retained_object_count FROM record_disposal_tombstone WHERE resource_id = ?", shared.requestId))
        }

        val followed = finishedFixture(daysAgo = 10)
        schedule(followed, minimumDays = 0, disposalDays = 0)
        dataSource.connection.use { connection ->
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
                followed.exchangeId,
                followed.templateVersionId,
                followed.owner.id,
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
                followed.requestId,
                followed.principal.id,
            )
        }
        assertRefused(InformationRequestErrorCatalog.RECORD_REFERENCED, followed, RecordDisposalBasis.RETENTION_SCHEDULE)
    }

    @Test
    fun `scheduled disposal waits while the owner is operationally suspended and resumes once it is lifted`() {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        ownerStatus(fixture, "SUSPENDED")

        worker.run()

        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId)
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM record_disposal_claim WHERE resource_id = ?",
                    fixture.requestId
                )
            )
        }

        ownerStatus(fixture, "ACTIVE")
        worker.run()

        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId)
            )
        }
    }

    private fun ownerStatus(fixture: SubmissionFixtureRef, status: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                fixture.owner.id
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, current_period_end, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', ?, 5, now() + interval '30 days', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.owner.id,
                status,
            )
        }
    }

    @Test
    fun `a superseding follow-up is disposed first and the request it superseded follows it`() {
        val (source, successorId) = supersededPair()
        schedule(source, minimumDays = 0, disposalDays = 0)

        worker.run()
        worker.run()

        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request WHERE id IN (?, ?)",
                    source.requestId,
                    successorId
                )
            )
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM record_disposal_tombstone WHERE resource_id IN (?, ?)",
                    source.requestId,
                    successorId
                )
            )
        }
    }

    @Test
    fun `a privacy deletion claims requests that refer to each other and finalizes the referring one first`() {
        val (source, successorId) = supersededPair()
        schedule(source, minimumDays = 0, disposalDays = null)

        val batch = QuarkusTransaction.requiringNew().call {
            disposalService.claimFinished(
                listOf(source.requestId, successorId),
                RecordDisposalBasis.PRIVACY_DELETION,
                privacyDeletion(source),
                source.principal,
            )
        }

        assertEquals(listOf(successorId, source.requestId), batch.claimed.map { it.claim.resourceId })
        batch.claimed.forEach { assertEquals(RecordDisposalState.FINALIZED, disposalService.process(it.claim.id)) }
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request WHERE id IN (?, ?)",
                    source.requestId,
                    successorId
                )
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'record.disposal.attempt_failed' AND target_id IN (?, ?)",
                    source.requestId.toString(),
                    successorId.toString(),
                ),
            )
        }
    }

    @Test
    fun `a request through which a participant linked an account is disposed and the link keeps the account`() {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val accountLinkId = linkAccountThrough(fixture)

        val outcome = disposalService.claimAndProcess(
            fixture.requestId,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            null,
            fixture.principal
        )

        assertTrue(outcome is InformationRequestDisposalOutcome.Claimed, "$outcome")
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId)
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM participant_account_link WHERE id = ? AND linked_via_information_request_id IS NULL",
                    accountLinkId,
                ),
            )
        }
    }

    @Test
    fun `a retained participant account link loads after its disposed request access link is cleared`() {
        val fixture = finishedFixture(10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val linkId = linkAccountThrough(fixture)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE share SET resource_type = 'INFORMATION_REQUEST', resource_id = ?, role_name = 'CONTRIBUTOR' WHERE id = (SELECT share_id FROM share_link WHERE id = (SELECT linked_via_share_link_id FROM participant_account_link WHERE id = ?))",
                fixture.requestId,
                linkId
            )
        }
        disposalService.claimAndProcess(
            fixture.requestId,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            null,
            fixture.principal
        )
        val restored = QuarkusTransaction.requiringNew().call { requireNotNull(accountLinks.findById(linkId)) }
        assertEquals(fixture.principal.id, restored.appUserId)
        val provenance = runCatching { restored.linkedViaShareLinkId }
        assertTrue(provenance.isSuccess, provenance.exceptionOrNull().toString())
        assertNull(provenance.getOrThrow())
        assertNull(restored.linkedViaInformationRequestId)
    }

    @Test
    fun `a failed finalization is audited with its stage and attempt`() {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val claimId = claimOf(fixture)
        val stored = QuarkusTransaction.requiringNew().call { disposals.view(claimId) }
        val failing = mock<RecordDisposalService>()
        whenever(failing.view(claimId)).thenReturn(
            stored.copy(claim = stored.claim.apply { state = RecordDisposalState.OBJECTS_DELETED }),
        )
        whenever(failing.finalize(claimId)).thenThrow(IllegalStateException("finalization refused"))
        whenever(failing.recordAttemptFailure(eq(claimId), any())).thenReturn(stored.claim.apply { attemptCount = 3 })
        val service = InformationRequestDisposalService(
            requestRepository,
            gate,
            eligibility,
            failing,
            content,
            preservationAudit,
            Clock.systemUTC()
        )

        assertEquals(RecordDisposalState.OBJECTS_DELETED, service.process(claimId))

        val failure = attemptFailures(fixture.requestId).single()
        assertEquals(claimId.toString(), failure.getValue("claimId"))
        assertEquals("FINALIZATION", failure.getValue("stage"))
        assertEquals("IllegalStateException", failure.getValue("errorCode"))
        assertEquals("3", failure.getValue("attemptCount"))
    }

    @Test
    fun `refused requests ahead of an eligible one do not starve it and a repeated pass audits each denial once`() {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val subjectId = UUID.randomUUID()
        val held = (1..55).map { UUID.fromString("00000000-0000-4000-8000-%012d".format(it)) }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO subject_identity_ref (id, owner_type, owner_organization_id, subject_kind, created_at) VALUES (?, 'ORGANIZATION', ?, 'RECORD', now())",
                subjectId,
                fixture.owner.id,
            )
            held.forEach { requestId ->
                execute(
                    connection,
                    """
                    INSERT INTO information_request
                        (id, exchange_id, template_version_id, owner_type, owner_organization_id, state, gates_exchange_closure,
                         aggregate_revision, party_revision, created_at, updated_at, issued_at, cancelled_at)
                    VALUES (?, ?, ?, 'ORGANIZATION', ?, 'CANCELLED', TRUE, 1, 1, now() - INTERVAL '60 days',
                            now() - INTERVAL '60 days', now() - INTERVAL '60 days', now() - INTERVAL '20 days')
                    """.trimIndent(),
                    requestId,
                    fixture.exchangeId,
                    fixture.templateVersionId,
                    fixture.owner.id,
                )
                execute(
                    connection,
                    """
                    INSERT INTO information_request_party
                        (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at,
                         created_at, updated_at)
                    VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    requestId,
                    subjectId,
                )
            }
        }
        QuarkusTransaction.requiringNew().run {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    fixture.owner,
                    "SUBJECT_IDENTITY",
                    subjectId.toString(),
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "preserve the subject",
                    null,
                    null,
                    fixture.principal,
                ),
            )
        }

        worker.run()
        worker.run()

        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId)
            )
            assertEquals(
                held.size,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request WHERE owner_organization_id = ? AND id::text LIKE '00000000-0000-4000-8000-%'",
                    fixture.owner.id
                ),
            )
            held.forEach { requestId ->
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'record.disposal.denied' AND target_id = ?",
                        requestId.toString()
                    ),
                    requestId.toString(),
                )
            }
        }
    }

    @Test
    fun `a subject export shared with an open request keeps its other source from retention and privacy disposal`() {
        val shared = sharedSubjectExport()
        schedule(shared.source, minimumDays = 0, disposalDays = 0)

        listOf(RecordDisposalBasis.RETENTION_SCHEDULE, RecordDisposalBasis.PRIVACY_DELETION).forEach { basis ->
            val refused = disposalService.claim(
                shared.source.requestId,
                basis,
                UUID.randomUUID().takeIf { basis == RecordDisposalBasis.PRIVACY_DELETION },
                shared.source.principal,
            ) as? InformationRequestDisposalOutcome.Refused
            assertEquals(InformationRequestErrorCatalog.RECORD_REFERENCED, refused?.reasonCode, basis.name)
            assertTrue(refused?.detail.orEmpty().contains("SHARED_RECORD_EXPORT"), refused?.detail)
        }
        assertEquals(2, exportSourceCount(shared.exportId))
    }

    @Test
    fun `a subject export shared with a held request keeps its other source from disposal until the hold is released`() {
        val shared = sharedSubjectExport()
        finish(shared.sibling.requestId, daysAgo = 10)
        schedule(shared.source, minimumDays = 0, disposalDays = 0)
        val hold = QuarkusTransaction.requiringNew().call {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    shared.source.owner, "INFORMATION_REQUEST", shared.sibling.requestId.toString(),
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES, "preserve the sibling request", null, null,
                    shared.source.principal,
                ),
            )
        }

        assertRefused(InformationRequestErrorCatalog.RECORD_HELD, shared.source, RecordDisposalBasis.RETENTION_SCHEDULE)
        assertEquals(2, exportSourceCount(shared.exportId))

        QuarkusTransaction.requiringNew().run {
            holds.release(
                ReleaseRecordPreservationHoldCommand(
                    hold.hold.id,
                    shared.source.owner,
                    "matter closed",
                    shared.source.principal
                ),
            )
        }
        val claimed = disposalService.claim(
            shared.source.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, shared.source.principal,
        )
        assertTrue(claimed is InformationRequestDisposalOutcome.Claimed, claimed.toString())
    }

    @Test
    fun `a shared subject export goes with its first disposed source once no other source needs it`() {
        val shared = sharedSubjectExport()
        finish(shared.sibling.requestId, daysAgo = 10)
        schedule(shared.source, minimumDays = 0, disposalDays = 0)
        val claimed = disposalService.claim(
            shared.source.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, shared.source.principal,
        ) as InformationRequestDisposalOutcome.Claimed

        val refusal = assertThrows(RecordPreservationException::class.java) {
            QuarkusTransaction.requiringNew().call {
                holds.place(
                    PlaceRecordPreservationHoldCommand(
                        shared.source.owner, "INFORMATION_REQUEST", shared.sibling.requestId.toString(),
                        RecordPreservationScope.RESOURCE, "preserve the sibling request", null, null,
                        shared.source.principal,
                    ),
                )
            }
        }
        assertEquals(RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS, refusal.reasonCode)
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimed.view.claim.id))
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_record_export WHERE id = ?",
                    shared.exportId
                )
            )
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", shared.sibling.requestId)
            )
        }
    }

    @Test
    fun `a reference committed while a claim waits keeps its object and disposal leaves that object in place`() {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val other = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val stored = storedFile(fixture)

        val outcome = dataSource.connection.use { reference ->
            reference.autoCommit = false
            execute(
                reference,
                "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)",
                other.exchangeId,
                fixture.documentId
            )
            val pending = CompletableFuture.supplyAsync {
                withRequestContext {
                    disposalService.claim(
                        fixture.requestId,
                        RecordDisposalBasis.RETENTION_SCHEDULE,
                        null,
                        fixture.principal
                    )
                }
            }
            awaitLockWaiterOrCompletion(pending)
            reference.commit()
            reference.autoCommit = true
            pending.get(30, TimeUnit.SECONDS)
        }

        val claimed = outcome as InformationRequestDisposalOutcome.Claimed
        assertTrue(claimed.view.objects.single().retained, claimed.view.objects.toString())
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimed.view.claim.id))
        assertTrue(stored.isFile)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM document_version WHERE id = ?", fixture.documentVersionId)
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM exchange_document WHERE documents_id = ?",
                    fixture.documentId
                )
            )
        }
    }

    @Test
    fun `a reference attempted after a claim is refused and the claimed object is deleted`() {
        val fixture = finishedFixture(daysAgo = 10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val other = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val stored = storedFile(fixture)
        val claimed =
            disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal)
                    as InformationRequestDisposalOutcome.Claimed
        assertFalse(claimed.view.objects.single().retained)

        val refusal = assertThrows(SQLException::class.java) {
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)",
                    other.exchangeId,
                    fixture.documentId
                )
            }
        }
        assertTrue(
            refusal.message.orEmpty().contains("a record under disposal cannot gain a reference"),
            refusal.message
        )
        assertEquals(RecordDisposalState.FINALIZED, disposalService.process(claimed.view.claim.id))
        assertFalse(stored.exists())
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM exchange_document WHERE documents_id = ?",
                    fixture.documentId
                )
            )
        }
    }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }

    private fun awaitLockWaiterOrCompletion(pending: CompletableFuture<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!pending.isDone && System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting > 0) return
            Thread.onSpinWait()
        }
    }

    private fun sharedSubjectExport(): SharedSubjectExport =
        dataSource.connection.use { connection ->
            val source = SubmissionRuntimeSqlFixture(connection)
            val sibling = SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = source.template.organizationId)
            val subjectId = UUID.randomUUID()
            source.insertSubject(subjectId, UUID.randomUUID())
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at,
                     created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                sibling.requestId,
                subjectId,
            )
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
                source.template.organizationId,
                "d".repeat(64),
                source.template.userId,
            )
            listOf(source.requestId, sibling.requestId).forEach { requestId ->
                execute(
                    connection,
                    "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (?, ?)",
                    exportId,
                    requestId,
                )
            }
            finish(source.requestId, daysAgo = 10)
            SharedSubjectExport(SubmissionFixtureRef.of(source), SubmissionFixtureRef.of(sibling), exportId)
        }

    private fun finish(requestId: UUID, daysAgo: Int) =
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() - make_interval(days => ?) WHERE id = ?",
                daysAgo,
                requestId,
            )
        }

    private fun exportSourceCount(exportId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_record_export_source WHERE export_id = ?",
                exportId
            )
        }

    private fun attemptFailures(requestId: UUID): List<Map<String, String>> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT payload_json FROM audit_outbox WHERE event_type_key = 'record.disposal.attempt_failed' AND target_id = ? ORDER BY recorded_at",
            ).use { statement ->
                statement.setString(1, requestId.toString())
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                Json.parseToJsonElement(rows.getString(1)).jsonObject
                                    .mapValues { it.value.jsonPrimitive.content },
                            )
                        }
                    }
                }
            }
        }

    private fun assertRefused(reasonCode: String, fixture: SubmissionFixtureRef, basis: RecordDisposalBasis)
    {
        val outcome = disposalService.claim(fixture.requestId, basis, if (basis == RecordDisposalBasis.PRIVACY_DELETION) UUID.randomUUID() else null, fixture.principal)
        assertEquals(reasonCode, (outcome as InformationRequestDisposalOutcome.Refused).reasonCode)
    }

    private fun claimOf(fixture: SubmissionFixtureRef): UUID =
        (disposalService.claim(fixture.requestId, RecordDisposalBasis.RETENTION_SCHEDULE, null, fixture.principal) as InformationRequestDisposalOutcome.Claimed)
            .view.claim.id

    @Test
    fun `repeated disposal failures move behind claims with fewer attempts`() {
        val older = finishedFixture(daysAgo = 10)
        val newer = finishedFixture(daysAgo = 10)
        schedule(older, minimumDays = 0, disposalDays = 0)
        schedule(newer, minimumDays = 0, disposalDays = 0)
        val first = disposalService.claim(
            older.requestId,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            null,
            InformationRequestDisposalService.SYSTEM
        ) as InformationRequestDisposalOutcome.Claimed
        val second = disposalService.claim(
            newer.requestId,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            null,
            InformationRequestDisposalService.SYSTEM
        ) as InformationRequestDisposalOutcome.Claimed
        repeat(2) {
            QuarkusTransaction.requiringNew()
                .run { disposals.recordAttemptFailure(first.view.claim.id, "STORAGE_RETRY") }
        }
        val own = setOf(first.view.claim.id, second.view.claim.id)
        val selected = QuarkusTransaction.requiringNew().call { disposals.openClaimIds(10_000) }.filter { it in own }
        assertEquals(listOf(second.view.claim.id, first.view.claim.id), selected)
    }

    @Test
    fun `an invalid historical schedule does not prevent another owner disposal`() {
        val fixture = finishedFixture(10)
        schedule(fixture, minimumDays = 0, disposalDays = 0)
        val valid = QuarkusTransaction.requiringNew()
            .call { requireNotNull(schedules.current(fixture.owner, "INFORMATION_REQUEST")) }
        val invalid = com.docuhyphen.app.api.model.entity.RecordRetentionSchedule().apply {
            ownerKind = valid.ownerKind
            ownerId = UUID.randomUUID()
            disposalAfterDays = Int.MAX_VALUE
        }
        val configured = mock<RecordRetentionScheduleService>()
        whenever(configured.currentSchedules("INFORMATION_REQUEST")).thenReturn(listOf(invalid, valid))
        val isolated = InformationRequestDisposalWorker(
            disposals,
            configured,
            requestRepository,
            disposalService,
            Clock.systemUTC(),
            subscriptions,
        )
        org.junit.jupiter.api.Assertions.assertDoesNotThrow { isolated.run() }
        dataSource.connection.use {
            assertEquals(0, queryInt(it, "SELECT count(*) FROM information_request WHERE id = ?", fixture.requestId))
        }
    }

    private fun finishedFixture(daysAgo: Int): SubmissionFixtureRef =
        dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() - make_interval(days => ?) WHERE id = ?",
                daysAgo,
                runtime.requestId,
            )
            SubmissionFixtureRef.of(runtime)
        }

    private fun linkAccountThrough(fixture: SubmissionFixtureRef): UUID {
        val participantId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        val shareLinkId = UUID.randomUUID()
        val accountLinkId = UUID.randomUUID()
        val email = "linked-${participantId.toString().take(8)}@process.test"
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO external_participant (id, owner_organization_id, email, email_lower, created_date) VALUES (?, ?, ?, ?, now())",
                participantId,
                fixture.owner.id,
                email,
                email,
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'PARTICIPANT', ?, 'PARTICIPANT', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                fixture.exchangeId,
                participantId,
            )
            execute(
                connection,
                """
                INSERT INTO share_link (id, share_id, token_hash, status, created_at, link_mode, verification_strength)
                VALUES (?, ?, ?, 'ACTIVE', now(), 'VERIFICATION_BOOTSTRAP', 'EMAIL_OTP')
                """.trimIndent(),
                shareLinkId,
                shareId,
                "hash-$shareLinkId",
            )
            execute(
                connection,
                """
                INSERT INTO participant_account_link
                    (id, participant_id, app_user_id, linked_via_information_request_id, linked_via_share_link_id, linked_at)
                VALUES (?, ?, ?, ?, ?, now())
                """.trimIndent(),
                accountLinkId,
                participantId,
                fixture.principal.id,
                fixture.requestId,
                shareLinkId,
            )
        }
        return accountLinkId
    }

    private fun privacyDeletion(fixture: SubmissionFixtureRef): UUID {
        val subjectId = UUID.randomUUID()
        val privacyRequestId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO subject_identity_ref (id, owner_type, owner_organization_id, subject_kind) VALUES (?, 'ORGANIZATION', ?, 'RECORD')",
                subjectId,
                fixture.owner.id,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_privacy_request
                    (id, owner_kind, owner_id, subject_identity_ref_id, request_kind, purpose_key, policy_basis_key, state,
                     recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
                VALUES (?, 'ORGANIZATION', ?, ?, 'DELETION', 'subject.request', 'policy.basis', 'RECORDED', 'USER', ?, now())
                """.trimIndent(),
                privacyRequestId,
                fixture.owner.id,
                subjectId,
                fixture.principal.id,
            )
        }
        return privacyRequestId
    }

    private fun supersededPair(): Pair<SubmissionFixtureRef, UUID> {
        val source = finishedFixture(daysAgo = 10)
        val successorId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state, cancelled_at,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'CANCELLED', now() - interval '5 days', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                successorId,
                source.exchangeId,
                source.templateVersionId,
                source.owner.id,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_lineage (id, successor_request_id, source_request_id, lineage_kind,
                                                         created_by_principal_kind, created_by_principal_id)
                VALUES (?, ?, ?, 'SUPERSEDING', 'USER', ?)
                """.trimIndent(),
                UUID.randomUUID(),
                successorId,
                source.requestId,
                source.principal.id,
            )
            execute(
                connection,
                """
                UPDATE information_request
                SET state = 'SUPERSEDED', cancelled_at = NULL, superseded_at = now() - interval '10 days',
                    superseded_by_request_id = ?
                WHERE id = ?
                """.trimIndent(),
                successorId,
                source.requestId,
            )
        }
        return source to successorId
    }

    private fun finishedFixtureWithRecords(daysAgo: Int): Pair<SubmissionFixtureRef, Map<String, UUID>> =
        dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            val notices = NoticeSqlFixture(connection, ClockSqlFixture(connection, runtime))
            val packageId = UUID.randomUUID()
            runtime.insertPackage(packageId, 1)
            val intentId = UUID.randomUUID()
            notices.insertClockIntent(intentId, "RESPONSE_REMINDER", notices.clockEventId)
            notices.claim(intentId)
            val noticeId = UUID.randomUUID()
            notices.insertNotice(noticeId, intentId)
            val exportId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request_record_export
                    (id, export_kind, information_request_id, owner_kind, owner_id, schema_version, content_json,
                     content_hash_algorithm, content_hash, content_length, storage_location, transfer_decision,
                     requested_by_principal_kind, requested_by_principal_id, requested_at)
                VALUES (?, 'REQUEST_RECORD', ?, 'ORGANIZATION', ?, 1, '{}', 'SHA_256', ?, 2, 'primary', 'NOT_REQUESTED',
                        'USER', ?, now())
                """.trimIndent(),
                exportId,
                runtime.requestId,
                runtime.template.organizationId,
                "c".repeat(64),
                runtime.template.userId,
            )
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() - make_interval(days => ?) WHERE id = ?",
                daysAgo,
                runtime.requestId,
            )
            SubmissionFixtureRef.of(runtime) to mapOf(
                "INFORMATION_REQUEST_SUBMISSION_PACKAGE" to packageId,
                "INFORMATION_REQUEST_EVIDENCE_ARTIFACT" to runtime.artifactId,
                "INFORMATION_REQUEST_EVIDENCE_VERSION" to runtime.evidenceVersionId,
                "INFORMATION_REQUEST_OUTBOUND_NOTICE" to noticeId,
                "INFORMATION_REQUEST_RECORD_EXPORT" to exportId,
            )
        }

    private fun schedule(fixture: SubmissionFixtureRef, minimumDays: Int, disposalDays: Int?)
    {
        QuarkusTransaction.requiringNew().run {
            schedules.publish(PublishRecordRetentionScheduleCommand(fixture.owner, "INFORMATION_REQUEST", minimumDays, disposalDays, fixture.principal))
        }
    }

    private fun storedFile(fixture: SubmissionFixtureRef, asDirectory: Boolean = false): File
    {
        val target = File(storageRoot, "document-versions/${fixture.documentVersionId}/process-record.pdf")
        target.parentFile.mkdirs()
        if (asDirectory)
        {
            target.mkdirs()
            File(target, "held-open").writeText("occupied")
        }
        else
        {
            target.writeText("abc")
        }
        return target
    }
}
