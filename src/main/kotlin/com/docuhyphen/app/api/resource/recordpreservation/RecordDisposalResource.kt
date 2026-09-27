package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/record-disposals")
@Produces(APPLICATION_JSON)
class RecordDisposalResource @Inject constructor(
    private val administration: RecordPreservationAdministration,
)
{
    @GET
    fun list(): Response
    {
        return try
        {
            Response.ok(administration.disposals().map(RecordPreservationDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record disposal list failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(RecordDisposalResource::class.java)
    }
}
