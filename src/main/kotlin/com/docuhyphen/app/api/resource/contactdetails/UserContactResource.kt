package com.docuhyphen.app.api.resource.contactdetails

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.AvatarUrls
import com.docuhyphen.app.api.model.dto.UserContactDto
import com.docuhyphen.app.api.model.entity.UserContact
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.UserContactService
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("me/contacts")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class UserContactResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userContactService: UserContactService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(UserContactResource::class.java)
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 50
    }

    @GET
    fun searchContacts(
        @QueryParam("query") query: String?,
        @QueryParam("limit") limit: Int?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val ownerId = authTokenContext.authToken.appUser?.id
                ?: throw UnauthorizedException("User must be authenticated")

            val limited = directoryLookupGuardService.enforce(
                endpointKey = "user-contacts-search",
                targetOrganizationId = null,
                requestId = requestId,
                query = query,
            )
            if (limited != null)
            {
                return limited
            }

            val effectiveLimit = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
            val results = userContactService.searchContacts(ownerId, query!!.trim(), effectiveLimit)
            val capped = directoryLookupGuardService.capResults(results)
            val idsWithAvatar = userContactService.findContactIdsWithAvatar(capped.mapNotNull { it.contactAppUserId })
            Response.ok(capped.map { it.toDto(idsWithAvatar) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError(exception.message))
                        .build()
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

    @GET
    @Path("/recent")
    fun recentContacts(
        @QueryParam("limit") limit: Int?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val ownerId = authTokenContext.authToken.appUser?.id
                ?: throw UnauthorizedException("User must be authenticated")

            // No min-query-length check applies here (no query). Still subject to rate limit.
            val limited = directoryLookupGuardService.enforce(
                endpointKey = "user-contacts-recent",
                targetOrganizationId = null,
                requestId = requestId,
                query = null,
            )
            if (limited != null)
            {
                return limited
            }

            val effectiveLimit = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
            val results = userContactService.recentContacts(ownerId, effectiveLimit)
            val capped = directoryLookupGuardService.capResults(results)
            val idsWithAvatar = userContactService.findContactIdsWithAvatar(capped.mapNotNull { it.contactAppUserId })
            Response.ok(capped.map { it.toDto(idsWithAvatar) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError(exception.message))
                        .build()
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

    private fun UserContact.toDto(idsWithAvatar: Set<UUID>): UserContactDto = UserContactDto(
        contactAppUserId = this.contactAppUserId,
        email = this.contactEmail,
        firstName = this.contactFirstName,
        lastName = this.contactLastName,
        lastSharedAt = this.lastSharedAt,
        shareCount = this.shareCount,
        avatarUrl = this.contactAppUserId
            ?.takeIf { it in idsWithAvatar }
            ?.let { AvatarUrls.forUser(it) },
    )
}
