package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestTemplatePublicationService @Inject constructor(
    private val authoringService: InformationRequestTemplateAuthoringService,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
)
{
    /**
     * Freezes the one editable Version of a Template together with the exact runtime contracts its
     * configuration needs. The capability rows are flushed first because the database validates
     * their completeness while the Version changes status, and freezes them as soon as it does.
     */
    @Transactional
    fun publishTemplate(templateDefinitionId: UUID): InformationRequestTemplateDto
    {
        val mutation = authoringService.requireMutationContext(templateDefinitionId)
        val definition = mutation.definition
        val draft = versionRepository.findDraftForUpdate(definition.id)
            ?: throw IllegalStateException(
                "Information request template $templateDefinitionId has no editable version",
            )
        val required = capabilityRepository.findRequiredCapabilities(draft.id)
        if (required.isEmpty())
        {
            throw InformationRequestTemplateValidationException(
                "Information request template version ${draft.versionNumber} configures no requirements",
            )
        }
        InformationRequestTemplatePublicationReadiness.requireReady(
            authoringService.projectTemplate(definition).draftVersion,
        )

        required.forEach { capability ->
            capabilityRepository.save(
                InformationRequestTemplateVersionCapability().apply {
                    templateVersionId = draft.id
                    capabilityKey = capability
                    requiredContractVersion = capability.contractVersion
                },
            )
        }
        capabilityRepository.flushChanges()

        val publishedAt = Timestamp.from(Instant.now())
        draft.status = InformationRequestTemplateStatus.PUBLISHED
        draft.publishedAt = publishedAt
        draft.publishedByAppUserId = mutation.principal.id
        versionRepository.update(draft)

        definition.status = InformationRequestTemplateStatus.PUBLISHED
        definition.updatedAt = publishedAt
        definitionRepository.update(definition)

        authoringService.recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH,
            definition,
            mutation.principal.id,
        )
        return authoringService.projectTemplate(definition)
    }
}
