package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.PromoteInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBusinessDecisionService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/accepted-facts")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestAcceptedFactResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestAcceptedFactEndpoint(facts, factQueries, decisions)

    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.promotedFrom(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request accepted fact list failed", exception)
        }
    }

    @POST
    fun promote(
        @PathParam("id") id: String,
        request: PromoteInformationRequestAcceptedFactRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.promote(requestId(id), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request accepted fact promotion failed", exception)
        }
    }

    @POST
    @Path("/{factId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("factId") factId: String,
        request: RevokeInformationRequestAcceptedFactRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.revoke(
                requestId(id),
                InformationRequestCommandHttp.uuid(factId, "accepted fact id"),
                request,
                accessContextFactory.currentAuthenticated(),
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request accepted fact revocation failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAcceptedFactResource::class.java)
    }
}
