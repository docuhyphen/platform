package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant

@ApplicationScoped
class InformationRequestAccessContextFactory @Inject constructor(
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    fun currentAuthenticated(): RequestAccessContext
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")
        return RequestAccessContext(principal, authorizationContextFactory.currentContext())
    }

    fun fromBootstrapSession(
        shareLink: ShareLink,
        session: RequestAccessSession,
        participant: PrincipalRef,
    ): RequestAccessContext
    {
        if (session.shareLinkId != shareLink.id)
        {
            throw ForbiddenException("Session is not bound to this ShareLink")
        }
        if (session.revokedAt != null)
        {
            throw ForbiddenException("Session has been revoked")
        }
        val expiresAt = session.expiresAt
        if (expiresAt == null || !expiresAt.toInstant().isAfter(Instant.now()))
        {
            throw ForbiddenException("Session has expired")
        }
        if (session.participantPrincipalKind != participant.kind || session.participantPrincipalId != participant.id)
        {
            throw ForbiddenException("Session is not bound to this recipient")
        }
        return RequestAccessContext(participant, AuthorizationContext(sessionRef = session.id.toString()))
    }
}

