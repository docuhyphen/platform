package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class OrganizationIdpSecretRotationRunbookService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val adminActionGuardService: AdminActionGuardService,
    private val rotationSchedulerService: OrganizationIdpSecretRotationSchedulerService,
    private val userRoleService: UserRoleService,
)
{
    fun previewEmergencyRotationCandidates(organizationId: String): OrganizationIdpRotationPreviewResult
    {
        val actor = requireOrgAdminActor(organizationId)
        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }

        return rotationSchedulerService.previewRotationForOrganization(orgId)
            .also {
                adminActionGuardService.enforce(
                    action = "ORG_IDP_SECRET_ROTATION_PREVIEW",
                    actorId = actor.id,
                    context = AdminApprovalContext(
                        requestId = null,
                    ),
                )
            }
    }

    fun runEmergencyRotation(
        organizationId: String,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationIdpRotationRunResult
    {
        val actor = requireOrgAdminActor(organizationId)

        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_ROTATION_RUNBOOK",
            actorId = actor.id,
            context = adminApprovalContext,
        )

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



