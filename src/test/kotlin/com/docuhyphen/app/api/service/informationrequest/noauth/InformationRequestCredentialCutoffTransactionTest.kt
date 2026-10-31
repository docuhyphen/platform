package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeDecision
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestOwnershipChangeService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.informationrequest.party.*
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnershipChangePolicy
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestCredentialCutoffTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var partySupport: InformationRequestPartyCommandSupport

    @Inject
    lateinit var partyCapacity: InformationRequestPartyCapacity

    @Inject
    lateinit var parentLifecycle: InformationRequestParentLifecycleService

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
    lateinit var history: InformationRequestTransitionHistoryService

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

    @Inject
    lateinit var recipients: ExchangeRecipientService

    @Inject
    lateinit var shares: ShareService

    @Inject
    lateinit var mutationGate: InformationRequestMutationGate

    @Inject
    lateinit var eligibility: InformationRequestPartyEligibilityService

    @Inject
    lateinit var delegatedAuthorities: InformationRequestDelegatedAuthorityService

    @Inject
    lateinit var lifecycle: InformationRequestLifecycleService

    @Inject
    lateinit var exchangeCompletion: InformationRequestExchangeCompletionService

    @Inject
    lateinit var readAccess: InformationRequestNoAuthReadAccessService

    @Inject
    lateinit var groups: PrincipalGroupService

    @Inject
    lateinit var contactProof: InformationRequestContactProofService

    @Test
    fun `revoking a party records every automatic link cutoff with the revoking caller's provenance`() {
        val fixture = fixture()
        val first = fixture.link("ACTIVE")
        val second = fixture.link("ACTIVE")
        fixture.link("REVOKED")
        val firstSession = fixture.session(first, live = true)
        val secondSession = fixture.session(second, live = true)
        val command = revokeParty(fixture, "revoke-party-with-links")

        partyRevocations.revoke(command)
        partyRevocations.revoke(command)

        assertEquals(
            listOf("REVOKE_ACCESS_LINK|PARTY_REVOKED", "REVOKE_ACCESS_LINK|PARTY_REVOKED", "REVOKE_PARTY|"),
            fixture.history(),
        )
        assertCutoffRecords(
            fixture,
            "information_request.access_link.revoke",
            "PARTY_REVOKED",
            2,
            "HUMAN",
            fixture.managerId,
            MANAGER_SESSION
        )
        assertEquals(
            setOf(first.toString(), second.toString()),
            fixture.payloadValues("information_request.access_link.revoke", "shareLinkId")
        )
        assertEquals(listOf("REVOKED", "REVOKED"), listOf(first, second).map(fixture::linkStatus))
        assertTrue(fixture.sessionRevoked(firstSession))
        assertTrue(fixture.sessionRevoked(secondSession))
        fixture.assertNoCredentialMaterialInPayloads(listOf(first, second))
    }

    @Test
    fun `reassigning a party records the cutoff of the previous principal's links`() {
        val fixture = fixture()
        fixture.grantExecution()
        val link = fixture.link("ACTIVE")
        val session = fixture.session(link, live = true)
        val replacementUserId = fixture.user()

        partyRevocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.requestId,
                partyId = fixture.partyId,
                principal = PrincipalRef.user(replacementUserId),
                access = fixture.managerAccess(),
                precondition = fixture.partiesPrecondition(),
                idempotencyKey = "reassign-party-with-link",
            ),
        )

        assertEquals(listOf("REVOKE_ACCESS_LINK|PARTY_REASSIGNED", "REASSIGN|"), fixture.history())
        assertCutoffRecords(
            fixture,
            "information_request.access_link.revoke",
            "PARTY_REASSIGNED",
            1,
            "HUMAN",
            fixture.managerId,
            MANAGER_SESSION
        )
        assertEquals("REVOKED", fixture.linkStatus(link))
        assertTrue(fixture.sessionRevoked(session))
    }

    @Test
    fun `requiring recipient sign-in cuts off every request access link on the Exchange under the setting's author`() {
        val fixture = fixture()
        val first = fixture.link("ACTIVE")
        val second = fixture.link("ACTIVE")
        val session = fixture.session(first, live = true)
        val author = PrincipalRef.user(fixture.managerId)

        repeat(2) {
            QuarkusTransaction.requiringNew().run {
                parentLifecycle.enforceRecipientSignIn(
                    fixture.exchangeId,
                    InformationRequestAuditActor.of(
                        RequestAccessContext(
                            author,
                            AuthorizationContext(sessionRef = MANAGER_SESSION)
                        )
                    )
                )
            }
        }

        assertEquals(
            listOf("REVOKE_ACCESS_LINK|RECIPIENT_SIGN_IN_REQUIRED", "REVOKE_ACCESS_LINK|RECIPIENT_SIGN_IN_REQUIRED"),
            fixture.history(),
        )
        assertCutoffRecords(
            fixture,
            "information_request.access_link.revoke",
            "RECIPIENT_SIGN_IN_REQUIRED",
            2,
            "HUMAN",
            fixture.managerId,
            MANAGER_SESSION
        )
        assertEquals(listOf("REVOKED", "REVOKED"), listOf(first, second).map(fixture::linkStatus))
        assertTrue(fixture.sessionRevoked(session))
        assertTrue(fixture.partyActive())
    }

    @Test
    fun `removing an organization member under a revoking policy records the revocation and its link cutoffs as a system change`() {
        val fixture = fixture()
        val link = fixture.link("ACTIVE")
        val session = fixture.session(link, live = true)
        val revoking = mock<RecordOwnershipChangePolicy>()
        whenever(revoking.decide(any())).thenReturn(RecordOwnershipChangeDecision.REVOKE)
        val ownershipChanges =
            InformationRequestOwnershipChangeService(parties, requests, revoking, partyRevocations, mutationGate)

        QuarkusTransaction.requiringNew()
            .run { ownershipChanges.memberRemoved(fixture.organizationId, fixture.contributorUserId) }

        assertEquals(
            listOf("REVOKE_ACCESS_LINK|ORGANIZATION_MEMBER_REMOVED", "REVOKE_PARTY|ORGANIZATION_MEMBER_REMOVED"),
            fixture.history(),
        )
        assertCutoffRecords(
            fixture,
            "information_request.access_link.revoke",
            "ORGANIZATION_MEMBER_REMOVED",
            1,
            "SYSTEM",
            SYSTEM_ACTOR,
            null
        )
        assertEquals(1, fixture.auditCount("information_request.party.revoke", "ORGANIZATION_MEMBER_REMOVED"))
        assertEquals("REVOKED", fixture.linkStatus(link))
        assertTrue(fixture.sessionRevoked(session))
    }

    @ParameterizedTest
    @ValueSource(strings = ["DELETE", "DEACTIVATE"])
    fun `removing a group revokes the request party it holds and that party's Share as a system change`(removal: String) {
        val fixture = fixture()
        val groupId = fixture.holdPartyAsGroup()

        QuarkusTransaction.requiringNew().run {
            if (removal == "DELETE") groups.deleteGroup(groupId) else groups.deactivateGroup(groupId)
        }

        assertEquals(listOf("REVOKE_PARTY|GROUP_REMOVED"), fixture.history())
        assertEquals(1, fixture.auditCount("information_request.party.revoke", "GROUP_REMOVED"))
        assertFalse(fixture.partyActive())
        assertEquals("REVOKED", fixture.shareStatus())
    }

    @ParameterizedTest
    @ValueSource(strings = ["REJECTED", "RESCINDED", "DELETED"])
    fun `a terminated parent records each live session and access link cutoff before any cancellation`(outcome: String) {
        val fixture = fixture()
        val link = fixture.link("ACTIVE")
        val firstLive = fixture.session(link, live = true)
        val secondLive = fixture.session(link, live = true)
        val expired = fixture.session(link, live = false)
        val deleted = outcome == "DELETED"
        val status = if (deleted) ExchangeStatus.ACCEPTED_STARTED else ExchangeStatus.valueOf(outcome)
        val reason = "PARENT_$outcome"

        repeat(2) {
            QuarkusTransaction.requiringNew().run {
                parentLifecycle.apply(
                    fixture.exchangeId,
                    status,
                    deleted,
                    InformationRequestAuditActor.of(fixture.managerAccess())
                )
            }
        }

        val expected = listOf(
            "REVOKE_ACCESS_SESSION|$reason",
            "REVOKE_ACCESS_SESSION|$reason",
            "REVOKE_ACCESS_LINK|$reason",
        ) + "CANCEL|$reason"
        assertEquals(expected, fixture.history())
        assertCutoffRecords(
            fixture,
            "information_request.access_session.revoke",
            reason,
            2,
            "HUMAN",
            fixture.managerId,
            MANAGER_SESSION
        )
        assertEquals(
            setOf(firstLive.toString(), secondLive.toString()),
            fixture.payloadValues("information_request.access_session.revoke", "accessSessionId")
        )
        assertEquals(
            setOf(link.toString()),
            fixture.payloadValues("information_request.access_session.revoke", "shareLinkId")
        )
        assertCutoffRecords(
            fixture,
            "information_request.access_link.revoke",
            reason,
            1,
            "HUMAN",
            fixture.managerId,
            MANAGER_SESSION
        )
        assertTrue(fixture.sessionRevoked(firstLive))
        assertTrue(fixture.sessionRevoked(secondLive))
        assertFalse(fixture.sessionRevoked(expired))
        assertEquals("REVOKED", fixture.linkStatus(link))
    }

    @Test
    fun `cancelling a request revokes its access links and leaves live sessions reading until they expire`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions, prepare = { connection, runtime ->
            execute(
                connection, """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(), UUID.randomUUID(), runtime.exchangeId, runtime.template.userId
            )
        })
        val issued = scenario.issueSession()
        val owner = RequestAccessContext(
            PrincipalRef.user(scenario.runtime.template.userId),
            AuthorizationContext(sessionRef = MANAGER_SESSION),
        )
        val command = CancelInformationRequestCommand(
            requestId = scenario.requestId,
            reasonCode = "author_cancelled",
            access = owner,
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew().call {
                    InformationRequestETag.aggregateOf(requireNotNull(requests.findById(scenario.requestId)))
                },
            ),
            idempotencyKey = "cancel-request-with-access-links",
        )

        lifecycle.cancel(command)
        lifecycle.cancel(command)

        assertEquals(
            listOf("CANCEL|author_cancelled", "REVOKE_ACCESS_LINK|REQUEST_CANCELLED"),
            credentialHistory(scenario.requestId),
        )
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    """
                    SELECT count(*) FROM audit_outbox
                    WHERE target_id = ? AND event_type_key = 'information_request.access_link.revoke'
                      AND reason = 'REQUEST_CANCELLED' AND actor_id = ? AND session_id = ?
                    """.trimIndent(),
                    scenario.requestId.toString(),
                    scenario.runtime.template.userId,
                    MANAGER_SESSION,
                ),
            )
            assertEquals(
                "REVOKED",
                queryString(connection, "SELECT status FROM share_link WHERE id = ?", scenario.link.id),
            )
            assertNull(
                queryString(
                    connection,
                    "SELECT revoked_at::text FROM request_access_session WHERE id = ?",
                    issued.session.id,
                ),
            )
        }
        assertEquals(
            scenario.participant,
            readAccess.resolve(scenario.requestId, scenario.rawToken, issued.sessionToken).principal
        )
        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            contactProof.issueChallenge(scenario.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, challenge.reasonCode)
    }

    @Test
    fun `ending the Exchange with its open requests cancelled revokes their access links and leaves live sessions reading`() {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions, prepare = { connection, runtime ->
            execute(
                connection,
                "UPDATE information_request SET gates_exchange_closure = FALSE WHERE id = ?",
                runtime.requestId
            )
        })
        val issued = scenario.issueSession()
        val owner = RequestAccessContext(
            PrincipalRef.user(scenario.runtime.template.userId),
            AuthorizationContext(sessionRef = MANAGER_SESSION),
        )

        QuarkusTransaction.requiringNew().run {
            val exchange = requireNotNull(exchanges.lockExchangeForUpdate(scenario.runtime.exchangeId))
            exchangeCompletion.prepareEnding(exchange, InformationRequestAuditActor.of(owner), cancelRemaining = true)
        }

        assertEquals(
            listOf("CANCEL|EXCHANGE_ENDED", "REVOKE_ACCESS_LINK|REQUEST_CANCELLED"),
            credentialHistory(scenario.requestId),
        )
        dataSource.connection.use { connection ->
            assertEquals(
                "REVOKED",
                queryString(connection, "SELECT status FROM share_link WHERE id = ?", scenario.link.id),
            )
            assertNull(
                queryString(
                    connection,
                    "SELECT revoked_at::text FROM request_access_session WHERE id = ?",
                    issued.session.id,
                ),
            )
        }
        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            contactProof.issueChallenge(scenario.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, challenge.reasonCode)
    }

    @ParameterizedTest
    @ValueSource(strings = ["audit", "event"])
    fun `a failed link cutoff record rolls back the party revocation with its links and sessions`(failure: String) {
        val fixture = fixture()
        val link = fixture.link("ACTIVE")
        val session = fixture.session(link, live = true)
        val failing =
            partyRevocationsRecordingLinksWith(failingHistory(failure, "information_request.access_link.revoke"))

        val refusal = assertThrows(Throwable::class.java) {
            QuarkusTransaction.requiringNew().run { failing.revoke(revokeParty(fixture, "failed-cutoff-$failure")) }
        }

        assertExpectedFailure(failure, refusal)
        assertTrue(fixture.history().isEmpty())
        assertEquals("ACTIVE", fixture.linkStatus(link))
        assertFalse(fixture.sessionRevoked(session))
        assertTrue(fixture.partyActive())
        assertEquals(0, fixture.receiptCount())
    }

    @ParameterizedTest
    @ValueSource(strings = ["audit-REJECTED", "event-REJECTED", "audit-DELETED", "event-DELETED"])
    fun `a failed session cutoff record rolls back every parent effect on the request`(case: String) {
        val (failure, outcome) = case.split('-')
        val fixture = fixture()
        val link = fixture.link("ACTIVE")
        val session = fixture.session(link, live = true)
        val deleted = outcome == "DELETED"
        val failing = InformationRequestParentLifecycleService(
            requests,
            sessions,
            failingHistory(failure, "information_request.access_session.revoke"),
            shares,
            mock(),
            mock(),
            mock(),
            mock(),
        )

        val refusal = assertThrows(Throwable::class.java) {
            QuarkusTransaction.requiringNew().run {
                failing.apply(
                    fixture.exchangeId,
                    if (deleted) ExchangeStatus.ACCEPTED_STARTED else ExchangeStatus.REJECTED,
                    deleted,
                    InformationRequestAuditActor.of(fixture.managerAccess()),
                )
            }
        }

        assertExpectedFailure(failure, refusal)
        assertTrue(fixture.history().isEmpty())
        assertFalse(fixture.sessionRevoked(session))
        assertEquals("ISSUED", fixture.requestState())
    }

    private fun credentialHistory(requestId: UUID): List<String> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT mutation, reason_code FROM information_request_transition
            WHERE information_request_id = ? AND mutation IN ('CANCEL', 'REVOKE_ACCESS_LINK', 'REVOKE_ACCESS_SESSION')
            ORDER BY sequence_number
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, requestId)
            statement.executeQuery().use { rows ->
                generateSequence {
                    if (rows.next()) "${rows.getString(1)}|${
                        rows.getString(2).orEmpty()
                    }" else null
                }.toList()
            }
        }
    }

    private fun assertExpectedFailure(failure: String, refusal: Throwable) {
        val causes = generateSequence(refusal) { it.cause }.toList()
        val expected =
            if (failure == "audit") AuditCaptureFailedException::class.java else IllegalStateException::class.java
        assertTrue(causes.any { expected.isInstance(it) }, "Expected $expected in $causes")
    }

    private fun failingHistory(failure: String, eventTypeKey: String): InformationRequestTransitionHistoryService {
        val recorder = if (failure == "audit") mock<AuditRecorder>().also {
            whenever(it.record(any())).thenAnswer { invocation ->
                val draft = invocation.getArgument<AuditEventDraft>(0)
                if (draft.eventTypeKey == eventTypeKey) AuditCaptureResult.Degraded(
                    draft.eventId,
                    "Audit outbox unavailable"
                )
                else AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
            }
        }
        else audit
        val eventPublisher = if (failure == "event") mock<DomainEventPublisher>().also {
            doThrow(IllegalStateException("Event outbox unavailable"))
                .whenever(it).publish(argThat<DomainEvent> { type == eventTypeKey }, any())
        }
        else publisher
        return InformationRequestTransitionHistoryService(
            transitions,
            recorder,
            eventPublisher,
            InformationRequestResponseStart(requests, Clock.systemUTC()),
            actorLabels,
            receiptScope,
        )
    }

    private fun partyRevocationsRecordingLinksWith(linkHistory: InformationRequestTransitionHistoryService) =
        InformationRequestPartyRevocationService(
            parties,
            partySupport,
            partyCapacity,
            mutationGate,
            eligibility,
            InformationRequestTrustedRecipientEligibilityService(
                recipients,
                parties
            ),
            delegatedAuthorities,
            InformationRequestBootstrapShareLinkService(
                requests,
                parties,
                exchanges,
                links,
                authorization,
                receipts,
                sessions,
                limits,
                InformationRequestAccessLinkRecorder(linkHistory),
                mock<InformationRequestContinuationGuard>(),
            ),
            history,
            mock(),
        )

    private fun revokeParty(fixture: CutoffFixture, key: String) = RevokeInformationRequestPartyCommand(
        requestId = fixture.requestId,
        partyId = fixture.partyId,
        access = fixture.managerAccess(),
        precondition = fixture.partiesPrecondition(),
        idempotencyKey = key,
    )

    private fun assertCutoffRecords(
        fixture: CutoffFixture,
        eventTypeKey: String,
        reason: String,
        count: Int,
        actorKind: String,
        actorId: UUID,
        sessionReference: String?,
    ) {
        dataSource.connection.use { connection ->
            assertEquals(
                count,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key = ?",
                    fixture.requestId.toString(),
                    eventTypeKey
                )
            )
            assertEquals(
                count, queryInt(
                    connection,
                    """
                SELECT count(*) FROM audit_outbox a
                JOIN information_request_transition t ON t.id::text = a.business_transaction_id
                JOIN workflow_event_outbox e ON e.event_id = a.event_id
                WHERE a.target_id = ? AND a.event_type_key = ? AND a.reason = ? AND a.actor_kind = ? AND a.actor_id = ?
                  AND COALESCE(a.session_id, '') = ? AND t.reason_code = ? AND t.party_id = ? AND t.actor_id = ?
                  AND e.ordering_key = ?
            """.trimIndent(),
                    fixture.requestId.toString(),
                    eventTypeKey,
                    reason,
                    actorKind,
                    actorId,
                    sessionReference.orEmpty(),
                    reason,
                    fixture.partyId,
                    actorId,
                    InformationRequestTransitionHistoryService.orderingKeyOf(fixture.requestId)
                )
            )
        }
    }

    private fun fixture(): CutoffFixture = dataSource.connection.use { connection ->
        val runtime = SubmissionRuntimeSqlFixture(connection)
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), UUID.randomUUID(), runtime.exchangeId, runtime.template.userId
        )
        val shareId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), shareId, runtime.requestId, runtime.contributorUserId
        )
        execute(
            connection,
            "UPDATE information_request_party SET share_id = ? WHERE id = ?",
            shareId,
            runtime.contributorPartyId
        )
        CutoffFixture(runtime, shareId)
    }

    private inner class CutoffFixture(private val runtime: SubmissionRuntimeSqlFixture, private val shareId: UUID) {
        val requestId: UUID = runtime.requestId
        val exchangeId: UUID = runtime.exchangeId
        val partyId: UUID = runtime.contributorPartyId
        val contributorUserId: UUID = runtime.contributorUserId
        val organizationId: UUID = runtime.template.organizationId
        val managerId: UUID = runtime.template.userId

        fun user(): UUID = UUID.randomUUID().also { id ->
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO app_user
                        (id, is_active, created_date, email, email_verification_completed, is_temporary,
                         sign_in_attempts, exchange_version, multifactor_authentication_type,
                         is_password_temporary, email_mfa_fallback_enabled)
                    VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(), id, "cutoff-party-${id.toString().take(8)}@process.test"
                )
            }
        }

        fun managerAccess() =
            RequestAccessContext(PrincipalRef.user(managerId), AuthorizationContext(sessionRef = MANAGER_SESSION))

        fun partiesPrecondition(): CommandPrecondition = QuarkusTransaction.requiringNew().call {
            CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partiesOf(
                    requireNotNull(
                        requests.findById(
                            requestId
                        )
                    )
                )
            )
        }

        fun link(status: String): UUID = UUID.randomUUID().also { id ->
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO share_link
                        (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                         contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
                    VALUES (?, ?, ?, ?, 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 0, 0, 5, now() + interval '30 days', now(), 'EMAIL_OTP')
                """.trimIndent(), id, shareId, CommandRequestFingerprint.sha256Hex("cutoff-token-$id"), status
                )
            }
        }

        fun session(linkId: UUID, live: Boolean): UUID = UUID.randomUUID().also { id ->
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    """
                    INSERT INTO request_access_session
                        (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                         issued_at, expires_at, credential_hash, created_at)
                    VALUES (?, ?, 'USER', ?, 'EMAIL_OTP', now() - interval '2 hours',
                            now() + (CASE WHEN ? THEN interval '1 hour' ELSE interval '-1 hour' END), ?, now() - interval '2 hours')
                """.trimIndent(),
                    id,
                    linkId,
                    contributorUserId,
                    live,
                    CommandRequestFingerprint.sha256Hex(id.toString())
                )
            }
        }

        fun grantExecution() {
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO request_execution_grant
                        (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                         enforcement_mode, acting_party_cap, issued_at, created_at)
                    VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now())
                """.trimIndent(), UUID.randomUUID(), requestId, organizationId
                )
            }
        }

        fun history(): List<String> = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT mutation, reason_code FROM information_request_transition WHERE information_request_id = ? ORDER BY sequence_number",
            ).use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) "${rows.getString(1)}|${
                            rows.getString(2).orEmpty()
                        }"
                        else null
                    }.toList()
                }
            }
        }

        fun payloadValues(eventTypeKey: String, key: String): Set<String> = dataSource.connection.use { connection ->
            queryStrings(
                connection,
                "SELECT payload_json::jsonb ->> ? FROM audit_outbox WHERE target_id = ? AND event_type_key = ?",
                key,
                requestId.toString(),
                eventTypeKey
            )
        }

        fun auditCount(eventTypeKey: String, reason: String): Int = dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key = ? AND reason = ?",
                requestId.toString(),
                eventTypeKey,
                reason
            )
        }

        fun assertNoCredentialMaterialInPayloads(linkIds: List<UUID>) {
            dataSource.connection.use { connection ->
                val payloads = queryStrings(
                    connection,
                    "SELECT payload_json FROM audit_outbox WHERE target_id = ?",
                    requestId.toString()
                ) +
                        queryStrings(
                            connection,
                            "SELECT envelope_json FROM workflow_event_outbox WHERE ordering_key = ?",
                            InformationRequestTransitionHistoryService.orderingKeyOf(requestId)
                        )
                linkIds.forEach { linkId ->
                    val tokenHash = CommandRequestFingerprint.sha256Hex("cutoff-token-$linkId")
                    assertTrue(payloads.none { it.contains(tokenHash) })
                }
            }
        }

        fun linkStatus(linkId: UUID): String? =
            dataSource.connection.use { queryString(it, "SELECT status FROM share_link WHERE id = ?", linkId) }

        fun shareStatus(): String? =
            dataSource.connection.use { queryString(it, "SELECT status FROM share WHERE id = ?", shareId) }

        fun holdPartyAsGroup(): UUID = UUID.randomUUID().also { groupId ->
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    "UPDATE information_request_party SET principal_kind = 'PRINCIPAL_GROUP', principal_id = ? WHERE id = ?",
                    groupId,
                    partyId,
                )
                execute(
                    connection,
                    "UPDATE share SET principal_kind = 'PRINCIPAL_GROUP', principal_id = ? WHERE id = ?",
                    groupId,
                    shareId,
                )
            }
        }

        fun sessionRevoked(sessionId: UUID): Boolean = dataSource.connection.use {
            queryString(it, "SELECT revoked_at::text FROM request_access_session WHERE id = ?", sessionId) != null
        }

        fun partyActive(): Boolean = dataSource.connection.use {
            queryString(
                it,
                "SELECT active::text FROM information_request_party WHERE id = ?",
                partyId
            ) == "true"
        }

        fun requestState(): String? = dataSource.connection.use {
            queryString(
                it,
                "SELECT state FROM information_request WHERE id = ?",
                requestId
            )
        }

        fun receiptCount(): Int = dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM command_receipt WHERE resource_id = ?",
                requestId
            )
        }
    }

    private companion object {
        const val MANAGER_SESSION = "cutoff-manager-session"
        val SYSTEM_ACTOR: UUID = UUID(0, 0)
    }
}
