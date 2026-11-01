package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.model.auth.TokenRefreshCommand
import com.docuhyphen.app.api.resource.auth.operations.TokenRefreshResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.TokenRefreshResponse
import com.docuhyphen.app.api.service.auth.AuthRequestRateLimitedException
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.TokenRefreshDeniedException
import com.docuhyphen.app.api.service.auth.TokenRefreshService
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class TokenRefreshResource @Inject constructor(
    private val tokenRefreshService: TokenRefreshService,
    private val clientIpResolver: ClientIpResolver,
) : TokenRefreshResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(TokenRefreshResource::class.java)
        private const val TOO_MANY_REQUESTS = 429
    }

    override fun refreshToken(
        refreshTokenCookie: Cookie?,
        csrfCookie: Cookie?,
        csrfHeader: String?,
        originHeader: String?,
        refererHeader: String?,
        userAgent: String?,
        requestId: String?,
        request: HttpServerRequest,
    ): Response
    {
        return try
        {
            val issued = tokenRefreshService.refresh(
                TokenRefreshCommand(
                    refreshToken = refreshTokenCookie?.value,
                    csrfCookie = csrfCookie?.value,
                    csrfHeader = csrfHeader,
                    originHeader = originHeader,
                    refererHeader = refererHeader,
                    userAgent = userAgent,
                    requestId = requestId,
                    clientIp = clientIpResolver.resolve(request),
                )
            )
            Response.ok(TokenRefreshResponse(issued.accessToken, issued.idToken))
                .cookie(issued.cookies.refreshCookie, issued.cookies.csrfCookie)
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is TokenRefreshDeniedException ->
                    Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError(exception.message, exception.reasonCode.name))
                        .build()

                else ->
                {
                    logger.error("Error refreshing token", exception)
                    Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Failed to refresh token", RevocationReasonCode.REFRESH_INVALID.name))
                        .build()
                }
            }
        }
    }
}
