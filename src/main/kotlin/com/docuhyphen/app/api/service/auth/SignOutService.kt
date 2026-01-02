package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.AuthTokenNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.repository.AuthTokenRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@RequestScoped
class SignOutService @Inject constructor(
    private val authTokenRepository: AuthTokenRepository,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutService::class.java)
    }

    fun signOut(outOfAllDevices: Boolean = false)
    {
        if (outOfAllDevices)
        {
            logger.info("Signing out from all devices.")
            authTokenRepository.deleteAllByUserId(authTokenContext.authToken.appUser!!.id)

            //ToDo: send web socket notification to all devices
            return
        }

        logger.info("Signing out from the current device.")

        val token = authTokenContext.authToken.token
        val authToken = authTokenRepository.findByToken(token!!)
            ?: throw AuthTokenNotFoundException()

        authTokenRepository.delete(authToken)
        logger.info("User signed out successfully.")
    }
}