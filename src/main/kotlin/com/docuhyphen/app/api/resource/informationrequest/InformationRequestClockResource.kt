package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockChange
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestClockRequest
import com.docuhyphen.app.api.resource.model.StartInformationRequestClockRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestClockService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/clocks")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestClockResource @Inject constructor(
    policies: InformationRequestClockPolicyService,
    clocks: InformationRequestClockService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestClockEndpoint(policies, clocks)

    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.clocks(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock list failed", exception)
        }
    }

    @POST
    fun start(
        @PathParam("id") id: String,
        request: StartInformationRequestClockRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.start(requestId(id), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock start failed", exception)
        }
    }

    @POST
    @Path("/{clockId}/pauses")
    fun pause(
        @PathParam("id") id: String,
        @PathParam("clockId") clockId: String,
        request: ChangeInformationRequestClockRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            change(id, clockId, InformationRequestClockChange.PAUSE, request, ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock pause failed", exception)
        }
    }

    @POST
    @Path("/{clockId}/resumptions")
    fun resume(
        @PathParam("id") id: String,
        @PathParam("clockId") clockId: String,
        request: ChangeInformationRequestClockRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            change(id, clockId, InformationRequestClockChange.RESUME, request, ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock resumption failed", exception)
        }
    }

    @POST
    @Path("/{clockId}/extensions")
    fun extend(
        @PathParam("id") id: String,
        @PathParam("clockId") clockId: String,
        request: ChangeInformationRequestClockRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            change(id, clockId, InformationRequestClockChange.EXTEND, request, ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock extension failed", exception)
        }
    }

    @Suppress("LongParameterList")
    private fun change(
        id: String,
        clockId: String,
        change: InformationRequestClockChange,
        request: ChangeInformationRequestClockRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response =
        endpoint.change(
            requestId(id),
            InformationRequestCommandHttp.uuid(clockId, "clock id"),
            change,
            request,
            accessContextFactory.currentAuthenticated(),
            ifMatch,
            idempotencyKey,
        )

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestClockResource::class.java)
    }
}
