package com.dochyphen.app.api.service.auth

import com.dochyphen.app.api.exception.AuthTokenNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.repository.AuthTokenRepository
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

    fun signOut()
    {
        val token = authTokenContext.authToken.token
        val authToken = authTokenRepository.findByToken(token!!)
            ?: throw AuthTokenNotFoundException()

        authTokenRepository.delete(authToken)
        logger.info("User signed out successfully.")
    }
}