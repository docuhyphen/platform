package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/information-requests/{id}/carry-forwards")
@Produces(APPLICATION_JSON)
class InformationRequestNoAuthCarryForwardResource @Inject constructor(
    private val lineageQueryService: InformationRequestLineageQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    @GET
    fun list(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                Response.ok(
                    lineageQueryService.carryForwards(requestId, access).map(InformationRequestLineageDtoMapper::toDto).toTypedArray(),
                ).build()
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request carry-forward lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthCarryForwardResource::class.java)
    }
}
