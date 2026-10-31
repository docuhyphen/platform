package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.noauth.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestBootstrapShareLinkTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var service: InformationRequestBootstrapShareLinkService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var parties: InformationRequestPartyRepository

    @Inject
    lateinit var exchanges: ExchangeLookupService

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    @Inject
    lateinit var transitions: InformationRequestTransitionRepository

    @Inject
    lateinit var authorization: AuthorizationService

    @Inject
    lateinit var receipts: CommandReceiptService

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var receiptScope: CommandReceiptScope

    @Inject
    lateinit var limits: InformationRequestAbuseLimits

    @Inject
    lateinit var audit: AuditRecorder

    @Inject
    @TransactionalEventSink
    lateinit var publisher: DomainEventPublisher

    @Test
    fun `parallel issuance under one command key creates exactly one access link`() {
        val fixture = fixture()
        val command = issue(fixture, "issue-parallel")

        val results = inParallel(4) { service.issue(command) }

        assertEquals(1, results.count { it.isSuccess })
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM share_link WHERE share_id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                    fixture.contributorPartyId
                )
            )
        }
    }

    @Test
    fun `bootstrap commands commit exactly one correlated record each and replay never repeats a credential change`() {
        val fixture = fixture()
        val issued = service.issue(issue(fixture, "issue-durable"))
        assertThrows(InformationRequestLifecycleException::class.java) {
            service.issue(
                issue(
                    fixture,
                    "issue-durable"
                )
            )
        }
        val rotate = rotate(fixture, issued.shareLink.id, "rotate-durable")
        service.rotate(rotate)
        assertThrows(InformationRequestLifecycleException::class.java) { service.rotate(rotate) }
        val replace = replace(fixture, issued.shareLink.id, "replace-durable")
        val replacement = service.replace(replace)
        assertThrows(InformationRequestLifecycleException::class.java) { service.replace(replace) }
        val revoke = revoke(fixture, replacement.shareLink.id, "revoke-durable")
        service.revoke(revoke)
        service.revoke(revoke)

        assertRecords(fixture, 4)
        dataSource.connection.use { connection ->
            assertEquals(
                4,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox a JOIN workflow_event_outbox e ON e.event_id = a.event_id JOIN information_request_transition t ON t.id::text = a.business_transaction_id WHERE t.information_request_id = ?",
                    fixture.requestId
                )
            )
            assertEquals(
                4,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND actor_id = ? AND session_id = 'bootstrap-manager-session'",
                    fixture.requestId.toString(),
                    fixture.template.userId
                )
            )
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM share_link WHERE share_id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                    fixture.contributorPartyId
                )
            )
        }
    }

    @Test
    fun `audit degradation rolls back initial issuance and its receipt and records`() {
        val fixture = fixture()
        val failing = withRecording(degradedAudit(), publisher)
        assertThrows(AuditCaptureFailedException::class.java) {
            QuarkusTransaction.requiringNew().run { failing.issue(issue(fixture, "failed-issue")) }
        }
        assertRecords(fixture, 0)
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM share_link WHERE share_id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                    fixture.contributorPartyId
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["rotate", "replace", "revoke"])
    fun `audit degradation preserves the prior link challenge and session and rolls back every new record`(operation: String) {
        failedChange(operation, degradedAudit(), publisher, AuditCaptureFailedException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["rotate", "replace", "revoke"])
    fun `event publication failure rolls back the changed link session and already written audit intent`(operation: String) {
        val failedPublisher = mock<DomainEventPublisher>()
        doThrow(IllegalStateException("Event outbox unavailable")).whenever(failedPublisher).publish(any(), any())
        failedChange(operation, audit, failedPublisher, IllegalStateException::class.java)
    }

    private fun failedChange(
        operation: String,
        recorder: AuditRecorder,
        eventPublisher: DomainEventPublisher,
        exception: Class<out Throwable>
    ) {
        val fixture = fixture()
        val issued = service.issue(issue(fixture, "issue-before-failure"))
        val sessionId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE share_link SET contact_otp_hash = ?, contact_otp_expires_at = now() + interval '10 minutes', used_count = 2 WHERE id = ?",
                "a".repeat(64),
                issued.shareLink.id
            )
            execute(
                connection,
                """
                INSERT INTO request_access_session
                    (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength, issued_at, expires_at, credential_hash, created_at)
                VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), now() + interval '1 hour', ?, now())
            """.trimIndent(),
                sessionId,
                issued.shareLink.id,
                participantOf(connection, fixture),
                CommandRequestFingerprint.sha256Hex(sessionId.toString())
            )
        }
        val failing = withRecording(recorder, eventPublisher)
        assertThrows(exception) {
            QuarkusTransaction.requiringNew().run {
                when (operation) {
                    "rotate" -> failing.rotate(rotate(fixture, issued.shareLink.id, "failed-rotate"))
                    "replace" -> failing.replace(replace(fixture, issued.shareLink.id, "failed-replace"))
                    "revoke" -> failing.revoke(revoke(fixture, issued.shareLink.id, "failed-revoke"))
                }
            }
        }
        assertRecords(fixture, 1)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM share_link WHERE share_id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                    fixture.contributorPartyId
                )
            )
            assertEquals(
                "ACTIVE",
                queryString(connection, "SELECT status FROM share_link WHERE id = ?", issued.shareLink.id)
            )
            assertEquals(
                issued.shareLink.tokenHash,
                queryString(connection, "SELECT token_hash FROM share_link WHERE id = ?", issued.shareLink.id)
            )
            assertEquals(
                "a".repeat(64),
                queryString(connection, "SELECT contact_otp_hash FROM share_link WHERE id = ?", issued.shareLink.id)
            )
            assertEquals(2, queryInt(connection, "SELECT used_count FROM share_link WHERE id = ?", issued.shareLink.id))
            assertEquals(
                0,
                queryInt(connection, "SELECT rotation_count FROM share_link WHERE id = ?", issued.shareLink.id)
            )
            assertNull(
                queryString(
                    connection,
                    "SELECT revoked_at::text FROM request_access_session WHERE id = ?",
                    sessionId
                )
            )
        }
    }

    private fun participantOf(connection: java.sql.Connection, fixture: SubmissionRuntimeSqlFixture): UUID =
        UUID.fromString(
            requireNotNull(
                queryString(
                    connection,
                    "SELECT principal_id::text FROM information_request_party WHERE id = ?",
                    fixture.contributorPartyId
                )
            )
        )

    private fun fixture(): SubmissionRuntimeSqlFixture = dataSource.connection.use { connection ->
        SubmissionRuntimeSqlFixture(connection).also { fixture ->
            execute(
                connection, """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(), UUID.randomUUID(), fixture.exchangeId, fixture.template.userId
            )
            contributorAsParticipant(connection, fixture)
        }
    }

    private fun access(fixture: SubmissionRuntimeSqlFixture) = RequestAccessContext(
        PrincipalRef.user(fixture.template.userId),
        AuthorizationContext(sessionRef = "bootstrap-manager-session")
    )

    private fun precondition(fixture: SubmissionRuntimeSqlFixture) = QuarkusTransaction.requiringNew().call {
        CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(requireNotNull(parties.findById(fixture.contributorPartyId))))
    }

    private fun issue(fixture: SubmissionRuntimeSqlFixture, key: String) =
        IssueInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            fixture.contributorPartyId,
            access(fixture),
            precondition(fixture),
            key
        )

    private fun rotate(fixture: SubmissionRuntimeSqlFixture, linkId: UUID, key: String) =
        RotateInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            linkId,
            access(fixture),
            precondition(fixture),
            key
        )

    private fun replace(fixture: SubmissionRuntimeSqlFixture, linkId: UUID, key: String) =
        ReplaceInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            linkId,
            access(fixture),
            precondition(fixture),
            key
        )

    private fun revoke(fixture: SubmissionRuntimeSqlFixture, linkId: UUID, key: String) =
        RevokeInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            linkId,
            access(fixture),
            precondition(fixture),
            key
        )

    private fun degradedAudit(): AuditRecorder = mock<AuditRecorder>().also {
        whenever(it.record(any())).thenReturn(
            AuditCaptureResult.Degraded(
                UUID.randomUUID(),
                "Audit outbox unavailable"
            )
        )
    }

    private fun withRecording(
        recorder: AuditRecorder,
        eventPublisher: DomainEventPublisher
    ): InformationRequestBootstrapShareLinkService {
        val history = InformationRequestTransitionHistoryService(
            transitions,
            recorder,
            eventPublisher,
            InformationRequestResponseStart(requests, Clock.systemUTC()),
            actorLabels,
            receiptScope,
        )
        return InformationRequestBootstrapShareLinkService(
            requests,
            parties,
            exchanges,
            links,
            authorization,
            receipts,
            sessions,
            limits,
            InformationRequestAccessLinkRecorder(history),
            mock<InformationRequestContinuationGuard>(),
        )
    }

    private fun assertRecords(fixture: SubmissionRuntimeSqlFixture, count: Int) {
        dataSource.connection.use { connection ->
            assertEquals(
                count,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation LIKE '%ACCESS_LINK'",
                    fixture.requestId
                )
            )
            assertEquals(
                count,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key LIKE 'information_request.access_link.%'",
                    fixture.requestId.toString()
                )
            )
            assertEquals(
                count,
                queryInt(
                    connection,
                    "SELECT count(*) FROM workflow_event_outbox WHERE ordering_key = ?",
                    InformationRequestTransitionHistoryService.orderingKeyOf(fixture.requestId)
                )
            )
            assertEquals(
                count,
                queryInt(connection, "SELECT count(*) FROM command_receipt WHERE resource_id = ?", fixture.requestId)
            )
        }
    }

    private fun <T> inParallel(count: Int, call: () -> T): List<Result<T>> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(count)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val calls = (1..count).map {
                pool.submit<Result<T>> {
                    start.await()
                    val context = io.quarkus.arc.Arc.container().requestContext()
                    context.activate()
                    try {
                        runCatching(call)
                    } finally {
                        context.terminate()
                    }
                }
            }
            start.countDown()
            return calls.map { it.get(60, java.util.concurrent.TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
