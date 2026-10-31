package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.ChangeRecordPreservationHoldScopeRequest
import com.docuhyphen.app.api.resource.model.PlaceRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.model.ReleaseRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.recordpreservation.operations.RecordPreservationHoldResourceOperations
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAdministration
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class RecordPreservationHoldResource @Inject constructor(
    private val administration: RecordPreservationAdministration,
) : RecordPreservationHoldResourceOperations
{
    override fun list(statuses: List<String>?): Response
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

    override fun get(holdId: String): Response
    {
        return try
        {
            RecordPreservationHttp.hold(
                Response.Status.OK,
                administration.hold(RecordPreservationHttp.uuid(holdId, "hold id")),
            )
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold read failed", exception)
        }
    }

    override fun place(request: PlaceRecordPreservationHoldRequest?, idempotencyKey: String?): Response
    {
        return try
        {
            val body =
                request ?: throw RecordPreservationRequestException("A hold names its resource, scope, and reason")
            val key = RecordPreservationHttp.idempotencyKey(idempotencyKey)
            val view = administration.placeHold(
                body.resourceType,
                body.resourceId,
                body.scope,
                body.reason,
                body.caseReference,
                body.effectiveFrom?.toInstant(),
                key,
            )
            RecordPreservationHttp.hold(Response.Status.CREATED, view)
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold placement failed", exception)
        }
    }

    override fun changeScope(
        holdId: String,
        request: ChangeRecordPreservationHoldScopeRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val body = request ?: throw RecordPreservationRequestException("A scope change states the scope and reason")
            val key = RecordPreservationHttp.idempotencyKey(idempotencyKey)
            val view = administration.changeHoldScope(
                RecordPreservationHttp.uuid(holdId, "hold id"),
                body.scope,
                body.reason,
                CommandPreconditionHeader.requiredExact(ifMatch),
                key,
            )
            RecordPreservationHttp.hold(Response.Status.OK, view)
        }
        catch (exception: Exception)
        {
            RecordPreservationHttp.refused(logger, "Record preservation hold scope change failed", exception)
        }
    }

    override fun release(
        holdId: String,
        request: ReleaseRecordPreservationHoldRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val body = request ?: throw RecordPreservationRequestException("A release states its reason")
            val key = RecordPreservationHttp.idempotencyKey(idempotencyKey)
            val view = administration.releaseHold(
                RecordPreservationHttp.uuid(holdId, "hold id"),
                body.reason,
                CommandPreconditionHeader.requiredExact(ifMatch),
                key,
            )
            RecordPreservationHttp.hold(Response.Status.OK, view)
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
