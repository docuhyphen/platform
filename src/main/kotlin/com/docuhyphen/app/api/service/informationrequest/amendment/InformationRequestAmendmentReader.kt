package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentView
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestAmendmentChangeRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestAmendmentRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeStateReader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestAmendmentReader @Inject constructor(
    private val amendmentRepository: InformationRequestAmendmentRepository,
    private val changeRepository: InformationRequestAmendmentChangeRepository,
    private val noticeRepository: InformationRequestNoticeIntentRepository,
    private val noticeStates: InformationRequestNoticeStateReader,
)
{
    fun views(requestId: UUID): List<InformationRequestAmendmentView>
    {
        val changes = changeRepository.findForRequest(requestId).groupBy { it.amendmentId }
        val notices = noticeRepository.findForRequest(requestId).groupBy { it.amendmentId }
        val states = noticeStates.states(requestId)
        return amendmentRepository.findForRequest(requestId).map { amendment ->
            InformationRequestAmendmentView(amendment, changes[amendment.id].orEmpty(), notices[amendment.id].orEmpty(), states)
        }
    }

    fun view(requestId: UUID, amendmentId: UUID): InformationRequestAmendmentView =
        views(requestId).firstOrNull { it.amendment.id == amendmentId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Amendment not found")
}
