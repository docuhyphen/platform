package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import com.securedocsshare.app.repository.AuthenticationRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RequestScoped
class SignOutService @Inject constructor(
    private val authenticationRepo: AuthenticationRepository,
    private val mfaService: MfaService,
    private val emailService: EmailService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
)
{
    fun signOut(user: AppUser)
    {
        TODO("Not yet implemented")
    }
}