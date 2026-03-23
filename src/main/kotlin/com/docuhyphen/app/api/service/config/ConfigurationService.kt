package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ConfigurationService
{
    @ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:5173")
    lateinit var baseUrl: String

    @ConfigProperty(name = "app.email.subject-title", defaultValue = "DocuHyphen")
    lateinit var emailSubjectTitle: String

    fun getMaxSignUpCompletionOtpAttempts(): Long = 3
    fun getSignUpOtpExpiryMins(): Long = 5 //
    fun getAppPhoneSubjectTitle() = emailSubjectTitle
    fun getSignInEmailOtpMFAExpiryMins(): Long = 5
    fun getSignInSmsOtpMFAExpiryMins(): Long = 5
    fun getSignInTokenExpiryHours(): Long = 1
    fun getMaxSignInAttempts(): Long = 3
    fun getPasswordResetOtpExpiryMins(): Long = 10
    fun getMaxOtpRequestsPerMinute() = 5L
    fun getJwtSecret() =
        "myverysecurekeythatis32byteslong*)&GAS&G_A(&F9*FDA(&_FD_A(&F+(D&FA" //ToDo: store in a secure location get from environment variable or AWS Secrets Manager

//    fun getJwtSecret(): String = System.getenv("JWT_SECRET") ?: throw IllegalStateException("JWT_SECRET not set")
}