package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.RequestHeaders.REQUEST_ID
import com.docuhyphen.app.api.resource.model.StepUpCompleteRequest
import com.docuhyphen.app.api.resource.model.StepUpInitiateRequest
import com.docuhyphen.app.api.resource.model.StepUpOtpRegenerationRequest
import io.vertx.core.http.HttpServerRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/step-up")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface StepUpResourceOperations
{
    @POST
    @Path("/initiate")
    fun initiate(
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
        payload: StepUpInitiateRequest,
    ): Response

    @POST
    @Path("/complete")
    fun complete(
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
        payload: StepUpCompleteRequest,
    ): Response

    @POST
    @Path("/otp-regeneration")
    fun regenerateOtp(
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
        payload: StepUpOtpRegenerationRequest,
    ): Response
}
