package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.RoleAssignment
import com.docuhyphen.app.api.model.entity.RoleScopeType
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class RoleAssignmentRepository : BaseRepository<RoleAssignment>(RoleAssignment::class.java)
{
    fun findActiveForUser(appUserId: UUID): List<RoleAssignment> =
        entityManager.createQuery(
            """SELECT r FROM RoleAssignment r
               WHERE r.appUserId = :uid AND r.isActive = true""",
            RoleAssignment::class.java,
        )
            .setParameter("uid", appUserId)
            .resultList

    fun findActiveForUserInScope(
        appUserId: UUID,
        scopeType: RoleScopeType,
        scopeId: UUID?,
    ): List<RoleAssignment> =
        if (scopeId == null)
        {
            entityManager.createQuery(
                """SELECT r FROM RoleAssignment r
                   WHERE r.appUserId = :uid AND r.scopeType = :st AND r.scopeId IS NULL AND r.isActive = true""",
                RoleAssignment::class.java,
            )
                .setParameter("uid", appUserId)
                .setParameter("st", scopeType)
                .resultList
        }
        else
        {
            entityManager.createQuery(
                """SELECT r FROM RoleAssignment r
                   WHERE r.appUserId = :uid AND r.scopeType = :st AND r.scopeId = :sid AND r.isActive = true""",
                RoleAssignment::class.java,
            )
                .setParameter("uid", appUserId)
                .setParameter("st", scopeType)
                .setParameter("sid", scopeId)
                .resultList
        }

    /** Count of active APP_ADMIN role assignments, used to enforce the "keep ≥1 admin" invariant. */
    fun countActiveAppAdmins(): Long =
        entityManager.createQuery(
            """SELECT COUNT(r) FROM RoleAssignment r
               WHERE r.roleName = 'APP_ADMIN' AND r.scopeType = :st AND r.isActive = true""",
            Long::class.java,
        )
            .setParameter("st", RoleScopeType.APP)
            .singleResult ?: 0

    /** Active APP_ADMIN assignments (APP scope). */
    fun findActiveAppAdmins(): List<RoleAssignment> =
        entityManager.createQuery(
            """SELECT r FROM RoleAssignment r
               WHERE r.roleName = 'APP_ADMIN' AND r.scopeType = :st AND r.isActive = true""",
            RoleAssignment::class.java,
        )
            .setParameter("st", RoleScopeType.APP)
            .resultList

    /**
     * A user's APP-scope assignment for a given role (any status), if one exists. Used to make
     * grants idempotent, reactivate a soft-deleted row rather than inserting a duplicate.
     */
    fun findAppRoleForUser(appUserId: UUID, roleName: String): RoleAssignment? =
        entityManager.createQuery(
            """SELECT r FROM RoleAssignment r
               WHERE r.appUserId = :uid AND r.roleName = :rn AND r.scopeType = :st AND r.scopeId IS NULL""",
            RoleAssignment::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("rn", roleName)
            .setParameter("st", RoleScopeType.APP)
            .resultList
            .firstOrNull()
}

