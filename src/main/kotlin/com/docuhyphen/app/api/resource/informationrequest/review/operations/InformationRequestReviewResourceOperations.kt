package com.docuhyphen.app.api.resource.informationrequest.review.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.AssignInformationRequestReviewerRequest
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestReviewAssignmentRequest
import com.docuhyphen.app.api.resource.model.OverrideInformationRequestReviewItemRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestReviewCommentRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestReviewFindingRequest
import com.docuhyphen.app.api.resource.model.ReopenInformationRequestReviewRequest
import com.docuhyphen.app.api.resource.model.SaveInformationRequestReviewWorksheetRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/reviews")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestReviewResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @GET
    @Path("/{reviewId}")
    fun detail(@PathParam("id") id: String, @PathParam("reviewId") reviewId: String): Response

    @POST
    @Path("/{reviewId}/assignments")
    fun assign(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: AssignInformationRequestReviewerRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/recusal")
    fun recuse(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/delegation")
    fun delegate(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @PATCH
    @Path("/{reviewId}/assignments/{assignmentId}/worksheet")
    fun saveWorksheet(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: SaveInformationRequestReviewWorksheetRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
    ): Response

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/decisions")
    fun recordWorksheet(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/overrides")
    fun override(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: OverrideInformationRequestReviewItemRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/findings")
    fun finding(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: RecordInformationRequestReviewFindingRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/comments")
    fun comment(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: RecordInformationRequestReviewCommentRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/reconsiderations")
    fun reconsider(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{reviewId}/appeals")
    fun appeal(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
