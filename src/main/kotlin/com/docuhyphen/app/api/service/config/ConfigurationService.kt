package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ConfigurationService @Inject constructor(
    private val awsSecretsManagerService: AwsSecretsManagerService,

    @ConfigProperty(name = "app.base-url")
    val baseUrl: String,

    @ConfigProperty(name = "app.email.subject-title")
    val emailSubjectTitle: String,

    @ConfigProperty(name = "app.security.jwt.secret-provider")
    val jwtSecretProvider: String,

    @ConfigProperty(name = "app.security.jwt.local-secret")
    val localJwtSecret: String,

    @ConfigProperty(name = "app.security.aws.region")
    val awsRegion: String,

    @ConfigProperty(name = "app.security.jwt.aws-secret-id")
    val jwtAwsSecretId: String?,
)
{
    @Volatile
    private var cachedJwtSecret: String? = null

    fun getMaxSignUpCompletionOtpAttempts(): Long = 3
    fun getSignUpOtpExpiryMins(): Long = 5 //
    fun getAppPhoneSubjectTitle() = emailSubjectTitle
    fun getSignInEmailOtpMFAExpiryMins(): Long = 5
    fun getSignInSmsOtpMFAExpiryMins(): Long = 5
    fun getSignInTokenExpiryHours(): Long = 1
    fun getMaxSignInAttempts(): Long = 3
    fun getPasswordResetOtpExpiryMins(): Long = 10
    fun getMaxOtpRequestsPerMinute() = 5L
    fun getSignInResendCooldownSeconds(): Long = 30

    fun getJwtSecret(): String
    {
        cachedJwtSecret?.let { return it }

        val resolvedSecret = if (jwtSecretProvider.equals("aws", ignoreCase = true))
        {
            val secretId = jwtAwsSecretId?.trim().orEmpty()

            if (secretId.isBlank())
            {
                throw IllegalStateException("app.security.jwt.aws-secret-id is required when JWT secret provider is aws")
            }

            awsSecretsManagerService.getSecretString(secretId, awsRegion)
        }
        else
        {
            localJwtSecret
        }

        if (resolvedSecret.isBlank())
        {
            throw IllegalStateException("JWT secret cannot be blank")
        }

        cachedJwtSecret = resolvedSecret
        return resolvedSecret
    }
}