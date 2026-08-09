package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Owns writes to a user's internal password credential.
 *
 * Credential changes must be atomic with the identity-provider link they imply, and they must
 * invalidate every other live session, so they belong in one transactional service rather than
 * being spread across a resource method.
 */
@ApplicationScoped
class AppUserCredentialService @Inject constructor(
    private val appUserService: AppUserService,
    private val authenticationService: AuthenticationService,
    private val oauthUserLinkingService: OAuthUserLinkingService,
    private val userSessionService: UserSessionService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserCredentialService::class.java)
    }

    /**
     * Sets or replaces the user's password, records the INTERNAL identity provider link, and
     * signs every other device out.
     *
     * Sessions are cut because a password change is the standard response to a suspected
     * compromise: leaving other sessions alive would let an attacker keep the access they
     * already have.
     */
    @Transactional
    fun setPassword(appUserId: UUID, newPassword: String)
    {
        val appUser = appUserService.getById(appUserId)
            ?: throw IllegalArgumentException("User was not found")

        val salt = authenticationService.generatePasswordSalt()
        appUser.password = authenticationService.hashPassword(newPassword, salt)
        appUser.passwordSalt = salt
        appUser.isPasswordTemporary = false
        appUser.temporaryPasswordExpiresAt = null
        appUserService.update(appUser)

        oauthUserLinkingService.createLink(
            appUser,
            IdentityProviderType.INTERNAL,
            appUser.id.toString(),
            appUser.email,
        )

        authenticationService.deleteAllRefreshTokensForUser(appUser.id, RevocationReasonCode.SECURITY_POLICY)
        userSessionService.revokeAllUserSessions(appUser.id, RevocationReasonCode.SECURITY_POLICY)

        logger.info("Password credential updated for user={}", appUser.id)
    }
}

