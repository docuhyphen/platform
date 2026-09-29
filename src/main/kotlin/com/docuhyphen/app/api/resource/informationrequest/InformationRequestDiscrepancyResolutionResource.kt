package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.ResolveInformationRequestDiscrepancyCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.ResolveInformationRequestDiscrepancyRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestImportedValueService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/imported-value-discrepancies/{discrepancyId}/resolutions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestDiscrepancyResolutionResource @Inject constructor(
    private val importedValues: InformationRequestImportedValueService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun resolve(
        @PathParam("id") id: String,
        @PathParam("discrepancyId") discrepancyId: String,
        request: ResolveInformationRequestDiscrepancyRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val discrepancy = InformationRequestCommandHttp.uuid(discrepancyId, "discrepancy id")
            val body = request ?: throw InformationRequestCommandRequestException("A resolution states its outcome and reason")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val view = importedValues.resolve(
                ResolveInformationRequestDiscrepancyCommand(requestId, discrepancy, body.resolution, body.reasonCode, access, key),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestExternalSourceDtoMapper.toDto(view, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request discrepancy resolution failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestDiscrepancyResolutionResource::class.java)
    }
}
