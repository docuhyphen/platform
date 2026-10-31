package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.PublishRecordRetentionScheduleRequest
import com.docuhyphen.app.api.resource.recordpreservation.operations.RecordRetentionScheduleResourceOperations
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class RecordRetentionScheduleResource @Inject constructor(
    private val administration: RecordPreservationAdministration,
) : RecordRetentionScheduleResourceOperations
{
    override fun get(resourceType: String): Response
    {
        return try
        {
            RecordPreservationHttp.schedule(resourceType, administration.schedule(resourceType))
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record retention schedule read failed", exception)
        }
    }

    override fun publish(
        resourceType: String,
        request: PublishRecordRetentionScheduleRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val body =
                request ?: throw RecordPreservationRequestException("A retention schedule states its minimum retention")
            val key = RecordPreservationHttp.idempotencyKey(idempotencyKey)
            val view = administration.publishSchedule(
                resourceType,
                body.minimumRetentionDays,
                body.disposalAfterDays,
                CommandPreconditionHeader.requiredExact(ifMatch),
                key,
            )
            RecordPreservationHttp.schedule(resourceType, view)
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
