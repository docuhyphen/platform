package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.RequestHeaders.REQUEST_ID
import com.docuhyphen.app.api.resource.model.EmailFallbackChallengeRequest
import com.docuhyphen.app.api.resource.model.ResendOtpRequest
import com.docuhyphen.app.api.resource.model.SignInCompletionRequest
import com.docuhyphen.app.api.resource.model.SignInLookupRequest
import com.docuhyphen.app.api.resource.model.SignInRequest
import io.vertx.core.http.HttpServerRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/sign-in")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface SignInResourceOperations
{
    @POST
    @Path("/lookup")
    fun lookupSignInMethod(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        payload: SignInLookupRequest,
    ): Response

    @POST
    @Path("/initiate")
    fun signIn(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        payload: SignInRequest,
    ): Response

    @POST
    @Path("/completion")
    fun completeSignIn(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        payload: SignInCompletionRequest,
    ): Response

    @POST
    @Path("/otp-regeneration")
    fun resendOtp(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        payload: ResendOtpRequest,
    ): Response

    @POST
    @Path("/mfa-sessions/{sessionId}/email-challenges")
    fun createEmailFallbackChallenge(
        @Context request: HttpServerRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        @PathParam("sessionId") sessionId: String,
        payload: EmailFallbackChallengeRequest,
    ): Response
}
