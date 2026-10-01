package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.extension.maskEmailForLogs
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.slf4j.LoggerFactory

@ApplicationScoped
class SignUpCompletionFollowUpService @Inject constructor(
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val signUpEmailConfirmationTokenService: SignUpEmailConfirmationTokenService,
    private val transactionSynchronizationRegistry: TransactionSynchronizationRegistry,
)
{
    fun scheduleAfterCommit(email: String, confirmationToken: String?)
    {
        transactionSynchronizationRegistry.registerInterposedSynchronization(
            object : Synchronization
            {
                override fun beforeCompletion() = Unit

                override fun afterCompletion(status: Int)
                {
                    if (status != Status.STATUS_COMMITTED)
                    {
                        return
                    }
                    confirmationToken?.let(::revokeConfirmationToken)
                    sendCompletionEmail(email)
                }
            },
        )
    }

    private fun revokeConfirmationToken(confirmationToken: String)
    {
        runCatching { signUpEmailConfirmationTokenService.revokeToken(confirmationToken) }
            .onFailure { logger.warn("Failed to revoke a used sign-up confirmation link", it) }
    }

    private fun sendCompletionEmail(email: String)
    {
        runCatching {
            emailService.sendEmail(
                to = email,
                subject = "Account Created Successfully",
                body = emailTemplateService.renderSignUpCompletionEmail(email),
                useHtml = true,
            )
        }.onFailure { logger.warn("Failed to send the sign-up completion email to {}", email.maskEmailForLogs(), it) }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(SignUpCompletionFollowUpService::class.java)
    }
}
