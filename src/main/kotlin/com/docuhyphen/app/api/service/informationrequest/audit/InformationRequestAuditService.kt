package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.model.audit.AuditTargetQuery
import com.docuhyphen.app.api.model.audit.AuditTargetRecordPage
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditTargetHistoryService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRelatedRequestVisibility
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestAuditService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val history: AuditTargetHistoryService,
    private val transitionRepository: InformationRequestTransitionRepository,
)
{
    fun events(
        requestId: UUID,
        access: RequestAccessContext,
        search: InformationRequestAuditSearch
    ): InformationRequestAuditPage
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS), requestId)
        return pageOf(
            history.recordsForTarget(TARGET, requestId.toString(), queryOf(search, newestFirst = true)),
            search,
            InformationRequestRelatedRequestVisibility.of(gate, access),
        )
    }

    fun search(search: InformationRequestAuditSearch): InformationRequestAuditPage
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)
        val scope = when (owner.ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> AuditOwnerScope.Organization(owner.ownerId)
            InformationRequestOwnerType.USER -> AuditOwnerScope.Personal(owner.ownerId)
        }
        val targetTypes = if (search.requestId != null) setOf(TARGET) else InformationRequestAuditTargets.OWNER_SEARCH
        return pageOf(
            history.recordsForOwner(scope, targetTypes, queryOf(search, newestFirst = true)),
            search,
            EVERY_OWNER_REQUEST_SHOWN,
        )
    }

    fun reconciliation(requestId: UUID, access: RequestAccessContext): InformationRequestAuditReconciliation
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS), requestId)
        val audited = transitionRepository.findForRequest(requestId).mapNotNull { transition ->
            InformationRequestTransitionHistoryService.auditEventTypeFor(transition.mutation)
                ?.let { transition to it.key }
        }
        val transitionKeys = InformationRequestMutation.entries
            .mapNotNull { InformationRequestTransitionHistoryService.auditEventTypeFor(it)?.key }
            .toSet()
        val records = history.recordsForTarget(
            TARGET,
            requestId.toString(),
            AuditTargetQuery(eventTypeKeys = transitionKeys, limit = Int.MAX_VALUE),
        ).records
        val recorded = records.groupBy { it.businessTransactionId }
        val missing = audited
            .filter { (transition, key) ->
                recorded[transition.id.toString()].orEmpty().none { it.eventTypeKey == key }
            }
            .map { (transition, key) ->
                InformationRequestReconciliationGap(
                    transition.id,
                    transition.sequenceNumber,
                    transition.mutation.name,
                    key
                )
            }
        val expected = audited.associate { (transition, key) -> transition.id.toString() to key }
        val unmatched = records
            .filter { record -> expected[record.businessTransactionId] != record.eventTypeKey }
            .map { InformationRequestReconciliationStray(it.eventId, it.eventTypeKey, it.businessTransactionId) }
        return InformationRequestAuditReconciliation(
            requestId = requestId,
            auditedTransitionCount = audited.size,
            matchedTransitionCount = audited.size - missing.size,
            missing = missing,
            unmatched = unmatched,
            unsealedEventCount = records.count { !it.sealed },
        )
    }

    private fun pageOf(
        page: AuditTargetRecordPage,
        search: InformationRequestAuditSearch,
        relatedRequestShown: (UUID) -> Boolean,
    ) =
        InformationRequestAuditPage(
            events = page.records.map { record ->
                val projection = InformationRequestAuditPayloadPolicy.projectionOf(record.payload, relatedRequestShown)
                InformationRequestAuditEvent(
                    record = record,
                    eventClass = InformationRequestAuditPayloadPolicy.eventClassOf(record.eventTypeKey),
                    payload = projection.payload,
                    withheldKeyCount = projection.withheldKeyCount,
                )
            },
            total = page.total,
            limit = search.limit,
            offset = search.offset,
        )

    private fun queryOf(search: InformationRequestAuditSearch, newestFirst: Boolean) = AuditTargetQuery(
        eventTypePrefix = search.eventClass?.let(::prefixOf),
        eventTypeKeys = setOfNotNull(search.eventTypeKey),
        targetIds = setOfNotNull(search.requestId?.toString()),
        actorId = search.actorId,
        occurredAfter = search.occurredAfter,
        occurredBefore = search.occurredBefore,
        limit = search.limit,
        offset = search.offset,
        newestFirst = newestFirst,
    )

    private fun prefixOf(eventClass: String): String =
        if (eventClass.startsWith(RECORD_CLASS_PREFIX)) "record.${eventClass.removePrefix(RECORD_CLASS_PREFIX)}."
        else "information_request.$eventClass."

    private companion object
    {
        const val TARGET = InformationRequestAuditTargets.REQUEST
        const val RECORD_CLASS_PREFIX = "record_"
        val EVERY_OWNER_REQUEST_SHOWN: (UUID) -> Boolean = { true }
    }
}
