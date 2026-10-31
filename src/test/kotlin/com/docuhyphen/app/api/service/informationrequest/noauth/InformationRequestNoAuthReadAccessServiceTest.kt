package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.repository.informationrequest.noauth.RequestAccessSessionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.RequestAccessSessionStepUpWindow
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

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
    private val service = InformationRequestNoAuthReadAccessService(
        proof,
        sessions,
        InformationRequestAccessContextFactory(mock(), RequestAccessSessionStepUpWindow(Duration.ofMinutes(15)))
    )

    init
    {
        whenever(repository.requestStateForShare(any()))
            .thenReturn(com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState.ISSUED)
        whenever(repository.lockParentForShare(any())).thenReturn(InformationRequestParentSnapshot(com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED, lockedForUpdate = true))
        whenever(repository.save(any())).thenAnswer {
            it.getArgument<RequestAccessSession>(0).also { row -> saved[row.id] = row }
        }
        whenever(repository.update(any())).thenAnswer { it.getArgument<RequestAccessSession>(0) }
        whenever(repository.findSessionByIdForUpdate(any())).thenAnswer { saved[it.getArgument(0)] }
        whenever(repository.findById(any())).thenAnswer { saved[it.getArgument(0)] }
        whenever(repository.findActiveByShareLinkId(link.id)).thenAnswer { saved.values.filter { it.revokedAt == null } }
        whenever(proof.resolveBootstrapLink("bootstrap")).thenReturn(link to party)
    }

    private fun issue() = sessions.issue(link, participant, RequestAccessSessionVerificationStrength.EMAIL_OTP, null)

    @Test
    fun `a forwarded bootstrap link alone stays refused after recipient verification`()
    {
        val issued = issue()
        assertThrows(InformationRequestLifecycleException::class.java) { service.resolve(requestId, "bootstrap", null) }
        assertEquals(0, issued.session.useCount)
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)
        assertEquals(participant, access.principal)
        assertEquals(issued.session.id.toString(), access.authorization.sessionRef)
        assertEquals(1, issued.session.useCount)
    }

    @Test
    fun `a link and session verified for one request are refused as not found for another request without spending a use`() {
        val issued = issue()
        val otherRequestId = UUID.randomUUID()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.resolve(otherRequestId, "bootstrap", issued.sessionToken)
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        assertEquals(0, issued.session.useCount)
        assertNull(issued.session.lastUsedAt)
        verify(repository, never()).update(any())
        service.resolve(requestId, "bootstrap", issued.sessionToken)
        assertEquals(1, issued.session.useCount)
    }

    @Test
    fun `a session presented with a link it does not belong to stays forbidden whichever request it names`() {
        val issued = issue()
        val otherLink = ShareLink().apply { shareId = UUID.randomUUID(); tokenHash = "other" }
        whenever(proof.resolveBootstrapLink("other-bootstrap")).thenReturn(otherLink to party)

        assertThrows(ForbiddenException::class.java) {
            service.resolve(UUID.randomUUID(), "other-bootstrap", issued.sessionToken)
        }
        assertEquals(0, issued.session.useCount)
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
            assertThrows(InformationRequestLifecycleException::class.java) {
                service.resolve(
                    requestId,
                    "bootstrap",
                    credential
                )
            }
        assertEquals(0, issued.session.useCount)
    }

    @Test
    fun `expired and revoked sessions fail closed`()
    {
        for (expired in listOf(true, false))
        {
            val issued = issue()
            if (expired) issued.session.expiresAt = Timestamp.from(Instant.now().minusSeconds(1))
            else issued.session.revokedAt = Timestamp.from(Instant.now())
            assertThrows(InformationRequestLifecycleException::class.java) {
                service.resolve(
                    requestId,
                    "bootstrap",
                    issued.sessionToken
                )
            }
            assertEquals(0, issued.session.useCount)
        }
    }

    @Test
    fun `sessions cannot be combined with another link or a reassigned recipient`()
    {
        val issued = issue()
        val otherLink = ShareLink().apply { shareId = UUID.randomUUID(); tokenHash = "other" }
        whenever(proof.resolveBootstrapLink("other-bootstrap")).thenReturn(otherLink to party)
        assertThrows(ForbiddenException::class.java) {
            service.resolve(
                requestId,
                "other-bootstrap",
                issued.sessionToken
            )
        }
        party.principalId = UUID.randomUUID()
        assertThrows(ForbiddenException::class.java) { service.resolve(requestId, "bootstrap", issued.sessionToken) }
        party.principalId = participant.id
        party.principalKind = PrincipalKind.USER
        assertThrows(ForbiddenException::class.java) { service.resolve(requestId, "bootstrap", issued.sessionToken) }
        assertEquals(0, issued.session.useCount)
    }

    @Test
    fun `rotation revokes the original credential even when a fresh session exists for the link`()
    {
        val first = issue()
        sessions.revokeAllForShareLink(link.id)
        val second = issue()
        assertThrows(InformationRequestLifecycleException::class.java) {
            service.resolve(
                requestId,
                "bootstrap",
                first.sessionToken
            )
        }
        assertEquals(
            second.session.id.toString(),
            service.resolve(requestId, "bootstrap", second.sessionToken).authorization.sessionRef
        )
    }

    @Test
    fun `a resolved access names the session it was resolved through and stays current while that session does`() {
        val issued = issue()
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)

        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)

        assertEquals(issued.session.id, access.accessSessionId)
        service.requireCurrent(requestId, access)
        service.confirmCurrent(requestId, access)
    }

    @Test
    fun `an access without a request session is current without any session lookup`() {
        val signedIn = RequestAccessContext(
            PrincipalRef.user(UUID.randomUUID()),
            AuthorizationContext(mfaSatisfied = true),
        )

        assertEquals(signedIn, service.requireCurrent(requestId, signedIn))
        assertEquals(signedIn, service.confirmCurrent(requestId, signedIn))
        assertEquals(listOf(signedIn), readsMadeBy(signedIn) { })

        verify(repository, never()).findSessionByIdForUpdate(any())
    }

    @Test
    fun `a rechecked access carries the verification its session holds now, not when the access was resolved`() {
        val issued = issue()
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)
        assertTrue(access.authorization.mfaSatisfied)

        issued.session.issuedAt = Timestamp.from(Instant.now().minus(Duration.ofHours(1)))

        for (check in listOf(service::requireCurrent, service::confirmCurrent)) {
            val current = check(requestId, access)
            assertFalse(current.authorization.mfaSatisfied)
            assertEquals(access.principal, current.principal)
            assertEquals(access.accessSessionId, current.accessSessionId)
        }
    }

    @Test
    fun `a read made while its verification lapsed is made again with the verification its session holds now`() {
        val issued = issue()
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)

        val reads = readsMadeBy(access) {
            issued.session.issuedAt = Timestamp.from(Instant.now().minus(Duration.ofHours(1)))
        }

        assertEquals(listOf(true, false), reads.map { it.authorization.mfaSatisfied })
    }

    @Test
    fun `a read whose verification still holds is made once`() {
        val issued = issue()
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)

        assertEquals(listOf(access), readsMadeBy(access) { })
    }

    @Test
    fun `a read whose verification was already lapsed when resolved is made once`() {
        val issued = issue()
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
        issued.session.issuedAt = Timestamp.from(Instant.now().minus(Duration.ofHours(1)))
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)

        assertEquals(listOf(false), readsMadeBy(access) { }.map { it.authorization.mfaSatisfied })
    }

    private fun readsMadeBy(access: RequestAccessContext, duringFirstRead: () -> Unit): List<RequestAccessContext> {
        val reads = mutableListOf<RequestAccessContext>()
        val result = service.readCurrent(requestId, access) { current ->
            reads += current
            if (reads.size == 1) duringFirstRead()
            current
        }
        assertEquals(reads.last(), result)
        return reads
    }

    @Test
    fun `a resolved access is no longer current once its session is revoked, expires, or its Exchange requires sign-in`() {
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
        val expectations = listOf(
            InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
            InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED,
            InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
        )
        for (expected in expectations) {
            whenever(repository.recipientSignInRequired(link.id)).thenReturn(false)
            val issued = issue()
            val access = service.resolve(requestId, "bootstrap", issued.sessionToken)
            when (expected) {
                InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED -> issued.session.revokedAt =
                    Timestamp.from(Instant.now())

                InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED ->
                    issued.session.expiresAt = Timestamp.from(Instant.now().minusSeconds(1))

                else -> whenever(repository.recipientSignInRequired(link.id)).thenReturn(true)
            }

            for (check in listOf(service::requireCurrent, service::confirmCurrent)) {
                val refusal =
                    assertThrows(InformationRequestLifecycleException::class.java) { check(requestId, access) }
                assertEquals(expected, refusal.reasonCode)
            }
        }
    }

    @Test
    fun `a resolved access is no longer current once its link stops working or its party moves to another request`() {
        val issued = issue()
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)
        whenever(proof.resolveBootstrapLinkById(link.id)).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_REVOKED,
                "This access link has been revoked"
            ),
        )

        val revoked =
            assertThrows(InformationRequestLifecycleException::class.java) { service.requireCurrent(requestId, access) }

        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, revoked.reasonCode)
        val moved = InformationRequestParty().apply {
            informationRequestId = UUID.randomUUID()
            roleKey = party.roleKey
            principalKind = party.principalKind
            principalId = party.principalId
            shareId = party.shareId
            active = true
        }
        doReturn(link to moved).whenever(proof).resolveBootstrapLinkById(link.id)
        val elsewhere =
            assertThrows(InformationRequestLifecycleException::class.java) { service.requireCurrent(requestId, access) }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, elsewhere.reasonCode)
    }

    @Test
    fun `a resolved access is not current for another recipient than the one its session verified`() {
        val issued = issue()
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
        val access = service.resolve(requestId, "bootstrap", issued.sessionToken)
        val forwarded = access.copy(principal = PrincipalRef.participant(UUID.randomUUID()))

        assertThrows(ForbiddenException::class.java) { service.requireCurrent(requestId, forwarded) }
    }
}
