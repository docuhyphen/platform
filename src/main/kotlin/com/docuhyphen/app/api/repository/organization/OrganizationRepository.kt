package com.docuhyphen.app.api.repository.organization

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.Organization
import jakarta.enterprise.context.RequestScoped
import java.util.*

@RequestScoped
class OrganizationRepository : BaseRepository<Organization>(Organization::class.java)
{
    fun existsByRegistrationNumber(registrationNumber: String): Boolean
    {
        val query = entityManager.createQuery(
            "SELECT COUNT(c) FROM Organization c WHERE LOWER(c.registrationNumber) = LOWER(:registrationNumber)",
            Long::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.singleResult > 0
    }

    fun findByRegistrationNumber(registrationNumber: String): Organization?
    {
        val query = entityManager.createQuery(
            "SELECT COUNT(c) FROM Organization c WHERE LOWER(c.registrationNumber) = LOWER(:registrationNumber)",
            Organization::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.resultList.firstOrNull()
    }

    fun findByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Organization?
    {
        // Resolve the caller's organization through `organization_membership` (the replacement
        // for the retired `app_user.organization_id` join). Primary membership wins, then any
        // other ACTIVE membership. The personId guard preserves the legacy contract that the
        // user row actually carries that person.
        val query = entityManager.createQuery(
            """SELECT o FROM Organization o, OrganizationMembership m, AppUser u
               WHERE m.organizationId = o.id
                 AND m.appUserId = u.id
                 AND u.id = :appUserId
                 AND u.person.id = :personId
                 AND m.status = com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus.ACTIVE
               ORDER BY m.isPrimary DESC""",
            Organization::class.java
        )
        query.setParameter("appUserId", appUserId)
        query.setParameter("personId", personId)
        return query.resultList.firstOrNull()
    }

    fun findByAppUserIdAndAppId(appUserId: UUID, appId: UUID): Organization
    {
        TODO("Not implemented")

//        val query = entityManager.createQuery(
//            "SELECT c FROM Organization c JOIN c.appUsers u WHERE u.id = :appUserId AND u.application.id = :appId",
//            Organization::class.java
//        )
//        query.setParameter("appUserId", appUserId)
//        query.setParameter("appId", appId)
//        return query.resultList.firstOrNull()
//            ?: throw IllegalArgumentException("Organization not found for appUserId: $appUserId and appId: $appId")
    }

    fun searchDiscoverableForTrustRequests(
        activeOrganizationId: UUID,
        normalizedQuery: String,
        limit: Int,
    ): List<Organization>
    {
        val query = entityManager.createQuery(
            """SELECT o FROM Organization o
               WHERE o.id <> :activeOrganizationId
                 AND o.isActive = true
                 AND o.verificationComplete = true
                 AND o.settings.discoverableForTrustRequests = true
                 AND (LOWER(o.name) LIKE :nameQuery OR LOWER(o.registrationNumber) = :exactQuery)
               ORDER BY LOWER(o.name) ASC, o.id ASC""",
            Organization::class.java,
        )
        query.setParameter("activeOrganizationId", activeOrganizationId)
        query.setParameter("nameQuery", "%$normalizedQuery%")
        query.setParameter("exactQuery", normalizedQuery)
        query.maxResults = limit.coerceIn(1, 100)
        return query.resultList
    }

    fun findForPlatformAdministration(
        normalizedQuery: String?,
        active: Boolean?,
        tierCode: String?,
        sort: String,
        direction: String,
        limit: Int,
        offset: Int,
    ): List<Organization>
    {
        val orderField = if (sort == "createdDate") "o.createdDate" else "LOWER(o.name)"
        val orderDirection = if (direction == "desc") "DESC" else "ASC"
        val query = entityManager.createQuery(
            """SELECT o FROM Organization o
               LEFT JOIN OrganizationSubscriptionPolicy p ON p.organization = o
               WHERE (:query IS NULL
                      OR LOWER(o.name) LIKE :queryPattern
                      OR LOWER(o.registrationNumber) LIKE :queryPattern)
                 AND (:active IS NULL OR o.isActive = :active)
                 AND (:tierCode IS NULL OR COALESCE(p.tierCode, 'FREE') = :tierCode)
               ORDER BY $orderField $orderDirection, o.id ASC""",
            Organization::class.java,
        )
        applyPlatformFilters(query, normalizedQuery, active, tierCode)
        query.firstResult = offset
        query.maxResults = limit
        return query.resultList
    }

    fun countForPlatformAdministration(
        normalizedQuery: String?,
        active: Boolean?,
        tierCode: String?,
    ): Long
    {
        val query = entityManager.createQuery(
            """SELECT COUNT(o) FROM Organization o
               LEFT JOIN OrganizationSubscriptionPolicy p ON p.organization = o
               WHERE (:query IS NULL
                      OR LOWER(o.name) LIKE :queryPattern
                      OR LOWER(o.registrationNumber) LIKE :queryPattern)
                 AND (:active IS NULL OR o.isActive = :active)
                 AND (:tierCode IS NULL OR COALESCE(p.tierCode, 'FREE') = :tierCode)""",
            Long::class.java,
        )
        applyPlatformFilters(query, normalizedQuery, active, tierCode)
        return query.singleResult
    }

    private fun applyPlatformFilters(
        query: jakarta.persistence.Query,
        normalizedQuery: String?,
        active: Boolean?,
        tierCode: String?,
    )
    {
        query.setParameter("query", normalizedQuery)
        query.setParameter("queryPattern", normalizedQuery?.let { "%$it%" })
        query.setParameter("active", active)
        query.setParameter("tierCode", tierCode)
    }
}
