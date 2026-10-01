package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.SignUpRateLimitedException
import com.docuhyphen.app.api.exception.SignUpResendCooldownException
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SignUpRequestGuard @Inject constructor(
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val securityIncidentService: SecurityIncidentService,
    private val authAuditService: AuthAuditService,
)
{
    fun enforceInitiationBudget(clientIp: String, requestId: String?)
    {
        enforceClientBudget(
            action = INITIATION_ACTION,
            operation = "initiation",
            clientIp = clientIp,
            maxPerMinute = configurationService.getAuthRateLimitSignUpPerMinute(),
            requestId = requestId,
        )
    }

    fun enforceInitiationAddressBudget(clientIp: String, email: String, requestId: String?)
    {
        enforceDistinctAddressBudget(INITIATION_ACTION, clientIp, email, requestId)
    }

    fun enforceResendBudget(clientIp: String, requestId: String?)
    {
        enforceClientBudget(
            action = RESEND_ACTION,
            operation = "otp-regeneration",
            clientIp = clientIp,
            maxPerMinute = configurationService.getAuthRateLimitSignUpPerMinute(),
            requestId = requestId,
        )
    }

    fun enforceResendAddressBudget(clientIp: String, email: String, requestId: String?)
    {
        enforceDistinctAddressBudget(RESEND_ACTION, clientIp, email, requestId)

        val remainingSeconds = authRateLimitService.claimCooldown(
            resendCooldownKey(email),
            configurationService.getSignUpResendCooldownSeconds(),
        )

        if (remainingSeconds > 0)
        {
            throw SignUpResendCooldownException(remainingSeconds)
        }
    }

    fun enforceCompletionBudget(clientIp: String, requestId: String?)
    {
        enforceClientBudget(
            action = COMPLETION_ACTION,
            operation = "completion",
            clientIp = clientIp,
            maxPerMinute = configurationService.getAuthRateLimitSignUpCompletionPerMinute(),
            requestId = requestId,
        )
    }

    private fun enforceClientBudget(
        action: String,
        operation: String,
        clientIp: String,
        maxPerMinute: Long,
        requestId: String?,
    )
    {
        if (!authRateLimitService.isLimited(key = "auth:sign-up:$operation:$clientIp", maxPerMinute = maxPerMinute))
        {
            return
        }

        reject(action = action, requestId = requestId, details = "endpoint=sign-up-$operation;ip=$clientIp")
    }

    private fun enforceDistinctAddressBudget(action: String, clientIp: String, email: String, requestId: String?)
    {
        val distinctAddresses = authRateLimitService.countDistinct(
            key = "auth:sign-up:distinct:$clientIp",
            member = AuthThrottleKey.hash(email),
            windowSeconds = DISTINCT_ADDRESS_WINDOW_SECONDS,
        )

        if (distinctAddresses <= configurationService.getAuthRateLimitSignUpDistinctAddresses())
        {
            return
        }

        reject(
            action = action,
            requestId = requestId,
            details = "ip=$clientIp;reason=distinct_address_budget;distinctAddresses=$distinctAddresses",
        )
    }

    private fun resendCooldownKey(email: String): String = "auth:sign-up:resend:${AuthThrottleKey.hash(email)}"

    private fun reject(action: String, requestId: String?, details: String): Nothing
    {
        QuarkusTransaction.requiringNew().run {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGN_UP,
                severity = SecurityIncidentSeverity.MEDIUM,
                requestId = requestId,
                details = details,
            )
        }
        authAuditService.emit(
            action = action,
            outcome = "DENY",
            reasonCode = RevocationReasonCode.SECURITY_POLICY,
            requestId = requestId,
        )
        throw SignUpRateLimitedException()
    }

    private companion object
    {
        const val INITIATION_ACTION = "SIGN_UP_INITIATE"
        const val RESEND_ACTION = "SIGN_UP_OTP_REGENERATION"
        const val COMPLETION_ACTION = "SIGN_UP_COMPLETION"
        const val DISTINCT_ADDRESS_WINDOW_SECONDS = 600L
    }
}
