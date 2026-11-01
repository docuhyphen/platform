package com.docuhyphen.app.api.resource.contactdetails

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.contactdetails.operations.UserContactResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import com.docuhyphen.app.api.service.contactdetails.UserContactLookupService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class UserContactResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userContactLookupService: UserContactLookupService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
) : UserContactResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(UserContactResource::class.java)
        private const val SEARCH_ENDPOINT_KEY = "user-contacts-search"
        private const val RECENT_ENDPOINT_KEY = "user-contacts-recent"
    }

    override fun searchContacts(
        query: String?,
        limit: Int?,
        requestId: String?,
    ): Response
    {
        return try
        {
            val ownerId = authTokenContext.authToken.appUser?.id
                ?: throw UnauthorizedException("User must be authenticated")

            val limited = directoryLookupGuardService.enforce(
                endpointKey = SEARCH_ENDPOINT_KEY,
                targetOrganizationId = null,
                requestId = requestId,
                query = query,
            )
            if (limited != null)
            {
                return limited
            }

            val contacts = userContactLookupService.searchContacts(ownerId, query!!.trim(), limit)
            Response.ok(contacts.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error searching contacts", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while searching contacts"))
                        .build()
                }
            }
        }
    }

    override fun recentContacts(
        limit: Int?,
        requestId: String?,
    ): Response
    {
        return try
        {
            val ownerId = authTokenContext.authToken.appUser?.id
                ?: throw UnauthorizedException("User must be authenticated")

            val limited = directoryLookupGuardService.enforce(
                endpointKey = RECENT_ENDPOINT_KEY,
                targetOrganizationId = null,
                requestId = requestId,
                query = null,
            )
            if (limited != null)
            {
                return limited
            }

            val contacts = userContactLookupService.recentContacts(ownerId, limit)
            Response.ok(contacts.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching recent contacts", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while fetching recent contacts"))
                        .build()
                }
            }
        }
    }
}
