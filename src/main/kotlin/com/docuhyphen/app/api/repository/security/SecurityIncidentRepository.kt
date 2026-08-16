package com.docuhyphen.app.api.repository.security

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.SecurityIncident
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class SecurityIncidentRepository : BaseRepository<SecurityIncident>(SecurityIncident::class.java)
{
    fun findRecent(limit: Int, incidentType: String? = null): List<SecurityIncident>
    {
        val hasType = !incidentType.isNullOrBlank()
        val queryString = if (hasType)
        {
            "SELECT s FROM SecurityIncident s WHERE s.incidentType = :incidentType ORDER BY s.createdDate DESC"
        }
        else
        {
            "SELECT s FROM SecurityIncident s ORDER BY s.createdDate DESC"
        }

        val query: TypedQuery<SecurityIncident> = entityManager.createQuery(queryString, SecurityIncident::class.java)
        if (hasType)
        {
            query.setParameter("incidentType", incidentType)
        }

        return query.setMaxResults(limit.coerceIn(1, 100)).resultList
    }
}

