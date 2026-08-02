package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.extension.maskEmailForLogs
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.LastAppAdminException
import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Write-side API over app role assignments. Lets multiple App Admins be
 * granted / revoked at runtime, with a "never drop below one effective App Admin" invariant. App roles
 * are *additive*, a user keeps all their org/group/personal grants; an APP_ADMIN row simply
 * unions APP-wide capabilities on top (see [com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService]).
 *
 * Authorization of *who* may call grant/revoke lives at the endpoint
 * ([com.docuhyphen.app.api.resource.application.AppRoleResource]); this service trusts its actorId.
 */
@ApplicationScoped
class AppRoleAssignmentService @Inject constructor(
    private val appRoleAssignmentRepository: AppRoleAssignmentRepository,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val userRoleService: UserRoleService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppRoleAssignmentService::class.java)

        /** Roles that may be granted at APP scope via this service. */
        private val GRANTABLE_APP_ROLES = setOf(
            AppRoleName.APP_ADMIN,
            AppRoleName.APP_AUDITOR,
            AppRoleName.APP_SUPPORT,
        )
    }

    /** Effective APP_ADMIN assignments held by active, provisioned users. */
    fun listAppAdmins(actorId: UUID): List<AppRoleAssignment>
    {
        val assignments = appRoleAssignmentRepository.findActiveAppAdmins()
        authAuditService.emitRequired(
            action = "APP_ADMIN_LIST",
            outcome = "SUCCESS",
            actorId = actorId,
            actorRole = AppRoleName.APP_ADMIN.name,
            reason = "Platform administrator listed effective App Administrator assignments",
            structuredDetails = mapOf("result_count" to assignments.size.toString()),
        )
        return assignments
    }

    fun searchAppAdminCandidates(query: String, limit: Int, actorId: UUID): List<AppUser>
    {
        require(limit in 1..100) { "Limit must be between 1 and 100" }
        val normalizedQuery = query.trim()
        val candidates = if (normalizedQuery.length < 2)
        {
            emptyList()
        }
        else
        {
            appUserService.searchActiveUsers(normalizedQuery, limit)
        }
        authAuditService.emitRequired(
            action = "APP_ADMIN_CANDIDATE_SEARCH",
            outcome = "SUCCESS",
            actorId = actorId,
            actorRole = AppRoleName.APP_ADMIN.name,
            reason = "Platform administrator searched eligible App Administrator candidates",
            structuredDetails = mapOf(
                "query_length" to normalizedQuery.length.toString(),
                "result_count" to candidates.size.toString(),
                "limit" to limit.toString(),
            ),
        )
        return candidates
    }

    /** Require app administration privilege. Throws [SecurityException] if the actor lacks it. */
    fun requireAppAdmin(actorId: UUID, attemptedAction: String = "APP_ADMIN_OPERATION_DENIED")
    {
        if (!userRoleService.isAppAdmin(actorId))
        {
            authAuditService.emit(
                action = attemptedAction,
                outcome = "DENIED",
                actorId = actorId,
                reason = "Caller lacks effective App Administrator privilege",
                structuredDetails = mapOf("required_role" to AppRoleName.APP_ADMIN.name),
            )
            throw SecurityException("App administrator privilege required")
        }
    }

    /** Grant an APP-scope role to a user. Idempotent: reactivates a soft-deleted row if present. */
    @Transactional
    fun grantAppRole(targetAppUserId: UUID, roleName: AppRoleName, actorId: UUID?): AppRoleAssignment
    {
        require(roleName in GRANTABLE_APP_ROLES) { "Role $roleName is not grantable at APP scope" }
        val targetUser = appUserService.getById(targetAppUserId)
            ?: throw AppUserNotFoundException("User not found for id: $targetAppUserId")
        require(targetUser.isActive && targetUser.deprovisionedAt == null) {
            "App roles can only be granted to active, provisioned users"
        }

        val existing = appRoleAssignmentRepository.findAppRoleForUser(targetAppUserId, roleName)
        if (existing != null)
        {
            val beforeState = assignmentSnapshot(existing)
            if (!existing.isActive || existing.isExpired())
            {
                existing.isActive = true
                existing.grantedByAppUserId = actorId
                existing.grantedAt = Timestamp.from(Instant.now())
                existing.expiresAt = null
                appRoleAssignmentRepository.update(existing)
            }
            val afterState = assignmentSnapshot(existing)
            emitRequiredAudit(
                action = "APP_ADMIN_GRANT",
                actorId = actorId,
                assignment = existing,
                reason = if (beforeState == afterState) "already granted" else "regranted",
                beforeState = beforeState,
                afterState = afterState,
            )
            return existing
        }

        val assignment = AppRoleAssignment().apply {
            appUserId = targetAppUserId
            this.roleName = roleName
            grantedByAppUserId = actorId
            isActive = true
        }
        appRoleAssignmentRepository.save(assignment)
        emitRequiredAudit(
            action = "APP_ADMIN_GRANT",
            actorId = actorId,
            assignment = assignment,
            reason = "granted",
            beforeState = "absent",
            afterState = assignmentSnapshot(assignment),
        )
        return assignment
    }

    /**
     * Revoke (soft-delete) an APP-scope assignment. Refuses to remove the last effective APP_ADMIN.
     */
    @Transactional
    fun revokeAppRole(assignmentId: UUID, actorId: UUID?)
    {
        val assignment = appRoleAssignmentRepository.findById(assignmentId)
            ?: throw IllegalArgumentException("Role assignment not found")

        val beforeState = assignmentSnapshot(assignment)
        if (!assignment.isActive)
        {
            emitRequiredAudit(
                action = "APP_ADMIN_REVOKE",
                actorId = actorId,
                assignment = assignment,
                reason = "already revoked",
                beforeState = beforeState,
                afterState = beforeState,
            )
            return
        }

        val isAppAdmin = assignment.roleName == AppRoleName.APP_ADMIN
        if (
            isAppAdmin
            && appRoleAssignmentRepository.isEffective(assignment.id)
            && appRoleAssignmentRepository.countActiveAppAdmins() <= 1
        )
        {
            throw LastAppAdminException()
        }

        assignment.isActive = false
        appRoleAssignmentRepository.update(assignment)
        emitRequiredAudit(
            action = "APP_ADMIN_REVOKE",
            actorId = actorId,
            assignment = assignment,
            reason = "revoked",
            beforeState = beforeState,
            afterState = assignmentSnapshot(assignment),
        )
    }

    /**
     * First-run bootstrap: if no effective App Admin exists and `app.security.app-admin.bootstrap-email`
     * names an existing user, promote them to APP_ADMIN. Idempotent and safe to run on every boot
     * (incl. a fresh from-empty migration run). Blank/absent config disables it; an unmatched email
     * is logged and skipped (the user can be created later and re-bootstrapped on the next boot).
     */
    @Transactional
    fun bootstrapFirstAppAdmin(@Observes event: StartupEvent)
    {
        val email = configurationService.getBootstrapAppAdminEmail() ?: return
        if (appRoleAssignmentRepository.countActiveAppAdmins() > 0)
        {
            logger.debug("App Admin bootstrap skipped: an active App Admin already exists")
            return
        }
        val user = appUserService.findByEmail(email)
        if (user == null)
        {
            logger.warn("App Admin bootstrap: no user found for configured email '{}', skipping", email.maskEmailForLogs())
            return
        }
        if (!user.isActive || user.deprovisionedAt != null)
        {
            logger.warn(
                "App Admin bootstrap: configured user '{}' is inactive or deprovisioned, skipping",
                email.maskEmailForLogs(),
            )
            return
        }
        grantAppRole(user.id, AppRoleName.APP_ADMIN, actorId = null)
        logger.info("App Admin bootstrap: granted APP_ADMIN to '{}'", email.maskEmailForLogs())
    }

    private fun AppRoleAssignment.isExpired(now: Instant = Instant.now()): Boolean =
        expiresAt?.toInstant()?.isAfter(now) == false

    private fun emitRequiredAudit(
        action: String,
        actorId: UUID?,
        assignment: AppRoleAssignment,
        reason: String,
        beforeState: String,
        afterState: String,
    )
    {
        authAuditService.emitRequired(
            action = action,
            outcome = "SUCCESS",
            actorId = actorId,
            actorRole = actorId?.let { AppRoleName.APP_ADMIN.name } ?: "SYSTEM",
            reason = "$reason ${assignment.roleName.name}",
            targetType = "APP_USER",
            targetId = assignment.appUserId.toString(),
            structuredDetails = mapOf(
                "assignment_id" to assignment.id.toString(),
                "before_state" to beforeState,
                "after_state" to afterState,
            ),
        )
    }

    private fun assignmentSnapshot(assignment: AppRoleAssignment): String =
        "role=${assignment.roleName.name};active=${assignment.isActive};expiresAt=${assignment.expiresAt?.toInstant()}"
}
