package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestLifecycleAuditCaptureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var transitions: InformationRequestTransitionRepository

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var receiptScope: CommandReceiptScope

    @Inject
    @TransactionalEventSink
    lateinit var publisher: DomainEventPublisher

    @Test
    fun `an issuance whose audit record cannot be captured is rolled back whole`() {
        val fixture = dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection) }
        val draftId = insertDraft(fixture)
        val services = runtime.build(draftId, history = historyFailingOn(AuditEventType.INFORMATION_REQUEST_ISSUE.key))
        val precondition = CommandPrecondition.ExpectedRevision(
            QuarkusTransaction.requiringNew()
                .call { InformationRequestETag.aggregateOf(requireNotNull(requests.findById(draftId))) },
        )

        assertThrows(AuditCaptureFailedException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.lifecycle.issue(
                    IssueInformationRequestCommand(
                        requestId = draftId,
                        access = RequestAccessContext(
                            PrincipalRef.user(fixture.template.userId),
                            AuthorizationContext(sessionRef = "owner-session"),
                        ),
                        precondition = precondition,
                        idempotencyKey = "issue-without-audit",
                    ),
                )
            }
        }

        dataSource.connection.use { connection ->
            assertEquals(
                "DRAFT",
                queryString(connection, "SELECT state FROM information_request WHERE id = ?", draftId)
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ?",
                    draftId
                ),
            )
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM request_execution_grant WHERE request_id = ?", draftId)
            )
            assertEquals(0, queryInt(connection, "SELECT count(*) FROM command_receipt WHERE resource_id = ?", draftId))
        }
    }

    private fun historyFailingOn(eventTypeKey: String): InformationRequestTransitionHistoryService {
        val recorder = mock<AuditRecorder>().also {
            whenever(it.record(any())).thenAnswer { invocation ->
                val draft = invocation.getArgument<AuditEventDraft>(0)
                if (draft.eventTypeKey == eventTypeKey) AuditCaptureResult.Degraded(
                    draft.eventId,
                    "Audit outbox unavailable"
                )
                else AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
            }
        }
        return InformationRequestTransitionHistoryService(
            transitions,
            recorder,
            publisher,
            InformationRequestResponseStart(requests, Clock.systemUTC()),
            actorLabels,
            receiptScope,
        )
    }

    private fun insertDraft(fixture: SubmissionRuntimeSqlFixture): UUID {
        val draftId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
                """.trimIndent(),
                draftId,
                fixture.exchangeId,
                fixture.template.versionId,
                fixture.template.organizationId,
                fixture.template.now,
                fixture.template.now,
            )
        }
        return draftId
    }
}
