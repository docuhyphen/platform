package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthTokenType.ACCESS
import com.docuhyphen.app.api.model.entity.AuthTokenType.ID
import com.docuhyphen.app.api.model.entity.AuthTokenType.REFRESH
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.Claims
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
    private val configurationService: ConfigurationService,
    private val refreshTokenStore: RefreshTokenStore,
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

    fun generateAccessToken(appUser: AppUser): String
    {
        val expiryMinutes = configurationService.getAccessTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))

        return Jwts.builder()
            .subject(appUser.id.toString())
            .claim("email", appUser.email)
            .claim("role", appUser.role.name)
            .claim("token_type", ACCESS.name)
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    fun generateApplicationAccessToken(applicationId: UUID): String
    {
        val expiryMinutes = configurationService.getAccessTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))

        return Jwts.builder()
            .subject(applicationId.toString())
            .claim("token_type", ACCESS.name)
            .claim("type", "APPLICATION")
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    fun generateIdToken(appUser: AppUser): String
    {
        val expiryMinutes = configurationService.getIdTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))

        val builder = Jwts.builder()
            .subject(appUser.id.toString())
            .claim("email", appUser.email)
            .claim("token_type", ID.name)

        appUser.person?.let { person ->
            builder.claim("firstName", person.firstName)
            builder.claim("lastName", person.lastName)
        }

        return builder
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    fun generateRefreshToken(appUser: AppUser): Pair<String, String>
    {
        val expiryDays = configurationService.getRefreshTokenExpiryDays()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expiryDays))
        val jti = UUID.randomUUID().toString()

        val token = Jwts.builder()
            .subject(appUser.id.toString())
            .claim("token_type", REFRESH.name)
            .id(jti)
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()

        return Pair(token, jti)
    }

    fun generateLinkToken(email: String, provider: String, externalSubjectId: String): String
    {
        val expiryMinutes = configurationService.getLinkTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))

        return Jwts.builder()
            .subject(email)
            .claim("provider", provider)
            .claim("externalSubjectId", externalSubjectId)
            .claim("token_type", "LINK")
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    // ── Token verification ──

    fun verifyAccessToken(token: String?): Claims?
    {
        if (token.isNullOrBlank())
        {
            logger.warn("Access token is null or blank")
            return null
        }

        return try
        {
            Jwts.parser()
                .verifyWith(jwtSecretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        }
        catch (e: Exception)
        {
            logger.warn("Invalid access token: ${e.message}")
            null
        }
    }

    fun parseTokenClaims(token: String): Claims?
    {
        return try
        {
            Jwts.parser()
                .verifyWith(jwtSecretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        }
        catch (e: Exception)
        {
            logger.warn("Invalid token: ${e.message}")
            null
        }
    }

    // ── Refresh token operations (delegated to Redis) ──

    fun saveRefreshToken(appUser: AppUser, refreshToken: String, jti: String)
    {
        val expirySeconds = TimeUnit.DAYS.toSeconds(configurationService.getRefreshTokenExpiryDays())
        refreshTokenStore.save(appUser.id, jti, refreshToken, expirySeconds)
    }

    fun findRefreshTokenByJti(jti: String): StoredRefreshToken?
    {
        return refreshTokenStore.findByJti(jti)
    }

    fun deleteRefreshTokenByJti(jti: String)
    {
        refreshTokenStore.deleteByJti(jti)
    }

    fun deleteAllRefreshTokensForUser(userId: UUID)
    {
        refreshTokenStore.deleteAllByUserId(userId)
    }
}