package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.command.CommandMutationResult
import com.docuhyphen.app.api.model.command.CommandReceiptDecision
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.command.CommandReceiptService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestTemplateLifecycleService @Inject constructor(
    private val authoringService: InformationRequestTemplateAuthoringService,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val projectionLoader: InformationRequestTemplateProjectionLoader,
    private val commandReceiptService: CommandReceiptService,
)
{
    @Transactional
    fun retireVersion(
        templateDefinitionId: UUID,
        versionNumber: Int,
        idempotencyKey: String
    ): InformationRequestTemplateDto
    {
        val mutation = authoringService.requireMutationContext(templateDefinitionId)
        val receipt = InformationRequestTemplateCommandReceipts.forTemplate(
            templateDefinitionId,
            InformationRequestTemplateCommandReceipts.RETIRE,
            mutation.principal,
            idempotencyKey,
            versionNumber,
        )
        return runOnce(receipt) {
            val definition = lockDefinition(templateDefinitionId)
            val publishedBeforeRetirement = versionRepository.findPublished(definition.id)
            val version = requirePublishedVersion(definition.id, versionNumber)
            val draft = versionRepository.findDraft(definition.id)
            val retiredAt = Timestamp.from(Instant.now())

            version.status = InformationRequestTemplateStatus.RETIRED
            version.retiredAt = retiredAt
            version.retiredByPrincipalKind = mutation.principal.kind
            version.retiredByPrincipalId = mutation.principal.id
            versionRepository.update(version)

            definition.status = when {
                publishedBeforeRetirement.any { it.id != version.id } -> InformationRequestTemplateStatus.PUBLISHED
                draft != null -> InformationRequestTemplateStatus.DRAFT
                else -> InformationRequestTemplateStatus.RETIRED
            }
            definition.updatedAt = retiredAt
            definitionRepository.update(definition)

            authoringService.recordTemplateVersionEvent(
                AuditEventType.INFORMATION_REQUEST_TEMPLATE_RETIRE,
                definition,
                version,
                mutation.principal,
                null,
            )
            CommandMutationResult(
                authoringService.projectTemplate(definition),
                InformationRequestTemplateCommandReceipts.result(definition.id),
            )
        }
    }

    @Transactional
    fun createDraftVersion(
        templateDefinitionId: UUID,
        sourceVersionNumber: Int,
        idempotencyKey: String,
    ): InformationRequestTemplateDto
    {
        val mutation = authoringService.requireMutationContext(templateDefinitionId)
        val receipt = InformationRequestTemplateCommandReceipts.forTemplate(
            templateDefinitionId,
            InformationRequestTemplateCommandReceipts.NEW_VERSION,
            mutation.principal,
            idempotencyKey,
            sourceVersionNumber,
        )
        return runOnce(receipt) {
            val definition = lockDefinition(templateDefinitionId)
            if (versionRepository.findDraft(definition.id) != null) {
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
                    createdByPrincipalKind = mutation.principal.kind
                    createdByPrincipalId = mutation.principal.id
                },
            )
            copyConfiguration(source, draft)

            definition.updatedAt = Timestamp.from(Instant.now())
            definitionRepository.update(definition)
            authoringService.recordTemplateVersionEvent(
                AuditEventType.INFORMATION_REQUEST_TEMPLATE_NEW_VERSION,
                definition,
                draft,
                mutation.principal,
                source,
            )
            CommandMutationResult(
                authoringService.projectTemplate(definition),
                InformationRequestTemplateCommandReceipts.result(definition.id),
            )
        }
    }

    @Transactional
    fun cloneTemplate(
        sourceDefinitionId: UUID,
        sourceVersionNumber: Int,
        request: CreateInformationRequestTemplateRequest,
        idempotencyKey: String,
    ): InformationRequestTemplateDto
    {
        authoringService.getTemplate(sourceDefinitionId)
        val receipt = InformationRequestTemplateCommandReceipts.forTemplate(
            sourceDefinitionId,
            InformationRequestTemplateCommandReceipts.CLONE,
            authoringService.currentAuthor(),
            idempotencyKey,
            sourceVersionNumber,
            request.scopeKind,
            request.namespace.trim().lowercase(),
            request.templateKey.trim().lowercase(),
            request.displayName.trim(),
            request.description?.trim().orEmpty(),
        )
        val decision = commandReceiptService.runOnce(receipt) {
            lockDefinition(sourceDefinitionId)
            val source = requirePublishedVersion(sourceDefinitionId, sourceVersionNumber)
            val configuration = InformationRequestTemplateConfigurationMapper.toRequest(
                projectionLoader.loadVersion(source),
            )

            val target = authoringService.createDefinition(request)
            CommandMutationResult(
                authoringService.configureCopiedDraft(target.id, configuration, source),
                InformationRequestTemplateCommandReceipts.result(target.id),
            )
        }
        return when (decision) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> authoringService.getTemplate(decision.result.resourceId)
        }
    }

    private fun runOnce(
        receipt: CommandReceiptRequest,
        mutation: () -> CommandMutationResult<InformationRequestTemplateDto>,
    ): InformationRequestTemplateDto =
        when (val decision = commandReceiptService.runOnce(receipt, mutation)) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
                authoringService.projectTemplate(lockDefinition(decision.result.resourceId))
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
