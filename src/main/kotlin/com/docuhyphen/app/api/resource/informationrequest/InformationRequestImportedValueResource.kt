package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.ProposeInformationRequestImportedValueCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.DecideInformationRequestImportedValueRequest
import com.docuhyphen.app.api.resource.model.ProposeInformationRequestImportedValueRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestImportedValueService
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

@Path("/information-requests/{id}/imported-values")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestImportedValueResource @Inject constructor(
    private val importedValues: InformationRequestImportedValueService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            val access = accessContextFactory.currentAuthenticated()
            val listed = importedValues.values(requestId(id), access)
            Response.ok(listed.map { InformationRequestExternalSourceDtoMapper.toDto(it, access.principal) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request imported value list failed", exception)
        }
    }

    @POST
    fun propose(
        @PathParam("id") id: String,
        request: ProposeInformationRequestImportedValueRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val body = request ?: throw InformationRequestCommandRequestException("An imported value names its Requirement, value, source, and provenance")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val view = importedValues.propose(
                ProposeInformationRequestImportedValueCommand(
                    requestId = requestId,
                    requirementId = body.requirementId,
                    resultKey = body.resultKey,
                    valueType = body.valueType,
                    value = body.value,
                    sourceReference = body.sourceReference,
                    confidence = body.confidence,
                    verifiedAt = body.verifiedAt?.toInstant(),
                    expiresAt = body.expiresAt?.toInstant(),
                    provenanceReference = body.provenanceReference,
                    access = access,
                    idempotencyKey = key,
                ),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestExternalSourceDtoMapper.toDto(view, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request imported value proposal failed", exception)
        }
    }

    @POST
    @Path("/{valueId}/decisions")
    fun decide(
        @PathParam("id") id: String,
        @PathParam("valueId") valueId: String,
        request: DecideInformationRequestImportedValueRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val importedValueId = InformationRequestCommandHttp.uuid(valueId, "imported value id")
            val body = request ?: throw InformationRequestCommandRequestException("A decision states its outcome and reason")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val view = importedValues.decide(
                DecideInformationRequestImportedValueCommand(requestId, importedValueId, body.decision, body.reasonCode, access, key),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestExternalSourceDtoMapper.toDto(view, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request imported value decision failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestImportedValueResource::class.java)
    }
}
