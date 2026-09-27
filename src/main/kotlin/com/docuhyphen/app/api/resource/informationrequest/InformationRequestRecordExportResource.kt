package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.model.informationrequest.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.resource.model.CreateInformationRequestRecordExportRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRecordExportService
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

@Path("/information-requests/{id}/record-exports")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestRecordExportResource @Inject constructor(
    private val exports: InformationRequestRecordExportService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun create(
        @PathParam("id") id: String,
        request: CreateInformationRequestRecordExportRequest?,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val view = exports.create(
                CreateInformationRequestRecordExportCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    access = accessContextFactory.currentAuthenticated(),
                    transferRegion = request?.transferRegion,
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestAuditDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request record export failed", exception)
        }
    }

    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            val views = exports.exports(InformationRequestCommandHttp.uuid(id, "information request id"), accessContextFactory.currentAuthenticated())
            Response.ok(views.map(InformationRequestAuditDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request record export list failed", exception)
        }
    }

    @GET
    @Path("/{exportId}")
    fun get(@PathParam("id") id: String, @PathParam("exportId") exportId: String): Response
    {
        return try
        {
            val view = exports.read(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                InformationRequestCommandHttp.uuid(exportId, "record export id"),
                accessContextFactory.currentAuthenticated(),
            )
            Response.ok(InformationRequestAuditDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request record export read failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestRecordExportResource::class.java)
    }
}
