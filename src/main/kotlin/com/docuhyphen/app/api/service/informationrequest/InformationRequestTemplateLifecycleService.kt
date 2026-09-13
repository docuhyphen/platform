package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Lifecycle operations that preserve a frozen Template Version by copying its authored document
 * into new draft rows. Recorded runtime capabilities are deliberately not copied: they describe the
 * source Version at publication and are derived again when the new draft later freezes.
 */
@ApplicationScoped
class InformationRequestTemplateLifecycleService @Inject constructor(
    private val authoringService: InformationRequestTemplateAuthoringService,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val projectionLoader: InformationRequestTemplateProjectionLoader,
)
{
    @Transactional
    fun retireVersion(templateDefinitionId: UUID, versionNumber: Int): InformationRequestTemplateDto
    {
        val mutation = authoringService.requireMutationContext(templateDefinitionId)
        val definition = lockDefinition(templateDefinitionId)
        val publishedBeforeRetirement = versionRepository.findPublished(definition.id)
        val version = requirePublishedVersion(definition.id, versionNumber)
        val draft = versionRepository.findDraft(definition.id)
        val retiredAt = Timestamp.from(Instant.now())

        version.status = InformationRequestTemplateStatus.RETIRED
        version.retiredAt = retiredAt
        version.retiredByAppUserId = mutation.principal.id
        versionRepository.update(version)

        if (publishedBeforeRetirement.size == 1 && publishedBeforeRetirement.single().id == version.id && draft == null)
        {
            definition.status = InformationRequestTemplateStatus.RETIRED
        }
        definition.updatedAt = retiredAt
        definitionRepository.update(definition)

        authoringService.recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_RETIRE,
            definition,
            mutation.principal.id,
        )
        return authoringService.projectTemplate(definition)
    }

    @Transactional
    fun createDraftVersion(
        templateDefinitionId: UUID,
        sourceVersionNumber: Int,
    ): InformationRequestTemplateDto
    {
        val mutation = authoringService.requireMutationContext(templateDefinitionId)
        val definition = lockDefinition(templateDefinitionId)
        if (versionRepository.findDraft(definition.id) != null)
        {
            throw IllegalStateException(
                "Information request template $templateDefinitionId already has an editable version",
            )
        }

        val source = requirePublishedVersion(definition.id, sourceVersionNumber)
        val nextVersionNumber = versionRepository.findForDefinitions(listOf(definition.id))
            .maxOfOrNull { it.versionNumber }
            ?.plus(1)
            ?: 1
        val draft = versionRepository.save(
            InformationRequestTemplateVersion().apply {
                this.templateDefinitionId = definition.id
                versionNumber = nextVersionNumber
                createdByAppUserId = mutation.principal.id
            },
        )
        copyConfiguration(source, draft)

        definition.updatedAt = Timestamp.from(Instant.now())
        definitionRepository.update(definition)
        authoringService.recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_NEW_VERSION,
            definition,
            mutation.principal.id,
        )
        return authoringService.projectTemplate(definition)
    }

    @Transactional
    fun cloneTemplate(
        sourceDefinitionId: UUID,
        sourceVersionNumber: Int,
        request: CreateInformationRequestTemplateRequest,
    ): InformationRequestTemplateDto
    {
        authoringService.getTemplate(sourceDefinitionId)
        lockDefinition(sourceDefinitionId)
        val source = requirePublishedVersion(sourceDefinitionId, sourceVersionNumber)
        val configuration = InformationRequestTemplateConfigurationMapper.toRequest(
            projectionLoader.loadVersion(source),
        )

        val target = authoringService.createTemplate(request)
        return authoringService.replaceDraftConfiguration(target.id, configuration)
    }

    private fun lockDefinition(id: UUID): InformationRequestTemplateDefinition =
        definitionRepository.findByIdForUpdate(id)
            ?: throw IllegalArgumentException("Information request template not found: $id")

    private fun requirePublishedVersion(
        definitionId: UUID,
        versionNumber: Int,
    ): InformationRequestTemplateVersion
    {
        val version = versionRepository.findByNumber(definitionId, versionNumber)
            ?: throw IllegalArgumentException(
                "Information request template version not found: $definitionId version $versionNumber",
            )
        if (version.status != InformationRequestTemplateStatus.PUBLISHED)
        {
            throw IllegalStateException(
                "Information request template version $versionNumber is not published",
            )
        }
        return version
    }

    private fun copyConfiguration(
        source: InformationRequestTemplateVersion,
        draft: InformationRequestTemplateVersion,
    )
    {
        val sourceConfiguration = InformationRequestTemplateConfigurationMapper.toRequest(
            projectionLoader.loadVersion(source),
        )
        configurationWriter.replaceConfiguration(draft, sourceConfiguration)
    }
}
