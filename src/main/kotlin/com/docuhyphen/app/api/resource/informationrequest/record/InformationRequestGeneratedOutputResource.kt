package com.docuhyphen.app.api.resource.informationrequest.record

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.externalsource.RecordInformationRequestGeneratedOutputCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.record.operations.InformationRequestGeneratedOutputResourceOperations
import com.docuhyphen.app.api.resource.model.RecordInformationRequestGeneratedOutputRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestGeneratedOutputService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestGeneratedOutputResource @Inject constructor(
    private val outputs: InformationRequestGeneratedOutputService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestGeneratedOutputResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val access = accessContextFactory.currentAuthenticated()
            val listed = outputs.outputs(requestId(id), access)
            Response.ok(listed.map { InformationRequestExternalSourceDtoMapper.toDto(it, access.principal) }
                .toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request generated output list failed", exception)
        }
    }

    override fun record(
        id: String,
        request: RecordInformationRequestGeneratedOutputRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val body = request
                ?: throw InformationRequestCommandRequestException("A generated output states where it lives and what produced it")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val output = outputs.record(
                RecordInformationRequestGeneratedOutputCommand(
                    requestId = requestId,
                    packageId = body.packageId,
                    outputKey = body.outputKey,
                    externalReference = body.externalReference,
                    contentHashSha256 = body.contentHashSha256,
                    mediaType = body.mediaType,
                    producedBySource = body.producedBySource,
                    producedAt = body.producedAt.toInstant(),
                    access = access,
                    idempotencyKey = key,
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestExternalSourceDtoMapper.toDto(output, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request generated output recording failed",
                exception
            )
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestGeneratedOutputResource::class.java)
    }
}
