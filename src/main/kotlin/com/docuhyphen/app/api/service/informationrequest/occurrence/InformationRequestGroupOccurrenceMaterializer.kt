package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceTemplateData
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateBindingConfiguration
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.*
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestRequirementConfigurationHash
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestGroupOccurrenceMaterializer @Inject constructor(
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val groupOccurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateBindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val evidencePolicyRepository: InformationRequestTemplateEvidencePolicyRepository,
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository,
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository,
    private val evidenceLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val currentRepository: InformationRequestRequirementCurrentRepository,
    private val fieldsReader: FieldsReadFacade,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val supportingEvidenceLinkService: InformationRequestSupportingEvidenceLinkService,
) {
    internal fun templateOf(request: InformationRequest): InformationRequestGroupOccurrenceTemplateData {
        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.STATE_INVALID,
                "Information Request Template Version not found",
            )
        val groups = groupRepository.findForVersion(version.id)
        return InformationRequestGroupOccurrenceTemplateData(
            templateDefinitionId = version.templateDefinitionId,
            groupsByKey = groups.associateBy { it.groupKey },
            groupsById = groups.associateBy { it.id },
            childrenByParent = groups.groupBy { it.parentGroupId },
            bindings = templateBindingRepository.findOrdered(version.id),
            requirementsById = templateRequirementRepository
                .findAllByDefinition(version.templateDefinitionId)
                .associateBy { it.id },
            configuration = loadConfiguration(version.id),
        )
    }

    internal fun materialize(
        request: InformationRequest,
        template: InformationRequestGroupOccurrenceTemplateData,
        group: InformationRequestTemplateRequirementGroup,
        parent: InformationRequestGroupOccurrence?,
        displayIndex: Int,
        identityIndex: Int,
        now: Timestamp,
    ): InformationRequestGroupOccurrence {
        val occurrence = createOccurrence(
            request,
            group,
            parent,
            displayIndex,
            identityIndex,
            hasSchemaAssignment(request.id),
        )
        materializeAnchoredRequirements(request, template, group, occurrence, now)
        materializeChildMinimums(request, template, group, occurrence, now)
        supportingEvidenceLinkService.materialize(request)
        return occurrence
    }

    private fun createOccurrence(
        request: InformationRequest,
        group: InformationRequestTemplateRequirementGroup,
        parent: InformationRequestGroupOccurrence?,
        displayIndex: Int,
        identityIndex: Int,
        provisionFieldValueSet: Boolean,
    ): InformationRequestGroupOccurrence {
        val path =
            InformationRequestOccurrencePath.groupOccurrencePath(group.groupKey, identityIndex, parent?.occurrencePath)
        val occurrence = groupOccurrenceRepository.save(
            InformationRequestGroupOccurrence().apply {
                informationRequestId = request.id
                sourceTemplateGroupId = group.id
                parentOccurrenceId = parent?.id
                occurrenceIndex = displayIndex
                occurrencePath = path
            },
        )
        if (provisionFieldValueSet) {
            schemaAssignmentService.createOccurrenceValueSet(
                FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                path,
            )
        }
        return occurrence
    }

    private fun materializeChildMinimums(
        request: InformationRequest,
        template: InformationRequestGroupOccurrenceTemplateData,
        parentGroup: InformationRequestTemplateRequirementGroup,
        parentOccurrence: InformationRequestGroupOccurrence,
        now: Timestamp,
    ) {
        template.childrenByParent[parentGroup.id].orEmpty().forEach { child ->
            repeat(child.minOccurrences) { index ->
                val occurrence = createOccurrence(
                    request,
                    child,
                    parentOccurrence,
                    index,
                    index,
                    hasSchemaAssignment(request.id),
                )
                materializeAnchoredRequirements(request, template, child, occurrence, now)
                materializeChildMinimums(request, template, child, occurrence, now)
            }
        }
    }

    private fun materializeAnchoredRequirements(
        request: InformationRequest,
        template: InformationRequestGroupOccurrenceTemplateData,
        group: InformationRequestTemplateRequirementGroup,
        occurrence: InformationRequestGroupOccurrence,
        now: Timestamp,
    ) {
        template.bindings
            .filter { it.occurrenceAnchorKey == group.groupKey }
            .forEach { binding ->
                val requirement = template.requirementsById[binding.templateRequirementId]
                    ?: throw InformationRequestLifecycleException(
                        InformationRequestErrorCatalog.STATE_INVALID,
                        "Information Request Template Requirement not found",
                    )
                materializeRequirement(
                    request,
                    binding,
                    requirement,
                    template.configuration,
                    now,
                    occurrence.occurrencePath
                )
            }
    }

    private fun materializeRequirement(
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
        templateRequirement: InformationRequestTemplateRequirement,
        configuration: InformationRequestTemplateBindingConfiguration,
        now: Timestamp,
        occurrencePath: String,
    ) {
        val requirement = requirementRepository.save(
            InformationRequestRequirement().apply {
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                this.occurrencePath = occurrencePath
            },
        )
        val revision = revisionRepository.save(
            InformationRequestRequirementRevision().apply {
                informationRequestRequirementId = requirement.id
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
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
    }

    private fun hasSchemaAssignment(requestId: UUID): Boolean =
        fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId)) != null

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
}
