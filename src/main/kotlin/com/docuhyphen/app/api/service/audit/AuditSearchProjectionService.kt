package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

data class AuditProjectionCursor(
    val occurredAt: Instant,
    val eventId: UUID,
)

data class AuditProjectionEvent(
    val eventId: UUID,
    val category: String,
    val eventTypeKey: String,
    val outcome: String,
    val occurredAt: Instant,
    val recordedAt: Instant,
    val ledgerTime: Instant,
    val streamId: String,
    val streamSequence: Long,
    val actorKind: String,
    val actorId: UUID?,
    val actorRole: String?,
    val actorLabel: String?,
    val ownerType: String,
    val ownerId: UUID?,
    val organizationId: UUID?,
    val organizationLabel: String?,
    val targetType: String?,
    val targetId: String?,
    val targetLabel: String?,
    val reason: String?,
    val payload: Map<String, String>,
    val eventHash: String,
    val prevHash: String?,
)

data class AuditProjectionPage(
    val items: List<AuditProjectionEvent>,
    val nextCursor: AuditProjectionCursor?,
)

class AuditProjectionAccessDeniedException(message: String) : RuntimeException(message)

class AuditProjectionNotFoundException(message: String) : RuntimeException(message)

@ApplicationScoped
class AuditSearchProjectionService @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditEngagementService: AuditEngagementService,
    private val exchangeRetrievalService: ExchangeRetrievalService,
    private val auditRecorder: AuditRecorder,
)
{
    data class AuditAccessActor(
        val principal: PrincipalRef,
        val context: AuthorizationContext,
        val capabilities: Set<Capability>,
    )

    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditSearchProjectionService::class.java)
        private val defaultEngagementQueryRange = Duration.ofDays(30)
        private val payloadSerializer = MapSerializer(String.serializer(), String.serializer())
        private val safePayloadKeys = setOf(
            "reasoncode",
            "documenttitle",
            "workflowname",
            "stepindex",
            "instanceid",
            "stepinstanceid",
            "status",
            "categories",
            "case_reference",
            "resource_type",
            "resource_id",
            "sensitivity_level",
            "schema_versions",
            "segment_count",
            "first_sequence",
            "last_sequence",
            "stream_id",
        )
        private val platformPayloadKeys = setOf(
            "assignmentid",
            "categories",
            "firstsequence",
            "lastsequence",
            "limit",
            "querylength",
            "requestid",
            "resultcount",
            "schemaversions",
            "segmentcount",
            "sensitivitylevel",
            "sourceip",
            "statechanged",
            "status",
            "streamid",
        )
        private val platformDetailCategories = setOf(
            AuditCategory.ADMINISTRATION,
            AuditCategory.ARCHIVE,
            AuditCategory.AUDIT_GOVERNANCE,
            AuditCategory.PLATFORM,
            AuditCategory.SECURITY,
        )
    }

    fun listOrganizationEvents(
        actor: AuditAccessActor,
        organizationId: UUID,
        categories: Set<AuditCategory>,
        cursor: AuditProjectionCursor?,
        limit: Int,
        occurredAfter: Instant? = null,
        occurredBefore: Instant? = null,
    ): AuditProjectionPage = searchEvents(
        actor = actor,
        organizationId = organizationId,
        platformOnly = false,
        categories = categories,
        targetTypes = emptySet(),
        targetIds = emptySet(),
        actorIdFilter = null,
        cursor = cursor,
        limit = limit,
        occurredAfter = occurredAfter,
        occurredBefore = occurredBefore,
        auditTargetType = "ORGANIZATION",
        auditTargetId = organizationId.toString(),
    )

    fun getOrganizationEvent(
        actor: AuditAccessActor,
        organizationId: UUID,
        eventId: UUID,
    ): AuditProjectionEvent
    {
        val event = auditLedgerEventRepository.findByEventIdScoped(eventId = eventId, organizationId = organizationId)
            ?: throw AuditProjectionNotFoundException("Audit event not found")
        return resolveVisibleEvent(
            actor = actor,
            organizationId = organizationId,
            platformOnly = false,
            event = event,
            auditTargetType = "ORGANIZATION",
            auditTargetId = organizationId.toString(),
            recordView = true,
        ) ?: throw AuditProjectionAccessDeniedException("Audit event is outside the caller engagement scope")
    }

    fun listExchangeDocumentEvents(
        actor: AuditAccessActor,
        organizationId: UUID,
        exchangeId: UUID,
        documentId: UUID,
        cursor: AuditProjectionCursor?,
        limit: Int,
    ): AuditProjectionPage
    {
        if (!exchangeRetrievalService.hasDocumentInExchange(exchangeId, documentId))
        {
            recordDeniedAttempt(
                actor = actor,
                organizationId = organizationId,
                targetType = "DOCUMENT",
                targetId = documentId.toString(),
                reason = "This document is not part of the requested Exchange",
            )
            throw AuditProjectionNotFoundException("Document not found in the requested Exchange")
        }

        return searchEvents(
            actor = actor,
            organizationId = organizationId,
            platformOnly = false,
            categories = emptySet(),
            targetTypes = setOf("DOCUMENT", "Document"),
            targetIds = setOf(documentId.toString()),
            actorIdFilter = null,
            cursor = cursor,
            limit = limit,
            occurredAfter = null,
            occurredBefore = null,
            auditTargetType = "DOCUMENT",
            auditTargetId = documentId.toString(),
            auditTargetLabel = exchangeRetrievalService.getDocumentTitleForDisplay(exchangeId, documentId),
        )
    }

    /**
     * Same authorization/query/audit-the-audit pattern as [listExchangeDocumentEvents], but
     * widened from a single document to every document currently in the Exchange plus the
     * Exchange-level target itself. Backs the Exchange "Audit" tab
     * (`GET /exchanges/{exchangeId}/audit-events`) with one ledger query instead of one request
     * per document.
     */
    fun listExchangeEvents(
        actor: AuditAccessActor,
        organizationId: UUID,
        exchangeId: UUID,
        cursor: AuditProjectionCursor?,
        limit: Int,
    ): AuditProjectionPage
    {
        val documentIds = exchangeRetrievalService.getDocumentIdsForExchange(exchangeId)
        val targetIds = (documentIds.map { it.toString() } + exchangeId.toString()).toSet()

        return searchEvents(
            actor = actor,
            organizationId = organizationId,
            platformOnly = false,
            categories = emptySet(),
            targetTypes = setOf("DOCUMENT", "Document", "EXCHANGE", "Exchange"),
            targetIds = targetIds,
            actorIdFilter = null,
            cursor = cursor,
            limit = limit,
            occurredAfter = null,
            occurredBefore = null,
            auditTargetType = "EXCHANGE",
            auditTargetId = exchangeId.toString(),
            auditTargetLabel = exchangeRetrievalService.getExchangeNameForDisplay(exchangeId),
        )
    }

    fun listWorkflowDefinitionEvents(
        actor: AuditAccessActor,
        organizationId: UUID?,
        platformOnly: Boolean,
        definitionId: UUID,
        cursor: AuditProjectionCursor?,
        limit: Int,
    ): AuditProjectionPage = searchEvents(
        actor = actor,
        organizationId = organizationId,
        platformOnly = platformOnly,
        categories = setOf(AuditCategory.WORKFLOW),
        targetTypes = setOf("WORKFLOW_DEFINITION"),
        targetIds = setOf(definitionId.toString()),
        actorIdFilter = null,
        cursor = cursor,
        limit = limit,
        occurredAfter = null,
        occurredBefore = null,
        auditTargetType = "WORKFLOW_DEFINITION",
        auditTargetId = definitionId.toString(),
    )

    fun listApplicationEvents(
        actor: AuditAccessActor,
        applicationId: UUID,
        cursor: AuditProjectionCursor?,
        limit: Int,
    ): AuditProjectionPage = searchEvents(
        actor = actor,
        organizationId = null,
        platformOnly = true,
        categories = emptySet(),
        targetTypes = setOf("APPLICATION"),
        targetIds = setOf(applicationId.toString()),
        actorIdFilter = null,
        cursor = cursor,
        limit = limit,
        occurredAfter = null,
        occurredBefore = null,
        auditTargetType = "APPLICATION",
        auditTargetId = applicationId.toString(),
    )

    fun listPlatformEvents(
        actor: AuditAccessActor,
        categories: Set<AuditCategory>,
        cursor: AuditProjectionCursor?,
        limit: Int,
        occurredAfter: Instant? = null,
        occurredBefore: Instant? = null,
    ): AuditProjectionPage = searchEvents(
        actor = actor,
        organizationId = null,
        platformOnly = true,
        categories = categories,
        targetTypes = emptySet(),
        targetIds = emptySet(),
        actorIdFilter = null,
        cursor = cursor,
        limit = limit,
        occurredAfter = occurredAfter,
        occurredBefore = occurredBefore,
        auditTargetType = "PLATFORM",
        auditTargetId = "platform",
    )

    fun listPersonalSecurityEvents(
        actor: AuditAccessActor,
        cursor: AuditProjectionCursor?,
        limit: Int,
    ): AuditProjectionPage = searchEvents(
        actor = actor,
        organizationId = actor.context.activeOrgId,
        platformOnly = false,
        categories = setOf(AuditCategory.AUTHENTICATION, AuditCategory.SECURITY, AuditCategory.AUTHORIZATION),
        targetTypes = emptySet(),
        targetIds = emptySet(),
        actorIdFilter = actor.principal.id,
        cursor = cursor,
        limit = limit,
        occurredAfter = null,
        occurredBefore = null,
        auditTargetType = "APP_USER",
        auditTargetId = actor.principal.id.toString(),
        requireEngagement = false,
    )

    fun listPersonalOwnerEvents(
        actor: AuditAccessActor,
        ownerUserId: UUID,
        categories: Set<AuditCategory>,
        cursor: AuditProjectionCursor?,
        limit: Int,
        occurredAfter: Instant? = null,
        occurredBefore: Instant? = null,
    ): AuditProjectionPage
    {
        if (actor.principal.id != ownerUserId)
        {
            throw AuditProjectionAccessDeniedException("Audit owner does not match the caller")
        }
        return searchEvents(
            actor = actor,
            organizationId = null,
            platformOnly = false,
            categories = categories,
            targetTypes = emptySet(),
            targetIds = emptySet(),
            actorIdFilter = null,
            cursor = cursor,
            limit = limit,
            occurredAfter = occurredAfter,
            occurredBefore = occurredBefore,
            auditTargetType = "APP_USER",
            auditTargetId = ownerUserId.toString(),
            requireEngagement = false,
            ownerUserId = ownerUserId,
        )
    }

    fun listSecurityIncidentAreaEvents(
        actor: AuditAccessActor,
        cursor: AuditProjectionCursor?,
        limit: Int,
    ): AuditProjectionPage = searchEvents(
        actor = actor,
        organizationId = null,
        platformOnly = true,
        categories = setOf(AuditCategory.SECURITY, AuditCategory.AUTHENTICATION, AuditCategory.AUTHORIZATION),
        targetTypes = emptySet(),
        targetIds = emptySet(),
        actorIdFilter = null,
        cursor = cursor,
        limit = limit,
        occurredAfter = null,
        occurredBefore = null,
        auditTargetType = "SECURITY_INCIDENT",
        auditTargetId = "security-area",
    )

    fun recordDeniedAttempt(
        actor: AuditAccessActor,
        organizationId: UUID?,
        targetType: String?,
        targetId: String?,
        reason: String,
        targetLabel: String? = null,
    )
    {
        recordAuditActivity(
            eventType = AuditEventType.AUDIT_ACCESS_DENIED,
            actor = actor,
            organizationId = organizationId,
            targetType = targetType,
            targetId = targetId,
            targetLabel = targetLabel,
            outcome = AuditOutcome.DENIED,
            payload = mapOf("reason_code" to reason),
            reason = reason,
        )
    }

    private fun searchEvents(
        actor: AuditAccessActor,
        organizationId: UUID?,
        platformOnly: Boolean,
        categories: Set<AuditCategory>,
        targetTypes: Set<String>,
        targetIds: Set<String>,
        actorIdFilter: UUID?,
        cursor: AuditProjectionCursor?,
        limit: Int,
        occurredAfter: Instant?,
        occurredBefore: Instant?,
        auditTargetType: String,
        auditTargetId: String,
        auditTargetLabel: String? = null,
        requireEngagement: Boolean = shouldRequireEngagement(actor, organizationId, platformOnly),
        ownerUserId: UUID? = null,
    ): AuditProjectionPage
    {
        if (requireEngagement)
        {
            require((occurredAfter == null) == (occurredBefore == null)) {
                "Both occurredAfter and occurredBefore are required for an engagement-constrained search"
            }
        }
        if (occurredAfter != null && occurredBefore != null)
        {
            require(occurredBefore.isAfter(occurredAfter)) {
                "occurredBefore must be later than occurredAfter"
            }
        }

        val defaultRangeEnd = if (requireEngagement && occurredAfter == null) Instant.now() else null
        val effectiveOccurredAfter = occurredAfter ?: defaultRangeEnd?.minus(defaultEngagementQueryRange)
        val effectiveOccurredBefore = occurredBefore ?: defaultRangeEnd
        val requestedRange = if (effectiveOccurredAfter != null && effectiveOccurredBefore != null)
        {
            Duration.between(effectiveOccurredAfter, effectiveOccurredBefore)
        }
        else
        {
            null
        }
        val visible = mutableListOf<AuditProjectionEvent>()
        var rawCursor = cursor
        var lastRawEvent: AuditLedgerEvent? = null
        var rawResultsExhausted = false
        while (visible.size < limit && !rawResultsExhausted)
        {
            val events = if (ownerUserId != null)
            {
                auditLedgerEventRepository.searchPersonal(
                    ownerUserId = ownerUserId,
                    categories = categories.map { it.name }.toSet(),
                    targetTypes = targetTypes,
                    targetIds = targetIds,
                    actorId = actorIdFilter,
                    occurredAfter = effectiveOccurredAfter?.let(Timestamp::from),
                    occurredBefore = effectiveOccurredBefore?.let(Timestamp::from),
                    cursorOccurredAt = rawCursor?.occurredAt?.let(Timestamp::from),
                    cursorEventId = rawCursor?.eventId,
                    limit = limit,
                )
            }
            else
            {
                auditLedgerEventRepository.search(
                    organizationId = organizationId,
                    platformOnly = platformOnly,
                    categories = categories.map { it.name }.toSet(),
                    targetTypes = targetTypes,
                    targetIds = targetIds,
                    actorId = actorIdFilter,
                    occurredAfter = effectiveOccurredAfter?.let(Timestamp::from),
                    occurredBefore = effectiveOccurredBefore?.let(Timestamp::from),
                    cursorOccurredAt = rawCursor?.occurredAt?.let(Timestamp::from),
                    cursorEventId = rawCursor?.eventId,
                    limit = limit,
                )
            }
            rawResultsExhausted = events.size < limit

            for (event in events)
            {
                lastRawEvent = event
                resolveVisibleEvent(
                    actor = actor,
                    organizationId = organizationId,
                    platformOnly = platformOnly,
                    event = event,
                    auditTargetType = auditTargetType,
                    auditTargetId = auditTargetId,
                    requestedRange = requestedRange,
                    requireEngagement = requireEngagement,
                    recordView = false,
                )?.let(visible::add)
                if (visible.size == limit) break
            }

            rawCursor = lastRawEvent?.let { AuditProjectionCursor(it.occurredAt.toInstant(), it.eventId) }
        }

        recordAuditActivity(
            eventType = AuditEventType.AUDIT_SEARCH_PERFORMED,
            actor = actor,
            organizationId = organizationId,
            targetType = auditTargetType,
            targetId = auditTargetId,
            targetLabel = auditTargetLabel,
            outcome = AuditOutcome.SUCCESS,
            payload = mapOf(
                "result_count" to visible.size.toString(),
                "categories" to categories.joinToString(",") { it.name },
            ),
            reason = "Searched the audit trail",
            ownerUserId = ownerUserId,
        )

        val nextCursor = lastRawEvent?.let {
            AuditProjectionCursor(
                occurredAt = it.occurredAt.toInstant(),
                eventId = it.eventId,
            )
        }

        return AuditProjectionPage(
            items = visible,
            nextCursor = if (visible.size == limit || !rawResultsExhausted) nextCursor else null,
        )
    }

    private fun resolveVisibleEvent(
        actor: AuditAccessActor,
        organizationId: UUID?,
        platformOnly: Boolean,
        event: AuditLedgerEvent,
        auditTargetType: String,
        auditTargetId: String,
        requestedRange: Duration? = null,
        requireEngagement: Boolean = shouldRequireEngagement(actor, organizationId, platformOnly),
        recordView: Boolean,
    ): AuditProjectionEvent?
    {
        val category = runCatching { AuditCategory.valueOf(event.category) }.getOrNull() ?: return null
        val engagement = if (requireEngagement)
        {
            auditEngagementService.resolveAccess(
                principalUserId = actor.principal.id,
                organizationId = organizationId,
                resourceType = event.targetType,
                resourceId = event.targetId,
                category = category,
                requireSensitive = false,
                recentStepUpSatisfied = actor.context.mfaSatisfied,
                requestedRange = requestedRange,
            )
        }
        else
        {
            null
        }

        if (requireEngagement && engagement == null)
        {
            if (recordView)
            {
                recordDeniedAttempt(
                    actor = actor,
                    organizationId = organizationId,
                    targetType = event.targetType ?: auditTargetType,
                    targetId = event.targetId ?: auditTargetId,
                    reason = "No active audit engagement covers this event",
                )
            }
            return null
        }

        val canViewSensitive = canViewSensitive(actor, engagement, platformOnly)
        val canViewPlatformDetails = !platformOnly || category in platformDetailCategories
        val projection = AuditProjectionEvent(
            eventId = event.eventId,
            category = event.category,
            eventTypeKey = event.eventTypeKey,
            outcome = event.outcome,
            occurredAt = event.occurredAt.toInstant(),
            recordedAt = event.recordedAt.toInstant(),
            ledgerTime = event.ledgerTime.toInstant(),
            streamId = event.streamId,
            streamSequence = event.streamSequence,
            actorKind = event.actorKind,
            actorId = event.actorId.takeIf { canViewSensitive },
            actorRole = event.actorRole.takeIf { canViewSensitive },
            actorLabel = event.actorLabel.takeIf { canViewSensitive },
            ownerType = event.ownerType,
            ownerId = event.ownerId,
            organizationId = event.organizationId,
            organizationLabel = event.organizationLabel,
            targetType = event.targetType,
            targetId = event.targetId,
            targetLabel = event.targetLabel.takeIf { canViewSensitive && canViewPlatformDetails },
            reason = event.reason.takeIf { canViewSensitive && canViewPlatformDetails },
            payload = projectPayload(
                payload = parsePayload(event.payloadJson),
                canViewSensitive = canViewSensitive,
                platformOnly = platformOnly,
            ),
            eventHash = event.eventHash,
            prevHash = event.prevHash,
        )

        if (recordView)
        {
            recordAuditActivity(
                eventType = AuditEventType.AUDIT_EVENT_VIEWED,
                actor = actor,
                organizationId = organizationId,
                targetType = projection.targetType ?: auditTargetType,
                targetId = projection.targetId ?: projection.eventId.toString(),
                targetLabel = projection.targetLabel,
                outcome = AuditOutcome.SUCCESS,
                payload = mapOf("event_id" to projection.eventId.toString()),
                reason = "Viewed audit event details",
            )
        }

        return projection
    }

    private fun shouldRequireEngagement(
        actor: AuditAccessActor,
        organizationId: UUID?,
        platformOnly: Boolean,
    ): Boolean = requiresEngagementAccess(actor.capabilities, organizationId, platformOnly)

    private fun canViewSensitive(
        actor: AuditAccessActor,
        engagement: AuditEngagementService.EngagementAccess?,
        platformOnly: Boolean,
    ): Boolean
    {
        if (platformOnly && Capability.APP_ADMIN in actor.capabilities)
        {
            return true
        }
        if (Capability.ORG_AUDIT_VIEW_SENSITIVE in actor.capabilities)
        {
            return true
        }
        return engagement?.sensitivityLevel == AuditEngagementSensitivity.SENSITIVE
    }

    private fun parsePayload(payloadJson: String): Map<String, String> =
        runCatching { Json.decodeFromString(payloadSerializer, payloadJson) }.getOrDefault(emptyMap())

    private fun redactPayload(payload: Map<String, String>, canViewSensitive: Boolean): Map<String, String>
    {
        if (canViewSensitive)
        {
            return payload
        }

        return payload.filterKeys { key -> normalizeKey(key) in safePayloadKeys }
    }

    private fun projectPayload(
        payload: Map<String, String>,
        canViewSensitive: Boolean,
        platformOnly: Boolean,
    ): Map<String, String>
    {
        if (platformOnly)
        {
            return payload.filterKeys { key -> normalizeKey(key) in platformPayloadKeys }
        }
        return redactPayload(payload, canViewSensitive)
    }

    private fun normalizeKey(key: String): String = key.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun recordAuditActivity(
        eventType: AuditEventType,
        actor: AuditAccessActor,
        organizationId: UUID?,
        targetType: String?,
        targetId: String?,
        targetLabel: String? = null,
        outcome: AuditOutcome,
        payload: Map<String, String>,
        reason: String,
        ownerUserId: UUID? = null,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = outcome,
                    actorId = actor.principal.id,
                    actorKind = AuditActorKind.HUMAN,
                    actorRole = "AUDIT_READER",
                    owner = when
                    {
                        ownerUserId != null -> AuditOwnerScope.Personal(ownerUserId)
                        organizationId != null -> AuditOwnerScope.Organization(organizationId)
                        else -> AuditOwnerScope.Platform
                    },
                    targetType = targetType,
                    targetId = targetId,
                    targetLabel = targetLabel,
                    reason = reason,
                    payload = payload,
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditSearchProjectionService: AuditRecorder rejected {}: {}", eventType, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditSearchProjectionService: AuditRecorder capture failed for {}: {}", eventType, e.message, e)
        }
    }
}
