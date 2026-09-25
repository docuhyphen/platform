package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.sql.Timestamp
import java.time.Instant

class InformationRequestPrivateVersionPublisher(
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
)
{
    fun publish(
        definition: InformationRequestTemplateDefinition,
        versionNumber: Int,
        configuration: InformationRequestTemplateConfigurationRequest,
        actor: PrincipalRef,
    ): InformationRequestTemplateVersion
    {
        val actingUser = actor.id.takeIf { actor.kind == PrincipalKind.USER }
        val draft = versionRepository.save(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = definition.id
                this.versionNumber = versionNumber
                createdByAppUserId = actingUser
            },
        )

        schemaCompatibility.requireUsable(definition, configuration.schemaVersionId)
        configurationWriter.replaceConfiguration(draft, configuration)

        val required = capabilityRepository.findRequiredCapabilities(draft.id)
        if (required.isEmpty())
        {
            throw InformationRequestTemplateValidationException(
                "Information request template version $versionNumber configures no requirements",
            )
        }
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
        draft.publishedByAppUserId = actingUser
        versionRepository.update(draft)

        definition.status = InformationRequestTemplateStatus.PUBLISHED
        definition.updatedAt = publishedAt
        definitionRepository.update(definition)
        return draft
    }
}
