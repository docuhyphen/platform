package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.Company
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class CompanyRepository : BaseRepository<Company>(Company::class.java)
{
    fun existsByRegistrationNumber(registrationNumber: String): Boolean
    {
        val query = entityManager.createQuery(
            "SELECT COUNT(c) FROM Company c WHERE c.registrationNumber = :registrationNumber",
            Long::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.singleResult > 0
    }

    fun findByRegistrationNumber(registrationNumber: String): Company?
    {
        val query = entityManager.createQuery(
            "SELECT c FROM Company c WHERE c.registrationNumber = :registrationNumber",
            Company::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.resultList.firstOrNull()
    }

    fun findByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Company?
    {
        val query = entityManager.createQuery(
            "SELECT c FROM Company c JOIN c.appUsers u WHERE u.id = :appUserId AND u.person.id = :personId",
            Company::class.java
        )
        query.setParameter("appUserId", appUserId)
        query.setParameter("personId", personId)
        return query.resultList.firstOrNull()
    }
}