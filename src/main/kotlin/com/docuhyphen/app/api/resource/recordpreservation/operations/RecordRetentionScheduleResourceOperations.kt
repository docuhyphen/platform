package com.docuhyphen.app.api.resource.recordpreservation.operations

import com.docuhyphen.app.api.resource.model.PublishRecordRetentionScheduleRequest
import com.docuhyphen.app.api.resource.recordpreservation.RecordPreservationHttp
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/record-retention-schedules")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface RecordRetentionScheduleResourceOperations {
    @GET
    @Path("/{resourceType}")
    fun get(@PathParam("resourceType") resourceType: String): Response

    @PUT
    @Path("/{resourceType}")
    fun publish(
        @PathParam("resourceType") resourceType: String,
        request: PublishRecordRetentionScheduleRequest?,
        @HeaderParam(RecordPreservationHttp.IF_MATCH_HEADER) ifMatch: String?,
        @HeaderParam(RecordPreservationHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
