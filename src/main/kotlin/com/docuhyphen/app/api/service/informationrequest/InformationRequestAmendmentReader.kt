package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAmendmentChangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAmendmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeIntentRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestAmendmentReader @Inject constructor(
    private val amendmentRepository: InformationRequestAmendmentRepository,
    private val changeRepository: InformationRequestAmendmentChangeRepository,
    private val noticeRepository: InformationRequestNoticeIntentRepository,
)
{
    fun views(requestId: UUID): List<InformationRequestAmendmentView>
    {
        val changes = changeRepository.findForRequest(requestId).groupBy { it.amendmentId }
        val notices = noticeRepository.findForRequest(requestId).groupBy { it.amendmentId }
        return amendmentRepository.findForRequest(requestId).map { amendment ->
            InformationRequestAmendmentView(amendment, changes[amendment.id].orEmpty(), notices[amendment.id].orEmpty())
        }
    }

    fun view(requestId: UUID, amendmentId: UUID): InformationRequestAmendmentView =
        views(requestId).firstOrNull { it.amendment.id == amendmentId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Amendment not found")
}
