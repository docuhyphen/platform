package com.docuhyphen.app.api.resource.application

import com.docuhyphen.app.api.exception.ApplicationNotFoundException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.resource.application.operations.ApplicationManagementResourceOperations
import com.docuhyphen.app.api.resource.model.CreateApplicationRequestBody
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateCapabilitiesRequestBody
import com.docuhyphen.app.api.service.application.ApplicationAdministrationService
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class ApplicationManagementResource @Inject constructor(
    private val applicationAdministrationService: ApplicationAdministrationService,
) : ApplicationManagementResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationManagementResource::class.java)
        private const val SUBSCRIPTION_REFUSAL_MESSAGE = "Application management request refused by subscription policy"
        private const val FAILURE_MESSAGE = "An error occurred"
    }

    override fun listApplications(): Response
    {
        return try
        {
            Response.ok(applicationAdministrationService.listApplications()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing applications", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun createApplication(body: CreateApplicationRequestBody): Response
    {
        return try
        {
            Response.status(CREATED).entity(applicationAdministrationService.createApplication(body)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error creating application", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun getApplication(id: String): Response
    {
        return try
        {
            Response.ok(applicationAdministrationService.getApplication(parseApplicationId(id))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error reading application", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun rotateCredentials(id: String): Response
    {
        return try
        {
            Response.ok(applicationAdministrationService.rotateCredentials(parseApplicationId(id))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error rotating application credentials", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun updateCapabilities(
        id: String,
        body: UpdateCapabilitiesRequestBody,
    ): Response
    {
        return try
        {
            applicationAdministrationService.updateCapabilities(parseApplicationId(id), body.capabilities)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating application capabilities", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun deactivateApplication(id: String): Response
    {
        return try
        {
            applicationAdministrationService.deactivate(parseApplicationId(id))
            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deactivating application", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }
}
