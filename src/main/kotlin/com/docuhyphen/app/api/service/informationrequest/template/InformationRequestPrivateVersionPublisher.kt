package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestPrivateVersionPublisher @Inject constructor(
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
    private val freezer: InformationRequestTemplateVersionFreezer,
)
{
    fun publish(
        definition: InformationRequestTemplateDefinition,
        versionNumber: Int,
        configuration: InformationRequestTemplateConfigurationRequest,
        actor: PrincipalRef,
    ): InformationRequestTemplateVersion
    {
        val draft = versionRepository.save(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = definition.id
                this.versionNumber = versionNumber
                createdByPrincipalKind = actor.kind
                createdByPrincipalId = actor.id
            },
        )

        schemaCompatibility.requireUsable(definition, configuration.schemaVersionId)
        configurationWriter.replaceConfiguration(draft, configuration)
        freezer.freeze(definition, draft, actor)
        versionRepository.flushChanges()
        return draft
    }
}
