package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestGeneratedOutputCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RecordInformationRequestGeneratedOutputRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGeneratedOutputService
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

@Path("/information-requests/{id}/generated-outputs")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestGeneratedOutputResource @Inject constructor(
    private val outputs: InformationRequestGeneratedOutputService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            val access = accessContextFactory.currentAuthenticated()
            val listed = outputs.outputs(requestId(id), access)
            Response.ok(listed.map { InformationRequestExternalSourceDtoMapper.toDto(it, access.principal) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request generated output list failed", exception)
        }
    }

    @POST
    fun record(
        @PathParam("id") id: String,
        request: RecordInformationRequestGeneratedOutputRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val body = request ?: throw InformationRequestCommandRequestException("A generated output states where it lives and what produced it")
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
            Response.status(Response.Status.CREATED).entity(InformationRequestExternalSourceDtoMapper.toDto(output, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request generated output recording failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestGeneratedOutputResource::class.java)
    }
}
