package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class AppRoleAssignmentRepository : BaseRepository<AppRoleAssignment>(AppRoleAssignment::class.java)
{
    fun findActiveForUser(appUserId: UUID): List<AppRoleAssignment> =
        entityManager.createQuery(
            """SELECT r FROM AppRoleAssignment r
               WHERE r.appUserId = :uid AND r.isActive = true""",
            AppRoleAssignment::class.java,
        )
            .setParameter("uid", appUserId)
            .resultList

    fun countActiveAppAdmins(): Long =
        entityManager.createQuery(
            """SELECT COUNT(r) FROM AppRoleAssignment r
               WHERE r.roleName = :role AND r.isActive = true""",
            Long::class.java,
        )
            .setParameter("role", AppRoleName.APP_ADMIN)
            .singleResult ?: 0

    fun findActiveAppAdmins(): List<AppRoleAssignment> =
        entityManager.createQuery(
            """SELECT r FROM AppRoleAssignment r
               WHERE r.roleName = :role AND r.isActive = true""",
            AppRoleAssignment::class.java,
        )
            .setParameter("role", AppRoleName.APP_ADMIN)
            .resultList

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
