package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.repository.informationrequest.RequestAccessSessionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

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
    fun `revoking an active session stamps revokedAt once`()
    {
        val fixture = Fixture()
        val session = fixture.existingSession()

        val revoked = fixture.service.revoke(session.id)
        assertNotNull(revoked.revokedAt)

        val firstRevokedAt = revoked.revokedAt
        val revokedAgain = fixture.service.revoke(session.id)
        assertEquals(firstRevokedAt, revokedAgain.revokedAt)
    }

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

        init
        {
            whenever(sessionRepository.lockParentForShare(any())).thenReturn(InformationRequestParentSnapshot(com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED, lockedForUpdate = true))
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
