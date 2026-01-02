package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.AuthTokenInvalidException
import com.docuhyphen.app.api.exception.AuthTokenNotFoundException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.repository.AuthTokenRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

@RequestScoped
class AuthenticationService @Inject constructor(
    private val authTokenRepository: AuthTokenRepository,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
)
{
    val jwtSecretKey: SecretKey = Keys.hmacShaKeyFor(configurationService.getJwtSecret().toByteArray())

    companion object
    {
        val logger = LoggerFactory.getLogger(AuthenticationService::class.java.name)
    }

    fun generatePasswordSalt(): String = BCrypt.gensalt()

    fun hashPassword(password: String, salt: String): String = BCrypt.hashpw(password, salt)

    fun validatePassword(inputPassword: String, storedHash: String): Boolean = BCrypt.checkpw(inputPassword, storedHash)

    fun isValidEmail(email: String): Boolean
    {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }

    fun isEmailInvalid(email: String): Boolean = !isValidEmail(email)

    fun isPasswordStrong(password: String): Boolean
    {
        val MIN_LENGTH = 8
        val MAX_LENGTH = 30
        val UPPERCASE_REGEX = Regex(".*[A-Z].*")
        val LOWERCASE_REGEX = Regex(".*[a-z].*")
        val DIGIT_REGEX = Regex(".*\\d.*")
        val SPECIAL_CHAR_REGEX = Regex(""".*[!@#\$%^&*()_+\-=\[\]{};':"\\|,.<>/?].*""")

        if (password.isEmpty()) return false
        if (password.length < MIN_LENGTH || password.length > MAX_LENGTH) return false
        if (!password.contains(UPPERCASE_REGEX)) return false
        if (!password.contains(LOWERCASE_REGEX)) return false
        if (!password.contains(DIGIT_REGEX)) return false
        if (!password.contains(SPECIAL_CHAR_REGEX)) return false

        return true
    }

    fun generateSignInToken(appUser: AppUser): String
    {
        val tokenExpiryHrs = configurationService.getSignInTokenExpiryHours()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(tokenExpiryHrs))

        return Jwts.builder()
            .subject(appUser.id.toString())
            .claim("email", appUser.email)
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
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
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            val userId = UUID.fromString(claims.subject)

            appUserService.getById(userId) ?: throw AuthTokenNotFoundException()

            return authTokenRepository.findByToken(token) ?: throw AuthTokenNotFoundException()
        }
        catch (e: Exception)
        {
            logger.warn("Invalid token: ${e.message}")
            throw AuthTokenInvalidException()
        }
    }

    fun refreshToken(oldToken: String): String
    {
        val claims = Jwts.parser()
            .verifyWith(jwtSecretKey)
            .build()
            .parseSignedClaims(oldToken)
            .payload

        val newExpiration =
            Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(configurationService.getSignInTokenExpiryHours()))

        return Jwts.builder()
            .claims(claims)
            .issuedAt(Date())
            .expiration(newExpiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    fun saveAuthToken(authToken: AuthToken)
    {
        authTokenRepository.save(authToken)
    }
}