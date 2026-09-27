package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAction
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestResponseStart @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val clock: Clock,
)
{
    fun startsWith(command: InformationRequestTransitionHistoryCommand): Boolean =
        command.request.state == InformationRequestState.ISSUED && isResponseWork(command)

    fun start(request: InformationRequest)
    {
        val now = Timestamp.from(clock.instant())
        request.state = InformationRequestState.IN_PROGRESS
        request.startedAt = now
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
    }

    private fun isResponseWork(command: InformationRequestTransitionHistoryCommand): Boolean = when (command.mutation)
    {
        InformationRequestMutation.SAVE_RESPONSE,
        InformationRequestMutation.ATTEST_RESPONSE,
        InformationRequestMutation.SUBMIT,
        -> true
        InformationRequestMutation.ADMINISTER_EVIDENCE -> command.evidence?.action in RESPONDENT_EVIDENCE_ACTIONS
        else -> false
    }

    private companion object
    {
        val RESPONDENT_EVIDENCE_ACTIONS = setOf(
            InformationRequestEvidenceAction.UPLOAD,
            InformationRequestEvidenceAction.REPLACE,
            InformationRequestEvidenceAction.WITHDRAW,
        )
    }
}
