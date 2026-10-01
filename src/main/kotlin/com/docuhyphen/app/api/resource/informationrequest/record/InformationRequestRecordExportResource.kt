package com.docuhyphen.app.api.resource.informationrequest.record

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.record.operations.InformationRequestRecordExportResourceOperations
import com.docuhyphen.app.api.resource.model.CreateInformationRequestRecordExportRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestRecordExportResource @Inject constructor(
    private val exports: InformationRequestRecordExportService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestRecordExportResourceOperations
{
    override fun create(
        id: String,
        request: CreateInformationRequestRecordExportRequest?,
        idempotencyKey: String?,
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

    override fun list(id: String): Response
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

    override fun get(id: String, exportId: String): Response
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
