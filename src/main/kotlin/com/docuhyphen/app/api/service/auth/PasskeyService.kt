package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
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