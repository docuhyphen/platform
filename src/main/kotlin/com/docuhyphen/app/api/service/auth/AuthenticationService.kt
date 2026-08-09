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
    private val tokenSigningKeyProvider: TokenSigningKeyProvider,
)
{
    val jwtSecretKey: SecretKey get() = tokenSigningKeyProvider.keyFor(TokenPurpose.USER_TOKEN)

    companion object
    {
        val logger = LoggerFactory.getLogger(AuthenticationService::class.java.name)
    }

    fun generatePasswordSalt(): String = BCrypt.gensalt(configurationService.getPasswordBcryptCost())

    fun hashPassword(password: String, salt: String): String = BCrypt.hashpw(password, salt)

    fun validatePassword(inputPassword: String, storedHash: String): Boolean =
        runCatching { BCrypt.checkpw(inputPassword, storedHash) }.getOrDefault(false)

    fun isValidEmail(email: String): Boolean
    {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }

    fun isEmailInvalid(email: String): Boolean = !isValidEmail(email)

    /**
     * Composition rules for the internal identity provider.
     *
     * The upper bound only exists to keep unbounded input away from the hash function; it is
     * deliberately high enough that passphrases are practical, since length is the single
     * biggest contributor to resistance against offline cracking.
     */
    fun isPasswordStrong(password: String): Boolean
    {
        val minLength = configurationService.getPasswordMinLength()
        val maxLength = configurationService.getPasswordMaxLength()
        val uppercase = Regex(".*[A-Z].*")
        val lowercase = Regex(".*[a-z].*")
        val digit = Regex(".*\\d.*")
        val specialCharacter = Regex(""".*[!@#${'$'}%^&*()_+\-=\[\]{};':"\\|,.<>/?].*""")

        if (password.isEmpty()) return false
        if (password.length < minLength || password.length > maxLength) return false
        if (!password.contains(uppercase)) return false
        if (!password.contains(lowercase)) return false
        if (!password.contains(digit)) return false
        if (!password.contains(specialCharacter)) return false

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
        val issuer = configurationService.getJwtIssuer()

        val roleClaims = buildSet {
            if (userRoleService.isAppAdmin(appUser.id)) add("APP_ADMIN")
            addAll(userRoleService.primaryOrgRoles(appUser.id).map { it.name })
            if (isEmpty()) add("APP_USER")
        }

        val builder = Jwts.builder()
            .subject(appUser.id.toString())
            .issuer(issuer)
            .audience().add(issuer).and()
            .id(UUID.randomUUID().toString())
            .claim("email", appUser.email)
            .claim("roles", roleClaims.toList())
            .claim("exchange_version", appUser.sessionVersion)
            .claim("token_type", ACCESS.name)
            .claim("auth_time", authTime)

        sessionId?.let { builder.claim("exchange_id", it.toString()) }

        return builder
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(tokenSigningKeyProvider.keyFor(TokenPurpose.USER_TOKEN))
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
            .id(UUID.randomUUID().toString())
            .claim("token_type", ACCESS.name)
            .claim("principal_type", "APPLICATION")
            .claim("type", "APPLICATION")
            .claim("scopes", normalizedScopes.joinToString(","))
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(tokenSigningKeyProvider.keyFor(TokenPurpose.USER_TOKEN))
            .compact()
    }

    fun generateIdToken(appUser: AppUser, sessionId: UUID? = null, expiryMinutesOverride: Long? = null): String
    {
        val expiryMinutes = expiryMinutesOverride ?: configurationService.getIdTokenExpiryMinutes()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes))
        val issuer = configurationService.getJwtIssuer()

        val builder = Jwts.builder()
            .subject(appUser.id.toString())
            .issuer(issuer)
            .audience().add(issuer).and()
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
            .signWith(tokenSigningKeyProvider.keyFor(TokenPurpose.USER_TOKEN))
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
            .issuer(configurationService.getJwtIssuer())
            .claim("provider", provider)
            .claim("externalSubjectId", externalSubjectId)
            .claim("token_type", "LINK")
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(tokenSigningKeyProvider.keyFor(TokenPurpose.LINK_TOKEN))
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

        return parseWithPurpose(token, TokenPurpose.USER_TOKEN)
    }

    fun parseTokenClaims(token: String): Claims? = parseWithPurpose(token, TokenPurpose.USER_TOKEN)

    /** Parses a token that was signed with the account-linking key, never the session key. */
    fun parseLinkTokenClaims(token: String): Claims? = parseWithPurpose(token, TokenPurpose.LINK_TOKEN)

    private fun parseWithPurpose(token: String, purpose: TokenPurpose): Claims?
    {
        return try
        {
            Jwts.parser()
                .verifyWith(tokenSigningKeyProvider.keyFor(purpose))
                .requireIssuer(configurationService.getJwtIssuer())
                .build()
                .parseSignedClaims(token)
                .payload
        }
        catch (e: Exception)
        {
            logger.warn("Invalid token presented for purpose={}", purpose)
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
     * Constant-time comparison of the presented token's hash against the stored hash.
     *
     * The store never holds the raw token, so this is the only way to authenticate a
     * presentation, and [MessageDigest.isEqual] keeps the comparison free of a timing
     * side channel on the secret.
     */
    fun verifyRefreshTokenSecret(presentedToken: String, stored: StoredRefreshToken): Boolean
    {
        val presentedHash = hashToken(presentedToken).toByteArray(Charsets.UTF_8)
        val storedHash = stored.tokenHash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(presentedHash, storedHash)
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
            newTokenHash = newTokenHash,
            familyId = resolvedFamilyId,
            graceSeconds = configurationService.getRefreshRotationGraceSeconds(),
            expirySeconds = expirySeconds,
            nowEpochMillis = System.currentTimeMillis(),
        )

        if (result.status == RefreshRotationStatus.ROTATED || result.status == RefreshRotationStatus.GRACE_REPLAY)
        {
            refreshTokenRecordService.recordIssued(
                userId = appUser.id,
                userSessionId = sessionId,
                familyId = resolvedFamilyId,
                jti = newJti,
                tokenHash = newTokenHash,
                expiresAt = Instant.now().plusSeconds(expirySeconds),
            )
        }

        if (result.status == RefreshRotationStatus.ROTATED)
        {
            refreshTokenRecordService.recordRotation(
                currentJti = currentJti,
                successorJti = newJti,
                graceSeconds = configurationService.getRefreshRotationGraceSeconds(),
            )
        }

        // The successor's raw value only exists here, in memory, for the life of this request.
        return if (result.status == RefreshRotationStatus.ROTATED || result.status == RefreshRotationStatus.GRACE_REPLAY)
        {
            result.copy(successorToken = newToken)
        }
        else
        {
            result
        }
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
