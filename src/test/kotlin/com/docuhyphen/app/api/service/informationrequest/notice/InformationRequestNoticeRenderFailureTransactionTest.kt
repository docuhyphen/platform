package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSourceKind
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestRenderedNotice
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeRenderFailureRepository
import com.docuhyphen.app.api.repository.informationrequest.oversight.InformationRequestHealthRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestRespondentWorkDoubles
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestNoticeRenderFailureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var worker: InformationRequestNoticeWorker

    @Inject
    lateinit var states: InformationRequestNoticeStateReader

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository

    @Inject
    lateinit var renderFailures: InformationRequestNoticeRenderFailureRepository

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @Inject
    lateinit var health: InformationRequestHealthRepository

    @Test
    fun `a notice whose render keeps failing is recorded each time and given up after five attempts`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        remind(fixture)
        val failing = intentFor(fixture, fixture.attestorPartyId)
        val renderer = failingFor(setOf(failing))

        repeat(6) { worker.dispatchForRequest(fixture.requestId) }

        verify(renderer, times(5)).render(any(), argThat { id == failing }, any())
        assertEquals(
            5,
            QuarkusTransaction.requiringNew()
                .call { renderFailures.countsForRequests(listOf(fixture.requestId)) }[failing]
        )
        assertEquals(
            InformationRequestNoticeDeliveryState.FAILED,
            QuarkusTransaction.requiringNew().call { states.states(fixture.requestId) }[failing]
        )
        assertEquals(
            InformationRequestNoticeDeliveryState.DELIVERED,
            QuarkusTransaction.requiringNew().call { states.states(fixture.requestId) }[intentFor(
                fixture,
                fixture.contributorPartyId
            )],
        )
        assertFalse(
            failing in QuarkusTransaction.requiringNew().call { intentRepository.findUnclaimedIds(UNBOUNDED, 5) })
    }

    @Test
    fun `an intent that failed to render waits behind newer intents instead of holding them back`() {
        val older = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        remind(older)
        val newer = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        remind(newer)
        val failing = intentFor(older, older.attestorPartyId)
        failingFor(setOf(failing))

        worker.dispatchForRequest(older.requestId)

        val waiting =
            setOf(failing, intentFor(newer, newer.contributorPartyId), intentFor(newer, newer.attestorPartyId))
        val order = QuarkusTransaction.requiringNew().call { intentRepository.findUnclaimedIds(UNBOUNDED, 5) }
            .filter { it in waiting }
        assertEquals(3, order.size)
        assertEquals(failing, order.last())
    }

    @Test
    fun `an intent that gave up rendering no longer counts as an overdue notice intent in the health report`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        remind(fixture)
        val failing = intentFor(fixture, fixture.attestorPartyId)
        failingFor(setOf(failing))
        val horizon = Instant.now().plusSeconds(3600)

        worker.dispatchForRequest(fixture.requestId)
        val countedWhileRetrying = overdue(horizon)

        repeat(5) { worker.dispatchForRequest(fixture.requestId) }

        assertEquals(
            5,
            QuarkusTransaction.requiringNew()
                .call { renderFailures.countsForRequests(listOf(fixture.requestId)) }[failing]
        )
        assertEquals(countedWhileRetrying - 1, overdue(horizon))
    }

    private fun overdue(horizon: Instant): Long =
        QuarkusTransaction.requiringNew().call {
            health.countNoticeIntentsWithoutNoticeBefore(horizon, InformationRequestNoticeStateReader.MAXIMUM_ATTEMPTS)
        }

    private fun failingFor(intents: Set<UUID>): InformationRequestNoticeRenderer {
        val renderer = mock<InformationRequestNoticeRenderer>()
        whenever(renderer.render(any(), any(), any())).thenAnswer { invocation ->
            val intent = invocation.getArgument<InformationRequestNoticeIntent>(1)
            if (intent.id in intents) throw IllegalStateException("The notice could not be rendered")
            InformationRequestRenderedNotice(
                subject = "Reminder",
                body = "Please respond",
                sourceKind = InformationRequestNoticeSourceKind.PLATFORM_DEFAULT,
                sourceCommunicationId = null,
                sourceContentHash = HASH,
                renderedContentHash = HASH,
                allocations = emptyList(),
            )
        }
        QuarkusMock.installMockForType(renderer, InformationRequestNoticeRenderer::class.java)
        return renderer
    }

    private fun remind(fixture: SubmissionRuntimeSqlFixture) {
        val owner =
            InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, fixture.template.organizationId)
        val ownerAccess = mock<InformationRequestOwnerScopeAccess>()
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_SEND_REMINDERS)).thenReturn(
            RequestAccessContext(
                PrincipalRef.user(fixture.template.userId),
                AuthorizationContext(sessionRef = "owner-session")
            ),
        )
        val reminders = InformationRequestReminderService(
            ownerAccess,
            requestRepository,
            runtime.build(fixture.requestId).gate,
            partyRepository,
            intentRepository,
            transitionHistory,
            runtime.commandReceiptService,
            InformationRequestAbuseLimits(reminderCooldown = Duration.ofHours(24)),
            Clock.systemUTC(),
            InformationRequestRespondentWorkDoubles.everyRespondingRoleOutstanding(),
        )
        QuarkusTransaction.requiringNew().call {
            reminders.send(
                SendInformationRequestRemindersCommand(
                    listOf(fixture.requestId),
                    "remind-${UUID.randomUUID()}"
                )
            )
        }
    }

    private fun intentFor(fixture: SubmissionRuntimeSqlFixture, partyId: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            intentRepository.findForRequest(fixture.requestId).single { it.partyId == partyId }.id
        }

    private companion object {
        const val UNBOUNDED = 100_000
        const val HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
