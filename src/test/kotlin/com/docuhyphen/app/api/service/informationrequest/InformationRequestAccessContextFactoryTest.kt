package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The authenticated input path is the only one this factory offers today. It has to hand a runtime
 * Information Request service the exact principal and authorization facts the authenticated caller
 * carries, and it has to refuse rather than guess when the request carries no principal at all.
 */
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
        val factory = InformationRequestAccessContextFactory(authorizationContextFactory)

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
        val factory = InformationRequestAccessContextFactory(authorizationContextFactory)

        val access = factory.currentAuthenticated()

        assertEquals(principal, access.principal)
        assertSame(authorization, access.authorization)
    }

    @Test
    fun `a request with no principal is refused rather than given an empty context`()
    {
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(null)
        val factory = InformationRequestAccessContextFactory(authorizationContextFactory)

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
        val factory = InformationRequestAccessContextFactory(mock())

        val access = factory.fromBootstrapSession(shareLink, session, participant)

        assertEquals(participant, access.principal)
        assertEquals(session.id.toString(), access.authorization.sessionRef)
    }

    @Test
    fun `a session bound to a different ShareLink is refused rather than trusted`()
    {
        val shareLink = bootstrapLink()
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = UUID.randomUUID()
        }
        val factory = InformationRequestAccessContextFactory(mock())

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
        val factory = InformationRequestAccessContextFactory(mock())

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
        val factory = InformationRequestAccessContextFactory(mock())

        assertThrows<ForbiddenException> {
            factory.fromBootstrapSession(shareLink, session, PrincipalRef.participant(UUID.randomUUID()))
        }
    }

    private fun bootstrapLink(): ShareLink = ShareLink().apply {
        this.id = UUID.randomUUID()
        this.shareId = UUID.randomUUID()
        this.tokenHash = UUID.randomUUID().toString()
    }
}

