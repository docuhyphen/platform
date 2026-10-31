package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditedRead
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeSequenceAllocationRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.audit.informationRequestAuditOwner
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class InformationRequestNoticeQueryService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val states: InformationRequestNoticeStateReader,
    private val allocations: InformationRequestNoticeSequenceAllocationRepository,
    private val requestRepository: InformationRequestRepository,
    private val readAudit: InformationRequestReadAudit,
)
{
    @Transactional
    fun notices(requestId: UUID, access: RequestAccessContext): List<InformationRequestNoticeView>
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS), requestId)
        val request = requestRepository.findById(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        val views = states.views(requestId)
        readAudit.record(
            InformationRequestAuditedRead(
                owner = informationRequestAuditOwner(request),
                eventType = AuditEventType.INFORMATION_REQUEST_NOTICE_READ,
                requestId = requestId,
                actor = InformationRequestAuditActor.of(access),
                payload = mapOf(
                    "noticeCount" to views.size.toString(),
                    "noticeIntentIds" to views.joinToString(",") { it.intent.id.toString() },
                ),
                businessTransactionId = null,
            ),
        )
        return views
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
