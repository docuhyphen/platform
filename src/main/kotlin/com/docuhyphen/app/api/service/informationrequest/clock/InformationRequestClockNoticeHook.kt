package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestRespondentWork
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestClockNoticeHook @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val policies: InformationRequestClockPolicyService,
    private val respondentWork: InformationRequestRespondentWork,
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
        val outstanding = respondentWork.rolesWithOutstandingItems(request)
        val parties = partyRepository.findActiveForRequest(request.id)
            .filter {
                it.roleKey in InformationRequestShareRoleKey.RESPONDING_ROLES && it.roleKey in outstanding &&
                        it.principalKind != null && it.principalId != null
            }
            .distinctBy { it.principalKind to it.principalId }
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
}
