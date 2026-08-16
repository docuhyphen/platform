package com.docuhyphen.app.api.repository.identity

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityDomain
import com.docuhyphen.app.api.model.entity.OrganizationIdentityDomainStatus
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class OrganizationIdentityDomainRepository : BaseRepository<OrganizationIdentityDomain>(OrganizationIdentityDomain::class.java)
{
    fun findByOrganizationIdAndDomain(organizationId: UUID, domain: String): OrganizationIdentityDomain?
    {
        val query = entityManager.createQuery(
            """SELECT d FROM OrganizationIdentityDomain d
               WHERE d.organization.id = :organizationId AND d.domain = :domain""",
            OrganizationIdentityDomain::class.java,
        )
        query.setParameter("organizationId", organizationId)
        query.setParameter("domain", domain)
        return query.resultList.firstOrNull()
    }

    fun findVerifiedByDomain(domain: String): OrganizationIdentityDomain?
    {
        val query = entityManager.createQuery(
            """SELECT d FROM OrganizationIdentityDomain d
               WHERE d.domain = :domain AND d.status = :status""",
            OrganizationIdentityDomain::class.java,
        )
        query.setParameter("domain", domain)
        query.setParameter("status", OrganizationIdentityDomainStatus.VERIFIED)
        return query.resultList.firstOrNull()
    }

    fun findByOrganizationId(organizationId: UUID): List<OrganizationIdentityDomain>
    {
        val query = entityManager.createQuery(
            """SELECT d FROM OrganizationIdentityDomain d
               WHERE d.organization.id = :organizationId
               ORDER BY d.domain ASC""",
            OrganizationIdentityDomain::class.java,
        )
        query.setParameter("organizationId", organizationId)
        return query.resultList
    }

    fun findVerifiedOrganizationByDomain(domain: String): Organization?
    {
        val query = entityManager.createQuery(
            """SELECT o FROM OrganizationIdentityDomain d JOIN d.organization o
               WHERE d.domain = :domain
                 AND d.status = :status
                 AND o.isActive = true
                 AND o.verificationComplete = true""",
            Organization::class.java,
        )
        query.setParameter("domain", domain)
        query.setParameter("status", OrganizationIdentityDomainStatus.VERIFIED)
        return query.resultList.firstOrNull()
    }

    fun findAllVerifiedOrganizationsByDomain(domain: String): List<Organization>
    {
        return listOfNotNull(findVerifiedOrganizationByDomain(domain))
    }
}
