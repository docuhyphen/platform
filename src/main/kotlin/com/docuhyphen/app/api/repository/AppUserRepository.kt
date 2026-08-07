package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import java.util.UUID

@RequestScoped
class AppUserRepository : BaseRepository<AppUser>(AppUser::class.java)
{
    /**
     * Users belonging to an organization, resolved through `organization_membership`
     * (the replacement for the retired `app_user.organization_id` join). Returns members
     * with an ACTIVE membership, including users whose own `isActive` flag is false, since
     * deactivation flips `AppUser.isActive` but leaves the membership ACTIVE so admins can
     * still see/reactivate them.
     */
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

    /**
     * Variant that excludes temporary placeholder rows (created by the no-auth recipient
     * flow when an initiator shares with an email that doesn't yet have an account). Used
     * by the sign-up path so a temp row doesn't false-positive an "email already in use" check.
     */
    fun findActiveByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email) AND a.isTemporary = false",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    /** Returns a temp placeholder row if one exists for this email, otherwise null. */
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

    /**
     * Case-insensitive search across email + first/last name for the App
     * Admins picker (admin-scope, audit-logged at the resource layer). Excludes temporary
     * placeholder users since they can't be granted app-admin until they sign up. Returns
     * at most [limit] rows (capped at 50 to keep the response small).
     */
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

    fun detach(user: AppUser)
    {
        entityManager.detach(user)
    }

    /**
     * Of the supplied user ids, returns the subset that currently has a stored profile
     * picture. A single query keeps avatar enrichment of a contact/member list free of
     * per-row lookups.
     */
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
