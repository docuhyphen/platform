package com.dochyphen.app.api.service

import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.Person
import com.dochyphen.app.api.repository.AppUserRepository
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

    fun createAppUser(user: AppUser): AppUser
    {
        return appUserRepository.save(user)
    }

    fun update(user: AppUser)
    {
        appUserRepository.update(user)
    }

    fun getAppUserByEmail(email: String): AppUser?
    {
        return appUserRepository.findByEmail(email)
    }

    fun initiatePasswordUpdate()
    {

    }

    fun completePasswordUpdate()
    {

    }
}
