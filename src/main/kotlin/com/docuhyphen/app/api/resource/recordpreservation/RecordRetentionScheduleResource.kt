package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.resource.model.PublishRecordRetentionScheduleRequest
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/record-retention-schedules")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class RecordRetentionScheduleResource @Inject constructor(
    private val administration: RecordPreservationAdministration,
)
{
    @GET
    @Path("/{resourceType}")
    fun get(@PathParam("resourceType") resourceType: String): Response
    {
        return try
        {
            Response.ok(RecordPreservationDtoMapper.toDto(resourceType, administration.schedule(resourceType))).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record retention schedule read failed", exception)
        }
    }

    @PUT
    @Path("/{resourceType}")
    fun publish(
        @PathParam("resourceType") resourceType: String,
        request: PublishRecordRetentionScheduleRequest?
    ): Response
    {
        return try
        {
            val body =
                request ?: throw RecordPreservationRequestException("A retention schedule states its minimum retention")
            val view = administration.publishSchedule(resourceType, body.minimumRetentionDays, body.disposalAfterDays)
            Response.ok(RecordPreservationDtoMapper.toDto(resourceType, view)).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record retention schedule publication failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(RecordRetentionScheduleResource::class.java)
    }
}
