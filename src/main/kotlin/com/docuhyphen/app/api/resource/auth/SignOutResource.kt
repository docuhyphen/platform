package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.model.auth.SignOutCommand
import com.docuhyphen.app.api.resource.auth.operations.SignOutResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthRequestRateLimitedException
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.auth.CsrfValidationFailedException
import com.docuhyphen.app.api.service.auth.SignOutFlowService
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory

class SignOutResource @Inject constructor(
    private val signOutFlowService: SignOutFlowService,
    private val clientIpResolver: ClientIpResolver,
) : SignOutResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutResource::class.java)
        private const val TOO_MANY_REQUESTS = 429
    }

    override fun signOut(
        outOfAllDevices: Boolean,
        csrfCookie: Cookie?,
        csrfHeader: String?,
        originHeader: String?,
        refererHeader: String?,
        requestId: String?,
        request: HttpServerRequest,
    ): Response
    {
        return try
        {
            val clearedCookies = signOutFlowService.signOut(
                SignOutCommand(
                    outOfAllDevices = outOfAllDevices,
                    csrfCookie = csrfCookie?.value,
                    csrfHeader = csrfHeader,
                    originHeader = originHeader,
                    refererHeader = refererHeader,
                    requestId = requestId,
                    clientIp = clientIpResolver.resolve(request),
                )
            )
            Response.ok().cookie(clearedCookies.refreshCookie, clearedCookies.csrfCookie).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is CsrfValidationFailedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error during sign-out.", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred."))
                        .build()
                }
            }
        }
    }

    override fun getLogoutPropagationInfo(): Response
    {
        return try
        {
            Response.ok(signOutFlowService.propagationInfo()).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error describing sign-out propagation", exception)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An unexpected error occurred."))
                .build()
        }
    }
}
