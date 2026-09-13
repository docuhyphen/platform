package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAccess
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@ApplicationScoped
class InformationRequestNoAuthReadAccessService @Inject constructor(
    private val contactProofService: InformationRequestContactProofService,
    private val requestAccessSessionService: RequestAccessSessionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @Transactional
    fun resolve(rawToken: String, sessionToken: String? = null): InformationRequestNoAuthAccess
    {
        if (sessionToken.isNullOrBlank()) throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
            "A verified request session credential is required",
        )
        val session = requestAccessSessionService.authenticate(sessionToken)
        val (shareLink, party) = contactProofService.resolveBootstrapLink(rawToken)
        val participant = PrincipalRef(
            kind = requireNotNull(party.principalKind) { "Information Request party has no principal" },
            id = requireNotNull(party.principalId) { "Information Request party has no principal" },
        )
        val access = accessContextFactory.fromBootstrapSession(shareLink, session, participant)
        requestAccessSessionService.touchUse(session.id)
        return InformationRequestNoAuthAccess(access, party.informationRequestId)
    }
}
