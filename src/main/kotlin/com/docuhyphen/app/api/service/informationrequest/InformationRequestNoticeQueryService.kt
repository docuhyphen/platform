package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoticeView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeSequenceAllocationRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestNoticeQueryService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val states: InformationRequestNoticeStateReader,
    private val allocations: InformationRequestNoticeSequenceAllocationRepository,
)
{
    fun notices(requestId: UUID, access: RequestAccessContext): List<InformationRequestNoticeView>
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS), requestId)
        return states.views(requestId)
    }

    fun allocationsOf(view: InformationRequestNoticeView) =
        view.notice?.let { allocations.findForNotice(it.id) }.orEmpty()

    companion object
    {
        fun maskedEndpoint(endpoint: String?): String?
        {
            val value = endpoint ?: return null
            val at = value.indexOf('@')
            if (at <= 0) return "***"
            return "${value.first()}***${value.substring(at)}"
        }
    }
}
