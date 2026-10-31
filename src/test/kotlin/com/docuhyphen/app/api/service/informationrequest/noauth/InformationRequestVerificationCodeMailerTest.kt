package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.service.communication.EmailService
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.*

class InformationRequestVerificationCodeMailerTest {
    private val emailService = mock<EmailService>()
    private val registered = mutableListOf<Synchronization>()
    private val synchronizations = mock<TransactionSynchronizationRegistry>().also { registry ->
        doAnswer { registered += it.getArgument<Synchronization>(0) }
            .whenever(registry).registerInterposedSynchronization(any())
    }
    private val mailer = InformationRequestVerificationCodeMailer(emailService, synchronizations)

    @Test
    fun `a code is sent only once the transaction that issued it commits`() {
        mailer.sendAfterCommit(RECIPIENT, SUBJECT, BODY)
        verify(emailService, never()).sendEmail(any(), any(), any(), any())

        registered.single().afterCompletion(Status.STATUS_COMMITTED)

        verify(emailService).sendEmail(RECIPIENT, SUBJECT, BODY, false)
    }

    @Test
    fun `a code issued by a rolled back transaction is never sent`() {
        mailer.sendAfterCommit(RECIPIENT, SUBJECT, BODY)

        registered.single().afterCompletion(Status.STATUS_ROLLEDBACK)

        verify(emailService, never()).sendEmail(any(), any(), any(), any())
    }

    @Test
    fun `a delivery failure after commit does not escape the finished transaction`() {
        doThrow(IllegalStateException("mail service unavailable"))
            .whenever(emailService).sendEmail(any(), any(), any(), any())
        mailer.sendAfterCommit(RECIPIENT, SUBJECT, BODY)

        assertDoesNotThrow { registered.single().afterCompletion(Status.STATUS_COMMITTED) }
    }

    private companion object {
        const val RECIPIENT = "respondent@process.test"
        const val SUBJECT = "Verify your access to this request"
        const val BODY = "Your verification code is 123456."
    }
}

internal fun committingSynchronizations(): TransactionSynchronizationRegistry =
    mock<TransactionSynchronizationRegistry>().also { registry ->
        doAnswer { it.getArgument<Synchronization>(0).afterCompletion(Status.STATUS_COMMITTED) }
            .whenever(registry).registerInterposedSynchronization(any())
    }
