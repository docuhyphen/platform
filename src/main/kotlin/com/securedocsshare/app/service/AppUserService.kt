package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.Person
import com.securedocsshare.app.repository.AppUserRepository
import com.securedocsshare.app.repository.AuthTokenRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class AppUserService @Inject constructor(
    val appUserRepository: AppUserRepository
)
{
    fun getAppUserById(id: UUID): AppUser?
    {
        return appUserRepository.findById(id)
    }

    fun findUserByEmail(email: String): AppUser?
    {
        return appUserRepository.findByEmail(email)
    }

    fun updatePerson(appUser: AppUser, person: Person)
    {
        appUser.person = person
        appUserRepository.update(appUser)
    }
}
