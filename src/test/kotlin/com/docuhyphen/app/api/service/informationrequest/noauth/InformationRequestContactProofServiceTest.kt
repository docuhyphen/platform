package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditPayloadPolicy
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.model.CredentialCommandCutoffCase
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class InformationRequestContactProofServiceTest
{
    @Test
    fun `issuing a challenge for a valid bootstrap link emails an OTP to the party's contact and stores its hash`()
    {
        val fixture = Fixture()

        fixture.service.issueChallenge(fixture.rawToken)

        assertEquals(fixture.participantEmail, fixture.sentTo)
        assertEquals(1, fixture.updatedShareLinks.size)
        assertNotNull(fixture.shareLink.contactOtpHash)
    }

    @Test
    fun `contact proof is refused once the parent Exchange requires recipient sign-in`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        val sentOtp = fixture.lastSentOtp
        fixture.sentTo = null
        doThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
                "Sign-in required"
            )
        )
            .whenever(fixture.requestAccessSessionService).requireNoAuthPermitted(fixture.shareLink.id)

        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        val verification = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, sentOtp)
        }

        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, challenge.reasonCode)
        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, verification.reasonCode)
        assertNull(fixture.sentTo)
        verify(fixture.requestAccessSessionService, never()).issue(any(), any(), any(), anyOrNull())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CredentialCommandCutoffCase.SOURCE)
    fun `no code is sent or verified once the request or its parent Exchange takes no new sessions`(case: CredentialCommandCutoffCase) {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        fixture.sentTo = null
        case.applyTo(fixture.request, fixture.exchange)

        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        val verification = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)
        }

        assertEquals(case.reasonCode, challenge.reasonCode)
        assertEquals(case.reasonCode, verification.reasonCode)
        assertNull(fixture.sentTo)
        assertEquals(1, fixture.shareLink.contactOtpChallengeCount)
        assertEquals(0, fixture.shareLink.contactOtpFailedAttempts)
        assertEquals(0, fixture.shareLink.usedCount)
        verify(fixture.requestAccessSessionService, never()).issue(any(), any(), any(), anyOrNull())
    }

    @ParameterizedTest
    @EnumSource(value = InformationRequestState::class, names = ["CANCELLED", "SUPERSEDED"])
    fun `a live session keeps reading through a link that its request's cancellation or supersession revoked`(
        state: InformationRequestState,
    ) {
        val fixture = Fixture()
        fixture.shareLink.status = ShareLinkStatus.REVOKED
        fixture.request.state = state

        assertEquals(fixture.shareLink to fixture.party, fixture.service.resolveBootstrapLink(fixture.rawToken))
        assertEquals(fixture.shareLink to fixture.party, fixture.service.resolveBootstrapLinkById(fixture.shareLink.id))
    }

    @ParameterizedTest
    @EnumSource(value = InformationRequestState::class, names = ["ISSUED", "IN_PROGRESS", "CLOSED", "EXPIRED"])
    fun `a revoked link stops reads while its request was not cancelled or superseded`(state: InformationRequestState) {
        val fixture = Fixture()
        fixture.shareLink.status = ShareLinkStatus.REVOKED
        fixture.request.state = state

        val byToken = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.resolveBootstrapLink(fixture.rawToken)
        }
        val byId = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.resolveBootstrapLinkById(fixture.shareLink.id)
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, byToken.reasonCode)
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, byId.reasonCode)
    }

    @Test
    fun `issuing a challenge is refused for an unknown token`()
    {
        val fixture = Fixture()

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge("does-not-exist")
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, ex.reasonCode)
    }

    @Test
    fun `an Exchange direct-grant link or an Exchange recipient credential never opens a request on the access link read path`() {
        val fixture = Fixture()
        fixture.shareLink.linkMode = ShareLinkMode.DIRECT_GRANT

        val directGrant = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.resolveBootstrapLink(fixture.rawToken)
        }
        val exchangeCredential = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.resolveBootstrapLink("exchange-recipient-access-token")
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, directGrant.reasonCode)
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, exchangeCredential.reasonCode)
    }

    @Test
    fun `issuing a challenge is refused for a direct-grant ShareLink`()
    {
        val fixture = Fixture()
        fixture.shareLink.linkMode = ShareLinkMode.DIRECT_GRANT

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, ex.reasonCode)
    }

    @Test
    fun `issuing a challenge is refused for a revoked ShareLink`()
    {
        val fixture = Fixture()
        fixture.shareLink.status = ShareLinkStatus.REVOKED

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, ex.reasonCode)
    }

    @Test
    fun `issuing a challenge is refused for an expired ShareLink`()
    {
        val fixture = Fixture()
        fixture.shareLink.expiresAt = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED, ex.reasonCode)
    }

    @Test
    fun `issuing a challenge is refused once usage is exhausted`()
    {
        val fixture = Fixture()
        fixture.shareLink.maxUses = 1
        fixture.shareLink.usedCount = 1

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_EXHAUSTED, ex.reasonCode)
    }

    @Test
    fun `verifying the correct code mints a session bound to the party's participant and clears the code`()
    {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)

        val session = fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp).session

        assertEquals(fixture.shareLink.id, session.shareLinkId)
        assertEquals(PrincipalKind.PARTICIPANT, session.participantPrincipalKind)
        assertEquals(fixture.party.principalId, session.participantPrincipalId)
        assertEquals(RequestAccessSessionVerificationStrength.EMAIL_OTP, session.verificationStrength)
        assertNull(fixture.shareLink.contactOtpHash)
        assertNull(fixture.shareLink.contactOtpExpiresAt)
    }

    @Test
    fun `verifying the correct code consumes the bootstrap link use budget`()
    {
        val fixture = Fixture()
        fixture.shareLink.maxUses = 1
        fixture.service.issueChallenge(fixture.rawToken)

        fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)

        assertEquals(1, fixture.shareLink.usedCount)
        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_EXHAUSTED, ex.reasonCode)
    }

    @Test
    fun `verifying without a prior challenge is refused`()
    {
        val fixture = Fixture()

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, "123456")
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED, ex.reasonCode)
    }

    @Test
    fun `verifying an incorrect code is refused and does not mint a session`()
    {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, "000000")
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, ex.reasonCode)
        verify(fixture.requestAccessSessionService, never()).issue(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `repeated invalid verification attempts lock the challenge before a correct code can mint a session`()
    {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)

        repeat(5) {
            val ex = assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.verifyChallenge(fixture.rawToken, "000000")
            }
            assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, ex.reasonCode)
        }

        val locked = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_LOCKED, locked.reasonCode)
        val reissue = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_LOCKED, reissue.reasonCode)
        verify(fixture.requestAccessSessionService, never()).issue(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `resending cannot erase failures or issue unlimited challenges`()
    {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, "000000")
        }

        fixture.service.issueChallenge(fixture.rawToken)
        assertEquals(1, fixture.shareLink.contactOtpFailedAttempts)
        fixture.service.issueChallenge(fixture.rawToken)
        assertEquals(3, fixture.shareLink.contactOtpChallengeCount)

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_CHALLENGE_LIMIT, refused.reasonCode)
    }

    @Test
    fun `a respondent who verifies each visit keeps verifying until the link's use budget is spent`() {
        val fixture = Fixture()
        fixture.shareLink.maxUses = 5

        repeat(5) {
            fixture.service.issueChallenge(fixture.rawToken)
            fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)
        }

        assertEquals(5, fixture.shareLink.usedCount)
        val exhausted = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_EXHAUSTED, exhausted.reasonCode)
    }

    @Test
    fun `a verified code restores the resend allowance but keeps counting unanswered resends`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        fixture.service.issueChallenge(fixture.rawToken)
        fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)

        assertEquals(0, fixture.shareLink.contactOtpChallengeCount)
        repeat(3) { fixture.service.issueChallenge(fixture.rawToken) }
        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_CHALLENGE_LIMIT, refused.reasonCode)
    }

    @Test
    fun `verifying an expired code is refused`()
    {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        fixture.shareLink.contactOtpExpiresAt = Timestamp.from(Instant.now().minusSeconds(1))

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)
        }
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_EXPIRED, ex.reasonCode)
    }

    @Test
    fun `a link bound to a party that signs in with an account is refused before any code is sent`()
    {
        val fixture = Fixture(partyPrincipalKind = PrincipalKind.USER)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE, refusal.reasonCode)
        assertNull(fixture.sentTo)
    }

    @Test
    fun `a link of a participant who linked an account is refused before any code is sent or verified`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        fixture.sentTo = null
        fixture.party.linkedUserShareId = UUID.randomUUID()

        val challenge = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge(fixture.rawToken)
        }
        val verification = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED, challenge.reasonCode)
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED, verification.reasonCode)
        assertNull(fixture.sentTo)
    }

    @Test
    fun `a verified code records when the link was used and that the participant contact is verified`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)

        fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)

        assertNotNull(fixture.shareLink.lastUsedAt)
        verify(fixture.externalParticipantService).verifyContact(
            eq(ExternalParticipantOwner.Organization(requireNotNull(fixture.request.ownerOrganizationId))),
            eq(fixture.participantId),
            any(),
        )
    }

    @Test
    fun `code limits use limits and lockouts are signalled as abuse refusals`() {
        val limited = Fixture().apply { shareLink.contactOtpChallengeCount = 3 }
        assertThrows(InformationRequestLifecycleException::class.java) { limited.service.issueChallenge(limited.rawToken) }
        verify(limited.abuseSignal).refused(
            InformationRequestAbuseControl.CONTACT_CODE_CHALLENGE_LIMIT,
            "link=${limited.shareLink.id}"
        )

        val exhausted = Fixture().apply {
            shareLink.maxUses = 1
            shareLink.usedCount = 1
        }
        assertThrows(InformationRequestLifecycleException::class.java) { exhausted.service.issueChallenge(exhausted.rawToken) }
        verify(exhausted.abuseSignal).refused(
            InformationRequestAbuseControl.ACCESS_LINK_USE_LIMIT,
            "link=${exhausted.shareLink.id}"
        )

        val locking = Fixture()
        locking.service.issueChallenge(locking.rawToken)
        repeat(5) {
            assertThrows(InformationRequestLifecycleException::class.java) {
                locking.service.verifyChallenge(
                    locking.rawToken,
                    "000000"
                )
            }
        }
        assertThrows(InformationRequestLifecycleException::class.java) { locking.service.issueChallenge(locking.rawToken) }
        verify(locking.abuseSignal, times(2)).refused(eq(InformationRequestAbuseControl.CONTACT_CODE_LOCKOUT), any())
    }

    @Test
    fun `challenge, refusal, and verified session are recorded as public link actions of the party without contact material`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyChallenge(fixture.rawToken, "000000")
        }
        val issued = fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)

        assertEquals(
            listOf("ISSUE_CONTACT_CHALLENGE", "REFUSE_CONTACT_CHALLENGE", "ISSUE_ACCESS_SESSION"),
            fixture.history.map { it.mutation.name },
        )
        assertEquals(listOf(null, "CONTACT_PROOF_INVALID", null), fixture.history.map { it.reasonCode })
        assertTrue(fixture.history.all { it.partyId == fixture.party.id && it.actorId == fixture.participantId })
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(3)).record(audit.capture())
        assertEquals(
            listOf(
                "information_request.contact_challenge.issue",
                "information_request.contact_challenge.refuse",
                "information_request.access_session.issue"
            ),
            audit.allValues.map { it.eventTypeKey },
        )
        assertTrue(audit.allValues.all { it.actorKind == AuditActorKind.PUBLIC_LINK && it.actorId == fixture.participantId })
        assertTrue(audit.allValues.all { it.actorLabel == "participant:${fixture.participantId}" })
        assertEquals(listOf(null, null, issued.session.id.toString()), audit.allValues.map { it.sessionId })
        assertEquals("1", audit.allValues[1].payload["failedAttempt"])
        assertEquals(issued.session.id.toString(), audit.allValues[2].payload["accessSessionId"])
        assertEquals("1", audit.allValues[2].payload["usedCount"])
        audit.allValues.forEach { record ->
            assertEquals(fixture.shareLink.id.toString(), record.payload["shareLinkId"])
            assertEquals(
                0,
                InformationRequestAuditPayloadPolicy.projectionOf(record.payload) { _ -> true }.withheldKeyCount
            )
            val serialized = record.payload.toString()
            listOf(
                fixture.participantEmail,
                FIXED_OTP,
                fixture.tokenHash,
                "secret"
            ).forEach { assertFalse(serialized.contains(it)) }
        }
    }

    @Test
    fun `the refusal that locks the code records when the lock ends`() {
        val fixture = Fixture()
        fixture.service.issueChallenge(fixture.rawToken)

        repeat(5) {
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.verifyChallenge(fixture.rawToken, "000000")
            }
        }

        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(6)).record(audit.capture())
        val refusals = audit.allValues.drop(1)
        assertEquals((1..5).map(Int::toString), refusals.map { it.payload["failedAttempt"] })
        assertEquals(listOf(false, false, false, false, true), refusals.map { it.payload.containsKey("lockedUntil") })
        assertEquals(
            fixture.shareLink.contactOtpLockedUntil?.toInstant().toString(),
            refusals.last().payload["lockedUntil"]
        )
    }

    @Test
    fun `contact proof commands take the parent and request lock before the link lock`() {
        val fixture = Fixture()

        fixture.service.issueChallenge(fixture.rawToken)
        fixture.service.verifyChallenge(fixture.rawToken, fixture.lastSentOtp)

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository, fixture.shareLinks)
        repeat(2) {
            locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
            locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
            locks.verify(fixture.shareLinks).lockShareLinkForUpdate(fixture.shareLink.id)
        }
        verify(fixture.shareLinks, times(2)).lockShareLinkForUpdate(any())
    }

    @Test
    fun `a degraded audit refuses the challenge before any code is sent`() {
        val fixture = Fixture()
        whenever(fixture.auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Degraded(
                UUID.randomUUID(),
                "unavailable"
            )
        )

        assertThrows(AuditCaptureFailedException::class.java) { fixture.service.issueChallenge(fixture.rawToken) }

        verify(fixture.emailService, never()).sendEmail(any(), any(), any(), any())
        verifyNoInteractions(fixture.publisher)
    }

    private class Fixture(partyPrincipalKind: PrincipalKind = PrincipalKind.PARTICIPANT)
    {
        val rawToken = "raw-bootstrap-token"
        val tokenHash = sha256Hex(rawToken)
        val participantId: UUID = UUID.randomUUID()
        val participantEmail = "respondent@example.com"

        val shareLink = ShareLink().apply {
            id = UUID.randomUUID()
            shareId = UUID.randomUUID()
            tokenHash = this@Fixture.tokenHash
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            status = ShareLinkStatus.ACTIVE
        }

        val party = InformationRequestParty().apply {
            informationRequestId = UUID.randomUUID()
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = partyPrincipalKind
            principalId = participantId
            shareId = shareLink.shareId
            active = true
        }

        val updatedShareLinks = mutableListOf<ShareLink>()
        var sentTo: String? = null
        val lastSentOtp: String = FIXED_OTP

        val shareLinks = mock<ShareLinkService>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val externalParticipantService = mock<ExternalParticipantService>()
        val abuseSignal = mock<InformationRequestAbuseSignal>()
        val realOtpService = OtpService()
        val otpService: OtpService = mock {
            on { generateEmailOtp() } doReturn FIXED_OTP
            on { hashOtp(any()) } doAnswer { invocation -> realOtpService.hashOtp(invocation.getArgument(0)) }
            on { verifyEmailOtp(any(), any()) } doAnswer { invocation ->
                realOtpService.verifyEmailOtp(invocation.getArgument(0), invocation.getArgument(1))
            }
        }
        val emailService = mock<EmailService>()
        val requestAccessSessionService = mock<RequestAccessSessionService>()
        val request = InformationRequest().apply {
            id = party.informationRequestId
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        val exchange = Exchange().apply {
            id = request.exchangeId
            status = ExchangeStatus.ACCEPTED_STARTED
        }
        val exchangeLookup = mock<ExchangeLookupService>()
        val requestRepository = mock<InformationRequestRepository>()
        val transitions = mock<InformationRequestTransitionRepository>()
        val history = mutableListOf<InformationRequestTransition>()
        val auditRecorder = mock<AuditRecorder>()
        val publisher = mock<DomainEventPublisher>()

        val service = InformationRequestContactProofService(
            shareLinks = shareLinks,
            partyRepository = partyRepository,
            requestRepository = requestRepository,
            exchangeLookup = exchangeLookup,
            externalParticipantService = externalParticipantService,
            otpService = otpService,
            codeMailer = InformationRequestVerificationCodeMailer(emailService, committingSynchronizations()),
            requestAccessSessionService = requestAccessSessionService,
            contactProofRecorder = InformationRequestContactProofRecorder(
                InformationRequestTransitionHistoryService(
                    transitions, auditRecorder, publisher, mock<InformationRequestResponseStart>(),
                    InformationRequestAuditActorLabelResolver(mock<PrincipalDisplayService>()), CommandReceiptScope(),
                ),
            ),
            abuseSignal = abuseSignal,
        )

        init
        {
            whenever(shareLinks.findByTokenHash(any())).thenAnswer {
                (it.getArgument<String>(0) == tokenHash).let { matches -> if (matches) shareLink else null }
            }
            whenever(shareLinks.lockShareLinkForUpdate(shareLink.id)).thenReturn(shareLink)
            whenever(shareLinks.update(any())).thenAnswer {
                it.getArgument<ShareLink>(0).also { link -> updatedShareLinks += link }
            }
            whenever(partyRepository.findByShareId(any())).thenAnswer {
                (it.getArgument<UUID>(0) == party.shareId).let { matches -> if (matches) party else null }
            }
            whenever(partyRepository.findByIdForUpdate(party.id)).thenReturn(party)
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(exchangeLookup.lockExchangeForUpdate(request.exchangeId)).thenReturn(exchange)
            whenever(shareLinks.findShareLink(shareLink.id)).thenReturn(shareLink)
            whenever(transitions.nextSequenceNumber(request.id)).thenAnswer { history.size + 1 }
            whenever(transitions.save(any())).thenAnswer {
                it.getArgument<InformationRequestTransition>(0).also(history::add)
            }
            whenever(auditRecorder.record(any())).thenReturn(
                AuditCaptureResult.Captured(
                    UUID.randomUUID(),
                    UUID.randomUUID()
                )
            )
            whenever(externalParticipantService.findOwned(eq(participantId), any())).thenAnswer {
                ExternalParticipant().apply { id = participantId; email = participantEmail; emailLower = participantEmail }
            }
            whenever(emailService.sendEmail(any(), any(), any(), any())).thenAnswer {
                sentTo = it.getArgument(0)
                null
            }
            whenever(
                requestAccessSessionService.issue(any(), any(), any(), anyOrNull()),
            ).thenAnswer {
                RequestAccessSession().apply {
                    shareLinkId = it.getArgument<ShareLink>(0).id
                    val participant = it.getArgument<PrincipalRef>(1)
                    participantPrincipalKind = participant.kind
                    participantPrincipalId = participant.id
                    verificationStrength = it.getArgument(2)
                    expiresAt = it.getArgument<Timestamp?>(3) ?: Timestamp.from(Instant.now().plusSeconds(3600))
                }.let { session -> com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession(session, "secret") }
            }
        }
    }

    private companion object
    {
        const val FIXED_OTP = "654321"

        fun sha256Hex(raw: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
