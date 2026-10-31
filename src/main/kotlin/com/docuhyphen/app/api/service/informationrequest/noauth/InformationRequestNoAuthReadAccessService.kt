package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class InformationRequestNoAuthReadAccessService @Inject constructor(
    private val contactProofService: InformationRequestContactProofService,
    private val requestAccessSessionService: RequestAccessSessionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @Transactional
    fun resolve(requestId: UUID, rawToken: String, sessionToken: String?): RequestAccessContext
    {
        if (sessionToken.isNullOrBlank()) throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
            "A verified request session credential is required",
        )
        val session = requestAccessSessionService.authenticate(sessionToken)
        val access = bind(requestId, session, contactProofService.resolveBootstrapLink(rawToken))
        requestAccessSessionService.touchUse(session.id)
        return access
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun requireCurrent(requestId: UUID, access: RequestAccessContext): RequestAccessContext =
        verifyCurrent(requestId, access)

    @Transactional
    fun confirmCurrent(requestId: UUID, access: RequestAccessContext): RequestAccessContext =
        verifyCurrent(requestId, access)

    fun <T> readCurrent(requestId: UUID, access: RequestAccessContext, read: (RequestAccessContext) -> T): T {
        val result = read(access)
        val current = confirmCurrent(requestId, access)
        val verificationLapsed = access.authorization.mfaSatisfied && !current.authorization.mfaSatisfied
        return if (verificationLapsed) readCurrent(requestId, current, read) else result
    }

    private fun verifyCurrent(requestId: UUID, access: RequestAccessContext): RequestAccessContext {
        val sessionId = access.accessSessionId ?: return access
        val session = requestAccessSessionService.requireActive(sessionId)
        val current = bind(requestId, session, contactProofService.resolveBootstrapLinkById(session.shareLinkId))
        if (current.principal != access.principal) throw ForbiddenException("Session is not bound to this recipient")
        return current
    }

    private fun bind(
        requestId: UUID,
        session: RequestAccessSession,
        link: Pair<ShareLink, InformationRequestParty>,
    ): RequestAccessContext {
        val (shareLink, party) = link
        val participant = PrincipalRef(
            kind = requireNotNull(party.principalKind) { "Information Request party has no principal" },
            id = requireNotNull(party.principalId) { "Information Request party has no principal" },
        )
        val access = accessContextFactory.fromBootstrapSession(shareLink, session, participant)
        if (party.informationRequestId != requestId) throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.NOT_FOUND,
            "Information Request not found",
        )
        return access
    }
}
