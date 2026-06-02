package com.docuhyphen.app.api.repository

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

    fun getLinkedOrganizations(id: UUID, includePublic: Boolean = false): List<Organization>
    {
        val query = entityManager.createQuery(
            """
        SELECT DISTINCT o FROM Organization o
        WHERE
            o.id <> :orgId AND (
                o.id IN (
                    SELECT l.requestedOrganization.id FROM OrganizationSharingSessionLink l
                    WHERE l.requestingOrganization.id = :orgId AND l.status = 'ACCEPTED'
                    UNION
                    SELECT l.requestingOrganization.id FROM OrganizationSharingSessionLink l
                    WHERE l.requestedOrganization.id = :orgId AND l.status = 'ACCEPTED'
                )
                OR o.settings.allowShareWithoutPairing = :includePublic
            )
        """.trimIndent(),
            Organization::class.java
        )
        query.setParameter("orgId", id)
        query.setParameter("includePublic", includePublic)
        return query.resultList
    }

    fun findByVerifiedContactEmailDomain(domain: String): Organization?
    {
        val query = entityManager.createQuery(
            """
                SELECT o FROM Organization o
                WHERE o.isActive = true
                  AND o.verificationComplete = true
                  AND o.contactDetails.email IS NOT NULL
                  AND LOWER(SUBSTRING(o.contactDetails.email, LOCATE('@', o.contactDetails.email) + 1)) = :domain
            """.trimIndent(),
            Organization::class.java,
        )
        query.setParameter("domain", domain.lowercase())
        return query.resultList.firstOrNull()
    }

    fun findAllByVerifiedContactEmailDomain(domain: String): List<Organization>
    {
        val query = entityManager.createQuery(
            """
                SELECT o FROM Organization o
                WHERE o.isActive = true
                  AND o.verificationComplete = true
                  AND o.contactDetails.email IS NOT NULL
                  AND LOWER(SUBSTRING(o.contactDetails.email, LOCATE('@', o.contactDetails.email) + 1)) = :domain
            """.trimIndent(),
            Organization::class.java,
        )
        query.setParameter("domain", domain.lowercase())
        return query.resultList
    }
}