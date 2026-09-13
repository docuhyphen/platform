package com.docuhyphen.app.api.resource.command

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED

object CommandPreconditionResponse
{
    fun refused(
        refusal: CommandPreconditionException,
        requiredReasonCode: String = CommandPreconditionException.Kind.REQUIRED.reasonCode,
        staleReasonCode: String = CommandPreconditionException.Kind.STALE.reasonCode,
    ): Response
    {
        val status = when (refusal.kind)
        {
            CommandPreconditionException.Kind.REQUIRED -> 428
            CommandPreconditionException.Kind.STALE -> PRECONDITION_FAILED.statusCode
        }
        val reasonCode = when (refusal.kind)
        {
            CommandPreconditionException.Kind.REQUIRED -> requiredReasonCode
            CommandPreconditionException.Kind.STALE -> staleReasonCode
        }
        val builder = Response.status(status).entity(ResponseError(refusal.message, reasonCode))
        refusal.currentETag?.let { builder.header("ETag", it) }
        return builder.build()
    }
}
