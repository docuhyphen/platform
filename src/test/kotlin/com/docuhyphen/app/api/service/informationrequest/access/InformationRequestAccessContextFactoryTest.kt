package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class InformationRequestAccessContextFactoryTest
{
    @Test
    fun `an authenticated user becomes a request access context carrying that same principal and authorization`()
    {
        val principal = PrincipalRef.user(UUID.randomUUID())
        val authorization = AuthorizationContext(
            activeOrgId = UUID.randomUUID(),
            mfaSatisfied = true,
            sessionRef = UUID.randomUUID().toString(),
        )
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(authorization)
        val factory = InformationRequestAccessContextFactory(
            authorizationContextFactory,
            RequestAccessSessionStepUpWindow(Duration.ofMinutes(15))
        )

        val access = factory.currentAuthenticated()

        assertEquals(principal, access.principal)
        assertSame(authorization, access.authorization)
    }

    @Test
    fun `an authenticated application becomes a request access context carrying that application principal`()
    {
        val principal = PrincipalRef.application(UUID.randomUUID())
        val authorization = AuthorizationContext(applicationId = principal.id)
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(authorization)
        val factory = InformationRequestAccessContextFactory(
            authorizationContextFactory,
            RequestAccessSessionStepUpWindow(Duration.ofMinutes(15))
        )

        val access = factory.currentAuthenticated()

        assertEquals(principal, access.principal)
        assertSame(authorization, access.authorization)
    }

    @Test
    fun `a request with no principal is refused rather than given an empty context`()
    {
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(null)
        val factory = InformationRequestAccessContextFactory(
            authorizationContextFactory,
            RequestAccessSessionStepUpWindow(Duration.ofMinutes(15))
        )

        assertThrows<ForbiddenException> { factory.currentAuthenticated() }
    }

    @Test
    fun `a validated bootstrap session becomes a request access context carrying the resolved participant`()
    {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val shareLink = bootstrapLink()
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = shareLink.id
            participantPrincipalKind = participant.kind
            participantPrincipalId = participant.id
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        }
        val factory = factory()

        val access = factory.fromBootstrapSession(shareLink, session, participant)

        assertEquals(participant, access.principal)
        assertEquals(session.id.toString(), access.authorization.sessionRef)
    }

    @Test
    fun `a bootstrap session never acts as an account holder`() {
        val user = PrincipalRef.user(UUID.randomUUID())
        val shareLink = bootstrapLink()
        val session = verifiedSession(shareLink, user, Instant.now())

        assertThrows<ForbiddenException> {
            factory().fromBootstrapSession(shareLink, session, user)
        }
    }

    @Test
    fun `a session bound to a different ShareLink is refused rather than trusted`()
    {
        val shareLink = bootstrapLink()
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = UUID.randomUUID()
        }
        val factory = factory()

        assertThrows<ForbiddenException> {
            factory.fromBootstrapSession(shareLink, session, PrincipalRef.participant(UUID.randomUUID()))
        }
    }

    @Test
    fun `a revoked bootstrap session is refused`()
    {
        val shareLink = bootstrapLink()
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = shareLink.id
            revokedAt = Timestamp.from(Instant.now())
        }
        val factory = factory()

        assertThrows<ForbiddenException> {
            factory.fromBootstrapSession(shareLink, session, PrincipalRef.participant(UUID.randomUUID()))
        }
    }

    @Test
    fun `an expired bootstrap session is refused`()
    {
        val shareLink = bootstrapLink()
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = shareLink.id
            expiresAt = Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS))
        }
        val factory = factory()

        assertThrows<ForbiddenException> {
            factory.fromBootstrapSession(shareLink, session, PrincipalRef.participant(UUID.randomUUID()))
        }
    }

    @Test
    fun `a session verified within the step-up window satisfies recent verification`() {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val shareLink = bootstrapLink()
        val session = verifiedSession(shareLink, participant, issuedAt = Instant.now().minus(1, ChronoUnit.MINUTES))

        val access = factory().fromBootstrapSession(shareLink, session, participant)

        assertTrue(access.authorization.mfaSatisfied)
    }

    @Test
    fun `a session last verified longer ago than the step-up window does not satisfy recent verification`() {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val shareLink = bootstrapLink()
        val session = verifiedSession(shareLink, participant, issuedAt = Instant.now().minus(16, ChronoUnit.MINUTES))

        val access = factory().fromBootstrapSession(shareLink, session, participant)

        assertFalse(access.authorization.mfaSatisfied)
    }

    @Test
    fun `a step-up restarts the window for a session verified long ago`() {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val shareLink = bootstrapLink()
        val session =
            verifiedSession(shareLink, participant, issuedAt = Instant.now().minus(2, ChronoUnit.HOURS)).apply {
                steppedUpAt = Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES))
            }

        val access = factory().fromBootstrapSession(shareLink, session, participant)

        assertTrue(access.authorization.mfaSatisfied)
    }

    @Test
    fun `the step-up window ends when the session ends`() {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val session = verifiedSession(bootstrapLink(), participant, issuedAt = Instant.now()).apply {
            expiresAt = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES))
        }

        assertEquals(
            session.expiresAt.toInstant(),
            RequestAccessSessionStepUpWindow(Duration.ofMinutes(15)).verifiedUntil(session)
        )
    }

    private fun factory() =
        InformationRequestAccessContextFactory(mock(), RequestAccessSessionStepUpWindow(Duration.ofMinutes(15)))

    private fun verifiedSession(shareLink: ShareLink, participant: PrincipalRef, issuedAt: Instant) =
        RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = shareLink.id
            participantPrincipalKind = participant.kind
            participantPrincipalId = participant.id
            this.issuedAt = Timestamp.from(issuedAt)
            expiresAt = Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS))
        }

    private fun bootstrapLink(): ShareLink = ShareLink().apply {
        this.id = UUID.randomUUID()
        this.shareId = UUID.randomUUID()
        this.tokenHash = UUID.randomUUID().toString()
    }
}

