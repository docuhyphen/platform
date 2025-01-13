package com.securedocsshare.app.service

import jakarta.enterprise.context.ApplicationScoped

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
}