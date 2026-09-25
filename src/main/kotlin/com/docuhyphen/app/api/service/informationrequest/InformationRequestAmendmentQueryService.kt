package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReadableAmendment
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestAmendmentQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val reader: InformationRequestAmendmentReader,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val gate: InformationRequestMutationGate,
)
{
    fun amendments(requestId: UUID, access: RequestAccessContext): List<InformationRequestReadableAmendment>
    {
        val request = queryService.findById(requestId, access)
        return readable(request, reader.views(requestId), access)
    }

    fun readable(request: InformationRequest, view: InformationRequestAmendmentView, access: RequestAccessContext): InformationRequestReadableAmendment =
        readable(request, listOf(view), access).single()

    private fun readable(
        request: InformationRequest,
        views: List<InformationRequestAmendmentView>,
        access: RequestAccessContext,
    ): List<InformationRequestReadableAmendment>
    {
        val runtimeByTemplateRequirement = requirementRepository.findAllForRequest(request.id)
            .groupBy { it.sourceTemplateRequirementId }
        val visibleTemplateRequirements = views.flatMap { view -> view.changes.map { it.templateRequirementId } }
            .toSet()
            .filter { templateRequirementId ->
                runtimeByTemplateRequirement[templateRequirementId].orEmpty().any {
                    gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it.id)
                }
            }
            .toSet()
        val noticedParties = views.flatMap { view -> view.notices.map { it.partyId } }.toSet()
        val visibleParties = if (gate.permitsRequest(access, Action.INFORMATION_REQUEST_MANAGE_PARTIES, request.id))
            noticedParties
        else
            partyRepository.findActiveForPrincipal(access.principal.kind, access.principal.id)
                .filter { it.informationRequestId == request.id }
                .map { it.id }
                .toSet()
        return views.map { InformationRequestReadableAmendment(it, visibleTemplateRequirements, visibleParties) }
    }
}
