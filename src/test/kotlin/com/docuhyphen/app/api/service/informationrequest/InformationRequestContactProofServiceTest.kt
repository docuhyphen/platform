package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.user.AppUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

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
    fun `issuing a challenge is refused for an unknown token`()
    {
        val fixture = Fixture()

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issueChallenge("does-not-exist")
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, ex.reasonCode)
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
    fun `resolves a USER-kind party's contact email through AppUserService`()
    {
        val fixture = Fixture(partyPrincipalKind = PrincipalKind.USER)

        fixture.service.issueChallenge(fixture.rawToken)

        assertEquals(fixture.participantEmail, fixture.sentTo)
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

        val shareLinkRepository = mock<ShareLinkRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val appUserService = mock<AppUserService>()
        val externalParticipantRepository = mock<ExternalParticipantRepository>()
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

        val service = InformationRequestContactProofService(
            shareLinkRepository = shareLinkRepository,
            partyRepository = partyRepository,
            appUserService = appUserService,
            externalParticipantRepository = externalParticipantRepository,
            otpService = otpService,
            emailService = emailService,
            requestAccessSessionService = requestAccessSessionService,
        )

        init
        {
            whenever(shareLinkRepository.findByTokenHash(any())).thenAnswer {
                (it.getArgument<String>(0) == tokenHash).let { matches -> if (matches) shareLink else null }
            }
            whenever(shareLinkRepository.findByTokenHashForUpdate(any())).thenAnswer {
                (it.getArgument<String>(0) == tokenHash).let { matches -> if (matches) shareLink else null }
            }
            whenever(shareLinkRepository.update(any())).thenAnswer {
                it.getArgument<ShareLink>(0).also { link -> updatedShareLinks += link }
            }
            whenever(partyRepository.findByShareId(any())).thenAnswer {
                (it.getArgument<UUID>(0) == party.shareId).let { matches -> if (matches) party else null }
            }
            whenever(appUserService.getById(participantId)).thenAnswer {
                AppUser().apply { id = participantId; email = participantEmail }
            }
            whenever(externalParticipantRepository.findById(participantId)).thenAnswer {
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
                    expiresAt = it.getArgument(3)
                }.let { session -> com.docuhyphen.app.api.model.informationrequest.IssuedRequestAccessSession(session, "secret") }
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
