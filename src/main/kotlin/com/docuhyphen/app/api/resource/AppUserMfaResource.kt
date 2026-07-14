package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.AuthenticatorEnrollmentRequest
import com.docuhyphen.app.api.resource.model.AuthenticatorEnrollmentVerificationRequest
import com.docuhyphen.app.api.resource.model.MfaConfigurationUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthenticatorMfaService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/app-users/current")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AppUserMfaResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authenticatorMfaService: AuthenticatorMfaService,
)
{
    @GET
    @Path("/mfa-configurations")
    fun getConfiguration(): Response = withCurrentUser { appUser ->
        Response.ok(authenticatorMfaService.getConfiguration(appUser)).build()
    }

    @POST
    @Path("/authenticator-enrollments")
    fun startEnrollment(request: AuthenticatorEnrollmentRequest): Response = withCurrentUser { appUser ->
        Response.status(Response.Status.CREATED)
            .entity(authenticatorMfaService.startEnrollment(appUser, request.provider))
            .build()
    }

    @POST
    @Path("/authenticator-enrollments/{enrollmentId}/verifications")
    fun verifyEnrollment(
        @PathParam("enrollmentId") enrollmentId: String,
        request: AuthenticatorEnrollmentVerificationRequest,
    ): Response = withCurrentUser { appUser ->
        Response.ok(
            authenticatorMfaService.completeEnrollment(
                appUser,
                UUID.fromString(enrollmentId),
                request.code,
                request.emailFallbackEnabled,
            )
        ).build()
    }

    @PATCH
    @Path("/mfa-configurations")
    fun updateConfiguration(request: MfaConfigurationUpdateRequest): Response = withCurrentUser { appUser ->
        Response.ok(authenticatorMfaService.updateEmailFallback(appUser, request.emailFallbackEnabled)).build()
    }

    @DELETE
    @Path("/authenticator-enrollments")
    fun removeAuthenticator(): Response = withCurrentUser { appUser ->
        Response.ok(authenticatorMfaService.removeAuthenticator(appUser)).build()
    }

    private fun withCurrentUser(action: (com.docuhyphen.app.api.model.entity.AppUser) -> Response): Response
    {
        val appUser = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Authentication is required.")).build()
        return try
        {
            action(appUser)
        }
        catch (exception: IllegalArgumentException)
        {
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()
        }
    }
}
