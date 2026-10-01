package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InvalidOtpException
import com.docuhyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(AuthPostgreSQLResource::class, restrictToAnnotatedClass = true)
class SignInVerificationAttemptIntegrationTest
{
    @Inject
    lateinit var signInService: SignInService

    @Inject
    lateinit var configurationService: ConfigurationService

    @Inject
    lateinit var dataSource: DataSource

    private lateinit var otpService: OtpService

    @BeforeEach
    fun setUp()
    {
        otpService = slowCodeCheckingOtpService()
        QuarkusMock.installMockForType(otpService, OtpService::class.java)
    }

    @Test
    fun `parallel wrong sign-in codes cannot share one remaining attempt`()
    {
        val email = "sign-in-${UUID.randomUUID()}@example.test"
        val sessionId = UUID.randomUUID().toString()
        insertPendingEmailChallenge(email, sessionId)

        val failures = runConcurrently(PARALLEL_REQUESTS) {
            signInService.completeSignIn(email, WRONG_CODE, sessionId)
        }

        val checkedCodes = otpService.checkedCodeCount()
        val maxAttempts = configurationService.getMaxSignInAttempts().toInt()
        assertTrue(checkedCodes in 1..maxAttempts, "Checked codes: $checkedCodes")
        assertEquals(PARALLEL_REQUESTS, failures.size)
        assertTrue(failures.all { it is InvalidOtpException || it is MaxAttemptsOTPExceededException },
            "Unexpected failures: $failures")
    }

    private fun insertPendingEmailChallenge(email: String, sessionId: String)
    {
        val appUserId = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """INSERT INTO app_user
                   (id, is_active, created_date, email, email_verification_completed, is_temporary,
                    sign_in_attempts, exchange_version, multifactor_authentication_type,
                    is_password_temporary, email_mfa_fallback_enabled)
                   VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
            ).use { statement ->
                statement.setObject(1, appUserId)
                statement.setTimestamp(2, now)
                statement.setString(3, email)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """INSERT INTO mfa_record
                   (id, app_user_id, created_date, expiry_date, mfa_token, mfa_type, status,
                    attempt_count, exchange_id, ip_address)
                   VALUES (?, ?, ?, ?, ?, 'EMAIL', 'PENDING', 0, ?, '192.0.2.20')""",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, appUserId)
                statement.setTimestamp(3, now)
                statement.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(300)))
                statement.setString(5, BCrypt.hashpw(CODE, BCrypt.gensalt(4)))
                statement.setString(6, sessionId)
                statement.executeUpdate()
            }
        }
    }

    private companion object
    {
        const val CODE = "246810"
        const val WRONG_CODE = "135791"
        const val PARALLEL_REQUESTS = 8
    }
}
