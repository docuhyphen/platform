package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationExchangeLink
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import java.util.UUID

@RequestScoped
class OrganizationExchangeLinkRepository :
    BaseRepository<OrganizationExchangeLink>(OrganizationExchangeLink::class.java)
{
    fun findByRequestingOrganization(requestingOrganizationId: UUID): List<OrganizationExchangeLink>
    {
        val query: TypedQuery<OrganizationExchangeLink> = entityManager.createQuery(
            """
                SELECT o 
                FROM OrganizationExchangeLink o 
                WHERE o.requestingOrganization.id = :requestingOrganizationId""".trimIndent(),
            OrganizationExchangeLink::class.java
        )
        query.setParameter("requestingOrganizationId", requestingOrganizationId)
        return query.resultList
    }

    fun findByRequestedOrganization(requestedOrganizationId: UUID): List<OrganizationExchangeLink>
    {
        val query: TypedQuery<OrganizationExchangeLink> = entityManager.createQuery(
            """
                SELECT o 
                FROM OrganizationExchangeLink o
                WHERE o.requestedOrganization.id = :requestedOrganizationId""".trimIndent(),
            OrganizationExchangeLink::class.java
        )
        query.setParameter("requestedOrganizationId", requestedOrganizationId)
        return query.resultList
    }
}
