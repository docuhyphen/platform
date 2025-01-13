package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.AuthToken
import com.securedocsshare.app.api.model.AuthTokenExpiredException
import com.securedocsshare.app.api.model.AuthTokenInvalidException
import com.securedocsshare.app.api.model.AuthTokenNotFoundException
import com.securedocsshare.app.api.model.AuthTokenNotProvidedException
import com.securedocsshare.app.repository.AuthTokenRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RequestScoped
class AuthenticationService @Inject constructor(
    private val authTokenRepository: AuthTokenRepository,
    private val mfaService: MfaService,
    private val emailService: EmailService,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        val logger = LoggerFactory.getLogger(AuthenticationService::class.java.name)
    }

    fun generatePasswordSalt(length: Int = 16): ByteArray
    {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hashPassword(password: String, salt: ByteArray): String
    {
        val hmacSHA256 = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(salt, "HmacSHA256")
        hmacSHA256.init(secretKey)
        val hash = hmacSHA256.doFinal(password.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    fun validatePassword(inputPassword: String, storedHash: String, storedSalt: ByteArray): Boolean
    {
        val hashedInput = hashPassword(inputPassword, storedSalt)
        return hashedInput == storedHash
    }

    fun isValidEmail(email: String): Boolean
    {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }

    fun isEmailInvalid(email: String): Boolean
    {
        return !isValidEmail(email)
    }

    fun isPasswordStrong(password: String): Boolean
    {
        val MIN_LENGTH = 8
        val MAX_LENGTH = 20
        val UPPERCASE_REGEX = Regex(".*[A-Z].*")
        val LOWERCASE_REGEX = Regex(".*[a-z].*")
        val DIGIT_REGEX = Regex(".*\\d.*")
        val SPECIAL_CHAR_REGEX = Regex(""".*[!@#\$%^&*()_+\-=\[\]{};':"\\|,.<>/?].*""")

        if (password.isEmpty())
        {
            return false // Null or empty passwords are invalid
        }
        if (password.length < MIN_LENGTH || password.length > MAX_LENGTH)
        {
            return false // Check length
        }
        if (!password.contains(UPPERCASE_REGEX))
        {
            return false // Must contain at least one uppercase letter
        }
        if (!password.contains(LOWERCASE_REGEX))
        {
            return false // Must contain at least one lowercase letter
        }
        if (!password.contains(DIGIT_REGEX))
        {
            return false // Must contain at least one digit
        }
        if (!password.contains(SPECIAL_CHAR_REGEX))
        {
            return false // Must contain at least one special character
        }

        return true // Password meets all requirements
    }

    fun authenticateToken(token: String?): AuthToken?
    {
        if (token.isNullOrBlank())
        {
            logger.warn("Failed to get auth token: token is null or blank")
            return null
        }

        val secret = configurationService.getJwtSecret()
        val key = Keys.hmacShaKeyFor(secret.toByteArray())

        return try
        {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            val userId = UUID.fromString(claims.subject)
            val appUser = appUserService.getAppUserById(userId)

            if (appUser == null)
            {
                throw AuthTokenNotFoundException()
            }

            AuthToken().apply {
                this.appUser = appUser
                this.token = token
            }
        }
        catch (e: Exception)
        {
            logger.warn("Invalid token: ${e.message}")
            throw AuthTokenInvalidException()
        }
    }

    fun refreshToken(oldToken: String): String
    {
        val secret = configurationService.getJwtSecret()
        val key = Keys.hmacShaKeyFor(secret.toByteArray())

        val claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(oldToken)
            .body

        val newExpiration =
            Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(configurationService.getSignInTokenExpiryHours()))

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(Date())
            .setExpiration(newExpiration)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    }

    fun generateSignInToken(appUser: AppUser): String
    {
        val secret = configurationService.getJwtSecret()
        val tokenExpiryHrs = configurationService.getSignInTokenExpiryHours()

        val key = Keys.hmacShaKeyFor(secret.toByteArray())
        val expiration = Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(tokenExpiryHrs))

        return Jwts.builder()
            .setSubject(appUser.id.toString())
            .claim("email", appUser.email)
            .setIssuedAt(Date())
            .setExpiration(expiration)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
}
