package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.RequestHeaders.REQUEST_ID
import com.docuhyphen.app.api.resource.auth.AuthHttp.CSRF_TOKEN_COOKIE
import com.docuhyphen.app.api.resource.auth.AuthHttp.CSRF_TOKEN_HEADER
import com.docuhyphen.app.api.resource.auth.AuthHttp.ORIGIN_HEADER
import com.docuhyphen.app.api.resource.auth.AuthHttp.REFERER_HEADER
import com.docuhyphen.app.api.resource.auth.AuthHttp.REFRESH_TOKEN_COOKIE
import com.docuhyphen.app.api.resource.auth.AuthHttp.USER_AGENT_HEADER
import io.vertx.core.http.HttpServerRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/token")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface TokenRefreshResourceOperations
{
    @POST
    @Path("/refresh")
    fun refreshToken(
        @CookieParam(REFRESH_TOKEN_COOKIE) refreshTokenCookie: Cookie?,
        @CookieParam(CSRF_TOKEN_COOKIE) csrfCookie: Cookie?,
        @HeaderParam(CSRF_TOKEN_HEADER) csrfHeader: String?,
        @HeaderParam(ORIGIN_HEADER) originHeader: String?,
        @HeaderParam(REFERER_HEADER) refererHeader: String?,
        @HeaderParam(USER_AGENT_HEADER) userAgent: String?,
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
    ): Response
}
