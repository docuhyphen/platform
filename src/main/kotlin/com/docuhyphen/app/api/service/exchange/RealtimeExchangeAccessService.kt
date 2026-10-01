package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.auth.authz.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class RealtimeExchangeAccessService @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val userSessionService: UserSessionService,
    private val stepUpAuthService: StepUpAuthService,
)
{
    fun canSubscribe(appUserId: UUID, userSessionId: UUID, exchangeId: UUID): Boolean
    {
        val session = userSessionService.findSession(userSessionId) ?: return false
        val appUser = session.appUser?.takeIf { it.id == appUserId } ?: return false
        val decision = authorizationService.authorize(
            principal = PrincipalRef.user(appUserId),
            action = Action.EXCHANGE_VIEW,
            resource = ResourceRef.exchange(exchangeId),
            context = AuthorizationContext(
                actingUser = appUser,
                mfaSatisfied = stepUpAuthService.isFresh(userSessionId),
                clientIp = session.ipAddress,
                sessionRef = userSessionId.toString(),
            ),
        )
        return decision is Decision.Allow
    }
}
