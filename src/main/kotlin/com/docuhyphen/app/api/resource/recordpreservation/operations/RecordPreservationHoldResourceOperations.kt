package com.docuhyphen.app.api.resource.recordpreservation.operations

import com.docuhyphen.app.api.resource.model.ChangeRecordPreservationHoldScopeRequest
import com.docuhyphen.app.api.resource.model.PlaceRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.model.ReleaseRecordPreservationHoldRequest
import com.docuhyphen.app.api.resource.recordpreservation.RecordPreservationHttp
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/record-preservation-holds")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface RecordPreservationHoldResourceOperations {
    @GET
    fun list(@QueryParam("status") statuses: List<String>?): Response

    @GET
    @Path("/{holdId}")
    fun get(@PathParam("holdId") holdId: String): Response

    @POST
    fun place(
        request: PlaceRecordPreservationHoldRequest?,
        @HeaderParam(RecordPreservationHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @PATCH
    @Path("/{holdId}/scope")
    fun changeScope(
        @PathParam("holdId") holdId: String,
        request: ChangeRecordPreservationHoldScopeRequest?,
        @HeaderParam(RecordPreservationHttp.IF_MATCH_HEADER) ifMatch: String?,
        @HeaderParam(RecordPreservationHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{holdId}/release")
    fun release(
        @PathParam("holdId") holdId: String,
        request: ReleaseRecordPreservationHoldRequest?,
        @HeaderParam(RecordPreservationHttp.IF_MATCH_HEADER) ifMatch: String?,
        @HeaderParam(RecordPreservationHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
