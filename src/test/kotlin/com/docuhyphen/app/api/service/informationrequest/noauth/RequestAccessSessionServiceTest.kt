package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.noauth.RequestAccessSessionRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.model.CredentialCommandCutoffCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class RequestAccessSessionServiceTest
{
    @Test
    fun `issuing a session against a bootstrap ShareLink persists it bound to the ShareLink and participant`()
    {
        val fixture = Fixture()
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val expiresAt = Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS))

        val session = fixture.service.issue(
            shareLink = fixture.bootstrapLink,
            participant = participant,
            verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
            expiresAt = expiresAt,
        )

        val persisted = session.session
        assertEquals(fixture.bootstrapLink.id, persisted.shareLinkId)
        assertEquals(participant.kind, persisted.participantPrincipalKind)
        assertEquals(participant.id, persisted.participantPrincipalId)
        assertEquals(RequestAccessSessionVerificationStrength.EMAIL_OTP, persisted.verificationStrength)
        assertEquals(expiresAt, persisted.expiresAt)
        assertNull(persisted.revokedAt)
        assertNotNull(fixture.saved[persisted.id])
    }

    @Test
    fun `a session is only issued to a participant identified by contact proof`() {
        val fixture = Fixture()

        val refusal =
            org.junit.jupiter.api.assertThrows<InformationRequestLifecycleException> {
                fixture.service.issue(
                    shareLink = fixture.bootstrapLink,
                    participant = PrincipalRef.user(UUID.randomUUID()),
                    verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
                    expiresAt = null,
                )
            }

        assertEquals(
            InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE,
            refusal.reasonCode,
        )
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `no session is issued or honoured once its parent Exchange requires recipient sign-in`() {
        val fixture = Fixture()
        val session = fixture.existingSession()
        whenever(fixture.sessionRepository.recipientSignInRequired(fixture.bootstrapLink.id)).thenReturn(true)

        val issuing = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(
                shareLink = fixture.bootstrapLink,
                participant = PrincipalRef.participant(UUID.randomUUID()),
                verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
                expiresAt = null,
            )
        }
        val using = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.requireActive(session.id)
        }

        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, issuing.reasonCode)
        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, using.reasonCode)
        assertEquals(listOf(session.id), fixture.saved.keys.toList())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CredentialCommandCutoffCase.SOURCE)
    fun `no session is issued once the request or its parent Exchange takes no new sessions`(case: CredentialCommandCutoffCase) {
        val fixture = Fixture()
        fixture.requestState = case.requestState
        fixture.parent = case.parentSnapshot

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(
                shareLink = fixture.bootstrapLink,
                participant = PrincipalRef.participant(UUID.randomUUID()),
                verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
                expiresAt = null,
            )
        }

        assertEquals(case.reasonCode, refusal.reasonCode)
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `issuing a session against a direct-grant ShareLink is refused`()
    {
        val fixture = Fixture()
        val directLink = ShareLink().apply {
            id = UUID.randomUUID()
            shareId = UUID.randomUUID()
            tokenHash = "irrelevant"
            linkMode = ShareLinkMode.DIRECT_GRANT
        }

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.issue(
                shareLink = directLink,
                participant = PrincipalRef.participant(UUID.randomUUID()),
                verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
                expiresAt = null,
            )
        }
    }

    @Test
    fun `a credential with the wrong secret is refused before the session or its Exchange is locked`() {
        val fixture = Fixture()
        val session = fixture.existingSession().apply { credentialHash = sha256("b".repeat(43)) }
        whenever(fixture.sessionRepository.findById(session.id)).thenReturn(session)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.verifyCredential("${session.id}.${"a".repeat(43)}")
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED, refusal.reasonCode)
        verify(fixture.sessionRepository, never()).findSessionByIdForUpdate(any())
        verify(fixture.sessionRepository, never()).lockParentForShare(any())
    }

    @Test
    fun `a credential with the right secret locks and answers its session`()
    {
        val fixture = Fixture()
        val session = fixture.existingSession().apply { credentialHash = sha256("a".repeat(43)) }
        whenever(fixture.sessionRepository.findById(session.id)).thenReturn(session)

        val verified = fixture.service.verifyCredential("${session.id}.${"a".repeat(43)}")

        assertEquals(session.id, verified.id)
        verify(fixture.sessionRepository).findSessionByIdForUpdate(session.id)
    }

    private fun sha256(secret: String): String = MessageDigest.getInstance("SHA-256")
        .digest(secret.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Test
    fun `touching use increments the atomic use count and stamps last use`()
    {
        val fixture = Fixture()
        val session = fixture.existingSession()
        assertEquals(0, session.useCount)

        val touched = fixture.service.touchUse(session.id)

        assertEquals(1, touched.useCount)
        assertNotNull(touched.lastUsedAt)
    }

    @Test
    fun `requiring an active session returns it unchanged when not revoked or expired`()
    {
        val fixture = Fixture()
        val session = fixture.existingSession()

        val active = fixture.service.requireActive(session.id)

        assertEquals(session.id, active.id)
    }

    @Test
    fun `requiring an active session fails for an unknown session id`()
    {
        val fixture = Fixture()

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.requireActive(UUID.randomUUID())
        }
    }

    @Test
    fun `requiring an active session refuses a revoked session`()
    {
        val fixture = Fixture()
        val session = fixture.existingSession().apply { revokedAt = Timestamp.from(Instant.now()) }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.requireActive(session.id)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED, ex.reasonCode)
    }

    @Test
    fun `requiring an active session refuses an expired session`()
    {
        val fixture = Fixture()
        val session = fixture.existingSession().apply {
            expiresAt = Timestamp.from(Instant.now().minusSeconds(60))
        }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.requireActive(session.id)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED, ex.reasonCode)
    }

    private class Fixture
    {
        val saved = mutableMapOf<UUID, RequestAccessSession>()
        val bootstrapLink: ShareLink = ShareLink().apply {
            id = UUID.randomUUID()
            shareId = UUID.randomUUID()
            tokenHash = "bootstrap-hash"
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
        }
        val sessionRepository = mock<RequestAccessSessionRepository>()
        val service = RequestAccessSessionService(sessionRepository)
        var requestState = InformationRequestState.ISSUED
        var parent = InformationRequestParentSnapshot(
            com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED,
            lockedForUpdate = true,
        )

        init
        {
            whenever(sessionRepository.lockParentForShare(any())).thenAnswer { parent }
            whenever(sessionRepository.requestStateForShare(any())).thenAnswer { requestState }
            whenever(sessionRepository.save(any())).thenAnswer {
                it.getArgument<RequestAccessSession>(0).also { session -> saved[session.id] = session }
            }
            whenever(sessionRepository.update(any())).thenAnswer {
                it.getArgument<RequestAccessSession>(0).also { session -> saved[session.id] = session }
            }
            whenever(sessionRepository.findSessionByIdForUpdate(any())).thenAnswer {
                saved[it.getArgument(0)]
            }
            whenever(sessionRepository.findActiveByShareLinkId(any())).thenAnswer {
                val shareLinkId = it.getArgument<UUID>(0)
                saved.values.filter { session -> session.shareLinkId == shareLinkId && session.revokedAt == null }
            }
        }

        fun existingSession(): RequestAccessSession =
            RequestAccessSession().apply {
                id = UUID.randomUUID()
                shareLinkId = bootstrapLink.id
                participantPrincipalKind = PrincipalRef.participant(UUID.randomUUID()).kind
                participantPrincipalId = UUID.randomUUID()
                expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            }.also { saved[it.id] = it }
    }
}
