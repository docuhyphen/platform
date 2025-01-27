package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.Person
import com.securedocsshare.app.api.repository.AppUserRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.*

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

    fun addNewAppUser(user: AppUser): AppUser
    {
        return appUserRepository.save(user)
    }
}
