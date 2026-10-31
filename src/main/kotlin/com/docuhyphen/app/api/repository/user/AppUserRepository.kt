package com.docuhyphen.app.api.repository.user

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import java.util.*

@RequestScoped
class AppUserRepository : BaseRepository<AppUser>(AppUser::class.java)
{
    fun hasRetainedAccountRecords(appUserId: UUID): Boolean =
        entityManager.createNativeQuery(
            """
            SELECT EXISTS (
                SELECT 1 FROM field_definition WHERE scope_user_id = :userId OR created_by_app_user_id = :userId
                UNION ALL SELECT 1 FROM schema_definition WHERE scope_user_id = :userId OR created_by_app_user_id = :userId
                UNION ALL SELECT 1 FROM information_request_template_definition WHERE scope_user_id = :userId
                UNION ALL SELECT 1 FROM external_participant WHERE owner_app_user_id = :userId
                UNION ALL SELECT 1 FROM participant_account_link WHERE app_user_id = :userId
                UNION ALL SELECT 1 FROM information_request WHERE owner_user_id = :userId
            )
        """.trimIndent()
        ).setParameter("userId", appUserId).singleResult as Boolean

    fun findByOrganizationId(organizationId: UUID): List<AppUser>
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            """SELECT DISTINCT u FROM AppUser u, OrganizationMembership m
               WHERE m.appUserId = u.id AND m.organizationId = :oid AND m.status = :status""",
            AppUser::class.java,
        )
        query.setParameter("oid", organizationId)
        query.setParameter("status", OrganizationMembershipStatus.ACTIVE)
        return query.resultList
    }

    fun findByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email)",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    fun findByIdWithPerson(id: UUID): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            """SELECT a FROM AppUser a
               LEFT JOIN FETCH a.person
               WHERE a.id = :id""",
            AppUser::class.java,
        )
        query.setParameter("id", id)
        return query.resultList.firstOrNull()
    }

    fun findActiveByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email) AND a.isTemporary = false",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    fun findTemporaryByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email) AND a.isTemporary = true",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    fun findAllActiveUsers(): List<AppUser>
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE a.isActive = true",
            AppUser::class.java
        )
        return query.resultList
    }

    fun searchActiveUsers(query: String, limit: Int = 20): List<AppUser>
    {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val pattern = "%${q.lowercase()}%"
        val typed: TypedQuery<AppUser> = entityManager.createQuery(
            """SELECT a FROM AppUser a
               LEFT JOIN a.person p
               WHERE a.isActive = true
                 AND a.isTemporary = false
                 AND (LOWER(a.email) LIKE :pattern
                      OR LOWER(p.firstName) LIKE :pattern
                      OR LOWER(p.lastName) LIKE :pattern)
               ORDER BY LOWER(a.email)""",
            AppUser::class.java,
        )
        typed.setParameter("pattern", pattern)
        typed.maxResults = limit.coerceIn(1, 50)
        return typed.resultList
    }

    fun findForSubscriptionAdministration(query: String?, limit: Int, offset: Int): List<AppUser>
    {
        val normalized = query?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val jpql = buildString {
            append("SELECT a FROM AppUser a LEFT JOIN FETCH a.person p ")
            append("WHERE a.isTemporary = false AND a.application IS NULL ")
            if (normalized != null)
            {
                append("AND (LOWER(a.email) LIKE :query OR LOWER(p.firstName) LIKE :query OR LOWER(p.lastName) LIKE :query) ")
            }
            append("ORDER BY LOWER(a.email)")
        }
        val typed = entityManager.createQuery(jpql, AppUser::class.java)
        normalized?.let { typed.setParameter("query", "%$it%") }
        typed.firstResult = offset
        typed.maxResults = limit
        return typed.resultList
    }

    fun countForSubscriptionAdministration(query: String?): Long
    {
        val normalized = query?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val jpql = buildString {
            append("SELECT COUNT(a) FROM AppUser a LEFT JOIN a.person p ")
            append("WHERE a.isTemporary = false AND a.application IS NULL ")
            if (normalized != null)
            {
                append("AND (LOWER(a.email) LIKE :query OR LOWER(p.firstName) LIKE :query OR LOWER(p.lastName) LIKE :query)")
            }
        }
        val typed = entityManager.createQuery(jpql, java.lang.Long::class.java)
        normalized?.let { typed.setParameter("query", "%$it%") }
        return typed.singleResult.toLong()
    }

    fun detach(user: AppUser)
    {
        entityManager.detach(user)
    }

    fun findIdsWithAvatar(ids: Collection<UUID>): Set<UUID>
    {
        if (ids.isEmpty()) return emptySet()
        val query: TypedQuery<UUID> = entityManager.createQuery(
            "SELECT a.id FROM AppUser a WHERE a.id IN :ids AND a.avatarStorageKey IS NOT NULL",
            UUID::class.java,
        )
        query.setParameter("ids", ids)
        return query.resultList.toSet()
    }
}
