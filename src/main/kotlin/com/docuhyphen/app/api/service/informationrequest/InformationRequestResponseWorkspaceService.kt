package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.InformationRequestGroupOccurrenceDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponseWorkspaceDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.FieldValueReadCommand
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestResponseWorkspaceService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateProjectionLoader: InformationRequestTemplateProjectionLoader,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val responseRepository: InformationRequestResponseRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val authorizationService: AuthorizationService,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    private val groupAuthorizationService: InformationRequestGroupAuthorizationService,
    private val supportingEvidenceLinkService: InformationRequestSupportingEvidenceLinkService,
    private val evidenceDeploymentPolicy: InformationRequestEvidenceDeploymentPolicy,
)
{
    fun loadRequest(requestId: UUID, access: RequestAccessContext): InformationRequestDto
    {
        val request = queryService.findById(requestId, access)
        val templateVersion = loadTemplateVersion(request)
        val occurrences = occurrenceRepository.findForRequest(requestId)
        val activeRequirements = activeRequirements(requestId, occurrences)
        val requirements = activeRequirements.filter { canViewRequirement(it.id, access) }
        val evaluations = InformationRequestWorkspaceOccurrenceProjection.conditions(
            conditionEvaluationService.evaluate(requestId), templateVersion, requirements, activeRequirements,
        )
        return InformationRequestDtoMapper.toDto(request, evaluations)
    }

    fun load(requestId: UUID, access: RequestAccessContext): InformationRequestResponseWorkspaceDto
    {
        val request = queryService.findById(requestId, access)
        val templateVersionDto = loadTemplateVersion(request)
        val conditionEvaluations = conditionEvaluationService.evaluate(requestId)
        val responsesByRequirement = responseRepository.findAllForRequest(requestId)
            .associateBy { it.informationRequestRequirementId }
        val activeOccurrences = occurrenceRepository.findForRequest(requestId)
        val activeRequirements = activeRequirements(requestId, activeOccurrences)
        val disclosedRequirements = activeRequirements.filter { canViewRequirement(it.id, access) }
        val creationGroupKeys = recipientSafeCreationGroupKeys(
            templateVersion = templateVersionDto,
            bindings = bindingRepository.findOrdered(templateVersionDto.id),
            request = request,
            access = access,
        )
        val safeTemplateVersion = recipientSafeTemplateVersion(templateVersionDto, disclosedRequirements, creationGroupKeys)
        val safeConditionEvaluations = InformationRequestWorkspaceOccurrenceProjection.conditions(
            conditionEvaluations, templateVersionDto, disclosedRequirements, activeRequirements,
        )
        val authoredRequirementsByBindingId = templateVersionDto.sections
            .flatMap { it.requirements }
            .associateBy { it.id }
        val requirementsByBindingId = safeTemplateVersion.sections
            .flatMap { it.requirements }
            .associateBy { it.id }
        val authorizedRequirements = disclosedRequirements
            .filter { requirement -> InformationRequestActiveResponseProjection.isActive(
                authoredRequirementsByBindingId[requirement.sourceTemplateBindingId]?.conditionalRuleKey,
                requirement.occurrencePath, conditionEvaluations,
                responsesByRequirement[requirement.id]?.activeInResponse != false)
            }
        val fieldProjections = authorizedRequirements.associateWith { requirement ->
            fieldProjection(requestId, requirement, requirementsByBindingId[requirement.sourceTemplateBindingId], access)
        }

        return InformationRequestResponseWorkspaceDtoMapper.toDto(
            request = InformationRequestDtoMapper.toDto(request, safeConditionEvaluations),
            templateVersion = safeTemplateVersion,
            responseETag = InformationRequestETag.responsesOf(request),
            occurrences = InformationRequestWorkspaceOccurrenceProjection.occurrences(
                activeOccurrences, safeTemplateVersion, disclosedRequirements, activeRequirements, creationGroupKeys,
            )
                .map(InformationRequestGroupOccurrenceDtoMapper::toDto),
            schemaAssignment = InformationRequestActiveResponseProjection.schema(fieldProjections.values),
            responses = authorizedRequirements.map { requirement ->
                InformationRequestResponseWorkspaceDtoMapper.responseDto(
                    request = request,
                    requirement = requirement,
                    response = responsesByRequirement[requirement.id],
                    fieldProjection = fieldProjections[requirement],
                    updatedAt = request.updatedAt,
                )
            },
            supportingEvidenceLinks = supportingEvidenceLinkService
                .visibleLinks(request, authorizedRequirements.map { it.id }.toSet())
                .map(InformationRequestResponseWorkspaceDtoMapper::linkDto),
            evidenceUploadAvailable = evidenceDeploymentPolicy.uploadAvailable(),
            evidenceMalwareScanning = evidenceDeploymentPolicy.malwareScanningConfigured(),
        )
    }

    private fun loadTemplateVersion(request: InformationRequest): InformationRequestTemplateVersionDto
    {
        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")
        return templateProjectionLoader.loadVersion(version)
    }

    private fun activeRequirements(
        requestId: UUID,
        occurrences: List<InformationRequestGroupOccurrence>,
    ): List<InformationRequestRequirement>
    {
        val activePaths = occurrences.map { it.occurrencePath }.toSet()
        return requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activePaths) }
    }

    private fun canViewRequirement(requirementId: UUID, access: RequestAccessContext): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) is Decision.Allow

    private fun fieldProjection(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        requirementDto: InformationRequestTemplateRequirementDto?,
        access: RequestAccessContext,
    ): SchemaAssignmentDto?
    {
        if (requirementDto?.requirementType != InformationRequestRequirementType.FIELD) return null
        return schemaAssignmentService.getAssignment(
            FieldValueReadCommand(
                resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                access = FieldsAccessContext(access.principal, access.authorization),
                valueSet = valueSetRef(requirement.occurrencePath),
            ),
        )?.let { InformationRequestActiveResponseProjection.field(it, requirementDto.collectedFieldDefinitionId) }
    }

    private fun valueSetRef(occurrencePath: String): FieldValueSetRef =
        if (InformationRequestOccurrencePath.isRoot(occurrencePath))
            FieldValueSetRef.Root
        else
            FieldValueSetRef.Occurrence(occurrencePath)

    private fun recipientSafeTemplateVersion(
        templateVersion: InformationRequestTemplateVersionDto,
        disclosedRequirements: List<InformationRequestRequirement>,
        safeExtraGroupKeys: Set<String>,
    ): InformationRequestTemplateVersionDto
    {
        val disclosedBindingIds = disclosedRequirements.map { it.sourceTemplateBindingId }.toSet()
        val fullRequirements = templateVersion.sections.flatMap { it.requirements }
        val disclosedRequirementKeys = fullRequirements
            .filter { it.id in disclosedBindingIds }
            .map { it.requirementKey }
            .toSet()
        val disclosedFieldIds = fullRequirements
            .filter { it.id in disclosedBindingIds }
            .mapNotNull { it.collectedFieldDefinitionId }
            .toSet()
        val safeRules = templateVersion.conditionRules.filter { rule ->
            fullRequirements.any { it.id in disclosedBindingIds && it.conditionalRuleKey == rule.ruleKey } &&
                rule.predicates.all { predicate ->
                    (predicate.sourceRequirementKey == null || predicate.sourceRequirementKey in disclosedRequirementKeys) &&
                        (predicate.fieldDefinitionId == null || predicate.fieldDefinitionId in disclosedFieldIds)
                }
        }
        val safeRuleKeys = safeRules.map { it.ruleKey }.toSet()
        val safeSections = templateVersion.sections.mapNotNull { section ->
            val requirements = section.requirements
                .filter { it.id in disclosedBindingIds }
                .map { requirement ->
                    requirement.copy(
                        conditionalRuleKey = requirement.conditionalRuleKey?.takeIf { it in safeRuleKeys },
                        substituteRequirementKeys = requirement.substituteRequirementKeys.filter { it in disclosedRequirementKeys },
                        supportingEvidenceRequirementKeys = requirement.supportingEvidenceRequirementKeys.filter { it in disclosedRequirementKeys },
                    )
                }
            if (requirements.isEmpty()) null else section.copy(requirements = requirements)
        }
        val safeGroupKeys = recipientSafeGroupKeys(
            templateVersion.groups,
            safeSections.flatMap { it.requirements },
            safeExtraGroupKeys,
        )
        return templateVersion.copy(
            sections = safeSections,
            groups = templateVersion.groups.filter { it.groupKey in safeGroupKeys },
            conditionRules = safeRules,
        )
    }

    private fun recipientSafeCreationGroupKeys(
        templateVersion: InformationRequestTemplateVersionDto,
        bindings: List<InformationRequestTemplateRequirementBinding>,
        request: InformationRequest,
        access: RequestAccessContext,
    ): Set<String>
    {
        val groupsByParent = templateVersion.groups.groupBy { it.parentGroupKey }
        return templateVersion.groups
            .filter { authorizeGroupMaterialization(request, access, materializedBindings(it, groupsByParent, bindings)) }
            .map { it.groupKey }
            .toSet()
    }

    private fun authorizeGroupMaterialization(
        request: InformationRequest,
        access: RequestAccessContext,
        bindings: List<InformationRequestTemplateRequirementBinding>,
    ): Boolean
    {
        if (bindings.isEmpty()) return false
        return try
        {
            groupAuthorizationService.authorizeMaterializedBindings(access, request, bindings)
            true
        }
        catch (_: ForbiddenException)
        {
            false
        }
    }

    private fun materializedBindings(
        group: InformationRequestTemplateGroupDto,
        groupsByParent: Map<String?, List<InformationRequestTemplateGroupDto>>,
        bindings: List<InformationRequestTemplateRequirementBinding>,
    ): List<InformationRequestTemplateRequirementBinding> =
        bindings.filter { it.occurrenceAnchorKey == group.groupKey } +
            groupsByParent[group.groupKey].orEmpty()
                .filter { it.minOccurrences > 0 }
                .flatMap { materializedBindings(it, groupsByParent, bindings) }

    private fun recipientSafeGroupKeys(
        groups: List<InformationRequestTemplateGroupDto>,
        requirements: List<InformationRequestTemplateRequirementDto>,
        extraGroupKeys: Set<String>,
    ): Set<String>
    {
        val byKey = groups.associateBy { it.groupKey }
        val safe = mutableSetOf<String>()
        fun addWithParents(groupKey: String?)
        {
            val group = groupKey?.let(byKey::get) ?: return
            if (!safe.add(group.groupKey)) return
            addWithParents(group.parentGroupKey)
        }
        requirements.forEach { addWithParents(it.occurrenceAnchorKey) }
        extraGroupKeys.forEach { addWithParents(it) }
        return safe
    }

}
