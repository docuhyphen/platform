package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InvalidSignUpConfirmationTokenException
import com.docuhyphen.app.api.exception.PasswordRequirementsNotMetException
import com.docuhyphen.app.api.exception.SignUpVerificationBusyException
import com.docuhyphen.app.api.exception.SignUpVerificationRejectedException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.notification.AppAdminNotificationService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.transaction.UserTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mindrot.jbcrypt.BCrypt
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
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
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(AuthPostgreSQLResource::class, restrictToAnnotatedClass = true)
class SignUpCompletionIntegrationTest
{
    @Inject
    lateinit var signUpService: SignUpService

    @Inject
    lateinit var configurationService: ConfigurationService

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var transaction: UserTransaction

    private lateinit var otpService: OtpService
    private lateinit var emailService: EmailService
    private lateinit var emailTemplateService: EmailTemplateService
    private lateinit var confirmationTokenService: SignUpEmailConfirmationTokenService

    private val maxAttempts: Int
        get() = configurationService.getMaxSignUpCompletionOtpAttempts().toInt()

    @BeforeEach
    fun setUp()
    {
        otpService = slowCodeCheckingOtpService()
        emailService = mock()
        emailTemplateService = mock()
        confirmationTokenService = mock()
        whenever(emailTemplateService.renderSignUpInitiationEmail(any(), any(), any(), any())).thenReturn("Verification")
        whenever(emailTemplateService.renderSignUpOtpRegenerationEmail(any(), any(), any())).thenReturn("New code")
        whenever(emailTemplateService.renderSignUpCompletionEmail(any())).thenReturn("Completed")
        whenever(confirmationTokenService.issueToken(any(), any())).thenReturn(CONFIRMATION_TOKEN)

        QuarkusMock.installMockForType(otpService, OtpService::class.java)
        QuarkusMock.installMockForType(emailService, EmailService::class.java)
        QuarkusMock.installMockForType(emailTemplateService, EmailTemplateService::class.java)
        QuarkusMock.installMockForType(confirmationTokenService, SignUpEmailConfirmationTokenService::class.java)
        QuarkusMock.installMockForType(mock<AppAdminNotificationService>(), AppAdminNotificationService::class.java)
    }

    @Test
    fun `wrong code is still counted after the completion request fails`()
    {
        val email = uniqueEmail()
        insertSignUp(email)

        val failure = runCatching { complete(email, WRONG_CODE) }.exceptionOrNull()

        assertEquals(1, storedAttempts(email))
        assertTrue(failure is SignUpVerificationRejectedException, "Unexpected failure: $failure")
    }

    @Test
    fun `correct code is rejected once the attempt budget is exhausted`()
    {
        val email = uniqueEmail()
        insertSignUp(email)

        repeat(maxAttempts) { runCatching { complete(email, WRONG_CODE) } }

        assertThrows<SignUpVerificationRejectedException> { complete(email, CODE) }
        assertEquals(maxAttempts, storedAttempts(email))
        assertEquals(0, registeredAccountCount(email))
    }

    @Test
    fun `valid code within the budget completes signup exactly once`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        runCatching { complete(email, WRONG_CODE) }

        val account = complete(email, CODE)

