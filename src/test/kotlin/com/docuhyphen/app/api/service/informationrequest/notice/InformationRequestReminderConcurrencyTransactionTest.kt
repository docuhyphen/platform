package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestReminderResult
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.command.model.OverlappingCommandOutcome
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.notice.model.ReminderBatchScenario
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
import java.time.Clock
import java.time.Duration
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReminderConcurrencyTransactionTest {
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

    private val race by lazy { OverlappingCommandRace(dataSource) }

    @Test
    fun `overlapping batches whose request order crosses two Exchanges in opposite directions both remind every request`() {
        val scenario = scenario(sharedParent = false)

        val outcome = overlapping(scenario)

        assertTrue(outcome.overlapped, "the second batch waits behind the first")
        assertTrue(
            outcome.results.all { it.isSuccess },
            outcome.results.map { it.exceptionOrNull()?.stackTraceToString() }.toString()
        )
        assertRemindedOnce(scenario)
    }

    @Test
    fun `batches crossing two Exchanges sent one after the other both remind every request`() {
        val scenario = scenario(sharedParent = false)
        val reminders = reminders(scenario)

        listOf(scenario.firstBatch, scenario.secondBatch).forEach { batch ->
            QuarkusTransaction.requiringNew().call {
                reminders.send(SendInformationRequestRemindersCommand(batch, "remind-${UUID.randomUUID()}"))
            }
        }

        assertRemindedOnce(scenario)
    }

    @Test
    fun `overlapping batches under one Exchange both remind every request`() {
        val scenario = scenario(sharedParent = true)

        val outcome = overlapping(scenario)

        assertTrue(outcome.overlapped, "the second batch waits behind the first")
        assertTrue(
            outcome.results.all { it.isSuccess },
            outcome.results.map { it.exceptionOrNull()?.stackTraceToString() }.toString()
        )
        assertRemindedOnce(scenario)
    }

    private fun overlapping(scenario: ReminderBatchScenario): OverlappingCommandOutcome<List<InformationRequestReminderResult>> {
        val reminders = reminders(scenario)
        return race.whileRowLockedInTurn(
            "information_request",
            scenario.firstBatch.first(),
            listOf(scenario.firstBatch, scenario.secondBatch).map { batch ->
                { reminders.send(SendInformationRequestRemindersCommand(batch, "remind-${UUID.randomUUID()}")) }
            },
        )
    }

    private fun reminders(scenario: ReminderBatchScenario): InformationRequestReminderService {
        val owner = InformationRequestOwnerRef(
            InformationRequestOwnerType.ORGANIZATION,
            scenario.fixture.template.organizationId
        )
        val ownerAccess = mock<InformationRequestOwnerScopeAccess>()
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_SEND_REMINDERS)).thenReturn(
            RequestAccessContext(
                PrincipalRef.user(scenario.fixture.template.userId),
                AuthorizationContext(sessionRef = "owner-session")
            ),
        )
        return InformationRequestReminderService(
            ownerAccess,
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
    }

    private fun scenario(sharedParent: Boolean): ReminderBatchScenario =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection)
            val otherExchangeId = if (sharedParent) fixture.exchangeId else UUID.randomUUID()
            if (!sharedParent) {
                execute(
                    connection,
                    """
                    INSERT INTO exchange
                        (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                         created_date, last_activity, description, initial_share_message, name, status)
                    VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect further records', 'Please respond',
                            'Further collection', 'ACCEPTED_STARTED')
                    """.trimIndent(),
                    otherExchangeId,
                    fixture.template.organizationId,
                    fixture.contributorUserId,
                    fixture.template.now,
                    fixture.template.now,
                )
            }
            val prefix = UUID.randomUUID().mostSignificantBits
            val requestIds = (1L..4L).map { UUID(prefix, it) }
            requestIds.forEachIndexed { index, requestId ->
                insertIssuedRequest(
                    connection,
                    fixture,
                    requestId,
                    if (index == 0 || index == 3) fixture.exchangeId else otherExchangeId
                )
            }
            ReminderBatchScenario(
                fixture = fixture,
                requestIds = requestIds,
                firstBatch = listOf(requestIds[0], requestIds[2]),
                secondBatch = listOf(requestIds[1], requestIds[3]),
            )
        }

    private fun insertIssuedRequest(
        connection: java.sql.Connection,
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

    private fun assertRemindedOnce(scenario: ReminderBatchScenario) {
        scenario.requestIds.forEach { requestId ->
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
