package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.auth.OAuthRedirect
import com.docuhyphen.app.api.resource.auth.operations.OAuthResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.*
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class OAuthResource @Inject constructor(
    private val oauthAuthorizationService: OAuthAuthorizationService,
    private val oauthCallbackService: OAuthCallbackService,
    private val oauthLinkConfirmationService: OAuthLinkConfirmationService,
    private val oauthTokenHandoffService: OAuthTokenHandoffService,
    private val clientIpResolver: ClientIpResolver,
) : OAuthResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OAuthResource::class.java)
    }

    override fun authorize(
        providerName: String,
        flow: String?,
        orgIdpConfigId: String?,
        requestId: String?,
        request: HttpServerRequest,
    ): Response
    {
        return try
        {
            val authorizationUrl = oauthAuthorizationService.authorize(
                providerName = providerName,
                flow = flow,
                orgIdpConfigId = orgIdpConfigId,
                requestId = requestId,
                clientIp = clientIpResolver.resolve(request),
            )
            Response.temporaryRedirect(authorizationUrl).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException ->
                    Response.status(429).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error building authorization URL for $providerName", exception)
                    oauthAuthorizationService.recordAuthorizationFailure(requestId)
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError("Invalid provider: $providerName"))
                        .build()
                }
            }
        }
    }

    override fun callback(
        providerName: String,
        code: String?,
        state: String?,
        requestId: String?,
        request: HttpServerRequest,
    ): Response
    {
        return try
        {
            val redirect = oauthCallbackService.handleCallback(
                providerName = providerName,
                code = code,
                state = state,
                requestId = requestId,
                clientIp = clientIpResolver.resolve(request),
                userAgent = request.getHeader(AuthHttp.USER_AGENT_HEADER),
            )
            redirectResponse(redirect)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.error("Subscription denied during OAuth organization provisioning for $providerName", exception)
                    throw exception
                }

                else ->
                {
                    logger.error("Error during OAuth callback for $providerName", exception)
                    redirectResponse(oauthCallbackService.callbackFailed(requestId))
                }
            }
        }
    }

    override fun exchangeTokenHandoff(payload: OAuthTokenExchangeRequest): Response
    {
        return try
        {
            val code = payload.code?.trim()
            if (code.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("OAuth exchange code is required"))
                    .build()
            }

            val handoff = oauthTokenHandoffService.consume(code)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("OAuth exchange code is invalid or expired"))
                    .build()

            Response.ok(
                OAuthTokenExchangeResponse(
                    accessToken = handoff.accessToken,
                    idToken = handoff.idToken,
                    isNewUser = handoff.isNewUser,
                )
            ).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error exchanging OAuth handoff code", exception)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An error occurred while exchanging the OAuth code"))
                .build()
        }
    }

    override fun linkConfirm(
        payload: OAuthLinkConfirmRequest,
        requestId: String?,
        request: HttpServerRequest,
    ): Response
    {
        return try
        {
            if (payload.linkToken.isNullOrBlank() || payload.password.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Link token and password are required"))
                    .build()
            }

            val issued = oauthLinkConfirmationService.confirm(
                linkToken = payload.linkToken,
                password = payload.password,
                requestId = requestId,
                clientIp = clientIpResolver.resolve(request),
                userAgent = request.getHeader(AuthHttp.USER_AGENT_HEADER),
            )
            Response.ok(OAuthLinkConfirmResponse(issued.accessToken, issued.idToken))
                .cookie(issued.cookies.refreshCookie, issued.cookies.csrfCookie)
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is LinkConfirmationRejectedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is LinkTokenMalformedException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is LinkAccountNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is LinkAccountNotEligibleException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error during OAuth link confirmation", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred during account linking"))
                        .build()
                }
            }
        }
    }

    private fun redirectResponse(redirect: OAuthRedirect): Response
    {
        val builder = Response.temporaryRedirect(redirect.location)
        if (redirect.cookies.isNotEmpty())
        {
            builder.cookie(*redirect.cookies.toTypedArray())
        }
        return builder.build()
    }
}
