package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeClaimRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeSequenceAllocationRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestOutboundNoticeRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
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
    private val exchangeRepository: ExchangeRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val clockRepository: InformationRequestClockRepository,
    private val clockEventRepository: InformationRequestClockEventRepository,
    private val policies: InformationRequestClockPolicyService,
    private val contacts: InformationRequestPartyContactResolver,
    private val renderer: InformationRequestNoticeRenderer,
    private val sender: InformationRequestNoticeSender,
    private val auditRecorder: AuditRecorder,
)
{
    private val worker = "notice-worker:${UUID.randomUUID()}"

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun claimAndRender(intentId: UUID, now: Instant): InformationRequestOutboundNotice?
    {
        val intent = intentRepository.findById(intentId) ?: return null
        if (!claimRepository.claim(intent.id, intent.informationRequestId, worker, Timestamp.from(now))) return null
        val request = requireNotNull(requestRepository.findById(intent.informationRequestId))
        val party = requireNotNull(partyRepository.findById(intent.partyId))
        val endpoint = contacts.emailOf(request, party)
        val rendered = renderer.render(request, intent, overridesFor(request, intent))
        val notice = noticeRepository.save(
            InformationRequestOutboundNotice().apply {
                noticeIntentId = intent.id
                informationRequestId = request.id
                partyId = party.id
                recipientPrincipalKind = party.principalKind
                recipientPrincipalId = party.principalId
                channel = InformationRequestNoticeChannel.EMAIL
                recipientEndpoint = endpoint
                endpointState =
                    if (endpoint != null) InformationRequestNoticeEndpointState.RESOLVED else InformationRequestNoticeEndpointState.MISSING
                renderedSubject = rendered.subject
                renderedBody = rendered.body
                renderedContentHash = rendered.renderedContentHash
                sourceKind = rendered.sourceKind
                sourceCommunicationId = rendered.sourceCommunicationId
                sourceContentHash = rendered.sourceContentHash
                idempotencyKey = "information_request.notice|${intent.id}"
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
        val notice = noticeRepository.lock(noticeId) ?: return null
        if (notice.endpointState != InformationRequestNoticeEndpointState.RESOLVED) return null
        val attempts = attemptRepository.findForNotice(noticeId)
        if (attempts.any { it.outcome != InformationRequestNoticeAttemptOutcome.FAILED }) return null
        if (attempts.size >= InformationRequestNoticeStateReader.MAXIMUM_ATTEMPTS) return null
        val last = attempts.lastOrNull()
        if (last != null && last.attemptedAt.toInstant().plus(backoffAfter(attempts.size)).isAfter(now)) return null
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
        val request = requireNotNull(requestRepository.findById(notice.informationRequestId))
        audit(
            request, AuditEventType.INFORMATION_REQUEST_NOTICE_DELIVER, notice.id,
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
        exchangeRepository.findById(request.exchangeId)?.name?.let { overrides["EXCHANGE_NAME"] = it }
        intent.clockEventId?.let(clockEventRepository::findById)?.let { event ->
            val clock = clockRepository.findById(event.clockId)
            val zone = clock?.let { policies.versionView(it.policyVersionId)?.calendar?.zone } ?: ZoneId.of("UTC")
            event.dueAt?.let { overrides["DUE_AT"] = DUE_FORMAT.format(it.toInstant().atZone(zone)) }
        }
        return overrides
    }

    private fun audit(
        request: InformationRequest,
        eventType: AuditEventType,
        noticeId: UUID,
        payload: Map<String, String>,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(request),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorKind = AuditActorKind.SYSTEM,
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
        const val DELIVERY_ERROR = "DELIVERY_ERROR"
        const val MAXIMUM_BACKOFF_EXPONENT = 6
        val BASE_BACKOFF: Duration = Duration.ofMinutes(5)
        val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
    }
}
