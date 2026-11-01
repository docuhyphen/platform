package com.docuhyphen.app.api.resource.workflow

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.model.RegisterWebhookRequestBody
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.workflow.handler.WorkflowWebhookEndpointRequestHandler
import com.docuhyphen.app.api.resource.workflow.operations.WorkflowWebhookEndpointResourceOperations
import com.docuhyphen.app.api.service.application.WorkflowWebhookEndpointManagementService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class WorkflowWebhookEndpointResource @Inject constructor(
    webhookManagementService: WorkflowWebhookEndpointManagementService,
    private val authorizationContextFactory: AuthorizationContextFactory,
) : WorkflowWebhookEndpointResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowWebhookEndpointResource::class.java)

        private const val AUTHENTICATION_REQUIRED = "Authentication required"
        private const val FAILURE_MESSAGE = "An error occurred"
    }

    private val handler = WorkflowWebhookEndpointRequestHandler(webhookManagementService)

    override fun listWebhooks(orgId: String): Response
    {
        return try
        {
            val organizationId = RequestGuards.uuid(orgId, "Invalid id: $orgId")
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw SecurityException(AUTHENTICATION_REQUIRED)
            handler.list(organizationId, principal, authorizationContextFactory.currentContext())
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow webhook listing refused by subscription policy", exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing workflow webhook endpoints", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun registerWebhook(
        orgId: String,
        body: RegisterWebhookRequestBody
    ): Response
    {
        return try
        {
            val organizationId = RequestGuards.uuid(orgId, "Invalid id: $orgId")
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw SecurityException(AUTHENTICATION_REQUIRED)
            handler.register(organizationId, body, principal, authorizationContextFactory.currentContext())
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow webhook registration refused by subscription policy", exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error registering workflow webhook endpoint", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun getWebhook(
        orgId: String,
        id: String
    ): Response
    {
        return try
        {
            val endpointId = RequestGuards.uuid(id, "Invalid id: $id")
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw SecurityException(AUTHENTICATION_REQUIRED)
            handler.get(endpointId, principal, authorizationContextFactory.currentContext())
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow webhook read refused by subscription policy", exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting workflow webhook endpoint", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun enableWebhook(
        orgId: String,
        id: String
    ): Response
    {
        return try
        {
            val endpointId = RequestGuards.uuid(id, "Invalid id: $id")
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw SecurityException(AUTHENTICATION_REQUIRED)
            handler.enable(endpointId, principal, authorizationContextFactory.currentContext())
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow webhook enablement refused by subscription policy", exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error enabling workflow webhook endpoint", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun disableWebhook(
        orgId: String,
        id: String
    ): Response
    {
        return try
        {
            val endpointId = RequestGuards.uuid(id, "Invalid id: $id")
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw SecurityException(AUTHENTICATION_REQUIRED)
            handler.disable(endpointId, principal, authorizationContextFactory.currentContext())
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow webhook disablement refused by subscription policy", exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error disabling workflow webhook endpoint", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun rotateSigningSecret(
        orgId: String,
        id: String
    ): Response
    {
        return try
        {
            val endpointId = RequestGuards.uuid(id, "Invalid id: $id")
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw SecurityException(AUTHENTICATION_REQUIRED)
            handler.rotateSigningSecret(endpointId, principal, authorizationContextFactory.currentContext())
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow webhook secret rotation refused by subscription policy", exception)
                    throw exception
                }

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error rotating workflow webhook signing secret", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }
}
