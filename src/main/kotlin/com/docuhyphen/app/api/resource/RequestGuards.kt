package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import io.quarkus.security.UnauthorizedException
import java.util.*

object RequestGuards
{
    fun requireUser(authTokenContext: AuthTokenContext): AppUser =
        authTokenContext.authToken.appUser ?: throw UnauthorizedException("Unauthorized")

    fun uuid(raw: String, invalidMessage: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw RequestValidationException(invalidMessage)

    fun requireNotBlank(value: String?, message: String): String
    {
        if (value.isNullOrBlank()) throw RequestValidationException(message)
        return value
    }
}
