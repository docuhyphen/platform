package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestTemplatePostgreSQLResource
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestContactProofTransactionTest
{
    @Inject
    lateinit var contactProofService: InformationRequestContactProofService

    @Inject
    lateinit var shareRepository: ShareRepository

    @Inject
    lateinit var shareLinkRepository: ShareLinkRepository

    @Inject
    lateinit var otpService: OtpService

    @Test
    fun `invalid codes persist their attempt budget across refused transactions`()
    {
        val rawToken = UUID.randomUUID().toString()
        val link = createLink(rawToken)

        repeat(5) { attempt ->
            val refusal = assertThrows<InformationRequestLifecycleException> {
                contactProofService.verifyChallenge(rawToken, "000000")
            }
            assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, refusal.reasonCode)
            val persisted = QuarkusTransaction.requiringNew().call { shareLinkRepository.findById(link.id)!! }
            if (attempt < 4) assertEquals(attempt + 1, persisted.contactOtpFailedAttempts)
            else assertNotNull(persisted.contactOtpLockedUntil)
        }
    }

    @Test
    fun `parallel invalid codes serialize into one persisted lockout`()
    {
        val rawToken = UUID.randomUUID().toString()
        val link = createLink(rawToken)
        val pool = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        try
        {
            val calls = (1..5).map {
                pool.submit<String> {
                    start.await()
                    try
                    {
                        contactProofService.verifyChallenge(rawToken, "000000")
                        "accepted"
                    }
                    catch (refusal: InformationRequestLifecycleException)
                    {
                        refusal.reasonCode
                    }
                }
            }
            start.countDown()
            assertTrue(calls.all { it.get(30, TimeUnit.SECONDS) == InformationRequestErrorCatalog.CONTACT_PROOF_INVALID })
        }
        finally
        {
            pool.shutdownNow()
        }
        val persisted = QuarkusTransaction.requiringNew().call { shareLinkRepository.findById(link.id)!! }
        assertNotNull(persisted.contactOtpLockedUntil)
    }

    @Test
    fun `parallel challenges cannot exceed the persisted link send budget`()
    {
        val rawToken = UUID.randomUUID().toString()
        val link = createLink(rawToken)
        val emailService = mock<EmailService>()
        QuarkusMock.installMockForType(emailService, EmailService::class.java)
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(any())).thenAnswer {
            AppUser().apply { id = it.getArgument(0); email = "respondent@example.com" }
        }
        QuarkusMock.installMockForType(appUserService, AppUserService::class.java)
        val pool = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        try
        {
            val calls = (1..5).map {
                pool.submit<String> {
                    start.await()
                    try
                    {
                        contactProofService.issueChallenge(rawToken)
                        "issued"
                    }
                    catch (refusal: InformationRequestLifecycleException)
                    {
                        refusal.reasonCode
                    }
                }
            }
            start.countDown()
            val results = calls.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(3, results.count { it == "issued" })
            assertEquals(2, results.count { it == InformationRequestErrorCatalog.CONTACT_PROOF_CHALLENGE_LIMIT })
        }
        finally
        {
            pool.shutdownNow()
        }
        val persisted = QuarkusTransaction.requiringNew().call { shareLinkRepository.findById(link.id)!! }
        assertEquals(3, persisted.contactOtpChallengeCount)
    }

    @Test
    fun `session issuance failure rolls back bootstrap use and code consumption`()
    {
        val rawToken = UUID.randomUUID().toString()
        val link = createLink(rawToken)
        val sessionService = mock<RequestAccessSessionService>()
        whenever(sessionService.issue(any(), any(), any(), anyOrNull())).thenThrow(IllegalStateException("Session unavailable"))
        QuarkusMock.installMockForType(sessionService, RequestAccessSessionService::class.java)

        assertThrows<IllegalStateException> {
            contactProofService.verifyChallenge(rawToken, "654321")
        }

        val persisted = QuarkusTransaction.requiringNew().call { shareLinkRepository.findById(link.id)!! }
        assertEquals(0, persisted.usedCount)
        assertNotNull(persisted.contactOtpHash)
        assertNotNull(persisted.contactOtpExpiresAt)
        assertNull(persisted.contactOtpLockedUntil)
    }

    private fun createLink(rawToken: String): ShareLink
    {
        val link = QuarkusTransaction.requiringNew().call {
            val share = shareRepository.save(Share().apply {
                resourceId = UUID.randomUUID()
                principalId = UUID.randomUUID()
            })
            shareLinkRepository.save(ShareLink().apply {
                shareId = share.id
                tokenHash = sha256Hex(rawToken)
                linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
                contactOtpHash = otpService.hashOtp("654321")
                contactOtpExpiresAt = Timestamp.from(Instant.now().plusSeconds(600))
            })
        }
        val partyRepository = mock<InformationRequestPartyRepository>()
        whenever(partyRepository.findByShareId(any())).thenReturn(InformationRequestParty().apply {
            informationRequestId = UUID.randomUUID()
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.USER
            principalId = UUID.randomUUID()
            shareId = link.shareId
        })
        QuarkusMock.installMockForType(partyRepository, InformationRequestPartyRepository::class.java)
        return link
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
