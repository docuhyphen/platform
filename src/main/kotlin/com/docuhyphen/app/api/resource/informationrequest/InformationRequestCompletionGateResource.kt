package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestCompletionGateRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCompletionGateService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/completion-gate")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestCompletionGateResource @Inject constructor(
    private val completionGates: InformationRequestCompletionGateService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @PUT
    fun change(
        @PathParam("id") id: String,
        request: ChangeInformationRequestCompletionGateRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val body = request ?: throw InformationRequestCommandRequestException("A completion gate states whether the request gates its Exchange")
            val result = completionGates.change(
                ChangeInformationRequestCompletionGateCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    gatesExchangeClosure = body.gatesExchangeClosure,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.ok(InformationRequestDtoMapper.toDto(result.request)).header("ETag", result.requestETag).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request completion gate change failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestCompletionGateResource::class.java)
    }
}
