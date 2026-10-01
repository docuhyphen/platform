package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.RefreshInformationRequestCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestRefreshRuleResourceOperations
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRefreshRuleRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFollowUpService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestRefreshRuleResource @Inject constructor(
    private val followUpService: InformationRequestFollowUpService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestRefreshRuleResourceOperations
{
    override fun define(
        id: String,
        request: DefineInformationRequestRefreshRuleRequest,
        ifMatch: String?,
        idempotencyKey: String?,
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

    override fun refresh(
        id: String,
        ruleId: String,
        idempotencyKey: String?,
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
