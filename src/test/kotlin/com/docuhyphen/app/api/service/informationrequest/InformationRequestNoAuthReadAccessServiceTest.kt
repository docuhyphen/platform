package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.repository.informationrequest.RequestAccessSessionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestNoAuthReadAccessServiceTest
{
    private val requestId = UUID.randomUUID()
    private val participant = PrincipalRef.participant(UUID.randomUUID())
    private val link = ShareLink().apply {
        shareId = UUID.randomUUID()
        tokenHash = "bootstrap-hash"
        linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
        expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
    }
    private val party = InformationRequestParty().apply {
        informationRequestId = requestId
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = participant.kind
        principalId = participant.id
        shareId = link.shareId
        active = true
    }
    private val saved = mutableMapOf<UUID, RequestAccessSession>()
    private val repository = mock<RequestAccessSessionRepository>()
    private val sessions = RequestAccessSessionService(repository)
    private val proof = mock<InformationRequestContactProofService>()
    private val service = InformationRequestNoAuthReadAccessService(proof, sessions, InformationRequestAccessContextFactory(mock()))

    init
    {
        whenever(repository.lockParentForShare(any())).thenReturn(InformationRequestParentSnapshot(com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED, lockedForUpdate = true))
        whenever(repository.save(any())).thenAnswer {
            it.getArgument<RequestAccessSession>(0).also { row -> saved[row.id] = row }
        }
        whenever(repository.update(any())).thenAnswer { it.getArgument<RequestAccessSession>(0) }
        whenever(repository.findSessionByIdForUpdate(any())).thenAnswer { saved[it.getArgument(0)] }
        whenever(repository.findActiveByShareLinkId(link.id)).thenAnswer { saved.values.filter { it.revokedAt == null } }
        whenever(proof.resolveBootstrapLink("bootstrap")).thenReturn(link to party)
    }

    private fun issue() = sessions.issue(link, participant, RequestAccessSessionVerificationStrength.EMAIL_OTP, null)

    @Test
    fun `a forwarded bootstrap link alone stays refused after recipient verification`()
    {
        val issued = issue()
        assertThrows(InformationRequestLifecycleException::class.java) { service.resolve("bootstrap") }
        assertEquals(0, issued.session.useCount)
        val access = service.resolve("bootstrap", issued.sessionToken)
        assertEquals(requestId, access.requestId)
        assertEquals(participant, access.access.principal)
        assertEquals(issued.session.id.toString(), access.access.authorization.sessionRef)
        assertEquals(1, issued.session.useCount)
    }

    @Test
    fun `sessions have independent secrets stored only as hashes and expire within the link window`()
    {
        val first = issue()
        val second = issue()
        assertNotEquals(first.sessionToken, second.sessionToken)
        assertNotEquals(first.sessionToken.substringAfter('.'), first.session.credentialHash)
        assertEquals(64, first.session.credentialHash?.length)
        assertEquals(link.expiresAt, first.session.expiresAt)
        link.expiresAt = null
        assertNotNull(issue().session.expiresAt)
    }

    @Test
    fun `session ids wrong secrets and malformed credentials cannot authenticate`()
    {
        val issued = issue()
        for (credential in listOf(issued.session.id.toString(), "bootstrap", "${issued.session.id}.${"x".repeat(43)}"))
            assertThrows(InformationRequestLifecycleException::class.java) { service.resolve("bootstrap", credential) }
        assertEquals(0, issued.session.useCount)
    }

    @Test
    fun `expired revoked and non-expiring sessions fail closed`()
    {
        for (state in listOf("expired", "revoked", "missing-expiry"))
        {
            val issued = issue()
            when (state)
            {
                "expired" -> issued.session.expiresAt = Timestamp.from(Instant.now().minusSeconds(1))
                "revoked" -> issued.session.revokedAt = Timestamp.from(Instant.now())
                else -> issued.session.expiresAt = null
            }
            assertThrows(InformationRequestLifecycleException::class.java) { service.resolve("bootstrap", issued.sessionToken) }
            assertEquals(0, issued.session.useCount)
        }
    }

    @Test
    fun `sessions cannot be combined with another link or a reassigned recipient`()
    {
        val issued = issue()
        val otherLink = ShareLink().apply { shareId = UUID.randomUUID(); tokenHash = "other" }
        whenever(proof.resolveBootstrapLink("other-bootstrap")).thenReturn(otherLink to party)
        assertThrows(ForbiddenException::class.java) { service.resolve("other-bootstrap", issued.sessionToken) }
        party.principalId = UUID.randomUUID()
        assertThrows(ForbiddenException::class.java) { service.resolve("bootstrap", issued.sessionToken) }
        party.principalId = participant.id
        party.principalKind = PrincipalKind.USER
        assertThrows(ForbiddenException::class.java) { service.resolve("bootstrap", issued.sessionToken) }
        assertEquals(0, issued.session.useCount)
    }

    @Test
    fun `rotation revokes the original credential even when a fresh session exists for the link`()
    {
        val first = issue()
        sessions.revokeAllForShareLink(link.id)
        val second = issue()
        assertThrows(InformationRequestLifecycleException::class.java) { service.resolve("bootstrap", first.sessionToken) }
        assertEquals(second.session.id.toString(), service.resolve("bootstrap", second.sessionToken).access.authorization.sessionRef)
    }
}
