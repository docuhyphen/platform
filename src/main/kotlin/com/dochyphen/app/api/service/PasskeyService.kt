package com.dochyphen.app.api.service

import com.dochyphen.app.api.model.AppUser
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PasskeyService
{
    fun initiatePasskeyAuthentication(user: AppUser)
    {

        throw Exception("Method not implemented")
    }

    fun validatePasskeyAuthentication(user: AppUser, token: String): Boolean
    {
        throw Exception("Method not implemented")
    }
}
