package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.OrganizationSharingSessionLink
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import java.util.UUID

@RequestScoped
class OrganizationSharingSessionLinkRepository :
    BaseRepository<OrganizationSharingSessionLink>(OrganizationSharingSessionLink::class.java)
{
    fun findByRequestingOrganization(requestingOrganizationId: UUID): List<OrganizationSharingSessionLink>
    {
        val query: TypedQuery<OrganizationSharingSessionLink> = entityManager.createQuery(
            """
                SELECT o 
                FROM OrganizationSharingSessionLink o 
                WHERE o.requestingOrganization.id = :requestingOrganizationId""".trimIndent(),
            OrganizationSharingSessionLink::class.java
        )
        query.setParameter("requestingOrganizationId", requestingOrganizationId)
        return query.resultList
    }

    fun findByRequestedOrganization(requestedOrganizationId: UUID): List<OrganizationSharingSessionLink>
    {
        val query: TypedQuery<OrganizationSharingSessionLink> = entityManager.createQuery(
            """
                SELECT o 
                FROM OrganizationSharingSessionLink o
                WHERE o.requestedOrganization.id = :requestedOrganizationId""".trimIndent(),
            OrganizationSharingSessionLink::class.java
        )
        query.setParameter("requestedOrganizationId", requestedOrganizationId)
        return query.resultList
    }
}
