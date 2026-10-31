package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.extension.maskEmailForLogs
import com.docuhyphen.app.api.service.communication.EmailService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.slf4j.LoggerFactory

@ApplicationScoped
class InformationRequestVerificationCodeMailer @Inject constructor(
    private val emailService: EmailService,
    private val synchronizations: TransactionSynchronizationRegistry,
) {
    fun sendAfterCommit(to: String, subject: String, body: String) {
        synchronizations.registerInterposedSynchronization(
            object : Synchronization {
                override fun beforeCompletion() = Unit

                override fun afterCompletion(status: Int) {
                    if (status != Status.STATUS_COMMITTED) {
                        return
                    }
                    runCatching { emailService.sendEmail(to, subject, body) }
                        .onFailure {
                            logger.warn(
                                "Failed to send a verification code to {}",
                                to.maskEmailForLogs(),
                                it
                            )
                        }
                }
            },
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestVerificationCodeMailer::class.java)
    }
}
