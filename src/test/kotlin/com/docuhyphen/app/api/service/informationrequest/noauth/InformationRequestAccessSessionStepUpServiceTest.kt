package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.model.informationrequest.noauth.LockedInformationRequestBootstrapLink
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.RequestAccessSessionStepUpWindow
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditPayloadPolicy
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.model.CredentialCommandCutoffCase
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class InformationRequestAccessSessionStepUpServiceTest {
    @Test
    fun `a verified session is sent a fresh code at its party's contact and holds only the code's hash`() {
        val fixture = Fixture()

        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)

        assertEquals(fixture.contactEmail, fixture.sentTo)
        assertNotNull(fixture.session.stepUpCodeHash)
        assertFalse(fixture.session.stepUpCodeHash.orEmpty().contains(FIXED_CODE))
        assertTrue(fixture.session.stepUpCodeExpiresAt!!.toInstant().isAfter(Instant.now().plus(9, ChronoUnit.MINUTES)))
        assertEquals(1, fixture.session.stepUpChallengeCount)
        assertEquals(listOf("ISSUE_CONTACT_CHALLENGE|ACCESS_SESSION_STEP_UP"), fixture.history())
        val challenge = fixture.audits.single()
        assertEquals(fixture.session.id.toString(), challenge.sessionId)
        assertEquals(AuditActorKind.PUBLIC_LINK, challenge.actorKind)
        assertEquals("participant:${fixture.participant.id}", challenge.actorLabel)
        assertEquals(fixture.session.id.toString(), challenge.payload["accessSessionId"])
        assertTrue(challenge.payload.values.none { it.contains(FIXED_CODE) || it.contains(fixture.contactEmail) })
    }

    @Test
    fun `the right code steps the session up for the window and clears its pending code`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)

        val stepUp = fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)

        assertEquals(fixture.session.id, stepUp.sessionId)
        val steppedUpAt = requireNotNull(fixture.session.steppedUpAt).toInstant()
        assertEquals(steppedUpAt.plus(WINDOW), stepUp.verifiedUntil.toInstant())
        assertNull(fixture.session.stepUpCodeHash)
        assertNull(fixture.session.stepUpCodeExpiresAt)
        assertEquals(0, fixture.session.stepUpChallengeCount)
        assertEquals(0, fixture.session.stepUpFailedAttempts)
        assertTrue(
            fixture.factory.fromBootstrapSession(
                fixture.shareLink,
                fixture.session,
                fixture.participant
            ).authorization.mfaSatisfied
        )
        assertEquals(
            listOf("ISSUE_CONTACT_CHALLENGE|ACCESS_SESSION_STEP_UP", "STEP_UP_ACCESS_SESSION|"),
            fixture.history()
        )
        val recorded = fixture.audits.last()
        assertEquals("information_request.access_session.step_up", recorded.eventTypeKey)
        assertEquals(fixture.session.id.toString(), recorded.sessionId)
        assertEquals(stepUp.verifiedUntil.toInstant().toString(), recorded.payload["verifiedUntil"])
        assertTrue(fixture.audits.all { InformationRequestAuditPayloadPolicy.projectionOf(it.payload) { _ -> true }.withheldKeyCount == 0 })
    }

    @Test
    fun `verification never outlives the session it belongs to`() {
        val fixture = Fixture()
        fixture.session.expiresAt = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES))
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)

        val stepUp = fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)

        assertEquals(fixture.session.expiresAt, stepUp.verifiedUntil)
    }

    @Test
    fun `wrong codes are refused and recorded and the fifth locks step-up for the session`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)

        repeat(5) {
            val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.verify(fixture.rawToken, fixture.sessionToken, "000000")
            }
            assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, refusal.reasonCode)
        }
        val locked = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)
        }

        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_LOCKED, locked.reasonCode)
        assertNull(fixture.session.steppedUpAt)
        assertNotNull(fixture.session.stepUpLockedUntil)
        assertEquals(List(5) { "REFUSE_CONTACT_CHALLENGE|CONTACT_PROOF_INVALID" }, fixture.history().drop(1))
        val refusals = fixture.audits.drop(1)
        assertEquals((1..5).map(Int::toString), refusals.map { it.payload["failedAttempt"] })
        assertTrue(refusals.all { it.payload["accessSessionId"] == fixture.session.id.toString() })
        assertTrue(refusals.last().payload.containsKey("lockedUntil"))
        assertTrue(fixture.audits.all { InformationRequestAuditPayloadPolicy.projectionOf(it.payload) { _ -> true }.withheldKeyCount == 0 })
    }

    @Test
    fun `a missing or expired code is refused without stepping up`() {
        val fixture = Fixture()

        val missing = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)
        }
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)
        fixture.session.stepUpCodeExpiresAt = Timestamp.from(Instant.now().minusSeconds(1))
        val expired = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)
        }

        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED, missing.reasonCode)
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_EXPIRED, expired.reasonCode)
        assertNull(fixture.session.steppedUpAt)
    }

    @Test
    fun `a session holds at most five unanswered codes and a step-up allows more`() {
        val fixture = Fixture()
        repeat(5) { fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken) }

        val limited = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)
        }
        fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)

        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_CHALLENGE_LIMIT, limited.reasonCode)
        assertEquals(1, fixture.session.stepUpChallengeCount)
        assertEquals(6, fixture.sentCodes)
    }

    @Test
    fun `step-up needs a session credential before anything is locked`() {
        val fixture = Fixture()

        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken, null)
        }
        val verification = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verify(fixture.rawToken, " ", FIXED_CODE)
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED, challenge.reasonCode)
        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED, verification.reasonCode)
        verify(fixture.contactProof, never()).lockBootstrapLink(any(), any())
        assertNull(fixture.sentTo)
    }

    @Test
    fun `a session that belongs to another link cannot step up through this one`() {
        val fixture = Fixture()
        fixture.session.shareLinkId = UUID.randomUUID()

        assertThrows(ForbiddenException::class.java) {
            fixture.service.issueChallenge(
                fixture.rawToken,
                fixture.sessionToken
            )
        }
        fixture.session.stepUpCodeHash = fixture.otp.hashOtp(FIXED_CODE)
        fixture.session.stepUpCodeExpiresAt = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES))
        assertThrows(ForbiddenException::class.java) {
            fixture.service.verify(
                fixture.rawToken,
                fixture.sessionToken,
                FIXED_CODE
            )
        }

        assertNull(fixture.sentTo)
        assertNull(fixture.session.steppedUpAt)
        assertTrue(fixture.history().isEmpty())
    }

    @Test
    fun `step-up locks the request and link before the session and does not spend the link's uses`() {
        val fixture = Fixture()

        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)
        fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)

        val locks = inOrder(fixture.contactProof, fixture.sessions)
        repeat(2) {
            locks.verify(fixture.contactProof).lockBootstrapLink(fixture.rawToken, false)
            locks.verify(fixture.sessions).authenticate(fixture.sessionToken)
        }
        assertEquals(0, fixture.shareLink.usedCount)
    }

    @Test
    fun `a step-up records that the participant contact is verified`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)

        fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)

        verify(fixture.externalParticipantService).verifyContact(
            eq(ExternalParticipantOwner.Organization(requireNotNull(fixture.request.ownerOrganizationId))),
            eq(fixture.participant.id),
            any(),
        )
    }

    @Test
    fun `step-up code limits and lockouts are signalled as abuse refusals`() {
        val limited = Fixture().apply { session.stepUpChallengeCount = 5 }
        assertThrows(InformationRequestLifecycleException::class.java) {
            limited.service.issueChallenge(limited.rawToken, limited.sessionToken)
        }
        verify(limited.abuseSignal).refused(
            InformationRequestAbuseControl.CONTACT_CODE_CHALLENGE_LIMIT,
            "session=${limited.session.id}"
        )

        val locking = Fixture()
        locking.service.issueChallenge(locking.rawToken, locking.sessionToken)
        repeat(5) {
            assertThrows(InformationRequestLifecycleException::class.java) {
                locking.service.verify(locking.rawToken, locking.sessionToken, "000000")
            }
        }
        assertThrows(InformationRequestLifecycleException::class.java) {
            locking.service.issueChallenge(locking.rawToken, locking.sessionToken)
        }
        verify(locking.abuseSignal, times(2)).refused(eq(InformationRequestAbuseControl.CONTACT_CODE_LOCKOUT), any())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CredentialCommandCutoffCase.SOURCE)
    fun `a session cannot step up once its request or parent Exchange takes no new sessions`(case: CredentialCommandCutoffCase) {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)
        case.applyTo(fixture.request, fixture.parent)

        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)
        }
        val verification = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verify(fixture.rawToken, fixture.sessionToken, FIXED_CODE)
        }

        assertEquals(case.reasonCode, challenge.reasonCode)
        assertEquals(case.reasonCode, verification.reasonCode)
        assertEquals(1, fixture.sentCodes)
        assertEquals(1, fixture.session.stepUpChallengeCount)
        assertEquals(0, fixture.session.stepUpFailedAttempts)
        assertNull(fixture.session.steppedUpAt)
        assertEquals(listOf("ISSUE_CONTACT_CHALLENGE|ACCESS_SESSION_STEP_UP"), fixture.history())
    }

    @Test
    fun `a party without a contact address cannot be sent a step-up code`() {
        val fixture = Fixture()
        whenever(fixture.contactProof.contactEmailOf(fixture.request, fixture.party)).thenReturn(null)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken, fixture.sessionToken)
        }

        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED, refusal.reasonCode)
        assertNull(fixture.sentTo)
        assertNull(fixture.session.stepUpCodeHash)
    }

    private class Fixture {
        val rawToken = "raw-bootstrap-token"
        val sessionToken = "session-credential"
        val contactEmail = "respondent@process.test"
        val participant: PrincipalRef = PrincipalRef.participant(UUID.randomUUID())
        val shareLink = ShareLink().apply {
            id = UUID.randomUUID()
            shareId = UUID.randomUUID()
            tokenHash = "bootstrap-hash"
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            status = ShareLinkStatus.ACTIVE
        }
        val request = InformationRequest().apply {
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.PARTICIPANT
            principalId = participant.id
            shareId = shareLink.shareId
            active = true
        }
        val parent = Exchange().apply {
            id = request.exchangeId
            status = ExchangeStatus.ACCEPTED_STARTED
        }
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = shareLink.id
            participantPrincipalKind = participant.kind
            participantPrincipalId = participant.id
            issuedAt = Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS))
            expiresAt = Timestamp.from(Instant.now().plus(6, ChronoUnit.HOURS))
        }
        var sentTo: String? = null
        var sentCodes = 0
        val audits = mutableListOf<AuditEventDraft>()
        private val transitions = mutableListOf<InformationRequestTransition>()

        val otp = OtpService()
        private val otpService: OtpService = mock {
            on { generateEmailOtp() } doReturn FIXED_CODE
            on { hashOtp(any()) } doAnswer { otp.hashOtp(it.getArgument(0)) }
            on { verifyEmailOtp(any(), any()) } doAnswer { otp.verifyEmailOtp(it.getArgument(0), it.getArgument(1)) }
        }
        val contactProof = mock<InformationRequestContactProofService>()
        val abuseSignal = mock<InformationRequestAbuseSignal>()
        val externalParticipantService = mock<ExternalParticipantService>()
        val sessions = mock<RequestAccessSessionService>()
        private val emailService = mock<EmailService>()
        private val transitionRepository = mock<InformationRequestTransitionRepository>()
        private val auditRecorder = mock<AuditRecorder>()
        val factory = InformationRequestAccessContextFactory(mock(), RequestAccessSessionStepUpWindow(WINDOW))

        val service = InformationRequestAccessSessionStepUpService(
            contactProofService = contactProof,
            sessionService = sessions,
            accessContextFactory = factory,
            stepUpWindow = RequestAccessSessionStepUpWindow(WINDOW),
            otpService = otpService,
            codeMailer = InformationRequestVerificationCodeMailer(emailService, committingSynchronizations()),
            recorder = InformationRequestContactProofRecorder(
                InformationRequestTransitionHistoryService(
                    transitionRepository,
                    auditRecorder,
                    mock<DomainEventPublisher>(),
                    mock<InformationRequestResponseStart>(),
                    InformationRequestAuditActorLabelResolver(mock<PrincipalDisplayService>()),
                    CommandReceiptScope(),
                ),
            ),
            abuseSignal = abuseSignal,
            externalParticipantService = externalParticipantService,
        )

        fun history(): List<String> = transitions.map { "${it.mutation.name}|${it.reasonCode.orEmpty()}" }

        init {
            whenever(contactProof.lockBootstrapLink(rawToken, false)).thenReturn(
                LockedInformationRequestBootstrapLink(
                    request,
                    shareLink,
                    party,
                    parent,
                )
            )
            whenever(contactProof.contactEmailOf(request, party)).thenReturn(contactEmail)
            whenever(sessions.authenticate(sessionToken)).thenReturn(session)
            whenever(sessions.update(any())).thenAnswer { it.getArgument<RequestAccessSession>(0) }
            whenever(transitionRepository.nextSequenceNumber(request.id)).thenAnswer { transitions.size + 1 }
            whenever(transitionRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestTransition>(0).also(transitions::add)
            }
            whenever(auditRecorder.record(any())).thenAnswer {
                val draft = it.getArgument<AuditEventDraft>(0)
                audits += draft
                AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
            }
            whenever(emailService.sendEmail(any(), any(), any(), any())).thenAnswer {
                sentTo = it.getArgument(0)
                sentCodes += 1
                null
            }
        }
    }

    private companion object {
        const val FIXED_CODE = "654321"
        val WINDOW: Duration = Duration.ofMinutes(15)
    }
}
