package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.SignUpRateLimitedException
import com.docuhyphen.app.api.exception.SignUpResendCooldownException
import com.docuhyphen.app.api.exception.SignUpVerificationRejectedException
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.notification.AppAdminNotificationService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mindrot.jbcrypt.BCrypt
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(AuthAbuseControlResource::class, restrictToAnnotatedClass = true)
class SignUpAbuseControlIntegrationTest
{
    @Inject
    lateinit var signUpService: SignUpService

    @Inject
    lateinit var dataSource: DataSource

    private lateinit var emailService: EmailService

    @BeforeEach
    fun setUp()
    {
        emailService = mock()
        val emailTemplateService = mock<EmailTemplateService>()
        val confirmationTokenService = mock<SignUpEmailConfirmationTokenService>()
        whenever(emailTemplateService.renderSignUpInitiationEmail(any(), any(), any(), any())).thenReturn("Verification")
        whenever(emailTemplateService.renderSignUpOtpRegenerationEmail(any(), any(), any())).thenReturn("New code")
        whenever(confirmationTokenService.issueToken(any(), any())).thenReturn("confirmation-token-for-tests-0000000000000")

        QuarkusMock.installMockForType(emailService, EmailService::class.java)
        QuarkusMock.installMockForType(emailTemplateService, EmailTemplateService::class.java)
        QuarkusMock.installMockForType(confirmationTokenService, SignUpEmailConfirmationTokenService::class.java)
        QuarkusMock.installMockForType(mock<AppAdminNotificationService>(), AppAdminNotificationService::class.java)
    }

    @Test
    fun `initiation above the client budget stops before any code or email`()
    {
        val clientIp = uniqueClientIp()
        val email = uniqueEmail()
        val rejectedEmail = uniqueEmail()

        repeat(CLIENT_BUDGET) { signUpService.initiateSignUp(email, clientIp, null) }

        assertThrows<SignUpRateLimitedException> { signUpService.initiateSignUp(rejectedEmail, clientIp, null) }
        assertFalse(signUpExists(rejectedEmail))
        verify(emailService, never()).sendEmail(eq(rejectedEmail), any(), any(), anyOrNull())
        verify(emailService, times(1)).sendEmail(eq(email), any(), any(), anyOrNull())
        assertEquals(1, signUpIncidentCount(clientIp))
    }

    @Test
    fun `many addresses from one origin are bounded`()
    {
        val clientIp = uniqueClientIp()
        val acceptedEmails = List(DISTINCT_ADDRESS_BUDGET) { uniqueEmail() }
        val rejectedEmail = uniqueEmail()

        acceptedEmails.forEach { signUpService.initiateSignUp(it, clientIp, null) }

        assertThrows<SignUpRateLimitedException> { signUpService.initiateSignUp(rejectedEmail, clientIp, null) }
        assertTrue(acceptedEmails.all(::signUpExists))
        assertFalse(signUpExists(rejectedEmail))
        verify(emailService, never()).sendEmail(eq(rejectedEmail), any(), any(), anyOrNull())
    }

    @Test
    fun `one address is bounded across origins and can retry after the cooldown`()
    {
        val email = uniqueEmail()
        insertSignUp(email)

        signUpService.regenerateOtp(email, uniqueClientIp(), null)
        assertThrows<SignUpResendCooldownException> { signUpService.regenerateOtp(email, uniqueClientIp(), null) }

        Thread.sleep(RESEND_COOLDOWN_MILLIS)

        assertDoesNotThrow { signUpService.regenerateOtp(email, uniqueClientIp(), null) }
        verify(emailService, times(2)).sendEmail(eq(email), eq(RESEND_SUBJECT), any(), anyOrNull())
    }

    @Test
    fun `resend gives the same answers for unknown registered and pending addresses`()
    {
        val unknownEmail = uniqueEmail()
        val registeredEmail = uniqueEmail()
        val pendingEmail = uniqueEmail()
        insertRegisteredAccount(registeredEmail)
        insertSignUp(pendingEmail)

        listOf(unknownEmail, registeredEmail, pendingEmail).forEach { email ->
            val clientIp = uniqueClientIp()
            assertDoesNotThrow { signUpService.regenerateOtp(email, clientIp, null) }
            assertThrows<SignUpResendCooldownException> { signUpService.regenerateOtp(email, clientIp, null) }
        }

        verify(emailService, never()).sendEmail(eq(unknownEmail), any(), any(), anyOrNull())
        verify(emailService, never()).sendEmail(eq(registeredEmail), any(), any(), anyOrNull())
        verify(emailService).sendEmail(eq(pendingEmail), eq(RESEND_SUBJECT), any(), anyOrNull())
    }

