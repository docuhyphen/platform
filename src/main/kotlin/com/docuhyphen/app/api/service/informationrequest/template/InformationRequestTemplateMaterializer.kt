package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.fields.PublishedSchemaAssignmentCommand
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestRequirementAdvance
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestMaterializationResult
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateBindingConfiguration
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.*
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityExecutorRegistry
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestTemplateMaterializer @Inject constructor(
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateDefinitionRepository: InformationRequestTemplateDefinitionRepository,
    private val templateBindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val groupOccurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val evidencePolicyRepository: InformationRequestTemplateEvidencePolicyRepository,
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository,
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository,
    private val evidenceLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    private val capabilityRegistry: InformationRequestCapabilityExecutorRegistry,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val currentRepository: InformationRequestRequirementCurrentRepository,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val fieldsReader: FieldsReadFacade,
    private val supportingEvidenceLinkService: InformationRequestSupportingEvidenceLinkService,
)
{
    @Transactional
    fun materialize(
        request: InformationRequest,
        access: FieldsAccessContext,
    ): InformationRequestMaterializationResult
    {
        if (requirementRepository.findForRequest(request.id).isNotEmpty())
            throw IllegalStateException("Information request ${request.id} has already been materialized")

        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
                "Information request template version not found: ${request.templateVersionId}",
            )
        if (version.status != InformationRequestTemplateStatus.PUBLISHED)
            throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
                "Information request template version ${version.versionNumber} has not been published",
            )
        val definition = templateDefinitionRepository.findById(version.templateDefinitionId)
            ?: throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
                "Information request template not found: ${version.templateDefinitionId}",
            )
        requireRequestOwnerMatchesTemplate(request, definition.scopeKind, definition.scopeOrgId, definition.scopeUserId)

        val capabilityRequirements = capabilityRepository.findForVersion(version.id).map {
            InformationRequestCapabilityRequirement(it.capabilityKey, it.requiredContractVersion)
        }
        val unserved = capabilityRegistry.unserved(capabilityRequirements)
        if (unserved.isNotEmpty())
            throw InformationRequestCapabilityNotInstalledException(
                "Information request template version ${version.versionNumber} requires runtime capabilities " +
                        "this deployment does not serve: ${unserved.joinToString { it.capability.name }}",
                unserved,
            )

        val bindings = templateBindingRepository.findOrdered(version.id)
        val requirementsById = templateRequirementRepository
            .findAllByDefinition(version.templateDefinitionId)
            .associateBy { it.id }
        val configuration = loadConfiguration(version.id)
        val hasFieldRequirements = bindings.any { binding ->
            requirementsById[binding.templateRequirementId]?.requirementType == InformationRequestRequirementType.FIELD
        }

        if (hasFieldRequirements)
        {
            val schemaVersionId = version.schemaVersionId
                ?: throw IllegalStateException(
                    "Information request template version ${version.id} has Field requirements and no Schema Version",
                )
            try {
                schemaAssignmentService.assignSchemaVersionForCreation(
                    PublishedSchemaAssignmentCommand(
                        resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                        access = access,
                        schemaVersionId = schemaVersionId,
                        source = SchemaAssignmentSource.API,
                    ),
                )
            } catch (refused: FieldValidationException) {
                throw InformationRequestTemplateVersionUnavailableException(
                    InformationRequestTemplateVersionUnavailableException.SCHEMA_UNAVAILABLE,
                    "This Template Version's Schema can no longer be used, so no request can be created from it: ${refused.message}",
                )
            }
        }

        val groups = groupRepository.findForVersion(version.id)
        val occurrencePathsByGroupKey = materializeGroupOccurrences(request, groups, hasFieldRequirements)

        val now = Timestamp.from(Instant.now())
        var requirementCount = 0
        bindings.forEach { binding ->
            val templateRequirement = requirementsById[binding.templateRequirementId]
                ?: throw IllegalStateException("Template requirement ${binding.templateRequirementId} is missing")
            occurrencePathsForBinding(binding, occurrencePathsByGroupKey).forEach { occurrencePath ->
                materializeRequirement(
                    request,
                    version,
                    binding,
                    templateRequirement,
                    configuration,
                    now,
                    occurrencePath
                )
                requirementCount++
            }
        }
        supportingEvidenceLinkService.materialize(request)

        return InformationRequestMaterializationResult(requirementCount = requirementCount)
    }

    @Transactional
    fun advance(
        request: InformationRequest,
        fromTemplateVersionId: UUID,
        access: FieldsAccessContext,
    ): InformationRequestRequirementAdvance
    {
        val version = templateVersionRepository.findById(request.templateVersionId)
            ?.takeIf { it.status == InformationRequestTemplateStatus.PUBLISHED }
            ?: throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
                "Information request template version ${request.templateVersionId} is not published",
            )
        val bindings = templateBindingRepository.findOrdered(version.id)
        val requirementsById = templateRequirementRepository
            .findAllByDefinition(version.templateDefinitionId)
            .associateBy { it.id }
        val configuration = loadConfiguration(version.id)
        val occurrencePathsByGroupKey = advanceGroupOccurrences(
            request,
            groupRepository.findForVersion(fromTemplateVersionId),
            groupRepository.findForVersion(version.id),
        )
        val existing = requirementRepository.findAllForRequest(request.id)
            .associateBy { it.sourceTemplateRequirementId to it.occurrencePath }

        val now = Timestamp.from(Instant.now())
        val advanced = mutableListOf<UUID>()
        val added = mutableListOf<UUID>()
        bindings.forEach { binding ->
            val templateRequirement = requirementsById[binding.templateRequirementId]
                ?: throw IllegalStateException("Template requirement ${binding.templateRequirementId} is missing")
            occurrencePathsForBinding(binding, occurrencePathsByGroupKey).forEach { occurrencePath ->
                val runtime = existing[templateRequirement.id to occurrencePath]
                when
                {
                    runtime == null ->
                        added += materializeRequirement(
                            request, version, binding, templateRequirement, configuration, now, occurrencePath,
                        ).id

                    runtime.sourceTemplateBindingId != binding.id ->
                    {
                        advanceRequirement(runtime, version, binding, templateRequirement, configuration, now)
                        advanced += runtime.id
                    }
                }
            }
        }
        val boundTemplateRequirementIds = bindings.map { it.templateRequirementId }.toSet()
        revisionRepository.closeOpen(
            existing.values.filter { it.sourceTemplateRequirementId !in boundTemplateRequirementIds }.map { it.id },
            now,
        )
        supportingEvidenceLinkService.materialize(request)
        return InformationRequestRequirementAdvance(advancedRequirementIds = advanced, addedRequirementIds = added)
    }

    private fun advanceGroupOccurrences(
        request: InformationRequest,
        earlierGroups: List<InformationRequestTemplateRequirementGroup>,
        groups: List<InformationRequestTemplateRequirementGroup>,
    ): Map<String, List<String>>
    {
        val earlierKeys = earlierGroups.associate { it.id to it.groupKey }
        val groupsByKey = groups.associateBy { it.groupKey }
        val occurrences = groupOccurrenceRepository.findForRequest(request.id)
        groupOccurrenceRepository.findAllForRequest(request.id).forEach { occurrence ->
            val target = earlierKeys[occurrence.sourceTemplateGroupId]?.let(groupsByKey::get) ?: return@forEach
            if (target.id != occurrence.sourceTemplateGroupId)
            {
                occurrence.sourceTemplateGroupId = target.id
                groupOccurrenceRepository.update(occurrence)
            }
        }

        val usedPaths = groupOccurrenceRepository.findAllForRequest(request.id)
            .map { it.occurrencePath }
            .toMutableList()
        val siblingsByParent = occurrences.groupBy { it.sourceTemplateGroupId to it.parentOccurrenceId }
        val childrenByParent = groups.groupBy { it.parentGroupId }
        val pathsByGroupKey = mutableMapOf<String, MutableList<String>>()

        fun reconcile(group: InformationRequestTemplateRequirementGroup, parent: InformationRequestGroupOccurrence?)
        {
            val siblings = siblingsByParent[group.id to parent?.id].orEmpty()
            val nextDisplayIndex = siblings.maxOfOrNull { it.occurrenceIndex }?.plus(1) ?: 0
            val created = (0 until group.minOccurrences - siblings.size).map { offset ->
                val identityIndex = InformationRequestOccurrencePath.nextIdentityIndex(
                    group.groupKey,
                    parent?.occurrencePath,
                    usedPaths,
                )
                createOccurrence(
                    request,
                    group,
                    parent?.id,
                    parent?.occurrencePath,
                    nextDisplayIndex + offset,
                    identityIndex,
                    provisionFieldValueSet = false,
                ).also { usedPaths += it.occurrencePath }
            }
            val kept = siblings + created
            pathsByGroupKey.getOrPut(group.groupKey) { mutableListOf() }.addAll(kept.map { it.occurrencePath })
            childrenByParent[group.id].orEmpty().forEach { child -> kept.forEach { reconcile(child, it) } }
        }

        childrenByParent[null].orEmpty().forEach { reconcile(it, null) }
        provisionOccurrenceValueSets(request, pathsByGroupKey.values.flatten())
        return pathsByGroupKey
    }

    private fun provisionOccurrenceValueSets(request: InformationRequest, occurrencePaths: List<String>) {
        val resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id)
        if (fieldsReader.assignmentFor(resource) == null) return
        occurrencePaths.forEach { schemaAssignmentService.createOccurrenceValueSet(resource, it) }
    }

    @Suppress("LongParameterList")
    private fun createOccurrence(
        request: InformationRequest,
        group: InformationRequestTemplateRequirementGroup,
        parentOccurrenceId: UUID?,
        parentPath: String?,
        displayIndex: Int,
        identityIndex: Int,
        provisionFieldValueSet: Boolean,
    ): InformationRequestGroupOccurrence {
        val path = InformationRequestOccurrencePath.groupOccurrencePath(group.groupKey, identityIndex, parentPath)
        val occurrence = groupOccurrenceRepository.save(
            InformationRequestGroupOccurrence().apply {
                informationRequestId = request.id
                sourceTemplateGroupId = group.id
                this.parentOccurrenceId = parentOccurrenceId
                occurrenceIndex = displayIndex
                occurrencePath = path
            },
        )
        if (provisionFieldValueSet)
            schemaAssignmentService.createOccurrenceValueSet(
                FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                path,
            )
        return occurrence
    }

    private fun advanceRequirement(
        requirement: InformationRequestRequirement,
        version: InformationRequestTemplateVersion,
        binding: InformationRequestTemplateRequirementBinding,
        templateRequirement: InformationRequestTemplateRequirement,
        configuration: InformationRequestTemplateBindingConfiguration,
        now: Timestamp,
    )
    {
        requirement.sourceTemplateVersionId = version.id
        requirement.sourceTemplateBindingId = binding.id
        requirementRepository.update(requirement)
        requirementRepository.flushChanges()
        val pointer = currentRepository.findForRequirement(requirement.id)
            ?: throw IllegalStateException("Runtime requirement ${requirement.id} has no current revision")
        val superseded = revisionRepository.findById(pointer.currentRevisionId)
            ?: throw IllegalStateException("Runtime requirement ${requirement.id} has no current revision")
        val openedAt = if (superseded.effectiveFrom.after(now)) superseded.effectiveFrom else now
        revisionRepository.closeOpen(listOf(requirement.id), openedAt)
        val revision = revisionRepository.save(
            InformationRequestRequirementRevision().apply {
                informationRequestRequirementId = requirement.id
                informationRequestId = requirement.informationRequestId
                sourceTemplateVersionId = version.id
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                revisionNumber = pointer.currentRevisionNumber + 1
                occurrencePath = requirement.occurrencePath
                effectiveFrom = openedAt
                configurationHashSha256 =
                    InformationRequestRequirementConfigurationHash.of(binding, templateRequirement, configuration)
                optimisticVersion = 1
            },
        )
        pointer.currentRevisionId = revision.id
        pointer.currentRevisionNumber = revision.revisionNumber
        pointer.updatedAt = now
        currentRepository.update(pointer)
    }

    private fun occurrencePathsForBinding(
        binding: InformationRequestTemplateRequirementBinding,
        occurrencePathsByGroupKey: Map<String, List<String>>,
    ): List<String>
    {
        val anchor = binding.occurrenceAnchorKey
        return if (anchor.isNullOrBlank())
            listOf(ROOT_OCCURRENCE_PATH)
        else
            occurrencePathsByGroupKey[anchor].orEmpty()
    }

    private fun materializeGroupOccurrences(
        request: InformationRequest,
        groups: List<InformationRequestTemplateRequirementGroup>,
        hasFieldRequirements: Boolean,
    ): Map<String, List<String>>
    {
        val childrenByParent = groups.groupBy { it.parentGroupId }
        val pathsByGroupKey = mutableMapOf<String, MutableList<String>>()

        fun materializeUnder(
            group: InformationRequestTemplateRequirementGroup,
            parentOccurrenceId: UUID?,
            parentPath: String?,
        )
        {
            val created = (0 until group.minOccurrences).map { index ->
                createOccurrence(request, group, parentOccurrenceId, parentPath, index, index, hasFieldRequirements)
            }
            pathsByGroupKey.getOrPut(group.groupKey) { mutableListOf() }.addAll(created.map { it.occurrencePath })
            childrenByParent[group.id].orEmpty().forEach { child ->
                created.forEach { materializeUnder(child, it.id, it.occurrencePath) }
            }
        }

        childrenByParent[null].orEmpty().forEach { materializeUnder(it, null, null) }
        return pathsByGroupKey
    }

    private fun materializeRequirement(
        request: InformationRequest,
        version: InformationRequestTemplateVersion,
        binding: InformationRequestTemplateRequirementBinding,
        templateRequirement: InformationRequestTemplateRequirement,
        configuration: InformationRequestTemplateBindingConfiguration,
        now: Timestamp,
        occurrencePath: String,
    ): InformationRequestRequirement
    {
        val requirement = requirementRepository.save(
            InformationRequestRequirement().apply {
                informationRequestId = request.id
                sourceTemplateVersionId = version.id
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                this.occurrencePath = occurrencePath
            },
        )
        val revision = revisionRepository.save(
            InformationRequestRequirementRevision().apply {
                informationRequestRequirementId = requirement.id
                informationRequestId = request.id
                sourceTemplateVersionId = version.id
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                revisionNumber = 1
                this.occurrencePath = occurrencePath
                effectiveFrom = now
                configurationHashSha256 =
                    InformationRequestRequirementConfigurationHash.of(binding, templateRequirement, configuration)
                optimisticVersion = 1
            },
        )
        currentRepository.save(
            InformationRequestRequirementCurrent().apply {
                informationRequestRequirementId = requirement.id
                currentRevisionId = revision.id
                currentRevisionNumber = 1
                updatedAt = now
            },
        )
        return requirement
    }

    private fun loadConfiguration(templateVersionId: UUID) = InformationRequestTemplateBindingConfiguration(
        dispositionsByBinding = dispositionRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        policiesByBinding = evidencePolicyRepository.findForVersion(templateVersionId)
            .associateBy { it.templateBindingId },
        acceptedValuesByPolicy = acceptedValueRepository.findForVersion(templateVersionId)
            .groupBy { it.evidencePolicyId },
        substitutesByBinding = substituteRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        evidenceLinksByBinding = evidenceLinkRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
    )

    private fun requireRequestOwnerMatchesTemplate(
        request: InformationRequest,
        scopeKind: InformationRequestTemplateScopeKind,
        scopeOrgId: UUID?,
        scopeUserId: UUID?,
    )
    {
        val requestOwner = when
        {
            request.ownerOrganizationId != null -> ScopeReference.Organization(request.ownerOrganizationId!!)
            request.ownerUserId != null -> ScopeReference.Personal(request.ownerUserId!!)
            else -> ScopeReference.Platform
        }
        val templateOwner = when (scopeKind)
        {
            InformationRequestTemplateScopeKind.ORGANIZATION ->
                ScopeReference.Organization(requireNotNull(scopeOrgId))

            InformationRequestTemplateScopeKind.PERSONAL ->
                ScopeReference.Personal(requireNotNull(scopeUserId))

            InformationRequestTemplateScopeKind.PLATFORM -> ScopeReference.Platform
        }
        if (requestOwner != templateOwner)
            throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
                "Information request template version ${request.templateVersionId} belongs to a different owner",
            )
    }

    private companion object
    {
        const val ROOT_OCCURRENCE_PATH = "root"
    }
}
