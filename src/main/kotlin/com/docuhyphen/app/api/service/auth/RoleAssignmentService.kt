package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.LastAppAdminException
import com.docuhyphen.app.api.model.entity.RoleAssignment
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.model.entity.RoleScopeType
import com.docuhyphen.app.api.repository.RoleAssignmentRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Write-side API over APP-scope [RoleAssignment]s. Lets multiple App Admins be
 * granted / revoked at runtime, with a "never drop below one App Admin" invariant. App roles
 * are *additive*, a user keeps all their org/group/personal grants; an APP_ADMIN row simply
 * unions APP-wide capabilities on top (see [com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService]).
 *
 * Authorization of *who* may call grant/revoke lives at the endpoint
 * ([com.docuhyphen.app.api.resource.AppRoleResource]); this service trusts its actorId.
 */
@ApplicationScoped
class RoleAssignmentService @Inject constructor(
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RoleAssignmentService::class.java)

        /** Roles that may be granted at APP scope via this service. */
        private val GRANTABLE_APP_ROLES = setOf(
            RoleName.APP_ADMIN,
            RoleName.APP_AUDITOR,
            RoleName.APP_SUPPORT,
        )
    }

    /** Active APP_ADMIN assignments. */
    fun listAppAdmins(): List<RoleAssignment> = roleAssignmentRepository.findActiveAppAdmins()

    /** Grant an APP-scope role to a user. Idempotent: reactivates a soft-deleted row if present. */
    @Transactional
    fun grantAppRole(targetAppUserId: UUID, roleName: RoleName, actorId: UUID?): RoleAssignment
    {
        require(roleName in GRANTABLE_APP_ROLES) { "Role $roleName is not grantable at APP scope" }
        appUserService.getById(targetAppUserId)
            ?: throw AppUserNotFoundException("User not found for id: $targetAppUserId")

        val existing = roleAssignmentRepository.findAppRoleForUser(targetAppUserId, roleName.name)
        if (existing != null)
        {
            if (!existing.isActive)
            {
                existing.isActive = true
                existing.grantedByAppUserId = actorId
                existing.expiresAt = null
                roleAssignmentRepository.update(existing)
                emitAudit("APP_ROLE_GRANT", actorId, targetAppUserId, roleName, "reactivated")
            }
            return existing
        }

        val assignment = RoleAssignment().apply {
            appUserId = targetAppUserId
            this.roleName = roleName.name
            scopeType = RoleScopeType.APP
            scopeId = null // APP scope <=> scope_id NULL (DDL invariant)
            grantedByAppUserId = actorId
            isActive = true
        }
        roleAssignmentRepository.save(assignment)
        emitAudit("APP_ROLE_GRANT", actorId, targetAppUserId, roleName, "granted")
        return assignment
    }

    /**
     * Revoke (soft-delete) an APP-scope assignment. Refuses to remove the last active APP_ADMIN.
     */
    @Transactional
    fun revokeAppRole(assignmentId: UUID, actorId: UUID?)
    {
        val assignment = roleAssignmentRepository.findById(assignmentId)
            ?: throw IllegalArgumentException("Role assignment not found")
        require(assignment.scopeType == RoleScopeType.APP) { "Not an APP-scope assignment" }

        if (!assignment.isActive) return // already revoked, idempotent

        val isAppAdmin = assignment.roleName == RoleName.APP_ADMIN.name
        if (isAppAdmin && roleAssignmentRepository.countActiveAppAdmins() <= 1)
        {
            throw LastAppAdminException()
        }

        assignment.isActive = false
        roleAssignmentRepository.update(assignment)
        val role = runCatching { RoleName.valueOf(assignment.roleName) }.getOrNull()
        emitAudit("APP_ROLE_REVOKE", actorId, assignment.appUserId, role, "revoked")
    }

    /**
     * First-run bootstrap: if no active App Admin exists and `app.security.app-admin.bootstrap-email`
     * names an existing user, promote them to APP_ADMIN. Idempotent and safe to run on every boot
     * (incl. a fresh from-empty migration run). Blank/absent config disables it; an unmatched email
     * is logged and skipped (the user can be created later and re-bootstrapped on the next boot).
     */
    @Transactional
    fun bootstrapFirstAppAdmin(@Observes event: StartupEvent)
    {
        val email = configurationService.getBootstrapAppAdminEmail() ?: return
        if (roleAssignmentRepository.countActiveAppAdmins() > 0)
        {
            logger.debug("App Admin bootstrap skipped: an active App Admin already exists")
            return
        }
        val user = appUserService.findByEmail(email)
        if (user == null)
        {
            logger.warn("App Admin bootstrap: no user found for configured email '{}', skipping", email)
            return
        }
        grantAppRole(user.id, RoleName.APP_ADMIN, actorId = null)
        logger.info("App Admin bootstrap: granted APP_ADMIN to '{}'", email)
    }

    private fun emitAudit(action: String, actorId: UUID?, targetUserId: UUID?, role: RoleName?, reason: String)
    {
        runCatching {
            authAuditService.emit(
                action = action,
                outcome = "SUCCESS",
                actorId = actorId,
                reason = "$reason ${role?.name ?: ""}".trim(),
                targetType = "APP_USER",
                afterSnapshot = "targetUser=$targetUserId;role=${role?.name};active=${action == "APP_ROLE_GRANT"}",
            )
        }.onFailure { logger.error("Failed to emit audit for $action", it) }
    }
}
