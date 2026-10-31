package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValue
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestDiscrepancyView
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDiscrepancyRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDiscrepancyResolutionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementDisclosure
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestImportedValueRecords @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val queryService: InformationRequestQueryService,
    private val valueRepository: InformationRequestImportedValueRepository,
    private val decisionRepository: InformationRequestImportedValueDecisionRepository,
    private val discrepancyRepository: InformationRequestImportedValueDiscrepancyRepository,
    private val resolutionRepository: InformationRequestImportedValueDiscrepancyResolutionRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val disclosure: InformationRequestRequirementDisclosure,
) {
    fun values(requestId: UUID, access: RequestAccessContext): List<InformationRequestImportedValueView> {
        queryService.findById(requestId, access)
        InformationRequestConnectorService.requireRequestingSide(gate, access, requestId)
        val verified = verifiedRequirements(requestId, access)
        return views(
            requestId,
            valueRepository.findForRequest(requestId).filter { verified(it.informationRequestRequirementId) },
            access
        )
    }

    @Suppress("LongParameterList")
    fun runOnce(
        requestId: UUID,
        operation: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        replayAction: Action,
        mutation: () -> UUID,
    ): InformationRequestImportedValueView {
        gate.requireVisibleTarget(access, requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(requestId),
            operation = operation,
            actor = CommandActorRef.principal(access.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
        )
        val valueId = when (
            val decision = commandReceiptService.runOnce(receipt) {
                val id = mutation()
                CommandMutationResult(
                    id,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, id, 1, null)
                )
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> {
                gate.authorizeRequest(access, listOf(replayAction), requestId)
                decision.result.resourceId
            }
        }
        val value = valueOf(requestId, valueId)
        disclosure.requireRecentVerification(access, requestId, value.informationRequestRequirementId)
        return views(requestId, listOf(value), access).single()
    }

    fun requireRecentVerification(access: RequestAccessContext, requestId: UUID, requirementId: UUID) =
        disclosure.requireRecentVerification(access, requestId, requirementId)

    fun requireContent(access: RequestAccessContext, requestId: UUID, requirementId: UUID) =
        disclosure.requireContent(access, requestId, requirementId)

    fun contentReadable(access: RequestAccessContext): (UUID) -> Boolean {
        val decided = mutableMapOf<UUID, Boolean>()
        return { requirementId -> decided.getOrPut(requirementId) { disclosure.permitsContent(access, requirementId) } }
    }

    fun verifiedRequirements(requestId: UUID, access: RequestAccessContext): (UUID) -> Boolean {
        if (access.authorization.mfaSatisfied) return { true }
        val protected = disclosure.protectedRequirementIds(listOf(requestId))
        return { it !in protected }
    }

    fun requirementOf(requestId: UUID, requirementId: UUID): InformationRequestRequirement =
        requirementRepository.findForRequest(requestId).firstOrNull { it.id == requirementId }
            ?.takeIf { it.id !in requirementRepository.inactiveRequirementIds(requestId) }
            ?: throw io.quarkus.security.ForbiddenException("Access denied to this Information Request Requirement")

    fun valueOf(requestId: UUID, valueId: UUID): InformationRequestImportedValue =
        valueRepository.findById(valueId)?.takeIf { it.informationRequestId == requestId }
            ?: throw io.quarkus.security.ForbiddenException("Access denied to this Information Request Requirement")

    @Suppress("LongParameterList")
    fun recordHistory(
        request: InformationRequest,
        actor: InformationRequestAuditActor,
        mutation: InformationRequestMutation,
        scope: String,
        details: Map<String, String>,
        requireAuditCapture: Boolean = false,
    ) {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                auditIdempotencyKey = "information_request.external|$scope",
                details = details,
                requireAuditCapture = requireAuditCapture,
            ),
        )
    }

    fun requireReason(reasonCode: String, message: String) {
        if (reasonCode.isEmpty()) throw InformationRequestCommandRequestException(message)
        if (reasonCode.length > REASON_LENGTH) throw InformationRequestCommandRequestException("A reason is at most $REASON_LENGTH characters")
    }

    private fun views(
        requestId: UUID,
        values: List<InformationRequestImportedValue>,
        access: RequestAccessContext,
    ): List<InformationRequestImportedValueView> {
        val decisions = decisionRepository.findForRequest(requestId).associateBy { it.importedValueId }
        val resolutions = resolutionRepository.findForRequest(requestId).associateBy { it.discrepancyId }
        val discrepancies = discrepancyRepository.findForRequest(requestId).groupBy { it.importedValueId }
        val readable = contentReadable(access)
        return values.map { value ->
            InformationRequestImportedValueView(
                value = value,
                decision = decisions[value.id],
                discrepancies = discrepancies[value.id].orEmpty()
                    .takeIf { readable(value.informationRequestRequirementId) }
                    .orEmpty()
                    .map { InformationRequestDiscrepancyView(it, resolutions[it.id]) },
            )
        }
    }

    private companion object {
        const val REASON_LENGTH = 128
    }
}

internal fun InformationRequestImportedValue.isExpiredAt(now: Instant): Boolean =
    expiresAt?.toInstant()?.isAfter(now) == false
