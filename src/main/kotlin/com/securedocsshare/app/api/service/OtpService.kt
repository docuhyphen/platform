package com.securedocsshare.app.api.service

import jakarta.enterprise.context.ApplicationScoped
import org.mindrot.jbcrypt.BCrypt

@ApplicationScoped
class OtpService
{
    fun generateEmailOtp(): String
    {
//        return (100000..999999).random().toString()
        return "123456"
    }

    fun generateSmsOtp(): String
    {
        return (100000..999999).random().toString()
    }

    fun hashOtp(otp: String): String = BCrypt.hashpw(otp, BCrypt.gensalt())
}