    @Test
    fun `initiation cannot extend another caller's resend cooldown`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        val resendAvailableAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESEND_COOLDOWN_MILLIS)

        signUpService.regenerateOtp(email, uniqueClientIp(), null)
        repeat(INITIATION_REPEATS) {
            Thread.sleep(INITIATION_INTERVAL_MILLIS)
            signUpService.initiateSignUp(email, uniqueClientIp(), null)
        }
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(resendAvailableAt - System.nanoTime()).coerceAtLeast(0))

        assertDoesNotThrow { signUpService.regenerateOtp(email, uniqueClientIp(), null) }
    }

    @Test
    fun `repeated initiation from other origins keeps the active code usable`()
    {
        val email = uniqueEmail()
        insertSignUp(email)

        repeat(INITIATION_REPEATS) { signUpService.initiateSignUp(email, uniqueClientIp(), null) }

        val account = signUpService.completeSignUp(email, ACTIVE_CODE, PASSWORD, PASSWORD, uniqueClientIp(), null)
        assertEquals(email, account.email)
        verify(emailService, never()).sendEmail(eq(email), eq(INITIATION_SUBJECT), any(), anyOrNull())
    }

    @Test
    fun `initiation followed by resend answers the same for unknown registered and pending addresses`()
    {
        val unknownEmail = uniqueEmail()
        val registeredEmail = uniqueEmail()
        val pendingEmail = uniqueEmail()
        insertRegisteredAccount(registeredEmail)
        insertSignUp(pendingEmail)

        val outcomes = listOf(unknownEmail, registeredEmail, pendingEmail).map { email ->
            signUpService.initiateSignUp(email, uniqueClientIp(), null)
            runCatching { signUpService.regenerateOtp(email, uniqueClientIp(), null) }.exceptionOrNull()?.javaClass
        }

        assertEquals(1, outcomes.distinct().size, "Outcomes: $outcomes")
    }

    @Test
    fun `rejected completion over budget still records a security incident`()
    {
        val clientIp = uniqueClientIp()
        val email = uniqueEmail()

        repeat(COMPLETION_BUDGET) {
            assertThrows<SignUpVerificationRejectedException> { complete(email, clientIp) }
        }

        assertThrows<SignUpRateLimitedException> { complete(email, clientIp) }
        assertEquals(1, signUpIncidentCount(clientIp))
    }

    private fun complete(email: String, clientIp: String) =
        signUpService.completeSignUp(email, "135791", PASSWORD, PASSWORD, clientIp, null)

    private fun uniqueEmail(): String = "abuse-control-${UUID.randomUUID()}@example.test"

    private fun uniqueClientIp(): String = "198.51.100.${++nextAddress}"

    private fun insertSignUp(email: String)
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """INSERT INTO sign_up
                   (id, email, otp, created_at, otp_expiry_timestamp, otp_attempts, status)
                   VALUES (?, ?, ?, ?, ?, 0, 'PENDING')""",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setString(2, email)
                statement.setString(3, BCrypt.hashpw(ACTIVE_CODE, BCrypt.gensalt(4)))
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()))
                statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)))
                statement.executeUpdate()
            }
        }
    }

    private fun insertRegisteredAccount(email: String)
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """INSERT INTO app_user
                   (id, is_active, created_date, email, email_verification_completed, is_temporary,
                    sign_in_attempts, exchange_version, multifactor_authentication_type,
                    is_password_temporary, email_mfa_fallback_enabled)
                   VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, email)
                statement.executeUpdate()
            }
        }
    }

    private fun signUpExists(email: String): Boolean =
        count("SELECT COUNT(*) FROM sign_up WHERE email = ?", email) > 0

    private fun signUpIncidentCount(clientIp: String): Int =
        count(
            "SELECT COUNT(*) FROM security_incident WHERE incident_type = 'AUTH_RATE_LIMIT_SIGN_UP' AND details LIKE ?",
            "%ip=$clientIp",
        )

    private fun count(sql: String, parameter: String): Int
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, parameter)
                statement.executeQuery().use { result ->
                    result.next()
                    return result.getInt(1)
                }
            }
        }
    }

    private companion object
    {
        const val CLIENT_BUDGET = 4
        const val COMPLETION_BUDGET = 2
        const val DISTINCT_ADDRESS_BUDGET = 3
        const val RESEND_COOLDOWN_MILLIS = 2_500L
        const val RESEND_SUBJECT = "Sign Up verification code"
        const val INITIATION_SUBJECT = "Sign Up Email Verification"
        const val ACTIVE_CODE = "246810"
        const val INITIATION_REPEATS = 4
        const val INITIATION_INTERVAL_MILLIS = 500L
        const val PASSWORD = "Synthetic-Passphrase-42!"
        var nextAddress = 0
    }
}
