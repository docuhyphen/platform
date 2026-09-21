package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * The one way another domain resolves an exact Information Request Template Version.
 *
 * Selecting a Version and creating from one are asked separately because they happen at different
 * times. An author selects once, from what is publishable now. Everything that later creates a
 * request from that selection asks again, possibly long afterwards, by which time the Version may
 * have been retired. Both answers name the owner that holds the Version, because holding it is what
 * decides whether another record may name it at all.
 */
@ApplicationScoped
class InformationRequestTemplateReferenceService @Inject constructor(
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val entitlementGuard: InformationRequestTemplateEntitlementGuard,
)
{
    /**
     * The Version an author may name now.
     *
     * @throws InformationRequestTemplateVersionUnavailableException when no such Version exists,
     * when it is still being authored, or when it has been retired. A retired Version says so in
     * its own reason, because that is a Version that was publishable and is not any more rather
     * than one that never was.
     */
    fun requireSelectableVersion(templateVersionId: UUID): InformationRequestTemplateVersionReference
    {
        val (version, definition) = resolve(templateVersionId)
        when (version.status)
        {
            InformationRequestTemplateStatus.PUBLISHED -> Unit
            InformationRequestTemplateStatus.RETIRED -> throw retired(version)
            InformationRequestTemplateStatus.DRAFT -> throw
                InformationRequestTemplateVersionUnavailableException(
                    InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
                    "Information request template version ${version.versionNumber} is still being " +
                        "authored and its configuration can still change",
                )
        }
        return reference(version, definition)
    }

    /**
     * The Version something may create a new request from now. A reference that was valid when it
     * was made stays stored after retirement, so this is asked again on every instantiation rather
     * than assumed from the selection.
     *
     * @throws InformationRequestTemplateVersionUnavailableException with
     * [InformationRequestTemplateVersionUnavailableException.RETIRED] when the named Version has
     * been withdrawn from new use. Requests already created from it are unaffected.
     */
    fun requireInstantiableVersion(templateVersionId: UUID): InformationRequestTemplateVersionReference
    {
        val (version, definition) = resolve(templateVersionId)
        when (version.status)
        {
            InformationRequestTemplateStatus.PUBLISHED -> Unit
            InformationRequestTemplateStatus.RETIRED -> throw retired(version)
            InformationRequestTemplateStatus.DRAFT -> throw
                InformationRequestTemplateVersionUnavailableException(
                    InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
                    "Information request template version ${version.versionNumber} has not been " +
                        "published, so nothing can be created from it",
                )
        }
        return reference(version, definition)
    }

    /**
     * Reading a Version answers to the gates of the owner that holds it, so an owner without the
     * capability exposes no configuration through a reference either.
     */
    private fun resolve(
        templateVersionId: UUID,
    ): Pair<InformationRequestTemplateVersion, InformationRequestTemplateDefinition>
    {
        val version = versionRepository.findById(templateVersionId)
            ?: throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
                "Information request template version not found: $templateVersionId",
            )
        val definition = definitionRepository.findById(version.templateDefinitionId)
            ?: throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
                "Information request template not found: ${version.templateDefinitionId}",
            )
        entitlementGuard.requireTemplateAccess(
            definition.scopeKind,
            definition.scopeOrgId,
            definition.scopeUserId,
            null,
        )
        return version to definition
    }

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


