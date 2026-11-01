package com.docuhyphen.app.api.resource.user.operations

import com.docuhyphen.app.api.resource.model.AuthenticatorEnrollmentRequest
import com.docuhyphen.app.api.resource.model.AuthenticatorEnrollmentVerificationRequest
import com.docuhyphen.app.api.resource.model.MfaConfigurationUpdateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/app-users/current")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AppUserMfaResourceOperations {
    @GET
    @Path("/mfa-configurations")
    fun getConfiguration(): Response

    @POST
    @Path("/authenticator-enrollments")
    fun startEnrollment(request: AuthenticatorEnrollmentRequest): Response

    @POST
    @Path("/authenticator-enrollments/{enrollmentId}/verifications")
    fun verifyEnrollment(
        @PathParam("enrollmentId") enrollmentId: String,
        request: AuthenticatorEnrollmentVerificationRequest,
    ): Response

    @PATCH
    @Path("/mfa-configurations")
    fun updateConfiguration(request: MfaConfigurationUpdateRequest): Response

    @DELETE
    @Path("/authenticator-enrollments")
    fun removeAuthenticator(): Response
}
