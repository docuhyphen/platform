package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestClockNoticeHook @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val policies: InformationRequestClockPolicyService,
)
{
    fun reminded(
        request: InformationRequest,
        clock: InformationRequestClock,
        event: InformationRequestClockEvent
    ): Int =
        owe(
            request,
            event,
            InformationRequestNoticeKind.RESPONSE_REMINDER,
            policies.versionView(clock.policyVersionId)?.version?.reminderCommunicationId
        )

    fun overdue(request: InformationRequest, clock: InformationRequestClock, event: InformationRequestClockEvent): Int =
        owe(
            request,
            event,
            InformationRequestNoticeKind.RESPONSE_OVERDUE,
            policies.versionView(clock.policyVersionId)?.version?.overdueCommunicationId
        )

    private fun owe(
        request: InformationRequest,
        event: InformationRequestClockEvent,
        kind: InformationRequestNoticeKind,
        communicationId: UUID?,
    ): Int
    {
        val parties = partyRepository.findActiveForRequest(request.id)
            .filter { it.roleKey in RESPONDING_ROLES && it.principalKind != null && it.principalId != null }
        parties.forEach { party ->
            intentRepository.save(
                InformationRequestNoticeIntent().apply {
                    informationRequestId = request.id
                    clockEventId = event.id
                    partyId = party.id
                    noticeKind = kind
                    sourceCommunicationId = communicationId
                    createdAt = Timestamp.from(event.occurredAt.toInstant())
                },
            )
        }
        return parties.size
    }

    private companion object
    {
        val RESPONDING_ROLES = setOf(
            InformationRequestShareRoleKey.SUBJECT,
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.PREPARER,
            InformationRequestShareRoleKey.ATTESTOR,
        )
    }
}
