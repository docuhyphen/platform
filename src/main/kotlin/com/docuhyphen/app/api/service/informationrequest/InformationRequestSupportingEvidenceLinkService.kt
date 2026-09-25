package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestSupportingEvidenceLink
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSupportingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestSupportingEvidenceLinkService @Inject constructor(
    private val templateLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val linkRepository: InformationRequestSupportingEvidenceLinkRepository,
)
{
    fun materialize(request: InformationRequest)
    {
        val templateLinks = templateLinkRepository.findForVersion(request.templateVersionId)
        if (templateLinks.isEmpty()) return

        val recorded = linkRepository.findForRequest(request.id)
            .map { it.supportedRequirementId to it.supportingRequirementId }
            .toSet()
        val now = Timestamp.from(Instant.now())
        InformationRequestSupportingEvidenceLinkResolver.resolve(templateLinks, requirementRepository.findForRequest(request.id))
            .filter { (it.supportedRequirementId to it.supportingRequirementId) !in recorded }
            .forEach { target ->
                linkRepository.save(
                    InformationRequestSupportingEvidenceLink().apply {
                        informationRequestId = request.id
                        supportedRequirementId = target.supportedRequirementId
                        supportingRequirementId = target.supportingRequirementId
                        templateEvidenceLinkId = target.templateLinkId
                        createdAt = now
                    },
                )
            }
    }

    fun linksFrom(request: InformationRequest, supportedRequirementIds: Set<UUID>): List<InformationRequestSupportingEvidenceLink> =
        currentLinks(request).filter { it.supportedRequirementId in supportedRequirementIds }

    fun visibleLinks(request: InformationRequest, visibleRequirementIds: Set<UUID>): List<InformationRequestSupportingEvidenceLink> =
        currentLinks(request).filter {
            it.supportedRequirementId in visibleRequirementIds && it.supportingRequirementId in visibleRequirementIds
        }

    private fun currentLinks(request: InformationRequest): List<InformationRequestSupportingEvidenceLink>
    {
        val linked = InformationRequestSupportingEvidenceLinkResolver
            .resolve(templateLinkRepository.findForVersion(request.templateVersionId), requirementRepository.findForRequest(request.id))
            .map { it.supportedRequirementId to it.supportingRequirementId }
            .toSet()
        return linkRepository.findForRequest(request.id).filter { (it.supportedRequirementId to it.supportingRequirementId) in linked }
    }
}
