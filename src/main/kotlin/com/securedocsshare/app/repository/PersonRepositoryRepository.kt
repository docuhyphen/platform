package com.securedocsshare.app.repository

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.Person
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class PersonRepositoryRepository : BaseRepository<Person>(Person::class.java)
{
    fun existsByIdentificationNumber(string: String): Boolean
    {
        val query: TypedQuery<Long> = entityManager.createQuery(
            "SELECT COUNT(p) FROM Person p WHERE p.identificationNumber = :identificationNumber",
            Long::class.java
        )

        query.setParameter("identificationNumber", string)

        return query.singleResult > 0
    }

}
