package com.docuhyphen.app.api.repository.organization

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class OrganizationMembershipRepository :
    BaseRepository<OrganizationMembership>(OrganizationMembership::class.java)
{
    /** Active member count for an organization. */
    fun countActiveMembersOfOrg(organizationId: UUID): Long =
        entityManager.createQuery(
            """SELECT COUNT(m) FROM OrganizationMembership m
               WHERE m.organizationId = :oid AND m.status = :status""",
            Long::class.java,
        )
            .setParameter("oid", organizationId)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .singleResult ?: 0

    fun countActiveProvisionedMembers(organizationIds: Collection<UUID>): Map<UUID, Long>
    {
        if (organizationIds.isEmpty())
        {
            return emptyMap()
        }

        return entityManager.createQuery(
            """SELECT m.organizationId, COUNT(m)
               FROM OrganizationMembership m
               JOIN AppUser u ON u.id = m.appUserId
               WHERE m.organizationId IN :organizationIds
                 AND m.status = :status
                 AND u.isActive = true
                 AND u.isTemporary = false
                 AND u.deprovisionedAt IS NULL
               GROUP BY m.organizationId""",
            Array<Any>::class.java,
        )
            .setParameter("organizationIds", organizationIds)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .resultList
            .associate { row -> row[0] as UUID to row[1] as Long }
    }

    /** Deletes all membership rows for a user in an org (used on hard-delete of the user). */
    @Transactional
    fun deleteByUserAndOrg(appUserId: UUID, organizationId: UUID): Int =
        entityManager.createQuery(
            """DELETE FROM OrganizationMembership m
               WHERE m.appUserId = :uid AND m.organizationId = :oid""",
        )
            .setParameter("uid", appUserId)
            .setParameter("oid", organizationId)
            .executeUpdate()

    fun findActiveByUser(appUserId: UUID): List<OrganizationMembership> =
        entityManager.createQuery(
            """SELECT m FROM OrganizationMembership m
               WHERE m.appUserId = :uid AND m.status = :status""",
            OrganizationMembership::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .resultList

    fun findActiveByUserAndOrg(appUserId: UUID, organizationId: UUID): OrganizationMembership? =
        entityManager.createQuery(
            """SELECT m FROM OrganizationMembership m
               WHERE m.appUserId = :uid AND m.organizationId = :oid AND m.status = :status""",
            OrganizationMembership::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("oid", organizationId)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .resultList
            .firstOrNull()

    fun findByUserAndOrg(appUserId: UUID, organizationId: UUID): OrganizationMembership? =
        entityManager.createQuery(
            """SELECT m FROM OrganizationMembership m
               WHERE m.appUserId = :uid AND m.organizationId = :oid""",
            OrganizationMembership::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("oid", organizationId)
            .resultList
            .firstOrNull()

    fun findActiveMembershipsByOrganizationAndExactEmail(
        organizationId: UUID,
        normalizedEmail: String,
    ): List<OrganizationMembership> =
        entityManager.createQuery(
            """SELECT m FROM OrganizationMembership m
               JOIN AppUser u ON u.id = m.appUserId
               WHERE m.organizationId = :organizationId
                 AND m.status = :status
                 AND LOWER(u.email) = :normalizedEmail""",
            OrganizationMembership::class.java,
        )
            .setParameter("organizationId", organizationId)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .setParameter("normalizedEmail", normalizedEmail)
            .resultList

    fun findActiveMembersOfOrg(organizationId: UUID): List<OrganizationMembership> =
        entityManager.createQuery(
            """SELECT m FROM OrganizationMembership m
               WHERE m.organizationId = :oid AND m.status = :status""",
            OrganizationMembership::class.java,
        )
            .setParameter("oid", organizationId)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .resultList

    fun findPrimaryForUser(appUserId: UUID): OrganizationMembership? =
        entityManager.createQuery(
            """SELECT m FROM OrganizationMembership m
               WHERE m.appUserId = :uid AND m.isPrimary = true AND m.status = :status""",
            OrganizationMembership::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("status", OrganizationMembershipStatus.ACTIVE)
            .resultList
            .firstOrNull()
}

