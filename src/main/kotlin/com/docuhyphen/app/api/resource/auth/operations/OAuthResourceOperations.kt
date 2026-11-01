package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.RequestHeaders.REQUEST_ID
import com.docuhyphen.app.api.resource.model.OAuthLinkConfirmRequest
import com.docuhyphen.app.api.resource.model.OAuthTokenExchangeRequest
import io.vertx.core.http.HttpServerRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/oauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface OAuthResourceOperations
{
    @GET
    @Path("/{provider}/authorize")
    fun authorize(
        @PathParam("provider") providerName: String,
        @QueryParam("flow") flow: String?,
        @QueryParam("orgIdpConfigId") orgIdpConfigId: String?,
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
    ): Response

    @GET
    @Path("/{provider}/callback")
    fun callback(
        @PathParam("provider") providerName: String,
        @QueryParam("code") code: String?,
        @QueryParam("state") state: String?,
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
    ): Response

    @POST
    @Path("/token-exchanges")
    fun exchangeTokenHandoff(payload: OAuthTokenExchangeRequest): Response

    @POST
    @Path("/link-confirm")
    fun linkConfirm(
        payload: OAuthLinkConfirmRequest,
        @HeaderParam(REQUEST_ID) requestId: String?,
        @Context request: HttpServerRequest,
    ): Response
}
