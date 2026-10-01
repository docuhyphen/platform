package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.RequestHeaders.REQUEST_ID
import com.docuhyphen.app.api.resource.model.SignUpCompletionRequest
import com.docuhyphen.app.api.resource.model.SignUpEmailConfirmRequest
import com.docuhyphen.app.api.resource.model.SignUpInitiateRequest
import com.docuhyphen.app.api.resource.model.SignUpRegenerationRequest
import io.vertx.core.http.HttpServerRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/sign-up")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface SignUpResourceOperations
{
    @POST
    @Path("/initiation")
    fun initiateSignUp(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        payload: SignUpInitiateRequest,
    ): Response

    @POST
    @Path("/completion")
    fun completeSignUp(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        signUpRequest: SignUpCompletionRequest,
    ): Response

    @GET
    @Path("/email-confirm/{token}")
    fun checkEmailConfirmToken(@PathParam("token") token: String?): Response

    @POST
    @Path("/email-confirm")
    fun confirmEmailWithToken(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        confirmRequest: SignUpEmailConfirmRequest,
    ): Response

    @POST
    @Path("/otp-regeneration")
    fun regenerateOtp(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        regenerationRequest: SignUpRegenerationRequest,
    ): Response
}
