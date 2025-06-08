package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.OrganizationLinkNotFoundException
import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.BasicEntityToDtoTransformer
import com.dochyphen.app.api.model.entity.LinkStatus
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.organization.OrganizationSharingSessionLinkService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

@Path("organizations/links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationSharingSessionLinkResource @Inject constructor(
    private val linkService: OrganizationSharingSessionLinkService,
    private val authTokenContext: AuthTokenContext
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationSharingSessionLinkResource::class.java)
    }

    @POST
    fun createLink(
        @QueryParam("requestingOrganizationId") requestingOrganizationId: String?,
        @QueryParam("requestedOrganizationId") requestedOrganizationId: String?,
        @QueryParam("message") message: String? = null
    ): Response
    {
        return try
        {
            val link = linkService.createLink(
                requestingOrganizationId, requestedOrganizationId, message
            )

            Response.ok(BasicEntityToDtoTransformer.toDto(link)).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error creating organization link", exception)

            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while creating the organization link")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    fun getLinks(): Response
    {
        return try
        {
            linkService.getLinksByCurrentAppUser()?.let {

                if (it.isNotEmpty())
                {
                    val orgLinks = it
                        .map { orgLink -> BasicEntityToDtoTransformer.toDto(orgLink) }
                        .toTypedArray()

                    Response.ok(orgLinks).build()
                }
                else
                {
                    Response.noContent().build()
                }

            } ?: Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                is OrganizationNotFoundException,
                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while creating the organization link")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @PUT
    @Path("{linkId}")
    fun acceptLinkOrDecline(
        @PathParam("linkId") linkId: String?,
        @QueryParam("status") status: LinkStatus?,
        @QueryParam("rejectionReason") rejectionReason: String?,
    ): Response
    {
        return try
        {
            linkService.acceptLink(linkId, status, rejectionReason)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationLinkNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while creating the organization link")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @Path("/{linkId}")
    @DELETE
    fun deLink(
        @PathParam("linkId") linkId: String?,
    ): Response
    {
        return try
        {
            linkService.deLink(linkId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationLinkNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while creating the organization link")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}