package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.fields.FieldValueClearCommand
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldValueReadCommand
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldValueWriteCommand
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

interface InformationRequestResponseStore
{
    fun findCurrentForRequest(requestId: UUID): List<InformationRequestResponse>

    fun findAllForRequest(requestId: UUID): List<InformationRequestResponse>

    fun findCurrentForUpdate(requestId: UUID, requirementId: UUID): InformationRequestResponse?

    fun save(response: InformationRequestResponse): InformationRequestResponse

    fun update(response: InformationRequestResponse): InformationRequestResponse
}

sealed interface ResponseNarrativePatch
{
    data object Unchanged : ResponseNarrativePatch

    data object Clear : ResponseNarrativePatch

    data class Set(val value: String) : ResponseNarrativePatch
}

data class InformationRequestResponsePatch(
    val requirementId: UUID,
    val disposition: InformationRequestResponseDisposition? = null,
    val narrative: ResponseNarrativePatch = ResponseNarrativePatch.Unchanged,
    val fieldValues: ResponseFieldValuesPatch? = null,
)

data class ResponseFieldValuesPatch(
    val entries: List<FieldValueEntry>,
    val precondition: FieldsPrecondition,
)

data class PatchInformationRequestResponsesCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val patches: List<InformationRequestResponsePatch>,
    val confirmedHiddenResponseClears: Set<UUID> = emptySet(),
)

data class InformationRequestResponseDraftResult(
    val request: InformationRequest,
    val responseETag: String,
    val responses: List<InformationRequestResponse>,
    val requirementsById: Map<UUID, InformationRequestRequirement> = emptyMap(),
    val fieldValueProjectionsByRequirementId: Map<UUID, SchemaAssignmentDto> = emptyMap(),
)

