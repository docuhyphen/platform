package com.securedocsshare.app.repository

import com.securedocsshare.app.api.model.Company
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class CompanyRepository : BaseRepository<Company>(Company::class.java)
{

    fun findByRegistrationNumber(registrationNumber: String): Company?
    {
        val query = entityManager.createQuery(
            "SELECT c FROM Company c WHERE c.registrationNumber = :registrationNumber",
            Company::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.resultList.firstOrNull()
    }
}
