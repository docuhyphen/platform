package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateOriginKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestPrivateVersionPublisher
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateSchemaCompatibility
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestAmendmentTargetResolver @Inject constructor(
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    configurationWriter: InformationRequestTemplateConfigurationWriter,
    schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
)
{
    private val versionPublisher = InformationRequestPrivateVersionPublisher(
        definitionRepository,
        versionRepository,
        capabilityRepository,
        configurationWriter,
        schemaCompatibility,
    )

    fun current(request: InformationRequest): InformationRequestTemplateVersion =
        versionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request ${request.id} pins no Template Version")

    fun resolve(request: InformationRequest, command: AmendInformationRequestCommand): InformationRequestTemplateVersion
    {
        val current = current(request)
        val target = when
        {
            command.targetTemplateVersionId != null && command.configuration == null ->
                versionRepository.findById(command.targetTemplateVersionId)
            command.targetTemplateVersionId == null && command.configuration != null ->
                publishPrivateVersion(request, current, command)
            else -> null
        }
        if (target == null ||
            target.templateDefinitionId != current.templateDefinitionId ||
            target.status != InformationRequestTemplateStatus.PUBLISHED ||
            target.versionNumber <= current.versionNumber)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.AMENDMENT_TARGET_INVALID,
                "An amendment moves a request to a later published Version of its own Template",
            )
        }
        return target
    }

    private fun publishPrivateVersion(
        request: InformationRequest,
        current: InformationRequestTemplateVersion,
        command: AmendInformationRequestCommand,
    ): InformationRequestTemplateVersion?
    {
        val definition = definitionRepository.findById(current.templateDefinitionId)
            ?.takeIf { it.originKind == InformationRequestTemplateOriginKind.AD_HOC_REQUEST && it.originRequestId == request.id }
            ?: return null
        val nextNumber = versionRepository.findForDefinitions(listOf(definition.id)).maxOf { it.versionNumber } + 1
        return versionPublisher.publish(definition, nextNumber, requireNotNull(command.configuration), command.access.principal)
    }
}
