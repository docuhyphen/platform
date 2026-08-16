package com.docuhyphen.app.api.repository.organization

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PrincipalGroupRepository : BaseRepository<PrincipalGroup>(PrincipalGroup::class.java)
{
    fun findByOwnerOrg(organizationId: UUID): List<PrincipalGroup> =
        entityManager.createQuery(
            """SELECT g FROM PrincipalGroup g
               WHERE g.ownerOrganizationId = :oid AND g.isActive = true""",
            PrincipalGroup::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList

    fun findByOwnerUser(appUserId: UUID): List<PrincipalGroup> =
        entityManager.createQuery(
            """SELECT g FROM PrincipalGroup g
               WHERE g.ownerAppUserId = :uid AND g.isActive = true""",
            PrincipalGroup::class.java,
        )
            .setParameter("uid", appUserId)
            .resultList

    fun findExternallyPublishedFor(organizationId: UUID): List<PrincipalGroup> =
        entityManager.createQuery(
            """SELECT g FROM PrincipalGroup g
               WHERE g.scope = :scope
                 AND g.ownerOrganizationId = :oid
                 AND g.externallyPublished = true
                 AND g.isActive = true""",
            PrincipalGroup::class.java,
        )
            .setParameter("scope", PrincipalGroupScope.ORG)
            .setParameter("oid", organizationId)
            .resultList
}

