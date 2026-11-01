package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class ExchangeNoAuthDecisionAuditTest {
    private val recipientId: UUID = UUID.randomUUID()
    private val exchange: Exchange = Exchange().apply {
        status = ExchangeStatus.INITIATED
        requireRecipientSignIn = false
        recipientOtpHash = "stored-hash"
        recipientOtpExpiry = Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES))
        documents = mutableListOf()
    }
    private val repository = mock<ExchangeRepository>()
    private val otpService = mock<OtpService>()
    private val shareService = mock<ShareService>()
    private val auditRecorder = mock<AuditRecorder>()
    private val requestParentLifecycle = mock<InformationRequestParentLifecycleService>()
    private val tokenService = NoAuthExchangeAccessTokenService()

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ExchangeStatus::class, names = ["ACCEPTED_STARTED", "REJECTED"])
    fun `a recipient decision made through an access link is recorded on the audit ledger`(decision: ExchangeStatus) {
        whenever(repository.findById(exchange.id)).thenReturn(exchange)
        whenever(repository.findByIdForUpdate(exchange.id)).thenReturn(exchange)
        whenever(otpService.verifyEmailOtp(eq("123456"), eq("stored-hash"))).thenReturn(true)
        whenever(shareService.recipientConstraintsJson(exchange.id)).thenReturn("{}")
        whenever(shareService.primaryRecipientUserId(exchange.id)).thenReturn(recipientId)
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(
                UUID.randomUUID(),
                UUID.randomUUID()
            )
        )

        service().updateNoAuthExchange(exchange.id.toString(), decision, "123456", null, tokenService.issue(exchange))

        val draft = argumentCaptor<AuditEventDraft>().also { verify(auditRecorder).record(it.capture()) }.firstValue
        val expectedKey =
            if (decision == ExchangeStatus.ACCEPTED_STARTED) "exchange.lifecycle.accepted" else "exchange.lifecycle.rejected"
        assertEquals(expectedKey, draft.eventTypeKey)
        assertEquals(AuditActorKind.PUBLIC_LINK, draft.actorKind)
        assertEquals(recipientId, draft.actorId)
        assertEquals(mapOf("previousStatus" to "INITIATED", "newStatus" to decision.name), draft.payload)
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ExchangeStatus::class, names = ["ACCEPTED_STARTED", "REJECTED"])
    fun `a recipient decision made through an access link changes the requests in that recipient's name`(decision: ExchangeStatus) {
        whenever(repository.findById(exchange.id)).thenReturn(exchange)
        whenever(repository.findByIdForUpdate(exchange.id)).thenReturn(exchange)
        whenever(otpService.verifyEmailOtp(eq("123456"), eq("stored-hash"))).thenReturn(true)
        whenever(shareService.recipientConstraintsJson(exchange.id)).thenReturn("{}")
        whenever(shareService.primaryRecipientUserId(exchange.id)).thenReturn(recipientId)
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(
                UUID.randomUUID(),
                UUID.randomUUID()
            )
        )

        service().updateNoAuthExchange(exchange.id.toString(), decision, "123456", null, tokenService.issue(exchange))

        verify(requestParentLifecycle).apply(
            exchange.id,
            decision,
            false,
            InformationRequestAuditActor.beforeSession(PrincipalRef.user(recipientId)),
            ExchangeStatus.INITIATED,
        )
    }

    private fun service() = ExchangeUpdateService(
        exchangeRepository = repository,
        emailService = mock(),
        emailTemplateService = mock(),
        realtimeEventService = mock(),
        otpService = otpService,
        userContactService = mock(),
        shareService = shareService,
        exchangeRecipientService = mock(),
        externalParticipantRepository = mock(),
        principalGroupRepository = mock(),
        shareRepository = mock(),
        appUserService = mock(),
        workflowInstanceRepository = mock(),
        workflowStepRepository = mock(),
        workflowEngineService = mock(),
        authTokenContext = mock<AuthTokenContext>(),
        authorizationService = mock(),
        authorizationContextFactory = mock(),
        auditRecorder = auditRecorder,
        auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
            whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
        },
        noAuthExchangeAccessTokenService = tokenService,
        noAuthExchangeAccessWindowService = NoAuthExchangeAccessWindowService(repository, mock()),
        lifecycleNotificationService = mock(),
        documentThumbnailService = mock(),
        requestParentLifecycle = requestParentLifecycle,
        requestCompletion = mock(),
        endingIntents = mock(),
    )
}
