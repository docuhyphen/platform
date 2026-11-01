package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.InformationRequestGroupOccurrenceDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponseWorkspaceDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueReadCommand
import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestAuthorActions
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestWorkspacePurpose
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAuthorActionService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceDeploymentPolicy
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFirstViewService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupAuthorizationService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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
    private val firstViewService: InformationRequestFirstViewService,
    private val titleReader: InformationRequestTitleReader,
    private val standingService: InformationRequestExecutionStandingService,
    private val headerProjection: InformationRequestHeaderProjectionService,
    private val lockService: InformationRequestSubmissionLockService,
    private val evidenceQueries: InformationRequestEvidenceQueryService,
    private val authorActions: InformationRequestAuthorActionService,
    private val parentState: ExchangeLifecycleStateService,
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
        return headerProjection.headerOf(request, access, evaluations)
    }

    fun load(
        requestId: UUID,
        access: RequestAccessContext,
        purpose: InformationRequestWorkspacePurpose = InformationRequestWorkspacePurpose.RESPONSE,
    ): InformationRequestResponseWorkspaceDto
    {
        val request = queryService.findById(requestId, access)
        val manages = managesRequest(request, access)
        if (purpose == InformationRequestWorkspacePurpose.MANAGEMENT && !manages)
            throw ForbiddenException(MANAGEMENT_READ_REFUSAL)
        if (purpose == InformationRequestWorkspacePurpose.RESPONSE) {
            runCatching { firstViewService.recordIfFirst(requestId, access) }
                .onFailure {
                    logger.warn(
                        "Information Request first view was not recorded for request={}",
                        requestId,
                        it
                    )
                }
        }
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
        val safeTemplateVersion = reviewStagesFor(
            InformationRequestRecipientTemplateProjection.recipientSafe(
                templateVersionDto,
                disclosedRequirements.map { it.sourceTemplateBindingId }.toSet(),
                creationGroupKeys,
            ),
            manages,
        )
        val safeConditionEvaluations = InformationRequestWorkspaceOccurrenceProjection.conditions(
            conditionEvaluations, templateVersionDto, disclosedRequirements, activeRequirements,
        )
        val authoredRequirementsByBindingId = templateVersionDto.sections
            .flatMap { it.requirements }
            .associateBy { it.id }
        val requirementsByBindingId = safeTemplateVersion.sections
            .flatMap { it.requirements }
            .associateBy { it.id }
        val inEffect = { requirement: InformationRequestRequirement ->
            InformationRequestActiveResponseProjection.isActive(
                InformationRequestGoverningCondition.ruleKeyOf(authoredRequirementsByBindingId[requirement.sourceTemplateBindingId]),
                requirement.occurrencePath, conditionEvaluations,
                responsesByRequirement[requirement.id]?.activeInResponse != false
            )
        }
        val authorizedRequirements = disclosedRequirements.filter(inEffect)
        val requirementsAwaitingStepUp = awaitingStepUp(activeRequirements - disclosedRequirements.toSet(), access)
            .count(inEffect)
        val editLocks = lockService.editLocks(requestId, authorizedRequirements.map { it.id })
        val fileCounts = evidenceQueries.attachedFileCounts(
            requestId,
            authorizedRequirements
                .filter {
                    requirementsByBindingId[it.sourceTemplateBindingId]?.requirementType == InformationRequestRequirementType.DOCUMENT
                }
                .map { it.id },
            access,
        )
        val fieldProjections = authorizedRequirements.associateWith { requirement ->
            fieldProjection(
                requestId,
                requirement,
                requirementsByBindingId[requirement.sourceTemplateBindingId],
                access
            )
        }

        return InformationRequestResponseWorkspaceDtoMapper.toDto(
            request = headerProjection.headerOf(request, access, safeConditionEvaluations),
            title = titleReader.titleOf(request),
            respondentKey = InformationRequestRespondentKey.of(access.principal),
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
                    editLock = editLocks[requirement.id],
                    evidenceFileCount = fileCounts[requirement.id],
                )
            },
            supportingEvidenceLinks = supportingEvidenceLinkService
                .visibleLinks(request, authorizedRequirements.map { it.id }.toSet())
                .map(InformationRequestResponseWorkspaceDtoMapper::linkDto),
            evidenceUploadAvailable = evidenceDeploymentPolicy.uploadAvailable(),
            evidenceMalwareScanning = evidenceDeploymentPolicy.malwareScanningConfigured(),
            executionStanding = executionStandingFor(request, manages),
            requirementsAwaitingStepUp = requirementsAwaitingStepUp,
            canRequestSupplement = mayRequestSupplement(request, access),
            authorActions = if (manages) authorActions.actionsFor(
                request,
                access
            ) else InformationRequestAuthorActions(),
            creatableGroupKeys = creationGroupKeys,
            exchangeAccepted = parentState.snapshot(request.exchangeId)?.status != ExchangeStatus.INITIATED,
        )
    }

    private fun managesRequest(request: InformationRequest, access: RequestAccessContext): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
            ResourceRef.informationRequest(request.id),
            access.authorization,
        ) is Decision.Allow

    private fun reviewStagesFor(
        templateVersion: InformationRequestTemplateVersionDto,
        manages: Boolean,
    ): InformationRequestTemplateVersionDto =
        if (manages) templateVersion else InformationRequestRecipientTemplateProjection.forParticipant(templateVersion)

    private fun mayRequestSupplement(request: InformationRequest, access: RequestAccessContext): Boolean =
        SUPPLEMENT_ACTIONS.any { action ->
            authorizationService.authorize(
                access.principal,
                action,
                ResourceRef.informationRequest(request.id),
                access.authorization,
            ) is Decision.Allow
        }

    private fun awaitingStepUp(
        undisclosed: List<InformationRequestRequirement>,
        access: RequestAccessContext,
    ): List<InformationRequestRequirement> {
        if (access.authorization.mfaSatisfied) return emptyList()
        val steppedUp = access.copy(authorization = access.authorization.copy(mfaSatisfied = true))
        return undisclosed.filter { canViewRequirement(it.id, steppedUp) }
    }

    private fun executionStandingFor(
        request: InformationRequest,
        manages: Boolean,
    ): InformationRequestExecutionStanding {
        val standing = standingService.standingOf(request, standingService.ownerStanding(request))
        return if (manages) standing else standing.forParticipant()
    }

    private companion object
    {
        val SUPPLEMENT_ACTIONS =
            listOf(Action.INFORMATION_REQUEST_SUPERSEDE, Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT)
        const val MANAGEMENT_READ_REFUSAL =
            "Only a caller who manages this Information Request can open it for management"
        private val logger = org.slf4j.LoggerFactory.getLogger(InformationRequestResponseWorkspaceService::class.java)
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

    private fun recipientSafeCreationGroupKeys(
        templateVersion: InformationRequestTemplateVersionDto,
        bindings: List<InformationRequestTemplateRequirementBinding>,
        request: InformationRequest,
        access: RequestAccessContext,
    ): Set<String>
    {
        val groupsByParent = templateVersion.groups.groupBy { it.parentGroupKey }
        return templateVersion.groups
            .filter {
                authorizeGroupCreation(
                    request,
                    access,
                    materializedBindings(it, groupsByParent, bindings),
                    bindingsUnder(it, groupsByParent, bindings),
                )
            }
            .map { it.groupKey }
            .toSet()
    }

    private fun authorizeGroupCreation(
        request: InformationRequest,
        access: RequestAccessContext,
        materializedBindings: List<InformationRequestTemplateRequirementBinding>,
        groupBindings: List<InformationRequestTemplateRequirementBinding>,
    ): Boolean
    {
        return !(materializedBindings.isEmpty() && groupBindings.isEmpty()) && try {
            groupAuthorizationService.authorizeGroupCreation(access, request, materializedBindings, groupBindings)
            true
        } catch (_: ForbiddenException) {
            false
        }
    }

    private fun bindingsUnder(
        group: InformationRequestTemplateGroupDto,
        groupsByParent: Map<String?, List<InformationRequestTemplateGroupDto>>,
        bindings: List<InformationRequestTemplateRequirementBinding>,
    ): List<InformationRequestTemplateRequirementBinding> =
        bindings.filter { it.occurrenceAnchorKey == group.groupKey } +
                groupsByParent[group.groupKey].orEmpty().flatMap { bindingsUnder(it, groupsByParent, bindings) }

    private fun materializedBindings(
        group: InformationRequestTemplateGroupDto,
        groupsByParent: Map<String?, List<InformationRequestTemplateGroupDto>>,
        bindings: List<InformationRequestTemplateRequirementBinding>,
    ): List<InformationRequestTemplateRequirementBinding> =
        bindings.filter { it.occurrenceAnchorKey == group.groupKey } +
                groupsByParent[group.groupKey].orEmpty()
                    .filter { it.minOccurrences > 0 }
                    .flatMap { materializedBindings(it, groupsByParent, bindings) }

}