@ApplicationScoped
class InformationRequestResponseDraftService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeRepository: ExchangeRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val responseStore: InformationRequestResponseStore,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val schemaAssignmentRepository: SchemaAssignmentRepository,
    private val fieldValueSetRepository: FieldValueSetRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    private val structuredResponseValidationService: InformationRequestStructuredResponseValidationService,
    private val lockService: InformationRequestSubmissionLockService,
)
{
    @Transactional
    fun patch(command: PatchInformationRequestResponsesCommand): InformationRequestResponseDraftResult =
        runOnce(command) {
            mutate(command)
        }

    private fun runOnce(
        command: PatchInformationRequestResponsesCommand,
        mutation: () -> InformationRequestResponseDraftResult,
    ): InformationRequestResponseDraftResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = PATCH_RESPONSES_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = fingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = mutation()
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayResponseDraftResult(command, decision.result)
        }
    }

    private fun mutate(command: PatchInformationRequestResponsesCommand): InformationRequestResponseDraftResult
    {
        val exchange = lockParentExchangeOf(command.requestId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(command.requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        command.precondition.requireSatisfiedBy(InformationRequestETag.responsesOf(request))

        requireResponseMutationAllowed(exchange, request)
        requireContinuationEntitlement(exchange, request)

        val allRequirements = requirementRepository.findForRequest(request.id).associateBy { it.id }
        val activeOccurrencePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        val requirements = allRequirements.filterValues {
            InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths)
        }
        command.patches.forEach { patch ->
            val requirement = allRequirements[patch.requirementId]
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request Requirement not found",
                )
            if (!InformationRequestOccurrencePath.isActiveOccurrence(requirement.occurrencePath, activeOccurrencePaths))
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                    "This Information Request group occurrence has been removed",
                )
            }
        }
        lockService.requireUnlocked(request.id, command.patches.map { it.requirementId })
        structuredResponseValidationService.validate(
            InformationRequestStructuredResponseValidationContext(
                request = request,
                requirementsById = requirements,
                patches = command.patches,
                activeResponses = responseStore.findCurrentForRequest(request.id),
            ),
        )
        val currentRevisions = revisionRepository.findCurrentForRequest(request.id)
            .associateBy { it.informationRequestRequirementId }
        val now = Timestamp.from(Instant.now())
        val changed = mutableListOf<InformationRequestResponse>()
        val fieldValueProjections = mutableMapOf<UUID, SchemaAssignmentDto>()
        val revisionNumber = request.responseRevision + 1
        command.patches.forEach { patch ->
            val requirement = requirements.getValue(patch.requirementId)
            authorize(command.access, requirement.id)
            patch.disposition?.let { requirePermittedDisposition(requirement, it) }
            if (requirement.id !in currentRevisions)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.STATE_INVALID,
                    "Information Request Requirement has no current revision",
                )
            }
        }

        val fieldResultsByRequirement = writeBatchedFieldValues(request.id, requirements, command.access, command.patches)
        fieldResultsByRequirement.forEach { (requirementId, result) -> fieldValueProjections[requirementId] = result.projection }

        command.patches.forEach { patch ->
            val requirement = requirements.getValue(patch.requirementId)
            val revision = currentRevisions.getValue(requirement.id)
            val fieldResult = fieldResultsByRequirement[patch.requirementId]
            val response = responseStore.findCurrentForUpdate(request.id, requirement.id)
            val updated = applyPatch(
                current = response,
                request = request,
                requirement = requirement,
                revision = revision,
                patch = patch,
                fieldValueSetId = fieldResult?.valueSetId,
                fieldValuesChanged = fieldResult?.changed ?: false,
                revisionNumber = revisionNumber,
                access = command.access,
                now = now,
            )
            if (updated != null) changed += updated
        }

        val processedHiddenRequirements = mutableSetOf<UUID>()
        while (true)
        {
            val hiddenChanges = enforceHiddenResponsePolicies(
                request = request,
                requirements = requirements,
                confirmedClears = command.confirmedHiddenResponseClears,
                access = command.access,
                revisionNumber = revisionNumber,
                now = now,
                processedRequirementIds = processedHiddenRequirements,
            )
            if (hiddenChanges.isEmpty()) break
            changed += hiddenChanges
            processedHiddenRequirements += hiddenChanges.map { it.informationRequestRequirementId }
        }

        if (changed.isNotEmpty())
        {
            val previousState = request.state
            request.responseRevision += 1
            request.updatedAt = now
            requestRepository.update(request)
            transitionHistory.record(
                InformationRequestTransitionHistoryCommand(
                    request = request,
                    fromState = previousState,
                    toState = request.state,
                    mutation = InformationRequestMutation.SAVE_RESPONSE,
                    actor = command.access.principal,
                    idempotencyKey = historyIdempotencyKey(request.id, command.idempotencyKey),
                ),
            )
        }

        return InformationRequestResponseDraftResult(
            request = request,
            responseETag = InformationRequestETag.responsesOf(request),
            responses = activeResponses(request.id),
            requirementsById = requirements,
            fieldValueProjectionsByRequirementId = fieldValueProjections.mapValues { (id, projection) ->
                activeFieldProjection(request.id, requirements.getValue(id), projection)
            },
        )
    }

    private fun requireResponseMutationAllowed(exchange: Exchange, request: InformationRequest)
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true,
            ),
            request.state,
            InformationRequestMutation.SAVE_RESPONSE,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request response mutation is not allowed",
            )
        }
    }

    private fun requireContinuationEntitlement(exchange: Exchange, request: InformationRequest)
    {
        val grant = executionGrantService.findForRequest(request.id)
        if (grant == null)
        {
            entitlementGuard.requireRequestMutation(exchange)
        }
        else
        {
            entitlementGuard.requireNotOperationallySuspended(exchange)
            requireGrantNotRevoked(grant)
        }
    }

    private fun requireGrantNotRevoked(grant: RequestExecutionGrant)
    {
        if (grant.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
    }

    private fun authorize(access: RequestAccessContext, requirementId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to respond to Information Request Requirement")
        }
    }

    private fun requirePermittedDisposition(
        requirement: InformationRequestRequirement,
        disposition: InformationRequestResponseDisposition,
    )
    {
        if (disposition == InformationRequestResponseDisposition.NOT_ANSWERED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                "NOT_ANSWERED is not a response disposition",
            )
        }
        val permitted = dispositionRepository.findForBinding(requirement.sourceTemplateBindingId)
            .map { it.disposition }
            .toSet()
        if (disposition !in permitted)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                "This Information Request Requirement does not permit disposition $disposition",
            )
        }
    }

    private fun applyPatch(
        current: InformationRequestResponse?,
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        revision: InformationRequestRequirementRevision,
        patch: InformationRequestResponsePatch,
        fieldValueSetId: UUID?,
        fieldValuesChanged: Boolean,
        revisionNumber: Long,
        access: RequestAccessContext,
        now: Timestamp,
    ): InformationRequestResponse?
    {
        val nextDisposition = patch.disposition ?: current?.disposition
            ?: InformationRequestResponseDisposition.NOT_ANSWERED
        val nextNarrative = when (patch.narrative)
        {
            ResponseNarrativePatch.Unchanged -> current?.narrative
            ResponseNarrativePatch.Clear -> null
            is ResponseNarrativePatch.Set -> patch.narrative.value
        }
        requireNarrativeForDisposition(nextDisposition, nextNarrative)
        if (current != null &&
            current.disposition == nextDisposition &&
            current.narrative == nextNarrative &&
            (fieldValueSetId == null || current.fieldValueSetId == fieldValueSetId) &&
            current.activeInResponse &&
            current.hiddenByConditionRuleKey == null &&
            current.hiddenDataPolicy == null &&
            current.hiddenAt == null &&
            current.reconfirmationRequiredByAmendmentId == null &&
            !fieldValuesChanged)
        {
            return null
        }

        val response = current ?: InformationRequestResponse().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirement.id
            requirementRevisionId = revision.id
            occurrencePath = requirement.occurrencePath
            createdAt = now
        }
        response.requirementRevisionId = revision.id
        response.disposition = nextDisposition
        response.narrative = nextNarrative
        if (fieldValueSetId != null) response.fieldValueSetId = fieldValueSetId
        response.activeInResponse = true
        response.hiddenByConditionRuleKey = null
        response.hiddenDataPolicy = null
        response.hiddenAt = null
        response.reconfirmationRequiredByAmendmentId = null
        response.responseRevision = revisionNumber
        response.recordedByPrincipalKind = access.principal.kind
        response.recordedByPrincipalId = access.principal.id
        response.recordedBySessionRef = access.authorization.sessionRef
        response.updatedAt = now
        return if (current == null) responseStore.save(response) else responseStore.update(response)
    }

    private fun requireNarrativeForDisposition(
        disposition: InformationRequestResponseDisposition,
        narrative: String?,
    )
    {
        if (disposition in NARRATIVE_REQUIRED_DISPOSITIONS && narrative.isNullOrBlank())
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RESPONSE_NARRATIVE_REQUIRED,
                "This response disposition requires a narrative",
            )
        }
    }

    private fun enforceHiddenResponsePolicies(
        request: InformationRequest,
        requirements: Map<UUID, InformationRequestRequirement>,
        confirmedClears: Set<UUID>,
        access: RequestAccessContext,
        revisionNumber: Long,
        now: Timestamp,
        processedRequirementIds: Set<UUID>,
    ): List<InformationRequestResponse>
    {
        val evaluations = conditionEvaluationService.evaluate(request.id)
            .associateBy { it.ruleKey to it.occurrencePath }
        if (evaluations.isEmpty()) return emptyList()

        val bindingsById = bindingRepository.findOrdered(request.templateVersionId).associateBy { it.id }
        val responsesByRequirement = responseStore.findAllForRequest(request.id)
            .associateBy { it.informationRequestRequirementId }
        return requirements.values.mapNotNull { requirement ->
            if (requirement.id in processedRequirementIds) return@mapNotNull null
            val binding = bindingsById[requirement.sourceTemplateBindingId] ?: return@mapNotNull null
            val ruleKey = binding.conditionalRuleKey ?: return@mapNotNull null
            val evaluation = evaluations[ruleKey to requirement.occurrencePath]
                ?: evaluations[ruleKey to ROOT_OCCURRENCE_PATH]
                ?: return@mapNotNull null
            val response = responsesByRequirement[requirement.id]
            if (evaluation.state == InformationRequestConditionEvaluationState.TRUE)
            {
                return@mapNotNull reactivateResponse(
                    response = response ?: return@mapNotNull null,
                    ruleKey = ruleKey,
                    revisionNumber = revisionNumber,
                    now = now,
                )
            }
            val fieldsCleared = if (evaluation.hiddenDataPolicy == InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
                clearHiddenFields(request.id, requirement, binding.collectedFieldDefinitionId, confirmedClears, access)
            else false
            val responseToHide = response ?: if (fieldsCleared)
                responseStore.save(InformationRequestResponse().apply {
                    informationRequestId = request.id
                    informationRequestRequirementId = requirement.id
                    requirementRevisionId = revisionRepository.findCurrentForRequest(request.id)
                        .first { it.informationRequestRequirementId == requirement.id }.id
                    occurrencePath = requirement.occurrencePath
                    recordedByPrincipalKind = access.principal.kind
                    recordedByPrincipalId = access.principal.id
                    recordedBySessionRef = access.authorization.sessionRef
                    createdAt = now
                })
            else return@mapNotNull null
            hideResponse(
                response = responseToHide,
                ruleKey = ruleKey,
                policy = evaluation.hiddenDataPolicy,
                confirmedClears = confirmedClears,
                revisionNumber = revisionNumber,
                now = now,
                fieldsCleared = fieldsCleared,
            )
        }
    }

    private fun reactivateResponse(
        response: InformationRequestResponse,
        ruleKey: String,
        revisionNumber: Long,
        now: Timestamp,
    ): InformationRequestResponse?
    {
        if (response.activeInResponse ||
            response.hiddenByConditionRuleKey != ruleKey ||
            response.hiddenDataPolicy == null ||
            response.hiddenAt == null)
        {
            return null
        }
        if (response.hiddenDataPolicy == InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        {
            response.disposition = InformationRequestResponseDisposition.NOT_ANSWERED
            response.narrative = null
            response.fieldValueSetId = null
        }
        response.activeInResponse = true
        response.hiddenByConditionRuleKey = null
        response.hiddenDataPolicy = null
        response.hiddenAt = null
        response.responseRevision = revisionNumber
        response.updatedAt = now
        return responseStore.update(response)
    }

    private fun hideResponse(
        response: InformationRequestResponse,
        ruleKey: String,
        policy: InformationRequestConditionHiddenDataPolicy,
        confirmedClears: Set<UUID>,
        revisionNumber: Long,
        now: Timestamp,
        fieldsCleared: Boolean,
    ): InformationRequestResponse?
    {
        if (policy == InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        {
            if (response.hasActiveResponseData() && response.informationRequestRequirementId !in confirmedClears)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
                    "Hidden response data requires explicit clearing confirmation",
                )
            }
            response.disposition = InformationRequestResponseDisposition.NOT_ANSWERED
            response.narrative = null
            response.fieldValueSetId = null
        }

        if (!response.activeInResponse &&
            response.hiddenByConditionRuleKey == ruleKey &&
            response.hiddenDataPolicy == policy &&
            response.hiddenAt != null)
        {
            if (!fieldsCleared) return null
        }

        response.activeInResponse = false
        response.hiddenByConditionRuleKey = ruleKey
        response.hiddenDataPolicy = policy
        response.hiddenAt = now
        response.responseRevision = revisionNumber
        response.updatedAt = now
        return responseStore.update(response)
    }

    private fun InformationRequestResponse.hasActiveResponseData(): Boolean =
        disposition != InformationRequestResponseDisposition.NOT_ANSWERED ||
            narrative != null ||
            fieldValueSetId != null

    private fun clearHiddenFields(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        fieldDefinitionId: UUID?,
        confirmedClears: Set<UUID>,
        access: RequestAccessContext,
    ): Boolean
    {
        if (fieldDefinitionId == null) return false
        val valueSet = valueSetRef(requirement.occurrencePath)
        val resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId)
        val fieldsAccess = FieldsAccessContext(access.principal, access.authorization)
        val projection = schemaAssignmentService.getAssignment(FieldValueReadCommand(resource, fieldsAccess, valueSet))
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.STATE_INVALID,
                "The collected Field cannot be resolved for clearing")
        val field = InformationRequestActiveResponseProjection.field(projection, fieldDefinitionId).fields.singleOrNull()
            ?: throw ForbiddenException("Access denied to clear the collected Field")
        if (field.isEmpty) return false
        if (requirement.id !in confirmedClears)
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
                "Hidden response data requires explicit clearing confirmation")
        authorize(access, requirement.id)
        schemaAssignmentService.clearValues(FieldValueClearCommand(resource, fieldsAccess,
            setOf(field.fieldContractId), valueSet,
            FieldsPrecondition.ExpectedRevision(requireNotNull(projection.etag))))
        return true
    }

    private fun replayResponseDraftResult(
        command: PatchInformationRequestResponsesCommand,
        result: CommandResultReference,
    ): InformationRequestResponseDraftResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val exchange = lockParentExchangeOf(result.resourceId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(result.resourceId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request receipt target not found",
            )
        val receiptRevision = requireNotNull(result.revision) {
            "Information Request response receipt did not record a response revision"
        }
        requireResponseMutationAllowed(exchange, request)
        requireContinuationEntitlement(exchange, request)
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, command.access)
        val activeOccurrencePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(request.id).associateBy { it.id }
        val replayedRequirements = command.patches.associate { patch ->
            val requirement = requirements[patch.requirementId]
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request Requirement not found",
                )
            if (!InformationRequestOccurrencePath.isActiveOccurrence(requirement.occurrencePath, activeOccurrencePaths))
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                    "This Information Request group occurrence has been removed",
                )
            }
            authorize(command.access, requirement.id)
            requirement.id to requirement
        }
        val responses = activeResponses(request.id, receiptRevision)
        return InformationRequestResponseDraftResult(
            request = request,
            responseETag = requireNotNull(result.etag) {
                "Information Request response receipt did not record a response ETag"
            },
            responses = responses,
            requirementsById = requirements,
            fieldValueProjectionsByRequirementId = command.patches
                .filter {
                    it.fieldValues != null &&
                        responses.any { response -> response.informationRequestRequirementId == it.requirementId }
                }
                .associate { patch ->
                    val requirement = replayedRequirements.getValue(patch.requirementId)
                    patch.requirementId to readFieldValues(request.id, requirement.id, command.access)
                },
        )
    }

    private fun InformationRequestResponseDraftResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            revision = request.responseRevision,
            etag = responseETag,
        )

    private fun fingerprint(command: PatchInformationRequestResponsesCommand): String =
        CommandRequestFingerprint.sha256Hex(
            (listOf(PATCH_RESPONSES_OPERATION, command.requestId.toString()) +
                command.patches.map { patch ->
                    val narrative = when (val value = patch.narrative)
                    {
                        ResponseNarrativePatch.Unchanged -> "narrative:unchanged"
                        ResponseNarrativePatch.Clear -> "narrative:clear"
                        is ResponseNarrativePatch.Set -> "narrative:set:${value.value}"
                    }
                    val fields = patch.fieldValues?.let { fieldPatch ->
                        fieldPatch.entries.joinToString(
                            prefix = "fields:${fieldPreconditionFingerprint(fieldPatch.precondition)}:",
                            separator = ",",
                        ) { entry -> "${entry.fieldContractId}=${entry.value}" }
                    }.orEmpty()
                    "${patch.requirementId}|${patch.disposition?.name.orEmpty()}|$narrative|$fields"
                } +
                command.confirmedHiddenResponseClears.map { "confirmed-clear:$it" }.sorted()).joinToString("|"),
        )

    /**
     * A Requirement of type FIELD collects exactly one Field Definition, so every entry a caller
     * patches against it must resolve to that same Field, never a Field bound to a different
     * Requirement that happens to share the same occurrence.
     */
    private fun requireEntriesBoundToRequirement(requirement: InformationRequestRequirement, entries: List<FieldValueEntry>)
    {
        val collectedFieldDefinitionId = bindingRepository.findById(requirement.sourceTemplateBindingId)?.collectedFieldDefinitionId
        entries.forEach { entry ->
            val fieldDefinitionId = fieldContractRepository.findById(entry.fieldContractId)?.fieldDefinitionId
            if (fieldDefinitionId == null || fieldDefinitionId != collectedFieldDefinitionId)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.FIELD_ENTRY_NOT_BOUND,
                    "Field entry ${entry.fieldContractId} does not belong to this Information Request Requirement",
                )
            }
        }
    }

    /**
     * Two Requirements collecting Fields in the same root or repeated Value Set share that set's
     * revision, so writing them through separate [SchemaAssignmentService.setValues] calls makes
     * the first write advance the revision the second call's precondition still names, refusing a
     * save that never conflicted with anything but itself. Grouping by the addressed Value Set and
     * submitting one merged command per group validates each such precondition exactly once and
     * commits every entry it covers together.
     */
    private fun writeBatchedFieldValues(
        requestId: UUID,
        requirements: Map<UUID, InformationRequestRequirement>,
        access: RequestAccessContext,
        patches: List<InformationRequestResponsePatch>,
    ): Map<UUID, FieldResponsePatchResult>
    {
        val fieldPatches = patches.filter { it.fieldValues != null }
        fieldPatches.forEach { patch ->
            requireEntriesBoundToRequirement(requirements.getValue(patch.requirementId), patch.fieldValues!!.entries)
        }

        val resultsByRequirement = mutableMapOf<UUID, FieldResponsePatchResult>()
        fieldPatches.groupBy { valueSetRef(requirements.getValue(it.requirementId).occurrencePath) }
            .forEach { (valueSet, groupPatches) ->
                val precondition = mergeFieldPreconditions(groupPatches.map { it.fieldValues!!.precondition })
                val projection = schemaAssignmentService.setValues(
                    FieldValueWriteCommand(
                        resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                        access = FieldsAccessContext(access.principal, access.authorization),
                        entries = groupPatches.flatMap { it.fieldValues!!.entries },
                        valueSet = valueSet,
                        precondition = precondition,
                    ),
                )
                val assignment = schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, requestId)
                    ?: throw IllegalStateException("No schema is assigned")
                val valueSetId = when (valueSet)
                {
                    FieldValueSetRef.Root -> fieldValueSetRepository.findRoot(assignment.id)?.id
                    is FieldValueSetRef.Occurrence -> fieldValueSetRepository.findOccurrence(assignment.id, valueSet.occurrencePath)?.id
                } ?: throw IllegalStateException("Field Value Set was not stored")
                val changed = when (precondition)
                {
                    is FieldsPrecondition.ExpectedRevision -> projection.etag !in precondition.etags
                    else -> true
                }
                val result = FieldResponsePatchResult(valueSetId, projection, changed)
                groupPatches.forEach { patch -> resultsByRequirement[patch.requirementId] = result }
            }
        return resultsByRequirement
    }

    private fun mergeFieldPreconditions(preconditions: List<FieldsPrecondition>): FieldsPrecondition
    {
        val distinct = preconditions.distinct()
        if (distinct.size == 1) return distinct.single()
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED,
            "Field value patches addressing the same occurrence must share one precondition",
        )
    }

    private fun readFieldValues(
        requestId: UUID,
        requirementId: UUID,
        access: RequestAccessContext,
    ): SchemaAssignmentDto
    {
        val requirement = requirementRepository.findForRequest(requestId).first { it.id == requirementId }
        val projection = requireNotNull(
            schemaAssignmentService.getAssignment(
                FieldValueReadCommand(
                    resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                    access = FieldsAccessContext(access.principal, access.authorization),
                    valueSet = valueSetRef(requirement.occurrencePath),
                ),
            ),
        ) { "No schema is assigned" }
        return activeFieldProjection(requestId, requirement, projection)
    }

    private fun activeFieldProjection(requestId: UUID, requirement: InformationRequestRequirement,
                                      projection: SchemaAssignmentDto): SchemaAssignmentDto
    {
        val binding = bindingRepository.findById(requirement.sourceTemplateBindingId)
            ?: bindingRepository.findOrdered(requirement.sourceTemplateVersionId).firstOrNull { it.id == requirement.sourceTemplateBindingId }
        val response = responseStore.findAllForRequest(requestId).firstOrNull { it.informationRequestRequirementId == requirement.id }
        val active = InformationRequestActiveResponseProjection.isActive(binding?.conditionalRuleKey,
            requirement.occurrencePath, conditionEvaluationService.evaluate(requestId), response?.activeInResponse != false)
        return InformationRequestActiveResponseProjection.field(projection, binding?.collectedFieldDefinitionId.takeIf { active })
    }

    private fun activeResponses(requestId: UUID, maxResponseRevision: Long? = null): List<InformationRequestResponse>
    {
        val activeOccurrencePaths = occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
            .associateBy { it.id }
        val evaluations = conditionEvaluationService.evaluate(requestId)
        return responseStore.findCurrentForRequest(requestId).filter { response ->
            if (maxResponseRevision != null && response.responseRevision > maxResponseRevision) return@filter false
            val requirement = requirements[response.informationRequestRequirementId] ?: return@filter false
            val binding = bindingRepository.findById(requirement.sourceTemplateBindingId)
                ?: bindingRepository.findOrdered(requirement.sourceTemplateVersionId).firstOrNull { it.id == requirement.sourceTemplateBindingId }
            InformationRequestActiveResponseProjection.isActive(binding?.conditionalRuleKey, requirement.occurrencePath,
                evaluations, response.activeInResponse)
        }
    }

    private fun valueSetRef(occurrencePath: String): FieldValueSetRef =
        if (occurrencePath == ROOT_OCCURRENCE_PATH)
            FieldValueSetRef.Root
        else
            FieldValueSetRef.Occurrence(occurrencePath)

    private fun fieldPreconditionFingerprint(precondition: FieldsPrecondition): String = when (precondition)
    {
        FieldsPrecondition.Unconditioned -> "unconditioned"
        FieldsPrecondition.Absent -> "absent"
        is FieldsPrecondition.ExpectedRevision -> precondition.etags.sorted().joinToString(",", prefix = "expected:")
    }

    private fun historyIdempotencyKey(requestId: UUID, idempotencyKey: String): String =
        "information_request.response|$requestId|$idempotencyKey"

    private companion object
    {
        const val PATCH_RESPONSES_OPERATION = "patch-information-request-responses"
        const val ROOT_OCCURRENCE_PATH = "root"
        val NARRATIVE_REQUIRED_DISPOSITIONS = setOf(
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
            InformationRequestResponseDisposition.WAIVED,
        )
    }

    private data class FieldResponsePatchResult(
        val valueSetId: UUID,
        val projection: SchemaAssignmentDto,
        val changed: Boolean,
    )
}
