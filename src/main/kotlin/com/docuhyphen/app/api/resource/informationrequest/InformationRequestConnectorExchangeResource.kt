package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestInformationRequestConnectorExchangeCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RequestInformationRequestConnectorExchangeRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConnectorService
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

@Path("/information-requests/{id}/connector-exchanges")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestConnectorExchangeResource @Inject constructor(
    private val exchanges: InformationRequestConnectorService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            val access = accessContextFactory.currentAuthenticated()
            val listed = exchanges.exchanges(requestId(id), access)
            Response.ok(listed.map { InformationRequestExternalSourceDtoMapper.toDto(it, access.principal) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request connector exchange list failed", exception)
        }
    }

    @POST
    fun request(
        @PathParam("id") id: String,
        request: RequestInformationRequestConnectorExchangeRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val body = request ?: throw InformationRequestCommandRequestException("A connector exchange names its Requirement and connector")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val exchange = exchanges.request(
                RequestInformationRequestConnectorExchangeCommand(requestId, body.requirementId, body.connectorKey, body.lookupReference, access, key),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestExternalSourceDtoMapper.toDto(exchange, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request connector exchange request failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestConnectorExchangeResource::class.java)
    }
}
