package com.docuhyphen.app.api.service.user

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.AuthenticatorEnrollmentDto
import com.docuhyphen.app.api.model.dto.MfaConfigurationDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.service.auth.AuthenticatorMfaService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AppUserMfaService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authenticatorMfaService: AuthenticatorMfaService,
)
{
    fun getConfiguration(): MfaConfigurationDto =
        authenticatorMfaService.getConfiguration(currentUser())

    fun startEnrollment(provider: String?): AuthenticatorEnrollmentDto =
        authenticatorMfaService.startEnrollment(currentUser(), provider)

    fun completeEnrollment(enrollmentId: String, code: String?, emailFallbackEnabled: Boolean): MfaConfigurationDto =
        authenticatorMfaService.completeEnrollment(
            currentUser(),
            UUID.fromString(enrollmentId),
            code,
            emailFallbackEnabled,
        )

    fun updateEmailFallback(enabled: Boolean): MfaConfigurationDto =
        authenticatorMfaService.updateEmailFallback(currentUser(), enabled)

    fun removeAuthenticator(): MfaConfigurationDto =
        authenticatorMfaService.removeAuthenticator(currentUser())

    private fun currentUser(): AppUser =
        authTokenContext.authToken.appUser ?: throw UnauthorizedException("Authentication is required.")
}
