package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.resource.model.ChangeRecordPreservationHoldScopeRequest
import com.docuhyphen.app.api.resource.model.PlaceRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.model.ReleaseRecordPreservationHoldRequest
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/record-preservation-holds")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class RecordPreservationHoldResource @Inject constructor(
    private val administration: RecordPreservationAdministration,
)
{
    @GET
    fun list(@QueryParam("status") statuses: List<String>?): Response
    {
        return try
        {
            val filter = statuses.orEmpty().map { raw ->
                RecordPreservationHoldStatus.entries.firstOrNull { it.name == raw.trim().uppercase() }
                    ?: throw RecordPreservationRequestException("Unknown hold status: $raw")
            }.toSet()
            Response.ok(administration.holds(filter).map { RecordPreservationDtoMapper.toDto(it) }.toTypedArray())
                .build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold list failed", exception)
        }
    }

    @GET
    @Path("/{holdId}")
    fun get(@PathParam("holdId") holdId: String): Response
    {
        return try
        {
            Response.ok(
                RecordPreservationDtoMapper.toDto(
                    administration.hold(
                        RecordPreservationHttp.uuid(
                            holdId,
                            "hold id"
                        )
                    )
                )
            ).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold read failed", exception)
        }
    }

    @POST
    fun place(request: PlaceRecordPreservationHoldRequest?): Response
    {
        return try
        {
            val body =
                request ?: throw RecordPreservationRequestException("A hold names its resource, scope, and reason")
            val view = administration.placeHold(
                body.resourceType,
                body.resourceId,
                body.scope,
                body.reason,
                body.caseReference,
                body.effectiveFrom?.toInstant(),
            )
            Response.status(Response.Status.CREATED).entity(RecordPreservationDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold placement failed", exception)
        }
    }

    @PATCH
    @Path("/{holdId}/scope")
    fun changeScope(@PathParam("holdId") holdId: String, request: ChangeRecordPreservationHoldScopeRequest?): Response
    {
        return try
        {
            val body = request ?: throw RecordPreservationRequestException("A scope change states the scope and reason")
            val view =
                administration.changeHoldScope(RecordPreservationHttp.uuid(holdId, "hold id"), body.scope, body.reason)
            Response.ok(RecordPreservationDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold scope change failed", exception)
        }
    }

    @POST
    @Path("/{holdId}/release")
    fun release(@PathParam("holdId") holdId: String, request: ReleaseRecordPreservationHoldRequest?): Response
    {
        return try
        {
            val body = request ?: throw RecordPreservationRequestException("A release states its reason")
            val view = administration.releaseHold(RecordPreservationHttp.uuid(holdId, "hold id"), body.reason)
            Response.ok(RecordPreservationDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold release failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(RecordPreservationHoldResource::class.java)
    }
}
