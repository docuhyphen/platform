package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.ContactDetails
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class ContactDetailsRepository : BaseRepository<ContactDetails>(ContactDetails::class.java)
{
    fun findByPhoneNumber(phoneNumber: String): ContactDetails?
    {
        return try {
            entityManager.createQuery(
                "SELECT cd FROM ContactDetails cd WHERE cd.phoneNumber = :phoneNumber",
                ContactDetails::class.java
            )
                .setParameter("phoneNumber", phoneNumber)
                .singleResult
        }
        catch (e: Exception)
        {
            null
        }
    }

    fun findByEmail(email: String): ContactDetails?
    {
        return try
        {

        entityManager.createQuery(
            "SELECT cd FROM ContactDetails cd WHERE cd.email = :email",
            ContactDetails::class.java
        )
            .setParameter("email", email)
            .singleResult
        }
        catch (e: Exception)
        {
            null
        }
    }
}
