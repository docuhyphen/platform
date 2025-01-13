package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.AppUser
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
