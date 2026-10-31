package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeDefaults
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeRecipient
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestRenderedNotice
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.*
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.informationRequestAuditOwner
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyContactResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@ApplicationScoped
class InformationRequestNoticeDispatcher @Inject constructor(
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val claimRepository: InformationRequestNoticeClaimRepository,
    private val noticeRepository: InformationRequestOutboundNoticeRepository,
    private val allocationRepository: InformationRequestNoticeSequenceAllocationRepository,
    private val attemptRepository: InformationRequestNoticeDeliveryAttemptRepository,
    private val requestRepository: InformationRequestRepository,
    private val exchangeRetrieval: ExchangeRetrievalService,
    private val partyRepository: InformationRequestPartyRepository,
    private val clockRepository: InformationRequestClockRepository,
    private val clockEventRepository: InformationRequestClockEventRepository,
    private val policies: InformationRequestClockPolicyService,
    private val contacts: InformationRequestPartyContactResolver,
    private val renderer: InformationRequestNoticeRenderer,
    private val sender: InformationRequestNoticeSender,
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val exchangeLookup: ExchangeLookupService,
    private val continuationGuard: InformationRequestContinuationGuard,
    private val renderFailures: InformationRequestNoticeRenderFailureRepository,
)
{
    private val worker = "notice-worker:${UUID.randomUUID()}"

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun recordRenderFailure(intentId: UUID, now: Instant) {
        val intent = intentRepository.findById(intentId) ?: return
        if (claimRepository.findById(intentId) != null) return
        renderFailures.record(intent.id, intent.informationRequestId, RENDER_FAILED, Timestamp.from(now))
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun claimAndRender(intentId: UUID, now: Instant): InformationRequestOutboundNotice?
    {
        val intent = intentRepository.findById(intentId) ?: return null
        val request = requestRepository.findRequestByIdForShare(intent.informationRequestId) ?: return null
        if (!claimRepository.claim(intent.id, intent.informationRequestId, worker, Timestamp.from(now))) return null
        val party = requireNotNull(partyRepository.findById(intent.partyId))
        skipReasonOf(request, party, intent)?.let { reason -> return skipped(intent, request, party, reason, now) }
        val heldByGroup = party.principalKind == PrincipalKind.PRINCIPAL_GROUP
        val recipients = contacts.recipientsOf(request, party).ifEmpty {
            listOf(
                InformationRequestNoticeRecipient(
                    PrincipalRef(
                        requireNotNull(party.principalKind),
                        requireNotNull(party.principalId)
                    ), null
                )
            )
        }
        val rendered = renderer.render(request, intent, overridesFor(request, intent))
        return recipients.map { recipient ->
            renderFor(request, intent, party, recipient, rendered, if (heldByGroup) recipient.principal else null, now)
        }.first()
    }

    private fun renderFor(
        request: InformationRequest,
        intent: InformationRequestNoticeIntent,
        party: InformationRequestParty,
        recipient: InformationRequestNoticeRecipient,
        rendered: InformationRequestRenderedNotice,
        member: PrincipalRef?,
        now: Instant,
    ): InformationRequestOutboundNotice {
        val endpoint = recipient.email
        val deliveredSubject = sender.subjectForDelivery(rendered.subject)
        val notice = noticeRepository.save(
            InformationRequestOutboundNotice().apply {
                noticeIntentId = intent.id
                informationRequestId = request.id
                partyId = party.id
                recipientPrincipalKind = recipient.principal.kind
                recipientPrincipalId = recipient.principal.id
                channel = InformationRequestNoticeChannel.EMAIL
                recipientEndpoint = endpoint
                endpointState =
                    if (endpoint != null) InformationRequestNoticeEndpointState.RESOLVED else InformationRequestNoticeEndpointState.MISSING
                renderedSubject = deliveredSubject
                renderedBody = rendered.body
                renderedContentHash = InformationRequestNoticeContentHash.of(deliveredSubject, rendered.body)
                sourceKind = rendered.sourceKind
                sourceCommunicationId = rendered.sourceCommunicationId
                sourceContentHash = rendered.sourceContentHash
                idempotencyKey = member?.let { "information_request.notice|${intent.id}|${it.kind}:${it.id}" }
                    ?: "information_request.notice|${intent.id}"
                renderedAt = Timestamp.from(now)
            },
        )
        rendered.allocations.forEach { allocation ->
            allocationRepository.save(
                InformationRequestNoticeSequenceAllocation().apply {
                    outboundNoticeId = notice.id
                    sequenceKey = allocation.key
                    allocatedValue = allocation.value
                    renderedValue = allocation.renderedValue
                },
            )
        }
        if (endpoint == null) record(notice, 1, InformationRequestNoticeAttemptOutcome.SKIPPED, NO_ENDPOINT, now)
        audit(
            request, AuditEventType.INFORMATION_REQUEST_NOTICE_RENDER, notice.id,
            mapOf(
                "noticeId" to notice.id.toString(),
                "noticeIntentId" to intent.id.toString(),
                "noticeKind" to intent.noticeKind.name,
                "endpointState" to notice.endpointState.name,
                "sourceKind" to notice.sourceKind.name,
                "renderedContentHash" to notice.renderedContentHash,
                "sequenceAllocationCount" to rendered.allocations.size.toString(),
            ),
        )
        return notice
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun deliver(noticeId: UUID, now: Instant): InformationRequestNoticeAttemptOutcome?
    {
        val queued = noticeRepository.findById(noticeId) ?: return null
        val owningRequest = requestRepository.findRequestByIdForShare(queued.informationRequestId) ?: return null
        val notice = noticeRepository.findByIdForUpdate(noticeId) ?: return null
        if (notice.endpointState != InformationRequestNoticeEndpointState.RESOLVED) return null
        val attempts = attemptRepository.findForNotice(noticeId)
        if (attempts.any { it.outcome != InformationRequestNoticeAttemptOutcome.FAILED }) return null
        if (attempts.size >= InformationRequestNoticeStateReader.MAXIMUM_ATTEMPTS) return null
        val last = attempts.lastOrNull()
        if (last != null && last.attemptedAt.toInstant().plus(backoffAfter(attempts.size)).isAfter(now)) return null
        val intent = requireNotNull(intentRepository.findById(notice.noticeIntentId))
        deliverySkipReasonOf(
            owningRequest,
            partyRepository.findByIdForShare(notice.partyId),
            notice,
            intent
        )?.let { reason ->
            record(notice, attempts.size + 1, InformationRequestNoticeAttemptOutcome.SKIPPED, reason, now)
            auditSkip(owningRequest, notice, reason)
            return InformationRequestNoticeAttemptOutcome.SKIPPED
        }
        val failure = try
        {
            sender.send(notice)
            null
        }
        catch (exception: Exception)
        {
            exception.javaClass.simpleName.ifBlank { DELIVERY_ERROR }
        }
        val outcome =
            if (failure == null) InformationRequestNoticeAttemptOutcome.DELIVERED else InformationRequestNoticeAttemptOutcome.FAILED
        val attempt = record(notice, attempts.size + 1, outcome, failure, now)
        audit(
            owningRequest, AuditEventType.INFORMATION_REQUEST_NOTICE_DELIVER, notice.id,
            buildMap {
                put("noticeId", notice.id.toString())
                put("attemptNumber", attempt.attemptNumber.toString())
                put("outcome", outcome.name)
                failure?.let { put("failureCode", it) }
            },
            if (outcome == InformationRequestNoticeAttemptOutcome.DELIVERED) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
        )
        return outcome
    }

    private fun deliverySkipReasonOf(
        request: InformationRequest,
        party: InformationRequestParty?,
        notice: InformationRequestOutboundNotice,
        intent: InformationRequestNoticeIntent,
    ): String? {
        skipReasonOf(request, party, intent)?.let { return it }
        val stillHeld = if (party?.principalKind == PrincipalKind.PRINCIPAL_GROUP)
            contacts.recipientsOf(request, party).any {
                it.principal.kind == notice.recipientPrincipalKind && it.principal.id == notice.recipientPrincipalId
            }
        else
            party?.principalKind == notice.recipientPrincipalKind && party?.principalId == notice.recipientPrincipalId
        return if (stillHeld) null else RECIPIENT_REASSIGNED
    }

    private fun skipReasonOf(
        request: InformationRequest,
        party: InformationRequestParty?,
        intent: InformationRequestNoticeIntent,
    ): String? {
        if (party == null || !party.active) return PARTY_INACTIVE
        if (request.state.isTerminal && !owedByExpiringDeadline(request, intent)) return REQUEST_FINISHED
        val exchange = exchangeLookup.findExchange(request.exchangeId)
        if (exchange == null || exchange.isDeleted || exchange.status !in OPEN_PARENT_STATES) return PARENT_CLOSED
        intent.clockEventId?.let { eventId ->
            val event = clockEventRepository.findById(eventId) ?: return CLOCK_CHANGED
            val clock = clockRepository.findById(event.clockId) ?: return CLOCK_CHANGED
            if (clock.dueCycle != event.dueCycle || clock.dueAt != event.dueAt ||
                (clock.state != InformationRequestClockState.RUNNING && !owedByExpiringDeadline(request, intent))
            )
                return CLOCK_CHANGED
        }
        val locked = LockedInformationRequest(exchange, request)
        val continuation = when (intent.noticeKind) {
            InformationRequestNoticeKind.RESPONSE_REMINDER,
            InformationRequestNoticeKind.RESPONSE_OVERDUE,
                -> continuationGuard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER)

            InformationRequestNoticeKind.REQUIREMENTS_AMENDED -> continuationGuard.scheduledStandingOf(locked)
        }
        return continuation.takeUnless { it.proceeds }?.name
    }

    private fun owedByExpiringDeadline(request: InformationRequest, intent: InformationRequestNoticeIntent): Boolean {
        if (request.state != InformationRequestState.EXPIRED ||
            intent.noticeKind != InformationRequestNoticeKind.RESPONSE_OVERDUE
        ) return false
        val overdue = intent.clockEventId?.let(clockEventRepository::findById) ?: return false
        return clockEventRepository.findForClock(overdue.clockId).any {
            it.eventKind == InformationRequestClockEventKind.EXPIRED && it.dueCycle == overdue.dueCycle
        }
    }

    private fun skipped(
        intent: InformationRequestNoticeIntent,
        request: InformationRequest,
        party: InformationRequestParty,
        reason: String,
        now: Instant,
    ): InformationRequestOutboundNotice {
        val notice = noticeRepository.save(
            InformationRequestOutboundNotice().apply {
                noticeIntentId = intent.id
                informationRequestId = request.id
                partyId = party.id
                recipientPrincipalKind = party.principalKind
                recipientPrincipalId = party.principalId
                channel = InformationRequestNoticeChannel.EMAIL
                recipientEndpoint = null
                endpointState = InformationRequestNoticeEndpointState.MISSING
                renderedSubject = ""
                renderedBody = ""
                renderedContentHash = EMPTY_CONTENT_HASH
                sourceKind = InformationRequestNoticeSourceKind.PLATFORM_DEFAULT
                sourceContentHash = EMPTY_CONTENT_HASH
                idempotencyKey = "information_request.notice|${intent.id}"
                renderedAt = Timestamp.from(now)
            },
        )
        record(notice, 1, InformationRequestNoticeAttemptOutcome.SKIPPED, reason, now)
        auditSkip(request, notice, reason)
        return notice
    }

    private fun auditSkip(request: InformationRequest, notice: InformationRequestOutboundNotice, reason: String) {
        audit(
            request, AuditEventType.INFORMATION_REQUEST_NOTICE_DELIVER, notice.id,
            mapOf(
                "noticeId" to notice.id.toString(),
                "noticeIntentId" to notice.noticeIntentId.toString(),
                "outcome" to InformationRequestNoticeAttemptOutcome.SKIPPED.name,
                "failureCode" to reason,
            ),
            AuditOutcome.DENIED,
        )
    }

    private fun record(
        notice: InformationRequestOutboundNotice,
        number: Int,
        outcome: InformationRequestNoticeAttemptOutcome,
        failure: String?,
        now: Instant,
    ): InformationRequestNoticeDeliveryAttempt =
        attemptRepository.save(
            InformationRequestNoticeDeliveryAttempt().apply {
                outboundNoticeId = notice.id
                informationRequestId = notice.informationRequestId
                attemptNumber = number
                channel = notice.channel
                this.outcome = outcome
                failureCode = failure
                attemptedAt = Timestamp.from(now)
            },
        )

    private fun overridesFor(request: InformationRequest, intent: InformationRequestNoticeIntent): Map<String, String>
    {
        val overrides = mutableMapOf<String, String>()
        exchangeRetrieval.getExchangeNameForDisplay(request.exchangeId)?.let { overrides["EXCHANGE_NAME"] = it }
        val event = intent.clockEventId?.let(clockEventRepository::findById)
        if (event != null) {
            event.dueAt?.let { dueAt ->
                overrides[InformationRequestNoticeDefaults.DUE_AT] =
                    dueText(dueAt, clockRepository.findById(event.clockId))
            }
        } else {
            clockRepository.findForRequest(request.id)
                .filter { it.state == InformationRequestClockState.RUNNING }
                .minByOrNull { it.dueAt }
                ?.let { clock -> overrides[InformationRequestNoticeDefaults.DUE_AT] = dueText(clock.dueAt, clock) }
        }
        return overrides
    }

    private fun dueText(dueAt: Timestamp, clock: InformationRequestClock?): String {
        val zone = clock?.let { policies.versionView(it.policyVersionId)?.calendar?.zone } ?: ZoneId.of("UTC")
        return DUE_FORMAT.format(dueAt.toInstant().atZone(zone))
    }

    private fun audit(
        request: InformationRequest,
        eventType: AuditEventType,
        noticeId: UUID,
        payload: Map<String, String>,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
    )
    {
        val attribution = actorLabels.attribute(DISPATCHER)
        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(request),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                actorRole = "SYSTEM",
                targetType = "INFORMATION_REQUEST",
                targetId = request.id.toString(),
                payload = payload,
                idempotencyKey = "${eventType.key}|$noticeId|${payload["attemptNumber"] ?: "render"}",
                businessTransactionId = noticeId.toString(),
            ),
        )
    }

    private fun backoffAfter(attempts: Int): Duration =
        BASE_BACKOFF.multipliedBy(1L shl (attempts - 1).coerceIn(0, MAXIMUM_BACKOFF_EXPONENT))

    private companion object
    {
        const val NO_ENDPOINT = "NO_ENDPOINT"
        const val RENDER_FAILED = "RENDER_FAILED"
        const val DELIVERY_ERROR = "DELIVERY_ERROR"
        const val PARTY_INACTIVE = "PARTY_INACTIVE"
        const val CLOCK_CHANGED = "CLOCK_CHANGED"
        const val RECIPIENT_REASSIGNED = "RECIPIENT_REASSIGNED"
        const val REQUEST_FINISHED = "REQUEST_FINISHED"
        const val PARENT_CLOSED = "PARENT_CLOSED"
        const val EMPTY_CONTENT_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val OPEN_PARENT_STATES = setOf(ExchangeStatus.INITIATED, ExchangeStatus.ACCEPTED_STARTED)
        const val MAXIMUM_BACKOFF_EXPONENT = 6
        val BASE_BACKOFF: Duration = Duration.ofMinutes(5)
        val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
        val DISPATCHER = InformationRequestAuditActor.system(PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)))
    }
}
