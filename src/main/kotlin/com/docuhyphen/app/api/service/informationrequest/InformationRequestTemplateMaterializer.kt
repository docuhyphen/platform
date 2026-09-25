package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestRequirementAdvance
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.PublishedSchemaAssignmentCommand
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class InformationRequestMaterializationResult(
    val requirementCount: Int,
)

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
            schemaAssignmentService.assignPublishedSchemaVersion(
                PublishedSchemaAssignmentCommand(
                    resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                    access = access,
                    schemaVersionId = schemaVersionId,
                    source = SchemaAssignmentSource.API,
                ),
            )
        }

        val groups = groupRepository.findForVersion(version.id)
        val occurrencePathsByGroupKey = materializeGroupOccurrences(request, groups, hasFieldRequirements)

        val now = Timestamp.from(Instant.now())
        var requirementCount = 0
        bindings.forEach { binding ->
            val templateRequirement = requirementsById[binding.templateRequirementId]
                ?: throw IllegalStateException("Template requirement ${binding.templateRequirementId} is missing")
            occurrencePathsForBinding(binding, occurrencePathsByGroupKey).forEach { occurrencePath ->
                materializeRequirement(request, version, binding, templateRequirement, configuration, now, occurrencePath)
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
        val hasFieldRequirements = bindings.any { binding ->
            requirementsById[binding.templateRequirementId]?.requirementType == InformationRequestRequirementType.FIELD
        }
        val occurrencePathsByGroupKey = advanceGroupOccurrences(
            request,
            groupRepository.findForVersion(fromTemplateVersionId),
            groupRepository.findForVersion(version.id),
            hasFieldRequirements,
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
        supportingEvidenceLinkService.materialize(request)
        return InformationRequestRequirementAdvance(advancedRequirementIds = advanced, addedRequirementIds = added)
    }

    private fun advanceGroupOccurrences(
        request: InformationRequest,
        earlierGroups: List<InformationRequestTemplateRequirementGroup>,
        groups: List<InformationRequestTemplateRequirementGroup>,
        hasFieldRequirements: Boolean,
    ): Map<String, List<String>>
    {
        val earlierKeys = earlierGroups.associate { it.id to it.groupKey }
        val groupsByKey = groups.associateBy { it.groupKey }
        val occurrences = groupOccurrenceRepository.findForRequest(request.id)
        occurrences.forEach { occurrence ->
            val target = earlierKeys[occurrence.sourceTemplateGroupId]?.let(groupsByKey::get) ?: return@forEach
            if (target.id != occurrence.sourceTemplateGroupId)
            {
                occurrence.sourceTemplateGroupId = target.id
                groupOccurrenceRepository.update(occurrence)
            }
        }

        val occurrencesByGroup = occurrences.groupBy { it.sourceTemplateGroupId }.mapValues { (_, grouped) ->
            grouped.map { it.id to it.occurrencePath }
        }.toMutableMap()
        val started = groups.filter { it.groupKey !in earlierKeys.values && occurrencesByGroup[it.id].isNullOrEmpty() }
        val childrenByParent = started.groupBy { it.parentGroupId }

        fun start(group: InformationRequestTemplateRequirementGroup)
        {
            val parents: List<Pair<UUID?, String?>> = group.parentGroupId
                ?.let { parent -> occurrencesByGroup[parent].orEmpty().map { (id, path) -> id to path } }
                ?: listOf(null to null)
            val created = parents.flatMap { (parentOccurrenceId, parentPath) ->
                (0 until group.minOccurrences).map { index ->
                    val path = groupOccurrencePath(group.groupKey, index, parentPath)
                    val occurrence = groupOccurrenceRepository.save(
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            sourceTemplateGroupId = group.id
                            this.parentOccurrenceId = parentOccurrenceId
                            occurrenceIndex = index
                            occurrencePath = path
                        },
                    )
                    if (hasFieldRequirements)
                        schemaAssignmentService.createOccurrenceValueSet(
                            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                            path,
                        )
                    occurrence.id to path
                }
            }
            occurrencesByGroup[group.id] = created
            childrenByParent[group.id].orEmpty().forEach(::start)
        }

        started.filter { group -> started.none { it.id == group.parentGroupId } }.forEach(::start)
        val keysById = groups.associate { it.id to it.groupKey }
        return occurrencesByGroup.entries
            .mapNotNull { (groupId, created) -> keysById[groupId]?.let { it to created.map { (_, path) -> path } } }
            .toMap()
    }

    private fun advanceRequirement(
        requirement: InformationRequestRequirement,
        version: InformationRequestTemplateVersion,
        binding: InformationRequestTemplateRequirementBinding,
        templateRequirement: InformationRequestTemplateRequirement,
        configuration: TemplateBindingConfiguration,
        now: Timestamp,
    )
    {
        requirement.sourceTemplateVersionId = version.id
        requirement.sourceTemplateBindingId = binding.id
        requirementRepository.update(requirement)
        requirementRepository.flushChanges()
        val pointer = currentRepository.findForRequirement(requirement.id)
            ?: throw IllegalStateException("Runtime requirement ${requirement.id} has no current revision")
        val revision = revisionRepository.save(
            InformationRequestRequirementRevision().apply {
                informationRequestRequirementId = requirement.id
                informationRequestId = requirement.informationRequestId
                sourceTemplateVersionId = version.id
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                revisionNumber = pointer.currentRevisionNumber + 1
                occurrencePath = requirement.occurrencePath
                effectiveFrom = now
                configurationHashSha256 = configurationHash(binding, templateRequirement, configuration)
                optimisticVersion = 1
            },
        )
        pointer.currentRevisionId = revision.id
        pointer.currentRevisionNumber = revision.revisionNumber
        pointer.updatedAt = now
        currentRepository.update(pointer)
    }

    /**
     * The occurrence path(s) a binding's Requirement instance(s) must be materialized at: the single
     * fixed `root` path for a binding with no anchor, or one path per runtime occurrence already
     * materialized for the group it anchors to -- none at all where that group starts with no
     * occurrences.
     */
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

    /**
     * Creates this request's first runtime occurrence(s) of every group its Template Version defines,
     * one layer of nesting at a time so a child group's occurrences are created under each of its
     * parent's occurrences. A group starts with exactly its authored minimum occurrence count, which
     * may be zero; runtime add/remove/reorder of occurrences is handled elsewhere. Provisions each new
     * occurrence's Field Value Set alongside it when the request carries Field requirements, since
     * nothing else creates that set later on demand.
     *
     * @return every group's runtime occurrence paths, keyed by the group's stable key.
     */
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
                val path = groupOccurrencePath(group.groupKey, index, parentPath)
                val occurrence = groupOccurrenceRepository.save(
                    InformationRequestGroupOccurrence().apply {
                        informationRequestId = request.id
                        sourceTemplateGroupId = group.id
                        this.parentOccurrenceId = parentOccurrenceId
                        occurrenceIndex = index
                        occurrencePath = path
                    },
                )
                if (hasFieldRequirements)
                    schemaAssignmentService.createOccurrenceValueSet(
                        FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                        path,
                    )
                occurrence.id to path
            }
            pathsByGroupKey.getOrPut(group.groupKey) { mutableListOf() }.addAll(created.map { it.second })
            childrenByParent[group.id].orEmpty().forEach { child ->
                created.forEach { (occurrenceId, path) -> materializeUnder(child, occurrenceId, path) }
            }
        }

        childrenByParent[null].orEmpty().forEach { materializeUnder(it, null, null) }
        return pathsByGroupKey
    }

    private fun groupOccurrencePath(groupKey: String, index: Int, parentPath: String?): String =
        (parentPath?.let { "$it/" } ?: "") + "$groupKey[$index]"

    private fun materializeRequirement(
        request: InformationRequest,
        version: InformationRequestTemplateVersion,
        binding: InformationRequestTemplateRequirementBinding,
        templateRequirement: InformationRequestTemplateRequirement,
        configuration: TemplateBindingConfiguration,
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
                configurationHashSha256 = configurationHash(binding, templateRequirement, configuration)
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

    private fun loadConfiguration(templateVersionId: UUID) = TemplateBindingConfiguration(
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

    private fun configurationHash(
        binding: InformationRequestTemplateRequirementBinding,
        requirement: InformationRequestTemplateRequirement,
        configuration: TemplateBindingConfiguration,
    ): String
    {
        val policy = configuration.policiesByBinding[binding.id]
        val material = listOf(
            "binding:${binding.id}",
            "requirement:${requirement.id}",
            "requirementKey:${requirement.requirementKey}",
            "requirementType:${requirement.requirementType}",
            "prompt:${binding.prompt}",
            "helpText:${binding.helpText.orEmpty()}",
            "responseMode:${binding.responseMode}",
            "requiredness:${binding.requiredness}",
            "contributorRole:${binding.contributorRole}",
            "reviewPolicy:${binding.reviewPolicy}",
            "confidentiality:${binding.confidentialityCompartmentKey.orEmpty()}",
            "condition:${binding.conditionalRuleKey.orEmpty()}",
            "occurrence:${binding.occurrenceAnchorKey.orEmpty()}",
            "collectedField:${binding.collectedFieldDefinitionId?.toString().orEmpty()}",
            "dispositions:${configuration.dispositionsByBinding[binding.id].orEmpty().map { it.disposition.name }}",
            "policy:${policy?.stableHashMaterial().orEmpty()}",
            "accepted:${policy?.let { acceptedHashMaterial(it, configuration) }.orEmpty()}",
            "substitutes:${configuration.substitutesByBinding[binding.id].orEmpty().map { it.substituteTemplateBindingId }}",
            "supporting:${configuration.evidenceLinksByBinding[binding.id].orEmpty().map { it.supportingTemplateBindingId }}",
        ).joinToString(separator = "\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun acceptedHashMaterial(
        policy: InformationRequestTemplateEvidencePolicy,
        configuration: TemplateBindingConfiguration,
    ): List<String> = configuration.acceptedValuesByPolicy[policy.id]
        .orEmpty()
        .map { "${it.attribute}:${it.acceptedValue}" }

    private fun InformationRequestTemplateEvidencePolicy.stableHashMaterial(): String = listOf(
        minimumFileCount,
        maximumFileCount,
        maximumFileSizeBytes,
        maximumTotalSizeBytes,
        minimumPageCount,
        maximumPageCount,
        issuerRequirement,
        jurisdictionRequirement,
        languageRequirement,
        issueDateRequirement,
        expiryDateRequirement,
        coveragePeriodRequirement,
        certificationRequirement,
        signatureRequirement,
        maximumIssueAgeDays,
        minimumRemainingValidityDays,
        minimumCoverageDays,
        coverageContinuityRequired,
        waiverPolicy,
        conformancePolicy,
    ).joinToString(separator = "|")

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

    private data class TemplateBindingConfiguration(
        val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
        val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
        val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
        val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
        val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
    )

    private companion object
    {
        const val ROOT_OCCURRENCE_PATH = "root"
    }
}
