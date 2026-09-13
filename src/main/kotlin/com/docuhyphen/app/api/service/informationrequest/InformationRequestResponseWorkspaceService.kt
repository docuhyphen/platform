package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.InformationRequestGroupOccurrenceDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponseWorkspaceDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.FieldValueReadCommand
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
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
    private val schemaAssignmentService: SchemaAssignmentService,
    private val authorizationService: AuthorizationService,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
)
{
    fun load(requestId: UUID, access: RequestAccessContext): InformationRequestResponseWorkspaceDto
    {
        val request = queryService.findById(requestId, access)
        val templateVersion = templateVersionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")
        val templateVersionDto = templateProjectionLoader.loadVersion(templateVersion)
        val conditionEvaluations = conditionEvaluationService.evaluate(requestId)
        val requirementsByBindingId = templateVersionDto.sections
            .flatMap { it.requirements }
            .associateBy { it.id }
        val responsesByRequirement = responseRepository.findAllForRequest(requestId)
            .associateBy { it.informationRequestRequirementId }
        val activeOccurrences = occurrenceRepository.findForRequest(requestId)
        val activeOccurrencePaths = activeOccurrences.map { it.occurrencePath }.toSet()
        val authorizedRequirements = requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
            .filter { canViewRequirement(it.id, access) }
            .filter { requirement -> InformationRequestActiveResponseProjection.isActive(
                requirementsByBindingId[requirement.sourceTemplateBindingId]?.conditionalRuleKey,
                requirement.occurrencePath, conditionEvaluations,
                responsesByRequirement[requirement.id]?.activeInResponse != false)
            }
        val fieldProjections = authorizedRequirements.associateWith { requirement ->
            fieldProjection(requestId, requirement, requirementsByBindingId[requirement.sourceTemplateBindingId], access)
        }

        return InformationRequestResponseWorkspaceDtoMapper.toDto(
            request = InformationRequestDtoMapper.toDto(request, conditionEvaluations),
            templateVersion = templateVersionDto,
            responseETag = InformationRequestETag.responsesOf(request),
            occurrences = activeOccurrences.map(InformationRequestGroupOccurrenceDtoMapper::toDto),
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
        )
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
}
