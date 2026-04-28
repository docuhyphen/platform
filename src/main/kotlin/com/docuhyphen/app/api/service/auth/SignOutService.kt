package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@RequestScoped
class SignOutService @Inject constructor(
    private val refreshTokenStore: RefreshTokenStore,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutService::class.java)
    }

    fun signOut(outOfAllDevices: Boolean = false)
    {
        val appUser = authTokenContext.authToken.appUser!!

        if (outOfAllDevices)
        {
            logger.info("Signing out from all devices for user={}", appUser.id)
            refreshTokenStore.deleteAllByUserId(appUser.id)
            return
        }

        logger.info("Signing out from the current device for user={}", appUser.id)
        // Access tokens are stateless and short-lived — no server-side revocation needed.
        // The frontend clears its tokens and the refresh cookie is cleared by the resource.
        logger.info("User signed out successfully.")
    }
}