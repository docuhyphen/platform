package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom

@ApplicationScoped
class OtpService
{
    companion object
    {
        private val secureRandom = SecureRandom()
    }

    fun generateEmailOtp(): String
    {
        return generateSixDigitOtp()
    }


    fun generateSmsOtp(): String
    {
        return generateSixDigitOtp()
    }

    fun generatePhoneVerificationCode(): String
    {
        return generateSixDigitOtp()
    }

    private fun generateSixDigitOtp(): String
    {
        return (secureRandom.nextInt(900000) + 100000).toString()
    }

    fun hashOtp(otp: String): String = BCrypt.hashpw(otp, BCrypt.gensalt())

    fun verifyEmailOtp(plainOtp: String, hashedOtp: String): Boolean = BCrypt.checkpw(plainOtp, hashedOtp)
}