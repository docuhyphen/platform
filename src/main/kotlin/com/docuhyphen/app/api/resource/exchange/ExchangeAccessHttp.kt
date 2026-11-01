package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.resource.model.ResponseError
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.Logger

object ExchangeAccessHttp
{
    fun refused(logger: Logger, logMessage: String, failureMessage: String, exception: Exception): Response =
        when (exception)
        {
            is SubscriptionDenialException ->
            {
                logger.warn(
                    "{} refused by the subscription plan check: reason={} plan={}",
                    logMessage,
                    exception.denial.reason,
                    exception.denial.planCode,
                )
                throw exception
            }

            is ForbiddenException ->
                Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

            is ExchangeNotFoundException ->
                Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

            is OrganizationTrustException ->
                Response.status(CONFLICT)
                    .entity(ResponseError("This trusted recipient can no longer be selected"))
                    .build()

            is IllegalArgumentException ->
                Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

            else ->
            {
                logger.error(logMessage, exception)
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(failureMessage)).build()
            }
        }
}
