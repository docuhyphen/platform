package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.command.CommandMutationResult
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptDecision
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.canonicalForm
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class InformationRequestTemplatePublicationService @Inject constructor(
    private val authoringService: InformationRequestTemplateAuthoringService,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val freezer: InformationRequestTemplateVersionFreezer,
    private val commandReceiptService: CommandReceiptService,
)
{
    @Transactional
    fun publishTemplate(
        templateDefinitionId: UUID,
        precondition: CommandPrecondition,
        idempotencyKey: String,
    ): InformationRequestTemplateDto
    {
        val mutation = authoringService.requireMutationContext(templateDefinitionId)
        val definition = mutation.definition
        val receipt = InformationRequestTemplateCommandReceipts.forTemplate(
            definition.id,
            InformationRequestTemplateCommandReceipts.PUBLISH,
            mutation.principal,
            idempotencyKey,
            precondition.canonicalForm(),
        )
        val decision = commandReceiptService.runOnce(receipt) {
            val draft = versionRepository.findDraftForUpdate(definition.id)
                ?: throw IllegalStateException(
                    "Information request template $templateDefinitionId has no editable version",
                )
            precondition.requireSatisfiedBy(InformationRequestTemplateETag.draftOf(draft))
            freezer.freeze(definition, draft, mutation.principal)
            authoringService.recordTemplateVersionEvent(
                AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH,
                definition,
                draft,
                mutation.principal,
                null,
            )
            CommandMutationResult(
                authoringService.projectTemplate(definition),
                InformationRequestTemplateCommandReceipts.result(definition.id, draft.revision),
            )
        }
        return when (decision) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> authoringService.projectTemplate(definition)
        }
    }
}
