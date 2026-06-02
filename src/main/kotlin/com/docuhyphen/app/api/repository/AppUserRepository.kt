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
     * with an ACTIVE membership — including users whose own `isActive` flag is false, since
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

    fun detach(user: AppUser)
    {
        entityManager.detach(user)
    }
}
