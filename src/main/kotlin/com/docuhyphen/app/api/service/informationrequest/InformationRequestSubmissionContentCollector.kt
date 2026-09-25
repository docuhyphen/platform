package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionContent
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionContentItem
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionContentCollector @Inject constructor(
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val sectionRepository: InformationRequestTemplateSectionRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val responseStore: InformationRequestResponseStore,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val evidenceEvaluationService: InformationRequestEvidenceEvaluationService,
    private val supportingEvidenceLinkService: InformationRequestSupportingEvidenceLinkService,
    private val stages: InformationRequestSubmissionStages,
)
{
    fun collect(request: InformationRequest, stageKey: String?): InformationRequestSubmissionContent
    {
        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")
        val sections = sectionRepository.findOrdered(version.id)
        val stageOrder = stages.stageOrderOf(version, sections)
        requireKnownScope(version, stageOrder, stageKey)

        val sectionsById = sections.associateBy { it.id }
        val bindingsById = bindingRepository.findOrdered(version.id).associateBy { it.id }
        val templateRequirements = templateRequirementRepository.findAllByDefinition(version.templateDefinitionId)
            .associateBy { it.id }
        val activePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        val revisions = revisionRepository.findCurrentForRequest(request.id).associateBy { it.informationRequestRequirementId }
        val responses = responseStore.findCurrentForRequest(request.id)
            .filter { it.activeInResponse }
            .associateBy { it.informationRequestRequirementId }

        val items = requirementRepository.findForRequest(request.id)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activePaths) }
            .mapNotNull { requirement ->
                val binding = bindingsById[requirement.sourceTemplateBindingId] ?: return@mapNotNull null
                if (stageKey != null && sectionsById[binding.templateSectionId]?.submissionStageKey != stageKey)
                {
                    return@mapNotNull null
                }
                val templateRequirement = templateRequirements.getValue(requirement.sourceTemplateRequirementId)
                val response = responses[requirement.id]
                InformationRequestSubmissionContentItem(
                    requirement = requirement,
                    revision = revisions[requirement.id]
                        ?: throw IllegalStateException("Information Request Requirement has no current revision"),
                    binding = binding,
                    requirementKey = templateRequirement.requirementKey,
                    requirementType = templateRequirement.requirementType,
                    response = response,
                    fieldValueRevisionId = fieldRevisionOf(request.id, binding, response?.fieldValueSetId),
                    evidence = if (templateRequirement.requirementType == InformationRequestRequirementType.DOCUMENT)
                        evidenceEvaluationService.submissionFacts(requirement, response?.disposition)
                    else null,
                )
            }
            .sortedWith(compareBy({ it.requirement.occurrencePath }, { it.requirementKey }, { it.requirement.id }))

        val links = supportingEvidenceLinkService.linksFrom(request, items.map { it.requirement.id }.toSet())
            .sortedBy { it.id }
        return InformationRequestSubmissionContent(
            request = request,
            version = version,
            stageKey = stageKey,
            stageOrder = stageOrder,
            items = items,
            links = links,
            contentHash = contentHash(request, version, stageKey, items, links.map { it.id }),
        )
    }

    private fun requireKnownScope(version: InformationRequestTemplateVersion, stageOrder: List<String>, stageKey: String?)
    {
        val known = if (version.submissionMode == InformationRequestSubmissionMode.STAGED)
            stageKey != null && stageKey in stageOrder
        else
            stageKey == null
        if (!known)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_STAGE_UNKNOWN,
                if (version.submissionMode == InformationRequestSubmissionMode.STAGED)
                    "This Information Request is submitted in stages; name one of ${stageOrder.joinToString()}"
                else
                    "This Information Request is submitted as a whole and has no stages",
            )
        }
    }

    private fun fieldRevisionOf(requestId: UUID, binding: InformationRequestTemplateRequirementBinding, valueSetId: UUID?): UUID?
    {
        val fieldDefinitionId = binding.collectedFieldDefinitionId ?: return null
        val set = valueSetId ?: return null
        return fieldValueRevisions.latestRevision(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
            set,
            fieldDefinitionId,
        )?.id
    }

    private fun contentHash(
        request: InformationRequest,
        version: InformationRequestTemplateVersion,
        stageKey: String?,
        items: List<InformationRequestSubmissionContentItem>,
        linkIds: List<UUID>,
    ): String
    {
        val material = mutableListOf(
            "request:${request.id}",
            "templateVersion:${version.id}",
            "schemaVersion:${version.schemaVersionId ?: ""}",
            "scope:${stageKey ?: ""}",
        )
        items.filter { it.requirementType != InformationRequestRequirementType.RESPONSE_ATTESTATION }
            .sortedBy { it.requirement.id }
            .forEach { item ->
                val response = item.response
                material += listOf(
                    "requirement:${item.requirement.id}",
                    "revision:${item.revision.id}",
                    "binding:${item.binding.id}",
                    "path:${item.requirement.occurrencePath}",
                    "response:${response?.id ?: ""}:${response?.responseRevision ?: ""}",
                    "disposition:${response?.disposition ?: ""}",
                    "narrative:${response?.narrative?.let(::sha256Hex) ?: ""}",
                    "fieldRevision:${item.fieldValueRevisionId ?: ""}",
                    "evidence:" + item.evidence?.members.orEmpty().joinToString(",") {
                        "${it.versionId}:${it.documentVersionId ?: ""}:${it.contentHash ?: ""}"
                    },
                ).joinToString("|")
            }
        linkIds.sorted().forEach { material += "link:$it" }
        return sha256Hex(material.joinToString("\n"))
    }

    private fun sha256Hex(material: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
