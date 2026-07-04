package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class OrganizationIdpSecretRotationRunbookService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val rotationSchedulerService: OrganizationIdpSecretRotationSchedulerService,
    private val userRoleService: UserRoleService,
)
{
    @EnforceAdminAction("ORG_IDP_SECRET_ROTATION_PREVIEW")
    fun previewEmergencyRotationCandidates(organizationId: String): OrganizationIdpRotationPreviewResult
    {
        requireOrgAdminActor(organizationId)
        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }

        return rotationSchedulerService.previewRotationForOrganization(orgId)
    }

    @EnforceAdminAction("ORG_IDP_SECRET_ROTATION_RUNBOOK")
    fun runEmergencyRotation(
        organizationId: String,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationIdpRotationRunResult
    {
        val actor = requireOrgAdminActor(organizationId)

        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }

        return rotationSchedulerService.runManualRotationForOrganization(
            organizationId = orgId,
            actorId = actor.id,
            requestId = adminApprovalContext.requestId,
        )
    }

    fun getRotationStatus(organizationId: String): OrganizationIdpRotationStatusResult
    {
        requireOrgAdminActor(organizationId)

        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }

        return rotationSchedulerService.statusForOrganization(orgId)
    }

    private fun requireOrgAdminActor(organizationId: String): com.docuhyphen.app.api.model.entity.AppUser
    {
        val actor = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")

        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }

        if (!userRoleService.isOrgAdminIn(actor.id, orgId))
        {
            throw UnauthorizedException("User does not have permission to run emergency secret rotation")
        }

        return actor
    }
}



