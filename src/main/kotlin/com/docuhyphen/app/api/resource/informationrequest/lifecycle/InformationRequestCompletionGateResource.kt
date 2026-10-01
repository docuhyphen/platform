package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestCompletionGateResourceOperations
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestCompletionGateRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCompletionGateService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestCompletionGateResource @Inject constructor(
    private val completionGates: InformationRequestCompletionGateService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestCompletionGateResourceOperations
{
    override fun change(
        id: String,
        request: ChangeInformationRequestCompletionGateRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val body = request
                ?: throw InformationRequestCommandRequestException("A completion gate states whether the request gates its Exchange")
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
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request completion gate change failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestCompletionGateResource::class.java)
    }
}
