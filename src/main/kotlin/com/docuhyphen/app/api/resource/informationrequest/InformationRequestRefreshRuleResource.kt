package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.RefreshInformationRequestCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRefreshRuleRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestFollowUpService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/refresh-rules")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestRefreshRuleResource @Inject constructor(
    private val followUpService: InformationRequestFollowUpService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun define(
        @PathParam("id") id: String,
        request: DefineInformationRequestRefreshRuleRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val rule = followUpService.defineRefreshRule(
                DefineInformationRequestRefreshRuleCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    requirementKey = request.requirementKey,
                    leadDays = request.leadDays,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestLineageDtoMapper.toDto(rule)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request refresh rule definition failed", exception)
        }
    }

    @POST
    @Path("/{ruleId}/refreshes")
    fun refresh(
        @PathParam("id") id: String,
        @PathParam("ruleId") ruleId: String,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val result = followUpService.refresh(
                RefreshInformationRequestCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    refreshRuleId = InformationRequestCommandHttp.uuid(ruleId, "refresh rule id"),
                    access = accessContextFactory.currentAuthenticated(),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestLineageDtoMapper.toDto(result))
                .header("ETag", result.successorETag)
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request refresh failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestRefreshRuleResource::class.java)
    }
}
