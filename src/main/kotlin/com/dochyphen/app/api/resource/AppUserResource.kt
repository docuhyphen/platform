package com.dochyphen.app.api.resource

import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.exception.CompanyNotFoundException
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.CompanyService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/app-user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AppUserResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val companyService: CompanyService

)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserResource::class.java)
    }

    @GET
    fun getSignInAppUser(): Response
    {
        return try
        {

            authTokenContext.authToken.appUser?.let {

                Response.ok(it).build()

            } ?: Response.status(BAD_REQUEST).entity(ResponseError("No user found")).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error initiating sign up", exception)
            val responseError = ResponseError("A server error occurred while signing up.")
            Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }

    @GET
    @Path("{appUserId}/person/{personId}/company")
    fun getAppUserPersonCompany(
        @PathParam("appUserId") appUserId: String,
        @PathParam("personId") personId: String
    ): Response
    {

        return try
        {
            val company =
                companyService.getCompanyByAppUserIdAndPersonId(UUID.fromString(appUserId), UUID.fromString(personId))

            Response.ok(company).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is CompanyNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error fetching company", exception)
                    val responseError = ResponseError("A server error occurred while fetching the company.")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}