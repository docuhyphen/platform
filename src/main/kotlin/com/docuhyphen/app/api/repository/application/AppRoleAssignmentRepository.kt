package com.docuhyphen.app.api.repository.application

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class AppRoleAssignmentRepository : BaseRepository<AppRoleAssignment>(AppRoleAssignment::class.java)
{
    fun findActiveForUser(appUserId: UUID): List<AppRoleAssignment> =
        entityManager.createQuery(
            """SELECT r FROM AppRoleAssignment r, AppUser u
               WHERE r.appUserId = :uid
                 AND u.id = r.appUserId
                 AND r.isActive = true
                 AND (r.expiresAt IS NULL OR r.expiresAt > CURRENT_TIMESTAMP)
                 AND u.isActive = true
                 AND u.deprovisionedAt IS NULL""",
            AppRoleAssignment::class.java,
        )
            .setParameter("uid", appUserId)
            .resultList

    fun countActiveAppAdmins(): Long =
        entityManager.createQuery(
            """SELECT COUNT(r) FROM AppRoleAssignment r, AppUser u
               WHERE r.roleName = :role
                 AND u.id = r.appUserId
                 AND r.isActive = true
                 AND (r.expiresAt IS NULL OR r.expiresAt > CURRENT_TIMESTAMP)
                 AND u.isActive = true
                 AND u.deprovisionedAt IS NULL""",
            Long::class.java,
        )
            .setParameter("role", AppRoleName.APP_ADMIN)
            .singleResult ?: 0

    fun findActiveAppAdmins(): List<AppRoleAssignment> =
        entityManager.createQuery(
            """SELECT r FROM AppRoleAssignment r, AppUser u
               WHERE r.roleName = :role
                 AND u.id = r.appUserId
                 AND r.isActive = true
                 AND (r.expiresAt IS NULL OR r.expiresAt > CURRENT_TIMESTAMP)
                 AND u.isActive = true
                 AND u.deprovisionedAt IS NULL""",
            AppRoleAssignment::class.java,
        )
            .setParameter("role", AppRoleName.APP_ADMIN)
            .resultList

    fun isEffective(assignmentId: UUID): Boolean =
        entityManager.createQuery(
            """SELECT COUNT(r) FROM AppRoleAssignment r, AppUser u
               WHERE r.id = :assignmentId
                 AND u.id = r.appUserId
                 AND r.isActive = true
                 AND (r.expiresAt IS NULL OR r.expiresAt > CURRENT_TIMESTAMP)
                 AND u.isActive = true
                 AND u.deprovisionedAt IS NULL""",
            Long::class.java,
        )
            .setParameter("assignmentId", assignmentId)
            .singleResult > 0

    fun findAppRoleForUser(appUserId: UUID, roleName: AppRoleName): AppRoleAssignment? =
        entityManager.createQuery(
            """SELECT r FROM AppRoleAssignment r
               WHERE r.appUserId = :uid AND r.roleName = :role""",
            AppRoleAssignment::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("role", roleName)
            .resultList
            .firstOrNull()
}
