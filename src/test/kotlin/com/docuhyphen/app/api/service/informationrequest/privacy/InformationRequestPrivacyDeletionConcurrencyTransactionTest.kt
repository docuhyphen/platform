package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestState
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyTargetOutcome
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRequestView
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.command.model.OverlappingCommandOutcome
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestReminderService
import com.docuhyphen.app.api.service.informationrequest.privacy.model.PrivacyDeletionReminderScenario
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestRespondentWorkDoubles
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestPrivacyDeletionConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var gate: InformationRequestMutationGate

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Inject
    lateinit var privacyRepository: InformationRequestPrivacyRequestRepository

    @Inject
    lateinit var restrictionRepository: InformationRequestSubjectRestrictionRepository

    @Inject
    lateinit var exports: InformationRequestRecordExportService

    @Inject
    lateinit var restrictions: InformationRequestSubjectRestrictionService

    @Inject
    lateinit var corrections: InformationRequestItemCorrectionService

    @Inject
    lateinit var disposals: InformationRequestDisposalService

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    private val race by lazy { OverlappingCommandRace(dataSource) }

    @Test
    fun `a privacy deletion and a reminder batch whose requests cross two Exchanges in opposite directions both finish`() {
        val scenario = scenario(sharedParent = false)
        val deleted = AtomicReference<InformationRequestPrivacyRequestView>()

        val outcome = overlapping(scenario, deleted)

        assertTrue(outcome.overlapped, "the reminders wait behind the privacy deletion")
        assertTrue(
            outcome.results.all { it.isSuccess },
            outcome.results.map { it.exceptionOrNull()?.stackTraceToString() }.toString()
        )
        assertDeletedAndReminded(scenario, deleted.get())
    }

    @Test
    fun `a privacy deletion and a reminder batch across two Exchanges finish one after the other`() {
        val scenario = scenario(sharedParent = false)

        val deleted = QuarkusTransaction.requiringNew().call { privacy(scenario).submit(deletion(scenario)) }
        QuarkusTransaction.requiringNew().call {
            reminders(scenario).send(
                SendInformationRequestRemindersCommand(
                    scenario.remindedRequestIds,
                    "remind-${UUID.randomUUID()}"
                )
            )
        }

        assertDeletedAndReminded(scenario, deleted)
    }

    @Test
    fun `a privacy deletion and a reminder batch under one Exchange both finish`() {
        val scenario = scenario(sharedParent = true)
        val deleted = AtomicReference<InformationRequestPrivacyRequestView>()

        val outcome = overlapping(scenario, deleted)

        assertTrue(outcome.overlapped, "the reminders wait behind the privacy deletion")
        assertTrue(
            outcome.results.all { it.isSuccess },
            outcome.results.map { it.exceptionOrNull()?.stackTraceToString() }.toString()
        )
        assertDeletedAndReminded(scenario, deleted.get())
    }

    private fun overlapping(
        scenario: PrivacyDeletionReminderScenario,
        deleted: AtomicReference<InformationRequestPrivacyRequestView>,
    ): OverlappingCommandOutcome<Unit> {
        val privacy = privacy(scenario)
        val reminders = reminders(scenario)
        return race.whileRowLockedInTurn(
            "information_request",
            scenario.finishedSubjectRequestId,
            listOf(
                { deleted.set(privacy.submit(deletion(scenario))) },
                {
                    reminders.send(
                        SendInformationRequestRemindersCommand(
                            scenario.remindedRequestIds,
                            "remind-${UUID.randomUUID()}"
                        )
                    )
                    Unit
                },
            ),
        )
    }

    private fun privacy(scenario: PrivacyDeletionReminderScenario): InformationRequestPrivacyService =
        InformationRequestPrivacyService(
            ownerAccess(scenario, Action.INFORMATION_REQUEST_MANAGE_PRIVACY),
            privacyRepository,
            exports,
            restrictions,
            corrections,
            disposals,
            commandReceiptService,
            auditRecorder,
            actorLabels,
            Clock.systemUTC(),
        )

    private fun reminders(scenario: PrivacyDeletionReminderScenario): InformationRequestReminderService =
        InformationRequestReminderService(
            ownerAccess(scenario, Action.INFORMATION_REQUEST_SEND_REMINDERS),
            requestRepository,
            gate,
            partyRepository,
            intentRepository,
            transitionHistory,
            commandReceiptService,
            InformationRequestAbuseLimits(reminderCooldown = Duration.ofHours(24)),
            Clock.systemUTC(),
            InformationRequestRespondentWorkDoubles.everyRespondingRoleOutstanding(),
        )

    private fun ownerAccess(
        scenario: PrivacyDeletionReminderScenario,
        action: Action
    ): InformationRequestOwnerScopeAccess {
        val owner = InformationRequestOwnerRef(
            InformationRequestOwnerType.ORGANIZATION,
            scenario.fixture.template.organizationId
        )
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(owner, action)).thenReturn(
            RequestAccessContext(
                PrincipalRef.user(scenario.fixture.template.userId),
                AuthorizationContext(sessionRef = "owner-session")
            ),
        )
        return access
    }

    private fun deletion(scenario: PrivacyDeletionReminderScenario) =
        RecordInformationRequestPrivacyRequestCommand(
            scenario.subjectId,
            InformationRequestPrivacyRequestKind.DELETION,
            "subject.request",
            "policy.basis",
            idempotencyKey = "privacy-${UUID.randomUUID()}",
            authorization = AuthorizationContext(),
        )

    private fun scenario(sharedParent: Boolean): PrivacyDeletionReminderScenario =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection)
            val otherExchangeId = if (sharedParent) fixture.exchangeId else UUID.randomUUID()
            if (!sharedParent) insertExchange(connection, fixture, otherExchangeId)
            val higher = maxOf(fixture.exchangeId, otherExchangeId)
            val lower = minOf(fixture.exchangeId, otherExchangeId)
            val prefix = UUID.randomUUID().mostSignificantBits
            val requestIds = (1L..4L).map { UUID(prefix, it) }
            requestIds.forEachIndexed { index, requestId ->
                insertIssuedRequest(connection, fixture, requestId, if (index == 0 || index == 3) higher else lower)
            }
            val subjectId = UUID.randomUUID()
            fixture.insertSubject(subjectId)
            listOf(requestIds[0], requestIds[2]).forEach { insertSubjectParty(connection, fixture, it, subjectId) }
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                requestIds[0]
            )
            PrivacyDeletionReminderScenario(
                fixture = fixture,
                subjectId = subjectId,
                finishedSubjectRequestId = requestIds[0],
                openSubjectRequestId = requestIds[2],
                remindedRequestIds = listOf(requestIds[1], requestIds[3]),
            )
        }

    private fun insertExchange(connection: Connection, fixture: SubmissionRuntimeSqlFixture, exchangeId: UUID) {
        execute(
            connection,
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect further records', 'Please respond',
                    'Further collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
            exchangeId,
            fixture.template.organizationId,
            fixture.contributorUserId,
            fixture.template.now,
            fixture.template.now,
        )
    }

    private fun insertIssuedRequest(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        requestId: UUID,
        exchangeId: UUID
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, ?, ?, ?)
            """.trimIndent(),
            requestId,
            exchangeId,
            fixture.template.versionId,
            fixture.template.organizationId,
            fixture.template.now,
            fixture.template.now,
            fixture.template.now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'CONTRIBUTOR', 'USER', ?, TRUE, 1, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            fixture.contributorUserId,
            fixture.template.now,
            fixture.template.now,
            fixture.template.now,
        )
    }

    private fun insertSubjectParty(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        requestId: UUID,
        subjectId: UUID
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            subjectId,
            fixture.template.now,
            fixture.template.now,
            fixture.template.now,
        )
    }

    private fun assertDeletedAndReminded(
        scenario: PrivacyDeletionReminderScenario,
        deleted: InformationRequestPrivacyRequestView
    ) {
        assertEquals(InformationRequestPrivacyRequestState.COMPLETED, deleted.request.state)
        assertEquals(
            mapOf(
                scenario.finishedSubjectRequestId to InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED,
                scenario.openSubjectRequestId to InformationRequestPrivacyTargetOutcome.SKIPPED_OPEN,
            ),
            deleted.targets.associate { it.requestId to it.outcome },
        )
        assertEquals(
            0,
            count("SELECT count(*) FROM information_request WHERE id = ?", scenario.finishedSubjectRequestId)
        )
        assertEquals(1, count("SELECT count(*) FROM information_request WHERE id = ?", scenario.openSubjectRequestId))
        scenario.remindedRequestIds.forEach { requestId ->
            assertEquals(
                1,
                count(
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation = 'SEND_REMINDER'",
                    requestId
                ),
                "reminders recorded for $requestId",
            )
            assertEquals(
                1,
                count(
                    "SELECT count(*) FROM information_request_notice_intent WHERE information_request_id = ? AND notice_kind = 'RESPONSE_REMINDER'",
                    requestId
                ),
                "reminder notices owed for $requestId",
            )
            assertEquals(
                1,
                count(
                    "SELECT count(*) FROM command_receipt WHERE resource_id = ? AND operation_name = 'send-information-request-reminder'",
                    requestId
                ),
                "reminder receipts for $requestId",
            )
        }
    }

    private fun count(sql: String, requestId: UUID): Int =
        dataSource.connection.use { connection -> queryInt(connection, sql, requestId) }
}
