package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.resource.recordpreservation.operations.RecordDisposalResourceOperations
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class RecordDisposalResource @Inject constructor(
    private val administration: RecordPreservationAdministration,
) : RecordDisposalResourceOperations
{
    override fun list(): Response
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
