package com.securedocsshare.app.api.service

import jakarta.enterprise.context.RequestScoped

@RequestScoped
class ConfigurationService
{
    fun getAppBaseURL(): String
    {
        return "https://securedocsshare.com"
    }

    fun getMaxSignUpCompletionOtpAttempts(): Long = 3
    fun getSignUpOtpExpiryMins(): Long = 5 //
    fun getAppEmailSubjectTitle() = "Secure Doc Share"
    fun getSignInEmailOtpMFAExpiryMins(): Long = 5
    fun getSignInSmsOtpMFAExpiryMins(): Long = 5
    fun getSignInTokenExpiryHours(): Long = 1
    fun getMaxSignInAttempts(): Long = 3
    fun getPasswordResetOtpExpiryMins(): Long = 10
    fun getJwtSecret() = "myverysecurekeythatis32byteslong" //ToDo: store in a secure location get from environment variable or AWS Secrets Manager
//    fun getJwtSecret(): String = System.getenv("JWT_SECRET") ?: throw IllegalStateException("JWT_SECRET not set")
}