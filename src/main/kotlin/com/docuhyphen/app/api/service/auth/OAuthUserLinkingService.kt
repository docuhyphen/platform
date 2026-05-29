package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.model.entity.IdentityProviderLink
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.repository.IdentityProviderLinkRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.idp.OAuthUserInfo
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class LinkOrCreateResult(
    val appUser: AppUser,
    val isNewUser: Boolean,
    val requiresLinkConfirmation: Boolean = false,
    val linkToken: String? = null,
)

@RequestScoped
class OAuthUserLinkingService @Inject constructor(
    private val appUserService: AppUserService,
    private val identityProviderLinkRepository: IdentityProviderLinkRepository,
    private val authenticationService: AuthenticationService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OAuthUserLinkingService::class.java)
    }

    @Transactional
    fun linkOrCreateUser(
        provider: IdentityProviderType,
        userInfo: OAuthUserInfo,
    ): LinkOrCreateResult
    {
        // Check if there's already a link for this provider + subject
        val existingLink = identityProviderLinkRepository.findByProviderAndExternalSubjectId(
            provider, userInfo.subjectId
        )

        if (existingLink != null)
        {
            logger.info("Found existing IDP link for provider={} sub={}", provider, userInfo.subjectId)
            return LinkOrCreateResult(appUser = existingLink.appUser!!, isNewUser = false)
        }

        // Check if an AppUser exists with this email
        val existingUser = appUserService.findByEmail(userInfo.email.lowercase())

        if (existingUser != null)
        {
            // Enforce single external IDP: reject if user already has a different external provider
            val existingExternal = findExternalLink(existingUser.id)

            if (existingExternal != null && existingExternal.provider != provider)
            {
                throw ExternalProviderAlreadyLinkedException(
                    "User already has ${existingExternal.provider} linked. Unlink it first before linking $provider."
                )
            }

            // User exists but no link for this provider,  needs password confirmation to link
            logger.info("AppUser exists for email={}, requesting link confirmation", userInfo.email)

            val linkToken = authenticationService.generateLinkToken(
                email = userInfo.email,
                provider = provider.name,
                externalSubjectId = userInfo.subjectId,
            )

            return LinkOrCreateResult(
                appUser = existingUser,
                isNewUser = false,
                requiresLinkConfirmation = true,
                linkToken = linkToken,
            )
        }

        // No user exists,  create new AppUser
        logger.info("Creating new AppUser for OAuth email={}", userInfo.email)

        // Enforce platform-managed organization user caps for JIT provisioning.
        organizationIdentityPolicyService.enforceUserCapForEmail(userInfo.email)

        val newUser = AppUser().apply {
            this.email = userInfo.email.lowercase()
            this.password = null
            this.passwordSalt = null
            this.emailVerificationComplete = true
            this.role = AppUserRole.ORG_MEMBER
            this.roleSource = "JIT_IDP"
            this.roleAssignedAt = Timestamp.from(Instant.now())
        }

        if (userInfo.firstName != null || userInfo.lastName != null)
        {
            val person = com.docuhyphen.app.api.model.entity.Person().apply {
                this.firstName = userInfo.firstName ?: ""
                this.lastName = userInfo.lastName ?: ""
            }
            newUser.person = person
        }

        val savedUser = appUserService.create(newUser)

        val link = IdentityProviderLink().apply {
            this.appUser = savedUser
            this.provider = provider
            this.externalSubjectId = userInfo.subjectId
            this.externalEmail = userInfo.email
        }
        identityProviderLinkRepository.save(link)

        return LinkOrCreateResult(appUser = savedUser, isNewUser = true)
    }

    @Transactional
    fun createLink(appUser: AppUser, provider: IdentityProviderType, externalSubjectId: String, externalEmail: String)
    {
        val existingLink = identityProviderLinkRepository.findByProviderAndExternalSubjectId(provider, externalSubjectId)
        if (existingLink != null)
        {
            logger.warn("IDP link already exists for provider={} sub={}", provider, externalSubjectId)
            return
        }

        // Enforce single external IDP
        if (provider != IdentityProviderType.INTERNAL)
        {
            val existingExternal = findExternalLink(appUser.id)

            if (existingExternal != null && existingExternal.provider != provider)
            {
                throw ExternalProviderAlreadyLinkedException(
                    "User already has ${existingExternal.provider} linked. Unlink it first before linking $provider."
                )
            }
        }

        val link = IdentityProviderLink().apply {
            this.appUser = appUser
            this.provider = provider
            this.externalSubjectId = externalSubjectId
            this.externalEmail = externalEmail
        }
        identityProviderLinkRepository.save(link)

        logger.info("Created IDP link for user={} provider={}", appUser.id, provider)
    }

    fun getLinksForUser(userId: UUID): List<IdentityProviderLink>
    {
        return identityProviderLinkRepository.findAllByAppUserId(userId)
    }

    @Transactional
    fun unlinkProvider(userId: UUID, provider: IdentityProviderType, appUser: AppUser)
    {
        val links = identityProviderLinkRepository.findAllByAppUserId(userId)

        if (links.size <= 1)
        {
            throw IllegalStateException("Cannot unlink the last identity provider")
        }

        // If unlinking external provider, ensure user has INTERNAL (password set) as fallback
        if (provider != IdentityProviderType.INTERNAL)
        {
            val hasInternal = links.any { it.provider == IdentityProviderType.INTERNAL }

            if (!hasInternal && appUser.password.isNullOrBlank())
            {
                throw IllegalStateException(
                    "Set up a password via /auth/identity-providers/internal/setup-password before unlinking your external provider."
                )
            }

            // Auto-create INTERNAL link if user has a password but no INTERNAL link
            if (!hasInternal && !appUser.password.isNullOrBlank())
            {
                createLink(appUser, IdentityProviderType.INTERNAL, appUser.id.toString(), appUser.email)
            }
        }

        identityProviderLinkRepository.deleteByProviderAndAppUserId(provider, userId)

        if (provider == IdentityProviderType.INTERNAL)
        {
            appUser.password = null
            appUser.passwordSalt = null
            appUserService.update(appUser)
        }

        logger.info("Unlinked provider={} for user={}", provider, userId)
    }

    private fun findExternalLink(userId: UUID): IdentityProviderLink?
    {
        return identityProviderLinkRepository.findAllByAppUserId(userId)
            .firstOrNull { it.provider != IdentityProviderType.INTERNAL }
    }
}

class ExternalProviderAlreadyLinkedException(message: String) : RuntimeException(message)
