package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestDelegatedAuthorityTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var service: InformationRequestDelegatedAuthorityService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var requirements: InformationRequestRequirementRepository

    @Inject
    lateinit var parties: InformationRequestPartyRepository

    @Inject
    lateinit var authorities: InformationRequestDelegatedAuthorityRepository

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
    lateinit var gate: InformationRequestMutationGate

    @Inject
    lateinit var audit: AuditRecorder

    @Inject
    lateinit var eligibility: InformationRequestPartyEligibilityService

    @Inject
    lateinit var requirementRoles: InformationRequestRequirementAuthorizationContextProvider

    @Inject
    @TransactionalEventSink
    lateinit var publisher: DomainEventPublisher

    @Test
    fun `committed grants and revocations retain correlated immutable history and both outboxes without replay duplicates`() {
        val fixture = fixture()
        val grant = grantCommand(fixture, "grant-durable")
        val result = service.grant(grant)
        service.grant(grant)
        val revoke = RevokeInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.requestId,
            authorityId = result.authority.id,
            reason = "Private revocation narrative",
            access = grant.access,
            precondition = CommandPrecondition.ExpectedRevision(result.authoritiesETag),
            idempotencyKey = "revoke-durable",
        )
        service.revoke(revoke)
        service.revoke(revoke)

        dataSource.connection.use { connection ->
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation IN ('GRANT_DELEGATED_AUTHORITY', 'REVOKE_DELEGATED_AUTHORITY')",
                    fixture.requestId
                )
            )
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key LIKE 'information_request.delegated_authority.%'",
                    fixture.requestId.toString()
                )
            )
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox a JOIN workflow_event_outbox e ON e.event_id = a.event_id JOIN information_request_transition t ON t.id::text = a.business_transaction_id WHERE t.information_request_id = ?",
                    fixture.requestId
                )
            )
            assertEquals(
                2,
                queryInt(connection, "SELECT count(*) FROM command_receipt WHERE resource_id = ?", fixture.requestId)
            )
            assertEquals(
                3,
                queryInt(connection, "SELECT party_revision FROM information_request WHERE id = ?", fixture.requestId)
            )
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND actor_id = ? AND session_id = 'authority-manager-session'",
                    fixture.requestId.toString(),
                    fixture.template.userId
                )
            )
            assertEquals(
                "false",
                queryString(
                    connection,
                    "SELECT active::text FROM information_request_delegated_authority WHERE id = ?",
                    result.authority.id
                )
            )
        }
    }

    @Test
    fun `a degraded grant audit rolls back authority revision history receipts and events`() {
        val fixture = fixture()
        val failing = withRecording(degradedAudit(), publisher)

        assertThrows(AuditCaptureFailedException::class.java) {
            QuarkusTransaction.requiringNew().run { failing.grant(grantCommand(fixture, "failed-grant")) }
        }
        assertUnchanged(fixture, expectedAuthorities = 0, expectedRevision = 1)
    }

    @Test
    fun `a degraded revocation audit keeps the committed grant active and rolls back the revision and records`() {
        val fixture = fixture()
        val grant = service.grant(grantCommand(fixture, "grant-before-failure"))
        val failing = withRecording(degradedAudit(), publisher)

        assertThrows(AuditCaptureFailedException::class.java) {
            QuarkusTransaction.requiringNew().run {
                failing.revoke(
                    RevokeInformationRequestDelegatedAuthorityCommand(
                        requestId = fixture.requestId,
                        authorityId = grant.authority.id,
                        access = grantCommand(fixture, "unused").access,
                        precondition = CommandPrecondition.ExpectedRevision(grant.authoritiesETag),
                        idempotencyKey = "failed-revoke",
                    ),
                )
            }
        }
        assertUnchanged(fixture, expectedAuthorities = 1, expectedRevision = 2)
        dataSource.connection.use { connection ->
            assertEquals(
                "true",
                queryString(
                    connection,
                    "SELECT active::text FROM information_request_delegated_authority WHERE id = ?",
                    grant.authority.id
                )
            )
            assertEquals(
                null,
                queryString(
                    connection,
                    "SELECT revoked_at::text FROM information_request_delegated_authority WHERE id = ?",
                    grant.authority.id
                )
            )
        }
    }

    @Test
    fun `event publication failure rolls back the authority and the audit intent already written`() {
        val fixture = fixture()
        val failedPublisher = mock<DomainEventPublisher>()
        doThrow(IllegalStateException("Event outbox unavailable")).whenever(failedPublisher).publish(any(), any())
        val failing = withRecording(audit, failedPublisher)

        assertThrows(IllegalStateException::class.java) {
            QuarkusTransaction.requiringNew().run { failing.grant(grantCommand(fixture, "failed-event")) }
        }
        assertUnchanged(fixture, expectedAuthorities = 0, expectedRevision = 1)
    }

    @Test
    fun `a delegate that does not exist is refused and nothing is written`() {
        val fixture = fixture()
        listOf(PrincipalRef.user(UUID.randomUUID()), PrincipalRef.participant(UUID.randomUUID())).forEach { phantom ->
            assertThrows(IllegalArgumentException::class.java) {
                service.grant(grantCommand(fixture, "phantom-${phantom.kind}").copy(delegatePrincipal = phantom))
            }
        }
        assertUnchanged(fixture, expectedAuthorities = 0, expectedRevision = 1)
    }

    private fun fixture(): SubmissionRuntimeSqlFixture = dataSource.connection.use { connection ->
        SubmissionRuntimeSqlFixture(connection).also { fixture ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(), fixture.exchangeId, fixture.template.userId,
            )
        }
    }

    private fun grantCommand(
        fixture: SubmissionRuntimeSqlFixture,
        key: String
    ): GrantInformationRequestDelegatedAuthorityCommand =
        GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.requestId,
            assignedPartyId = fixture.contributorPartyId,
            delegatePrincipal = PrincipalRef.user(fixture.attestorUserId),
            authorityInstrumentRef = "authority-record:private-source",
            access = RequestAccessContext(
                PrincipalRef.user(fixture.template.userId),
                AuthorizationContext(sessionRef = "authority-manager-session")
            ),
            precondition = QuarkusTransaction.requiringNew().call {
                CommandPrecondition.ExpectedRevision(
                    InformationRequestETag.partiesOf(
                        requireNotNull(
                            requests.findById(
                                fixture.requestId
                            )
                        )
                    )
                )
            },
            idempotencyKey = key,
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
    ): InformationRequestDelegatedAuthorityService {
        val history = InformationRequestTransitionHistoryService(
            transitions,
            recorder,
            eventPublisher,
            InformationRequestResponseStart(requests, Clock.systemUTC()),
            actorLabels,
            receiptScope,
        )
        return InformationRequestDelegatedAuthorityService(
            requests,
            parties,
            requirements,
            authorities,
            authorization,
            receipts,
            gate,
            InformationRequestDelegatedAuthorityRecorder(history),
            eligibility,
            requirementRoles,
        )
    }

    private fun assertUnchanged(fixture: SubmissionRuntimeSqlFixture, expectedAuthorities: Int, expectedRevision: Int) {
        dataSource.connection.use { connection ->
            assertEquals(
                expectedAuthorities,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_delegated_authority WHERE information_request_id = ?",
                    fixture.requestId
                )
            )
            assertEquals(
                expectedRevision,
                queryInt(connection, "SELECT party_revision FROM information_request WHERE id = ?", fixture.requestId)
            )
            assertEquals(
                expectedAuthorities,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation IN ('GRANT_DELEGATED_AUTHORITY', 'REVOKE_DELEGATED_AUTHORITY')",
                    fixture.requestId
                )
            )
            assertEquals(
                expectedAuthorities,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key LIKE 'information_request.delegated_authority.%'",
                    fixture.requestId.toString()
                )
            )
            assertEquals(
                expectedAuthorities,
                queryInt(
                    connection,
                    "SELECT count(*) FROM workflow_event_outbox WHERE ordering_key = ?",
                    InformationRequestTransitionHistoryService.orderingKeyOf(fixture.requestId)
                )
            )
            assertEquals(
                expectedAuthorities,
                queryInt(connection, "SELECT count(*) FROM command_receipt WHERE resource_id = ?", fixture.requestId)
            )
        }
    }
}