        assertEquals(email, account.email)
        assertEquals("VERIFIED", storedStatus(email))
        assertThrows<SignUpVerificationRejectedException> { complete(email, CODE) }
        assertEquals(1, registeredAccountCount(email))
    }

    @Test
    fun `parallel wrong codes cannot share one remaining attempt`()
    {
        val email = uniqueEmail()
        insertSignUp(email)

        val failures = runConcurrently(PARALLEL_REQUESTS) { complete(email, WRONG_CODE) }

        val persistedAttempts = storedAttempts(email)
        assertTrue(persistedAttempts in 1..maxAttempts, "Persisted attempts: $persistedAttempts")
        assertEquals(persistedAttempts, otpService.checkedCodeCount())
        assertTrue(failures.all { it is SignUpVerificationRejectedException || it is SignUpVerificationBusyException },
            "Unexpected failures: $failures")
    }

    @Test
    fun `parallel valid codes create one account`()
    {
        val email = uniqueEmail()
        insertSignUp(email)

        val failures = runConcurrently(PARALLEL_REQUESTS) { complete(email, CODE) }

        assertEquals(1, registeredAccountCount(email))
        assertEquals(PARALLEL_REQUESTS - 1, failures.size)
        assertTrue(failures.all { it is SignUpVerificationRejectedException || it is SignUpVerificationBusyException },
            "Unexpected failures: $failures")
    }

    @Test
    fun `unverified completion gives the same answer for unknown registered and pending addresses`()
    {
        val unknownEmail = uniqueEmail()
        val registeredEmail = uniqueEmail()
        val pendingEmail = uniqueEmail()
        insertAccount(registeredEmail, temporary = false)
        insertSignUp(pendingEmail)

        val outcomes = listOf(unknownEmail, registeredEmail, pendingEmail).map { email ->
            runCatching { complete(email, WRONG_CODE) }.exceptionOrNull()
        }

        assertTrue(outcomes.all { it is SignUpVerificationRejectedException }, "Outcomes: $outcomes")
        assertEquals(1, outcomes.map { it?.message }.distinct().size)
    }

    @Test
    fun `completion endpoint answers unknown registered and pending addresses identically`()
    {
        val registeredEmail = uniqueEmail()
        val pendingEmail = uniqueEmail()
        insertAccount(registeredEmail, temporary = false)
        insertSignUp(pendingEmail)

        val responses = listOf(uniqueEmail(), registeredEmail, pendingEmail).map { email ->
            val response = given()
                .contentType("application/json")
                .body("""{"email":"$email","otp":"$WRONG_CODE","password":"$PASSWORD","confirmationPassword":"$PASSWORD"}""")
                .post("/auth/sign-up/completion")
            response.statusCode to response.body.asString()
        }

        assertEquals(1, responses.distinct().size, "Responses: $responses")
        assertEquals(400, responses.first().first)
    }

    @Test
    fun `a newly issued code starts with a fresh attempt budget`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        repeat(maxAttempts) { runCatching { complete(email, WRONG_CODE) } }
        expireCode(email)

        signUpService.initiateSignUp(email, CLIENT_IP, null)

        val issuedCode = argumentCaptor<String>()
        verify(emailTemplateService).renderSignUpInitiationEmail(eq(email), issuedCode.capture(), eq(CONFIRMATION_TOKEN), any())
        assertEquals(0, storedAttempts(email))
        assertEquals(email, complete(email, issuedCode.firstValue).email)
    }

    @Test
    fun `invited recipient can request a new code and complete signup`()
    {
        val email = uniqueEmail()
        val placeholderId = insertAccount(email, temporary = true)
        insertSignUp(email)

        signUpService.regenerateOtp(email, CLIENT_IP, null)

        val issuedCode = argumentCaptor<String>()
        verify(emailTemplateService).renderSignUpOtpRegenerationEmail(issuedCode.capture(), eq(CONFIRMATION_TOKEN), any())
        val account = complete(email, issuedCode.firstValue)
        assertEquals(placeholderId, account.id)
        assertFalse(account.isTemporary)
        assertEquals(1, registeredAccountCount(email))
    }

    @Test
    fun `weak password leaves the confirmation link usable for a retry`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        whenever(confirmationTokenService.peekToken(CONFIRMATION_TOKEN)).thenReturn(email)

        assertThrows<PasswordRequirementsNotMetException> { completeViaLink(WEAK_PASSWORD) }
        verify(confirmationTokenService, never()).revokeToken(any())

        assertEquals(email, completeViaLink(PASSWORD).email)
        verify(confirmationTokenService).revokeToken(CONFIRMATION_TOKEN)
    }

    @Test
    fun `invalid or expired link cannot complete signup`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        expireCode(email)

        assertThrows<InvalidSignUpConfirmationTokenException> { completeViaLink(PASSWORD) }

        whenever(confirmationTokenService.peekToken(CONFIRMATION_TOKEN)).thenReturn(email)
        assertThrows<InvalidSignUpConfirmationTokenException> { completeViaLink(PASSWORD) }
        assertEquals(0, registeredAccountCount(email))
    }

    @Test
    fun `parallel link submissions create one account`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        whenever(confirmationTokenService.peekToken(CONFIRMATION_TOKEN)).thenReturn(email)

        val failures = runConcurrently(PARALLEL_REQUESTS) { completeViaLink(PASSWORD) }

        assertEquals(1, registeredAccountCount(email))
        assertEquals(PARALLEL_REQUESTS - 1, failures.size)
        assertTrue(failures.all { it is InvalidSignUpConfirmationTokenException }, "Unexpected failures: $failures")
        verify(confirmationTokenService, times(1)).revokeToken(CONFIRMATION_TOKEN)
    }

    @Test
    fun `rolled back link completion keeps the link usable and sends no completion email`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        whenever(confirmationTokenService.peekToken(CONFIRMATION_TOKEN)).thenReturn(email)

        transaction.begin()
        completeViaLink(PASSWORD)
        transaction.rollback()

        assertEquals(0, registeredAccountCount(email))
        verify(confirmationTokenService, never()).revokeToken(any())
        verify(emailService, never()).sendEmail(eq(email), eq(COMPLETION_SUBJECT), any(), anyOrNull())

        assertEquals(email, completeViaLink(PASSWORD).email)
        verify(confirmationTokenService).revokeToken(CONFIRMATION_TOKEN)
        verify(emailService).sendEmail(eq(email), eq(COMPLETION_SUBJECT), any(), anyOrNull())
    }

    @Test
    fun `completion email failure does not undo signup`()
    {
        val email = uniqueEmail()
        insertSignUp(email)
        doThrow(IllegalStateException("Email delivery unavailable"))
            .whenever(emailService).sendEmail(eq(email), eq(COMPLETION_SUBJECT), any(), anyOrNull())

        assertEquals(email, complete(email, CODE).email)
        assertEquals(1, registeredAccountCount(email))
    }

    private fun complete(email: String, code: String): AppUser =
        signUpService.completeSignUp(email, code, PASSWORD, PASSWORD, CLIENT_IP, null)

    private fun completeViaLink(password: String): AppUser =
        signUpService.completeSignUpViaToken(CONFIRMATION_TOKEN, password, password, CLIENT_IP, null)

    private fun uniqueEmail(): String = "sign-up-${UUID.randomUUID()}@example.test"

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
                statement.setString(3, BCrypt.hashpw(CODE, BCrypt.gensalt(4)))
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()))
                statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)))
                statement.executeUpdate()
            }
        }
    }

    private fun expireCode(email: String)
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE sign_up SET otp_expiry_timestamp = ? WHERE email = ?",
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)))
                statement.setString(2, email)
                statement.executeUpdate()
            }
        }
    }

    private fun insertAccount(email: String, temporary: Boolean): UUID
    {
        val appUserId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """INSERT INTO app_user
                   (id, is_active, created_date, email, email_verification_completed, is_temporary,
                    sign_in_attempts, exchange_version, multifactor_authentication_type,
                    is_password_temporary, email_mfa_fallback_enabled)
                   VALUES (?, ?, ?, ?, ?, ?, 0, 0, 'EMAIL', FALSE, FALSE)""",
            ).use { statement ->
                statement.setObject(1, appUserId)
                statement.setBoolean(2, !temporary)
                statement.setTimestamp(3, Timestamp.from(Instant.now()))
                statement.setString(4, email)
                statement.setBoolean(5, !temporary)
                statement.setBoolean(6, temporary)
                statement.executeUpdate()
            }
        }
        return appUserId
    }

    private fun storedAttempts(email: String): Int =
        querySignUp(email) { it.getInt("otp_attempts") }

    private fun storedStatus(email: String): String =
        querySignUp(email) { it.getString("status") }

    private fun <T> querySignUp(email: String, read: (java.sql.ResultSet) -> T): T
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT otp_attempts, status FROM sign_up WHERE email = ?").use { statement ->
                statement.setString(1, email)
                statement.executeQuery().use { result ->
                    check(result.next()) { "No signup record for $email" }
                    return read(result)
                }
            }
        }
    }

    private fun registeredAccountCount(email: String): Int
    {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) FROM app_user WHERE LOWER(email) = LOWER(?) AND is_temporary = FALSE",
            ).use { statement ->
                statement.setString(1, email)
                statement.executeQuery().use { result ->
                    result.next()
                    return result.getInt(1)
                }
            }
        }
    }

    private companion object
    {
        const val CODE = "246810"
        const val WRONG_CODE = "135791"
        const val PASSWORD = "Synthetic-Passphrase-42!"
        const val WEAK_PASSWORD = "weak"
        const val CONFIRMATION_TOKEN = "confirmation-token-for-tests-0000000000000"
        const val COMPLETION_SUBJECT = "Account Created Successfully"
        const val CLIENT_IP = "192.0.2.50"
        const val PARALLEL_REQUESTS = 8
    }
}
