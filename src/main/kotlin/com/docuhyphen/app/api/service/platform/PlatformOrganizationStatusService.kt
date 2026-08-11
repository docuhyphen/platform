package com.docuhyphen.app.api.service.platform

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.dto.PlatformOrganizationStatusDto
import com.docuhyphen.app.api.model.dto.PlatformOrganizationStatusUpdateRequest
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

@RequestScoped
class PlatformOrganizationStatusService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val organizationRepository: OrganizationRepository,
    private val authAuditService: AuthAuditService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationStatusService::class.java)
    }
    @EnforceAdminAction("PLATFORM_ORGANIZATION_STATUS_UPDATE")
    @Transactional
    fun update(
        organizationId: String,
        request: PlatformOrganizationStatusUpdateRequest,
        adminApprovalContext: AdminApprovalContext,
    ): PlatformOrganizationStatusDto
    {
        val actorId = requirePlatformAdmin()
        require(request.changeReason == null || request.changeReason.length <= 1024) {
            "Change reason must be at most 1024 characters"
        }
        val organization = requireOrganization(organizationId)
        val beforeSnapshot = statusSnapshot(organization)
        organization.isActive = request.active
        organization.verificationComplete = request.verificationComplete
        organizationRepository.update(organization)
        val afterSnapshot = statusSnapshot(organization)

        // An organization that is now active and verified is a Business subscriber, so make sure
        // it owns a subscription record instead of relying on an implicit default.
        if (organization.isActive && organization.verificationComplete)
        {
            runCatching { subscriptionPolicyService.ensureOrganizationPolicy(organization) }
                .onFailure {
                    logger.warn(
                        "Failed to create subscription record for activated organization {}",
                        organization.id,
                        it,
                    )
                }
        }

        authAuditService.emitRequired(
            action = "PLATFORM_ORGANIZATION_STATUS_UPDATE",
            outcome = "SUCCESS",
            actorId = actorId,
            actorRole = "APP_ADMIN",
            requestId = adminApprovalContext.requestId,
            reason = request.changeReason?.trim()?.takeIf { it.isNotBlank() }
                ?: "Platform administrator updated organization account status",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = afterSnapshot,
            targetType = "ORGANIZATION",
            targetId = organization.id.toString(),
            structuredDetails = mapOf(
                "before_state" to beforeSnapshot,
                "after_state" to afterSnapshot,
            ),
        )

        return PlatformOrganizationStatusDto(
            organizationId = organization.id.toString(),
            active = organization.isActive,
            verificationComplete = organization.verificationComplete,
        )
    }

    private fun requirePlatformAdmin(): UUID
    {
        val actor = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")
        if (!userRoleService.isAppAdmin(actor.id))
        {
            authAuditService.emit(
                action = "PLATFORM_ORGANIZATION_STATUS_UPDATE",
                outcome = "DENIED",
                actorId = actor.id,
                reason = "Caller lacks effective App Administrator privilege",
                targetType = "PLATFORM_ORGANIZATION",
            )
            throw UnauthorizedException("User does not have permission to administer platform organizations")
        }
        return actor.id
    }

    private fun requireOrganization(organizationId: String): Organization
    {
        val id = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }
        return organizationRepository.findById(id)
            ?: throw OrganizationNotFoundException("Organization not found")
    }

    private fun statusSnapshot(organization: Organization): String =
        "active=${organization.isActive};verificationComplete=${organization.verificationComplete}"
}
