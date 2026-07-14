package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.extension.maskEmailForLogs
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.IdentityProviderLink
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.exception.InactiveAccountException
import com.docuhyphen.app.api.exception.SignUpRequiredException
import com.docuhyphen.app.api.repository.IdentityProviderLinkRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.idp.OAuthUserInfo
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
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
    private val organizationMembershipService: OrganizationMembershipService,
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
        trustedOrganization: Organization? = null,
    ): LinkOrCreateResult
    {
        // Check if there's already a link for this provider + subject
        var existingLink = identityProviderLinkRepository.findByProviderAndExternalSubjectId(
            provider, userInfo.subjectId
        )

        if (existingLink == null && !userInfo.legacySubjectId.isNullOrBlank())
        {
            existingLink = identityProviderLinkRepository.findByProviderAndExternalSubjectId(
                provider,
                userInfo.legacySubjectId,
            )
            if (existingLink != null)
            {
                existingLink.externalSubjectId = userInfo.subjectId
                identityProviderLinkRepository.update(existingLink)
            }
        }

        if (existingLink != null)
        {
            logger.info("Found existing IDP link for provider={}", provider)
            val linkedUser = existingLink.appUser!!
            requireSignInEligible(linkedUser)
            return LinkOrCreateResult(appUser = linkedUser, isNewUser = false)
        }

        // Check if an AppUser exists with this email
        val existingUser = appUserService.findByEmail(userInfo.email.lowercase())

        if (existingUser != null)
        {
            requireSignInEligible(existingUser)
            // Enforce single external IDP: reject if user already has a different external provider
            val existingExternal = findExternalLink(existingUser.id)

            if (existingExternal != null && existingExternal.provider != provider)
            {
                throw ExternalProviderAlreadyLinkedException(
                    "User already has ${existingExternal.provider} linked. Unlink it first before linking $provider."
                )
            }

            // User exists but no link for this provider,  needs password confirmation to link
            logger.info("AppUser exists for email={}, requesting link confirmation", userInfo.email.maskEmailForLogs())

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
        logger.info("Creating new AppUser for OAuth email={}", userInfo.email.maskEmailForLogs())

        // Enforce platform-managed organization user caps for JIT provisioning.
        trustedOrganization?.let(organizationIdentityPolicyService::enforceUserCapForOrganization)

        val newUser = AppUser().apply {
            this.email = userInfo.email.lowercase()
            this.password = null
            this.passwordSalt = null
            this.emailVerificationComplete = true
            // Org membership for JIT/IDP-provisioned users is recorded against the IDP's
            // organization in the identity-provider linking flow (organization_membership),
            // not as a standalone role on the user row.
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

        // JIT users become members of the organization that owns their email domain, recorded
        // in the organization_membership model so role resolution (UserRoleService) sees them.
        trustedOrganization?.let { organization ->
            organizationMembershipService.assignOrgRole(
                appUserId = savedUser.id,
                organizationId = organization.id,
                role = OrganizationRoleName.ORG_MEMBER,
                isPrimary = true,
            )
        }

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
            logger.warn("IDP link already exists for provider={}", provider)
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

    /**
     * Idempotent backfill: creates an INTERNAL link for a user who already has a password
     * but was signed up before the IDP-link table was introduced.
     */
    @Transactional
    fun ensureInternalLink(appUser: AppUser)
    {
        val existing = identityProviderLinkRepository.findByProviderAndExternalSubjectId(
            IdentityProviderType.INTERNAL, appUser.id.toString()
        )
        if (existing != null) return

        val link = IdentityProviderLink().apply {
            this.appUser = appUser
            this.provider = IdentityProviderType.INTERNAL
            this.externalSubjectId = appUser.id.toString()
            this.externalEmail = appUser.email
        }
        identityProviderLinkRepository.save(link)
        logger.info("Backfilled INTERNAL IDP link for existing user={}", appUser.id)
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

    private fun requireSignInEligible(appUser: AppUser)
    {
        if (appUser.isTemporary && appUser.deprovisionedAt == null)
        {
            throw SignUpRequiredException()
        }
        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            throw InactiveAccountException()
        }
    }
}

class ExternalProviderAlreadyLinkedException(message: String) : RuntimeException(message)
