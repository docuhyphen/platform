package com.docuhyphen.app.api.repository.user

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.Person
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class PersonRepositoryRepository : BaseRepository<Person>(Person::class.java)
{
    fun existsByIdentificationNumber(string: String): Boolean
    {
        val query: TypedQuery<Long> = entityManager.createQuery(
            "SELECT COUNT(p) FROM Person p WHERE LOWER(p.identificationNumber) = LOWER(:identificationNumber)",
            Long::class.java
        )

        query.setParameter("identificationNumber", string)

        return query.singleResult > 0
    }

}
