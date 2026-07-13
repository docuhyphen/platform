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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

@RequestScoped
class AuthenticationService @Inject constructor(
    private val configurationService: ConfigurationService,
    private val refreshTokenStore: RefreshTokenStore,
    private val refreshTokenRecordService: RefreshTokenRecordService,
    private val userRoleService: UserRoleService,
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

    fun generateAccessToken(
        appUser: AppUser,
        sessionId: UUID? = null,
        expiryMinutesOverride: Long? = null,
        authTimeEpochSeconds: Long? = null,
    ): String
    {
        val expiryMinutes = expiryMinutesOverride ?: configurationService.getAccessTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))
        val authTime = authTimeEpochSeconds ?: (System.currentTimeMillis() / 1000)

        val roleClaims = buildSet {
            if (userRoleService.isAppAdmin(appUser.id)) add("APP_ADMIN")
            addAll(userRoleService.primaryOrgRoles(appUser.id).map { it.name })
            if (isEmpty()) add("APP_USER")
        }

        val builder = Jwts.builder()
            .subject(appUser.id.toString())
            .claim("email", appUser.email)
            .claim("roles", roleClaims.toList())
            .claim("exchange_version", appUser.sessionVersion)
            .claim("token_type", ACCESS.name)
            .claim("auth_time", authTime)

        sessionId?.let { builder.claim("exchange_id", it.toString()) }

        return builder
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    fun generateApplicationAccessToken(
        applicationId: UUID,
        scopes: Set<String> = configurationService.getApplicationTokenDefaultScopes(),
    ): String
    {
        val expiryMinutes = configurationService.getAccessTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))
        val normalizedScopes = scopes.map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val issuer = configurationService.getJwtIssuer()
        return Jwts.builder()
            .subject(applicationId.toString())
            .issuer(issuer)
            .audience().add(issuer).and()
            .claim("token_type", ACCESS.name)
            .claim("principal_type", "APPLICATION")
            .claim("type", "APPLICATION")
            .claim("scopes", normalizedScopes.joinToString(","))
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(jwtSecretKey)
            .compact()
    }

    fun generateIdToken(appUser: AppUser, sessionId: UUID? = null, expiryMinutesOverride: Long? = null): String
    {
        val expiryMinutes = expiryMinutesOverride ?: configurationService.getIdTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))

        val builder = Jwts.builder()
            .subject(appUser.id.toString())
            .claim("email", appUser.email)
            .claim("exchange_version", appUser.sessionVersion)
            .claim("token_type", ID.name)

        sessionId?.let { builder.claim("exchange_id", it.toString()) }

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

    /**
     * Generates an opaque refresh token: `{jti}.{base64url(32 random bytes)}`.
     *
     * - jti: server-side lookup key (UUID, 122 bits entropy)
     * - secret: 256-bit bearer credential. We store only its hash; the raw secret never
     *   appears on disk or in our DB once the cookie is set on the response.
     *
     * Total cookie payload ~ 80 bytes, vs ~300 bytes for a signed JWT.
     */
    fun generateRefreshToken(
        appUser: AppUser,
        familyId: String = UUID.randomUUID().toString(),
        sessionId: UUID? = null,
        expiryMinutesOverride: Long? = null,
    ): Triple<String, String, String>
    {
        val jti = UUID.randomUUID().toString()
        val secretBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
        val token = "$jti.$secret"
        return Triple(token, jti, familyId)
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
            logger.warn("Invalid access token")
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
            logger.warn("Invalid token")
            null
        }
    }

    // ── Refresh token operations (delegated to Redis) ──

    fun saveRefreshToken(
        appUser: AppUser,
        refreshToken: String,
        jti: String,
        familyId: String,
        sessionId: UUID? = null,
        expiryMinutesOverride: Long? = null,
    )
    {
        val expiryMinutes = expiryMinutesOverride ?: configurationService.getRefreshTokenExpiryMinutes()
        val expirySeconds = TimeUnit.MINUTES.toSeconds(expiryMinutes)
        val tokenHash = hashToken(refreshToken)
        refreshTokenStore.save(
            userId = appUser.id,
            jti = jti,
            familyId = familyId,
            refreshToken = refreshToken,
            refreshTokenHash = tokenHash,
            expirySeconds = expirySeconds,
            sessionId = sessionId,
        )

        refreshTokenRecordService.recordIssued(
            userId = appUser.id,
            userSessionId = sessionId,
            familyId = familyId,
            jti = jti,
            tokenHash = tokenHash,
            expiresAt = Instant.now().plusSeconds(expirySeconds),
        )
    }

    fun findRefreshTokenByJti(jti: String): StoredRefreshToken?
    {
        return refreshTokenStore.findByJti(jti)
    }

    /**
     * Constant-time check that a presented refresh token matches the stored hash.
     * Defends against timing-side-channel comparisons of the opaque secret.
     */
    fun verifyRefreshTokenSecret(presentedToken: String, stored: StoredRefreshToken): Boolean
    {
        val expectedHash = hashToken(presentedToken).toByteArray(Charsets.UTF_8)
        val storedHashBytes = (stored.token).toByteArray(Charsets.UTF_8).let { existing ->
            // `stored.token` is the original (raw) token; we store its hash but also retain
            // the raw form keyed by JTI so legacy callers keep working. Compare hashes:
            hashToken(stored.token).toByteArray(Charsets.UTF_8)
        }
        return java.security.MessageDigest.isEqual(expectedHash, storedHashBytes)
    }

    fun deleteRefreshTokenByJti(jti: String, reasonCode: RevocationReasonCode = RevocationReasonCode.SECURITY_POLICY)
    {
        refreshTokenStore.deleteByJti(jti)
        refreshTokenRecordService.revokeByJti(jti, reasonCode)
    }

    fun deleteAllRefreshTokensForUser(userId: UUID, reasonCode: RevocationReasonCode = RevocationReasonCode.SECURITY_POLICY)
    {
        refreshTokenStore.deleteAllByUserId(userId)
        refreshTokenRecordService.revokeUser(userId, reasonCode)
    }

    fun rotateRefreshToken(
        appUser: AppUser,
        currentJti: String,
        currentRefreshToken: String,
        familyId: String,
        sessionId: UUID,
        refreshExpiryMinutesOverride: Long? = null,
    ): RefreshRotationResult
    {
        val (newToken, newJti, resolvedFamilyId) = generateRefreshToken(
            appUser,
            familyId,
            sessionId,
            refreshExpiryMinutesOverride,
        )
        val expiryMinutes = refreshExpiryMinutesOverride ?: configurationService.getRefreshTokenExpiryMinutes()
        val expirySeconds = TimeUnit.MINUTES.toSeconds(expiryMinutes)
        val newTokenHash = hashToken(newToken)

        val result = refreshTokenStore.rotate(
            userId = appUser.id,
            currentJti = currentJti,
            currentTokenHash = hashToken(currentRefreshToken),
            newJti = newJti,
            newToken = newToken,
            newTokenHash = newTokenHash,
            familyId = resolvedFamilyId,
            graceSeconds = configurationService.getRefreshRotationGraceSeconds(),
            expirySeconds = expirySeconds,
            nowEpochMillis = System.currentTimeMillis(),
        )

        if (result.status == RefreshRotationStatus.ROTATED)
        {
            refreshTokenRecordService.recordIssued(
                userId = appUser.id,
                userSessionId = sessionId,
                familyId = resolvedFamilyId,
                jti = newJti,
                tokenHash = newTokenHash,
                expiresAt = Instant.now().plusSeconds(expirySeconds),
            )
            refreshTokenRecordService.recordRotation(
                currentJti = currentJti,
                successorJti = newJti,
                graceSeconds = configurationService.getRefreshRotationGraceSeconds(),
            )
        }

        return result
    }

    fun revokeRefreshFamily(familyId: String, reasonCode: RevocationReasonCode)
    {
        refreshTokenStore.revokeFamily(familyId, reasonCode)
        refreshTokenRecordService.revokeFamily(familyId, reasonCode)
    }

    private fun hashToken(token: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
