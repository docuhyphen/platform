package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationIdentityDomain
import com.docuhyphen.app.api.model.entity.OrganizationIdentityDomainStatus
import com.docuhyphen.app.api.repository.identity.OrganizationIdentityDomainRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.net.IDN
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RequestScoped
class OrganizationIdentityDomainService @Inject constructor(
    private val domainRepository: OrganizationIdentityDomainRepository,
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
    private val txtRecordResolver: DomainTxtRecordResolver,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        const val DNS_RECORD_PREFIX = "_docuhyphen-verification"
        const val DNS_VALUE_PREFIX = "docuhyphen-domain-verification="
        private val DOMAIN_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
        private val secureRandom = SecureRandom()
    }

    fun list(organizationId: String): List<OrganizationIdentityDomain>
    {
        val orgId = requireOrgAdmin(organizationId).second
        return domainRepository.findByOrganizationId(orgId)
    }

    @EnforceAdminAction("ORG_IDENTITY_DOMAIN_CREATE")
    @Transactional
    fun create(organizationId: String, requestedDomain: String): OrganizationIdentityDomain
    {
        val (actor, orgId) = requireOrgAdmin(organizationId)
        subscriptionGuard.requireMutation(orgId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val organization = organizationRepository.findById(orgId)
            ?: throw IllegalArgumentException("Organization not found")
        val domain = normalizeAndValidate(requestedDomain)

        if (domainRepository.findByOrganizationIdAndDomain(orgId, domain) != null)
        {
            throw IllegalStateException("Domain is already registered for this organization")
        }

        val entity = OrganizationIdentityDomain().apply {
            this.organization = organization
            this.domain = domain
            this.verificationToken = generateToken()
            this.createdBy = actor.id
        }
        domainRepository.save(entity)
        audit("ORG_IDENTITY_DOMAIN_CREATE", actor, orgId, domain)
        return entity
    }

    @EnforceAdminAction("ORG_IDENTITY_DOMAIN_VERIFY")
    @Transactional
    fun verify(organizationId: String, domainId: String): OrganizationIdentityDomain
    {
        val (actor, orgId) = requireOrgAdmin(organizationId)
        subscriptionGuard.requireMutation(orgId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val domain = requireDomain(orgId, domainId)
        if (domain.status == OrganizationIdentityDomainStatus.VERIFIED)
        {
            return domain
        }

        val verifiedOwner = domainRepository.findVerifiedByDomain(domain.domain)
        if (verifiedOwner != null && verifiedOwner.organization?.id != orgId)
        {
            throw IllegalStateException("Domain is already verified by another organization")
        }

        val expectedValue = DNS_VALUE_PREFIX + domain.verificationToken
        val records = txtRecordResolver.resolve("$DNS_RECORD_PREFIX.${domain.domain}")
        if (expectedValue !in records)
        {
            throw IllegalStateException("Domain verification TXT record was not found")
        }

        domain.status = OrganizationIdentityDomainStatus.VERIFIED
        domain.verifiedDate = Timestamp.from(Instant.now())
        domain.verifiedBy = actor.id
        domainRepository.update(domain)
        audit("ORG_IDENTITY_DOMAIN_VERIFY", actor, orgId, domain.domain)
        return domain
    }

    @EnforceAdminAction("ORG_IDENTITY_DOMAIN_DELETE")
    @Transactional
    fun delete(organizationId: String, domainId: String)
    {
        val (actor, orgId) = requireOrgAdmin(organizationId)
        subscriptionGuard.requireMutation(orgId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val domain = requireDomain(orgId, domainId)
        domainRepository.delete(domain)
        audit("ORG_IDENTITY_DOMAIN_DELETE", actor, orgId, domain.domain)
    }

    private fun requireOrgAdmin(organizationId: String): Pair<AppUser, UUID>
    {
        val actor = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")
        val orgId = requireUuid(organizationId, "organization ID")
        if (!userRoleService.isOrgAdminIn(actor.id, orgId))
        {
            throw UnauthorizedException("User does not have permission to manage organization identity domains")
        }
        return actor to orgId
    }

    private fun requireDomain(organizationId: UUID, domainId: String): OrganizationIdentityDomain
    {
        val domain = domainRepository.findById(requireUuid(domainId, "identity domain ID"))
            ?: throw IllegalArgumentException("Identity domain not found")
        if (domain.organization?.id != organizationId)
        {
            throw UnauthorizedException("Identity domain does not belong to target organization")
        }
        return domain
    }

    private fun normalizeAndValidate(value: String): String
    {
        val withoutTrailingDot = value.trim().trimEnd('.')
        val domain = runCatching { IDN.toASCII(withoutTrailingDot, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrElse { throw IllegalArgumentException("Domain is invalid") }
        val labels = domain.split('.')
        if (domain.length > 253 || labels.size < 2 || labels.any { !DOMAIN_LABEL.matches(it) })
        {
            throw IllegalArgumentException("Domain is invalid")
        }
        return domain
    }

    private fun requireUuid(value: String, label: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw IllegalArgumentException("Invalid $label format") }

    private fun generateToken(): String
    {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun audit(action: String, actor: AppUser, organizationId: UUID, domain: String)
    {
        authAuditService.emit(
            action = action,
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = organizationId,
            reason = "Organization admin managed identity domain $domain",
        )
    }
}
