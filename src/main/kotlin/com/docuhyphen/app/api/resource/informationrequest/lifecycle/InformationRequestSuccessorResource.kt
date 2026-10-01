package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestSuccessorResourceOperations
import com.docuhyphen.app.api.resource.model.CreateInformationRequestSuccessorRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSuccessorService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestSuccessorResource @Inject constructor(
    private val successorService: InformationRequestSuccessorService,
    private val lineageQueryService: InformationRequestLineageQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestSuccessorResourceOperations
{
    override fun lineage(id: String): Response
    {
        return try
        {
            val view = lineageQueryService.lineage(requestId(id), accessContextFactory.currentAuthenticated())
            Response.ok(InformationRequestLineageDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request lineage lookup failed", exception)
        }
    }

    override fun create(
        id: String,
        request: CreateInformationRequestSuccessorRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val result = successorService.create(
                CreateInformationRequestSuccessorCommand(
                    sourceRequestId = requestId(id),
                    kind = request.kind,
                    targetTemplateVersionId = request.targetTemplateVersionId,
                    sourcePackageId = request.sourcePackageId,
                    reasonCode = request.reasonCode?.trim()?.ifBlank { null },
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
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
            InformationRequestCommandHttp.refused(logger, "Information Request follow-up creation failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSuccessorResource::class.java)
    }
}
