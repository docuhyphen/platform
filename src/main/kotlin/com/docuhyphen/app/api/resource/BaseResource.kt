package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.ResponseError
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.Logger

abstract class BaseResource
{

    protected abstract val logger: Logger

    protected fun handleException(exception: Exception, defaultMessage: String): Response
    {
        return when (exception)
        {
            is UnauthorizedException ->
            {
                val responseError = ResponseError(exception.message)
                Response.status(UNAUTHORIZED).entity(responseError).build()
            }

            is IllegalArgumentException ->
            {
                val responseError = ResponseError(exception.message)
                Response.status(BAD_REQUEST).entity(responseError).build()
            }

            else ->
            {
                val responseError = ResponseError(defaultMessage)
                Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
            }
        }
    }
}