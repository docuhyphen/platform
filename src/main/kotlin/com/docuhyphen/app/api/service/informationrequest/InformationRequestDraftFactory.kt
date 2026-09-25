package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestDraftFactory @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val materializer: InformationRequestTemplateMaterializer,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    fun createAlongside(
        exchange: Exchange,
        sibling: InformationRequest,
        templateVersionId: UUID,
        access: RequestAccessContext,
        idempotencyKey: String,
    ): InformationRequest
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(status = exchange.status, deleted = exchange.isDeleted, lockedForUpdate = true),
            null,
            InformationRequestMutation.CREATE_DRAFT,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(decision.reasonCode, "Information Request draft creation is not allowed")
        }
        entitlementGuard.requireRequestMutation(exchange)

        val now = Timestamp.from(clock.instant())
        val request = requestRepository.save(
            InformationRequest().apply {
                exchangeId = sibling.exchangeId
                this.templateVersionId = templateVersionId
                ownerType = sibling.ownerType
                ownerOrganizationId = sibling.ownerOrganizationId
                ownerUserId = sibling.ownerUserId
                state = InformationRequestState.DRAFT
                gatesExchangeClosure = sibling.gatesExchangeClosure
                createdByAppUserId = access.principal.id.takeIf { access.principal.kind == PrincipalKind.USER }
                createdAt = now
                updatedAt = now
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = null,
                toState = InformationRequestState.DRAFT,
                mutation = InformationRequestMutation.CREATE_DRAFT,
                actor = access.principal,
                idempotencyKey = "information_request.draft.create|follow-up|${request.id}|$idempotencyKey",
            ),
        )
        materializer.materialize(request, FieldsAccessContext(access.principal, access.authorization))
        return request
    }
}
