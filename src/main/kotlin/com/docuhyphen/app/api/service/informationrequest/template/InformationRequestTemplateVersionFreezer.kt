package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestTemplateVersionFreezer @Inject constructor(
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    private val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
    private val projectionLoader: InformationRequestTemplateProjectionLoader,
    private val conditionContracts: InformationRequestTemplateConditionContractValidator,
    private val fieldMembership: InformationRequestTemplateFieldMembershipValidator,
) {
    fun freeze(
        definition: InformationRequestTemplateDefinition,
        draft: InformationRequestTemplateVersion,
        actor: PrincipalRef,
    ) {
        val required = capabilityRepository.findRequiredCapabilities(draft.id)
        if (required.isEmpty()) {
            throw InformationRequestTemplateValidationException(
                "Information request template version ${draft.versionNumber} configures no requirements",
            )
        }
        schemaCompatibility.requireUsable(definition, draft.schemaVersionId)
        val stored = projectionLoader.loadVersion(draft)
        InformationRequestTemplatePublicationReadiness.requireReady(stored)
        conditionContracts.requireValidStored(draft.schemaVersionId, stored)
        fieldMembership.requireStoredFieldsBound(draft.schemaVersionId, stored)

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
        draft.publishedByPrincipalKind = actor.kind
        draft.publishedByPrincipalId = actor.id
        versionRepository.update(draft)

        definition.status = InformationRequestTemplateStatus.PUBLISHED
        definition.updatedAt = publishedAt
        definitionRepository.update(definition)
    }
}
