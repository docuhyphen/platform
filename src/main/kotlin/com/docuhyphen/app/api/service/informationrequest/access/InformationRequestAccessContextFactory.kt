package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant

@ApplicationScoped
class InformationRequestAccessContextFactory @Inject constructor(
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val stepUpWindow: RequestAccessSessionStepUpWindow,
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
        if (participant.kind != PrincipalKind.PARTICIPANT) {
            throw ForbiddenException("An access link session acts only for a participant")
        }
        if (session.revokedAt != null)
        {
            throw ForbiddenException("Session has been revoked")
        }
        if (!session.expiresAt.toInstant().isAfter(Instant.now()))
        {
            throw ForbiddenException("Session has expired")
        }
        if (session.participantPrincipalKind != participant.kind || session.participantPrincipalId != participant.id)
        {
            throw ForbiddenException("Session is not bound to this recipient")
        }
        return RequestAccessContext(
            participant,
            AuthorizationContext(
                mfaSatisfied = stepUpWindow.isVerified(session, Instant.now()),
                sessionRef = session.id.toString(),
            ),
            accessSessionId = session.id,
        )
    }
}

