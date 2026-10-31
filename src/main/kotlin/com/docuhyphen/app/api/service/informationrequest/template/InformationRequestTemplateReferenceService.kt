package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateHolder
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionDescription
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTemplateReferenceService @Inject constructor(
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val entitlementGuard: InformationRequestTemplateEntitlementGuard,
)
{
    fun requireSelectableVersion(
        templateVersionId: UUID,
        holder: InformationRequestTemplateHolder,
    ): InformationRequestTemplateVersionReference
    {
        val (version, definition) = resolve(templateVersionId, holder)
        when (version.status)
        {
            InformationRequestTemplateStatus.PUBLISHED -> Unit
            InformationRequestTemplateStatus.RETIRED -> throw retired(version)
            InformationRequestTemplateStatus.DRAFT -> throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
                "Information request template version ${version.versionNumber} is still being " +
                        "authored and its configuration can still change",
            )
        }
        return reference(version, definition)
    }

    fun requireInstantiableVersion(
        templateVersionId: UUID,
        holder: InformationRequestTemplateHolder,
    ): InformationRequestTemplateVersionReference
    {
        val (version, definition) = resolve(templateVersionId, holder)
        when (version.status)
        {
            InformationRequestTemplateStatus.PUBLISHED -> Unit
            InformationRequestTemplateStatus.RETIRED -> throw retired(version)
            InformationRequestTemplateStatus.DRAFT -> throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
                "Information request template version ${version.versionNumber} has not been " +
                        "published, so nothing can be created from it",
            )
        }
        return reference(version, definition)
    }

    fun describeVersion(templateVersionId: UUID): InformationRequestTemplateVersionDescription? {
        val version = versionRepository.findById(templateVersionId) ?: return null
        val definition = definitionRepository.findById(version.templateDefinitionId)
            ?.takeIf { it.originKind == InformationRequestTemplateOriginKind.REUSABLE }
            ?: return null
        val latestPublished = versionRepository.findLatestPublished(definition.id)
        return InformationRequestTemplateVersionDescription(
            templateVersionId = version.id,
            templateDefinitionId = definition.id,
            templateDisplayName = definition.displayName,
            versionNumber = version.versionNumber,
            status = version.status,
            latest = version.status == InformationRequestTemplateStatus.PUBLISHED && latestPublished?.id == version.id,
            latestPublishedVersionNumber = latestPublished?.versionNumber,
            retiredAt = version.retiredAt,
        )
    }

    private fun resolve(
        templateVersionId: UUID,
        holder: InformationRequestTemplateHolder,
    ): Pair<InformationRequestTemplateVersion, InformationRequestTemplateDefinition>
    {
        val version = versionRepository.findById(templateVersionId)
            ?: throw versionNotFound(templateVersionId)
        val definition = definitionRepository.findById(version.templateDefinitionId)
            ?.takeIf { it.originKind == InformationRequestTemplateOriginKind.REUSABLE && holder.holds(it) }
            ?: throw versionNotFound(templateVersionId)
        if (definition.scopeKind != InformationRequestTemplateScopeKind.PLATFORM)
        {
            entitlementGuard.requireTemplateAccess(
                definition.scopeKind,
                definition.scopeOrgId,
                definition.scopeUserId,
                null,
            )
        }
        return version to definition
    }

    private fun versionNotFound(templateVersionId: UUID) =
        InformationRequestTemplateVersionUnavailableException(
            InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
            "Information request template version not found: $templateVersionId",
        )

    private fun retired(version: InformationRequestTemplateVersion) =
        InformationRequestTemplateVersionUnavailableException(
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            "Information request template version ${version.versionNumber} has been retired; " +
                    "select a currently published version",
        )

    private fun reference(
        version: InformationRequestTemplateVersion,
        definition: InformationRequestTemplateDefinition,
    ) = InformationRequestTemplateVersionReference(
        templateVersionId = version.id,
        templateDefinitionId = definition.id,
        versionNumber = version.versionNumber,
        ownerScopeKind = definition.scopeKind,
        ownerOrganizationId = definition.scopeOrgId,
        ownerUserId = definition.scopeUserId,
    )
}


