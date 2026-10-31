package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.response.ConditionalResponseState
import com.docuhyphen.app.api.model.informationrequest.response.HiddenResponseClearTarget
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestConditionEffectDescriptor
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestHiddenResponseEnforcement
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestHiddenResponsePolicyEnforcer @Inject constructor(
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val responseStore: InformationRequestResponseStore,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val authorizationService: AuthorizationService,
    private val archiver: InformationRequestHiddenResponseArchiver,
    private val fieldsReader: FieldsReadFacade,
    private val hiddenFiles: InformationRequestHiddenResponseFiles,
    private val evidenceCollection: InformationRequestEvidenceCollectionService,
    private val submissionLocks: InformationRequestSubmissionLockService,
    private val conditionVisibility: InformationRequestConditionVisibility,
) {
    fun requireTargetsShown(request: InformationRequest, requirements: Collection<InformationRequestRequirement>) {
        conditionVisibility.requireShown(request, requirements)
    }

    fun enforce(
        request: InformationRequest,
        requirements: Map<UUID, InformationRequestRequirement>,
        confirmedClears: Set<UUID>,
        access: RequestAccessContext,
        revisionNumber: Long,
        now: Timestamp,
        patchedRequirementIds: Set<UUID> = emptySet(),
    ): InformationRequestHiddenResponseEnforcement {
        val removed = mutableSetOf<UUID>()
        val effects = mutableListOf<InformationRequestConditionEffectDescriptor>()
        repeat(2 * requirements.size + 2) {
            val round = enforceRound(
                request, requirements, confirmedClears, access, revisionNumber, now, removed, patchedRequirementIds,
            )
            if (round.isEmpty()) return InformationRequestHiddenResponseEnforcement(
                effects.map { it.response },
                effects
            )
            effects += round
            removed += round.filter { it.effect != InformationRequestConditionEffectKind.REACTIVATED }
                .map { it.requirement.id }
        }
        return InformationRequestHiddenResponseEnforcement(changed = effects.map { it.response }, effects = effects)
    }

    fun reconcileAmendedConditions(
        request: InformationRequest,
        requirements: Collection<InformationRequestRequirement>,
        revisionNumber: Long,
        now: Timestamp,
    ): List<InformationRequestResponse> {
        val hidden = responseStore.findAllForRequest(request.id)
            .filter { !it.activeInResponse && it.hiddenByConditionRuleKey != null }
        if (hidden.isEmpty()) return emptyList()
        val requirementsById = requirements.associateBy { it.id }
        val bindingsById = bindingRepository.findOrdered(request.templateVersionId).associateBy { it.id }
        val evaluations =
            conditionEvaluationService.evaluate(request.id).associateBy { it.ruleKey to it.occurrencePath }
        return hidden.mapNotNull { response ->
            val requirement = requirementsById[response.informationRequestRequirementId] ?: return@mapNotNull null
            val binding = bindingsById[requirement.sourceTemplateBindingId] ?: return@mapNotNull null
            val stored = requireNotNull(response.hiddenByConditionRuleKey)
            val ruleKey = InformationRequestGoverningCondition.ruleKeyOf(binding)
                ?: return@mapNotNull reactivateResponse(response, stored, revisionNumber, now, keepData = false)
            val evaluation = evaluations[ruleKey to requirement.occurrencePath]
                ?: evaluations[ruleKey to InformationRequestOccurrencePath.ROOT]
            if (evaluation?.state == InformationRequestConditionEvaluationState.TRUE) {
                reactivateResponse(response, stored, revisionNumber, now, keepData = false)
            } else if (ruleKey == stored) {
                null
            } else {
                response.hiddenByConditionRuleKey = ruleKey
                response.responseRevision = revisionNumber
                response.updatedAt = now
                responseStore.update(response)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun enforceRound(
        request: InformationRequest,
        requirements: Map<UUID, InformationRequestRequirement>,
        confirmedClears: Set<UUID>,
        access: RequestAccessContext,
        revisionNumber: Long,
        now: Timestamp,
        removedRequirementIds: Set<UUID>,
        patchedRequirementIds: Set<UUID>,
    ): List<InformationRequestConditionEffectDescriptor> {
        val evaluations = conditionEvaluationService.evaluate(request.id)
            .associateBy { it.ruleKey to it.occurrencePath }
        if (evaluations.isEmpty()) return emptyList()

        val bindingsById = bindingRepository.findOrdered(request.templateVersionId).associateBy { it.id }
        val responsesByRequirement = responseStore.findAllForRequest(request.id)
            .associateBy { it.informationRequestRequirementId }
        val states = requirements.values.mapNotNull { requirement ->
            val binding = bindingsById[requirement.sourceTemplateBindingId] ?: return@mapNotNull null
            val ruleKey = InformationRequestGoverningCondition.ruleKeyOf(binding) ?: return@mapNotNull null
            val evaluation = evaluations[ruleKey to requirement.occurrencePath]
                ?: evaluations[ruleKey to InformationRequestOccurrencePath.ROOT]
                ?: return@mapNotNull null
            ConditionalResponseState(
                requirement = requirement,
                ruleKey = ruleKey,
                collectedFieldDefinitionId = binding.collectedFieldDefinitionId,
                evaluation = evaluation,
                response = responsesByRequirement[requirement.id],
            )
        }
        val apply = { state: ConditionalResponseState ->
            applyConditionalState(
                request,
                state,
                access,
                revisionNumber,
                now,
                state.requirement.id in patchedRequirementIds
            )
        }
        val reactivating = states.filter(::awaitsReactivation)
        if (reactivating.isNotEmpty()) return reactivating.mapNotNull(apply)
        val hiding = states.filter {
            it.evaluation.state != InformationRequestConditionEvaluationState.TRUE && it.requirement.id !in removedRequirementIds
        }
        val governed = withRetention(request, hiding, access)
        requirePermittedRemovals(request, governed, confirmedClears, access)
        return governed.mapNotNull(apply)
    }

    private fun withRetention(
        request: InformationRequest,
        states: List<ConditionalResponseState>,
        access: RequestAccessContext,
    ): List<ConditionalResponseState> {
        val removing = states.filter(::removesByPolicy)
        if (removing.isEmpty()) return states
        val locked = submissionLocks.lockedRequirementIds(request.id, removing.map { it.requirement.id }) +
                removing.filter { state ->
                    hiddenFiles.activeChangeableBy(request, state.requirement.id, access).any { file ->
                        !submissionLocks.evidenceArtifactOpen(request.id, state.requirement.id, file.id)
                    }
                }.map { it.requirement.id }
        val retained = removing
            .filter { it.requirement.id in locked || !mayRespond(access, it.requirement.id) }
            .map { it.requirement.id }
            .toSet()
        return states.map { state ->
            if (state.requirement.id in retained)
                state.copy(evaluation = state.evaluation.copy(hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY))
            else state
        }
    }

    private fun awaitsReactivation(state: ConditionalResponseState): Boolean =
        state.evaluation.state == InformationRequestConditionEvaluationState.TRUE &&
                state.response?.let {
                    !it.activeInResponse && it.hiddenByConditionRuleKey == state.ruleKey &&
                            it.hiddenDataPolicy != null && it.hiddenAt != null
                } == true

    private fun removesByPolicy(state: ConditionalResponseState): Boolean =
        state.evaluation.state != InformationRequestConditionEvaluationState.TRUE &&
                state.evaluation.hiddenDataPolicy != InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY

    private fun requirePermittedRemovals(
        request: InformationRequest,
        states: List<ConditionalResponseState>,
        confirmedClears: Set<UUID>,
        access: RequestAccessContext,
    ) {
        val confirmedHidden = states
            .filter { it.requirement.id in confirmedClears && it.evaluation.state != InformationRequestConditionEvaluationState.TRUE }
            .map { it.requirement.id }
        confirmedHidden.forEach { requireClearPermitted(access, it) }
        submissionLocks.requireUnlocked(request.id, confirmedHidden)
        val removing = states.filter { removesData(request, it, access) }
        val unconfirmed = removing.mapNotNull { state ->
            state.requirement.takeIf {
                state.evaluation.hiddenDataPolicy == InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION &&
                        it.id !in confirmedClears
            }
        }
        if (unconfirmed.isNotEmpty()) {
            throw InformationRequestHiddenResponseClearRequiredException(
                unconfirmed.map { HiddenResponseClearTarget(it.id, it.sourceTemplateBindingId, it.occurrencePath) },
            )
        }
    }

    private fun removesData(
        request: InformationRequest,
        state: ConditionalResponseState,
        access: RequestAccessContext,
    ): Boolean =
        removesByPolicy(state) &&
                (state.response?.hasActiveResponseData() == true ||
                        collectedFieldValue(request.id, state, access) != null ||
                        hiddenFiles.activeChangeableBy(request, state.requirement.id, access).isNotEmpty())

    private fun applyConditionalState(
        request: InformationRequest,
        state: ConditionalResponseState,
        access: RequestAccessContext,
        revisionNumber: Long,
        now: Timestamp,
        patchedInThisChange: Boolean,
    ): InformationRequestConditionEffectDescriptor? {
        if (state.evaluation.state == InformationRequestConditionEvaluationState.TRUE) {
            val response = state.response ?: return null
            val hiddenUnder = response.hiddenDataPolicy ?: return null
            val reactivated =
                reactivateResponse(response, state.ruleKey, revisionNumber, now, patchedInThisChange) ?: return null
            return effectOf(state, reactivated, hiddenUnder, InformationRequestConditionEffectKind.REACTIVATED)
        }
        return when (state.evaluation.hiddenDataPolicy) {
            InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY ->
                hideResponse(state.response ?: return null, state, revisionNumber, now, dataRemoved = false)
                    ?.let {
                        effectOf(
                            state,
                            it,
                            state.evaluation.hiddenDataPolicy,
                            InformationRequestConditionEffectKind.HIDDEN
                        )
                    }

            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION ->
                clearAndHide(request, state, access, revisionNumber, now)

            InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE ->
                archiveAndHide(request, state, access, revisionNumber, now)
        }
    }

    private fun clearAndHide(
        request: InformationRequest,
        state: ConditionalResponseState,
        access: RequestAccessContext,
        revisionNumber: Long,
        now: Timestamp,
    ): InformationRequestConditionEffectDescriptor? {
        val hadData = state.response?.hasActiveResponseData() == true
        val fieldsCleared = collectedFieldValue(request.id, state, access)
            ?.let { (projection, field) ->
                clearField(
                    request.id,
                    state.requirement,
                    projection,
                    field,
                    access
                )
            } != null
        val filesWithdrawn = evidenceCollection.withdrawForCondition(
            request,
            state.requirement,
            hiddenFiles.activeChangeableBy(request, state.requirement.id, access),
            HIDDEN_REASON,
            access,
            now,
        )
        val response = state.response
            ?: if (fieldsCleared || filesWithdrawn) saveClearedResponse(
                request.id,
                state.requirement,
                access,
                now
            )
            else return null
        val removed = hadData || fieldsCleared || filesWithdrawn
        val hidden = hideResponse(response, state, revisionNumber, now, dataRemoved = removed) ?: return null
        return effectOf(
            state,
            hidden,
            state.evaluation.hiddenDataPolicy,
            if (removed) InformationRequestConditionEffectKind.CLEARED else InformationRequestConditionEffectKind.HIDDEN,
        )
    }

    private fun archiveAndHide(
        request: InformationRequest,
        state: ConditionalResponseState,
        access: RequestAccessContext,
        revisionNumber: Long,
        now: Timestamp,
    ): InformationRequestConditionEffectDescriptor? {
        val collected = collectedFieldValue(request.id, state, access)
        val hadData = state.response?.hasActiveResponseData() == true
        val files = hiddenFiles.activeChangeableBy(request, state.requirement.id, access)
        if (collected == null && !hadData && files.isEmpty()) {
            val response = state.response ?: return null
            return hideResponse(response, state, revisionNumber, now, dataRemoved = false)
                ?.let {
                    effectOf(
                        state,
                        it,
                        state.evaluation.hiddenDataPolicy,
                        InformationRequestConditionEffectKind.HIDDEN
                    )
                }
        }
        val response = state.response ?: saveClearedResponse(request.id, state.requirement, access, now)
        val archive = archiver.archive(
            requirement = state.requirement,
            response = response,
            ruleKey = state.ruleKey,
            collectedField = collected?.second,
            fieldValueSetETag = collected?.first?.etag,
            evidence = hiddenFiles.currentVersionsOf(files),
            access = access,
            now = now,
        )
        evidenceCollection.withdrawForCondition(request, state.requirement, files, ARCHIVED_REASON, access, now)
        collected?.let { (projection, field) -> clearField(request.id, state.requirement, projection, field, access) }
        val hidden = hideResponse(response, state, revisionNumber, now, dataRemoved = true) ?: return null
        return if (archive != null)
            effectOf(
                state,
                hidden,
                state.evaluation.hiddenDataPolicy,
                InformationRequestConditionEffectKind.ARCHIVED,
                archive.id
            )
        else
            effectOf(state, hidden, state.evaluation.hiddenDataPolicy, InformationRequestConditionEffectKind.HIDDEN)
    }

    private fun effectOf(
        state: ConditionalResponseState,
        response: InformationRequestResponse,
        policy: InformationRequestConditionHiddenDataPolicy,
        effect: InformationRequestConditionEffectKind,
        archiveId: UUID? = null,
    ) = InformationRequestConditionEffectDescriptor(
        requirement = state.requirement,
        response = response,
        ruleKey = state.ruleKey,
        expressionVersion = state.evaluation.expressionVersion,
        evaluationState = state.evaluation.state,
        hiddenDataPolicy = policy,
        effect = effect,
        archiveId = archiveId,
    )

    private fun saveClearedResponse(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        access: RequestAccessContext,
        now: Timestamp,
    ): InformationRequestResponse =
        responseStore.save(InformationRequestResponse().apply {
            informationRequestId = requestId
            informationRequestRequirementId = requirement.id
            requirementRevisionId = revisionRepository.findCurrentForRequest(requestId)
                .first { it.informationRequestRequirementId == requirement.id }.id
            occurrencePath = requirement.occurrencePath
            recordedByPrincipalKind = access.principal.kind
            recordedByPrincipalId = access.principal.id
            recordedBySessionRef = access.authorization.sessionRef
            createdAt = now
        })

    private fun reactivateResponse(
        response: InformationRequestResponse,
        ruleKey: String,
        revisionNumber: Long,
        now: Timestamp,
        keepData: Boolean,
    ): InformationRequestResponse? {
        if (response.activeInResponse ||
            response.hiddenByConditionRuleKey != ruleKey ||
            response.hiddenDataPolicy == null ||
            response.hiddenAt == null
        ) {
            return null
        }
        if (!keepData && response.hiddenDataPolicy != InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY) {
            response.clearAnswer()
            response.responseRevision = revisionNumber
        }
        response.activeInResponse = true
        response.hiddenByConditionRuleKey = null
        response.hiddenDataPolicy = null
        response.hiddenAt = null
        response.updatedAt = now
        return responseStore.update(response)
    }

    private fun hideResponse(
        response: InformationRequestResponse,
        state: ConditionalResponseState,
        revisionNumber: Long,
        now: Timestamp,
        dataRemoved: Boolean,
    ): InformationRequestResponse? {
        val policy = state.evaluation.hiddenDataPolicy
        if (!dataRemoved &&
            !response.activeInResponse &&
            response.hiddenByConditionRuleKey == state.ruleKey &&
            response.hiddenDataPolicy == policy &&
            response.hiddenAt != null
        ) {
            return null
        }
        if (dataRemoved) {
            response.clearAnswer()
            response.responseRevision = revisionNumber
        }
        response.activeInResponse = false
        response.hiddenByConditionRuleKey = state.ruleKey
        response.hiddenDataPolicy = policy
        response.hiddenAt = now
        response.updatedAt = now
        return responseStore.update(response)
    }

    private fun InformationRequestResponse.clearAnswer() {
        disposition = InformationRequestResponseDisposition.NOT_ANSWERED
        narrative = null
        fieldValueSetId = null
    }

    private fun InformationRequestResponse.hasActiveResponseData(): Boolean =
        disposition != InformationRequestResponseDisposition.NOT_ANSWERED ||
                narrative != null ||
                fieldValueSetId != null

    private fun collectedFieldValue(
        requestId: UUID,
        state: ConditionalResponseState,
        access: RequestAccessContext,
    ): Pair<SchemaAssignmentDto, FieldValueDto>? {
        val fieldDefinitionId = state.collectedFieldDefinitionId ?: return null
        val projection = readCollectedFields(requestId, state.requirement, access)
        val field =
            InformationRequestActiveResponseProjection.field(projection, fieldDefinitionId).fields.singleOrNull()
                ?: return if (holdsCollectedValue(
                        requestId,
                        state.requirement,
                        fieldDefinitionId
                    )
                ) throw clearNotPermitted()
                else null
        return if (field.isEmpty) null else projection to field
    }

    private fun holdsCollectedValue(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        fieldDefinitionId: UUID
    ): Boolean {
        val assignment = fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId))
            ?: return false
        val valueSet = fieldsReader.valueSetOf(assignment.id, valueSetRef(requirement.occurrencePath)) ?: return false
        val values = fieldsReader.valuesIn(valueSet.id)
        if (values.isEmpty()) return false
        val definitions = fieldsReader.contracts(values.map { it.fieldContractId }.distinct())
            .associate { it.id to it.fieldDefinitionId }
        return values.any { value ->
            definitions[value.fieldContractId] == fieldDefinitionId &&
                    !CanonicalValueCodec.isEmpty(value, fieldsReader.selectionCodesOf(value.id))
        }
    }

    private fun clearField(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        projection: SchemaAssignmentDto,
        field: FieldValueDto,
        access: RequestAccessContext,
    ): SchemaAssignmentDto =
        schemaAssignmentService.clearValues(
            FieldValueClearCommand(
                FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                FieldsAccessContext(access.principal, access.authorization),
                setOf(field.fieldContractId),
                valueSetRef(requirement.occurrencePath),
                FieldsPrecondition.ExpectedRevision(requireNotNull(projection.etag)),
            )
        )

    private fun readCollectedFields(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        access: RequestAccessContext,
    ): SchemaAssignmentDto =
        schemaAssignmentService.getAssignment(
            FieldValueReadCommand(
                FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                FieldsAccessContext(access.principal, access.authorization),
                valueSetRef(requirement.occurrencePath),
            )
        ) ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.STATE_INVALID,
            "The collected Field cannot be resolved for clearing"
        )

    private fun requireClearPermitted(access: RequestAccessContext, requirementId: UUID) {
        if (!mayRespond(access, requirementId)) throw clearNotPermitted()
    }

    private fun mayRespond(access: RequestAccessContext, requirementId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) !is Decision.Deny

    private fun clearNotPermitted() = InformationRequestLifecycleException(
        InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED,
        "This change would clear an answer you cannot clear",
    )

    private fun valueSetRef(occurrencePath: String): FieldValueSetRef =
        if (InformationRequestOccurrencePath.isRoot(occurrencePath))
            FieldValueSetRef.Root
        else
            FieldValueSetRef.Occurrence(occurrencePath)

    private companion object {
        const val HIDDEN_REASON = "hidden-by-condition"
        const val ARCHIVED_REASON = "archived-by-condition"
    }
}
