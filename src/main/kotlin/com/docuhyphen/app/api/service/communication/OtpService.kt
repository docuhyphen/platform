package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import org.mindrot.jbcrypt.BCrypt

@ApplicationScoped
class OtpService
{
    fun generateEmailOtp(): String
    {
        return (100000..999999).random().toString()
    }


    fun generateSmsOtp(): String
    {
        return (100000..999999).random().toString()
    }

    fun generatePhoneVerificationCode(): String
    {
        return (100000..999999).random().toString()
    }

    fun hashOtp(otp: String): String = BCrypt.hashpw(otp, BCrypt.gensalt())

    fun verifyEmailOtp(plainOtp: String, hashedOtp: String): Boolean = BCrypt.checkpw(plainOtp, hashedOtp)
}