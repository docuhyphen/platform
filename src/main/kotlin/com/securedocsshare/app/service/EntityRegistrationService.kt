package com.securedocsshare.app.service

import com.securedocsshare.app.interceptor.AuthTokenContext
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

@RequestScoped
class EntityRegistrationService @Inject constructor(
    private val emailService: EmailService,
)
{
    @Inject
    private lateinit var authTokenContext: AuthTokenContext

    fun registerPerson(firstName: String?, lastName: String?, idNumber: String?)
    {
        validatePersonRegistration(firstName, lastName, idNumber)


    }

    fun registerCompany(name: String?, registrationNumber: String?)
    {
        validateCompanyRegistration(name, registrationNumber)
    }

    private fun validatePersonRegistration(firstName: String?, lastName: String?, idNumber: String?)
    {

    }

    private fun validateCompanyRegistration(name: String?, registrationNumber: String?)
    {

    }
}
