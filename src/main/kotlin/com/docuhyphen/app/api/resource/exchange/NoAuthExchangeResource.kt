package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.exchange.handler.ExchangeDocumentRequestHandler
import com.docuhyphen.app.api.resource.exchange.operations.NoAuthExchangeResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateNoAuthExchange
import com.docuhyphen.app.api.service.exchange.*
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.io.File

class NoAuthExchangeResource @Inject constructor(
    private val exchangeRetrievalService: ExchangeRetrievalService,
    private val exchangeUpdateService: ExchangeUpdateService,
    exchangeDocumentService: ExchangeDocumentService,
    exchangeDocumentDetailsViewService: ExchangeDocumentDetailsViewService,
    private val shareLinkValidationService: ShareLinkValidationService,
) : NoAuthExchangeResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NoAuthExchangeResource::class.java)

        private const val OTP_RESPONSE_FLOOR_MILLIS = 1000L
    }

    private val handler = ExchangeDocumentRequestHandler(exchangeDocumentService, exchangeDocumentDetailsViewService)

    override fun getNoAuthExchange(
        exchangeId: String,
        shareLinkToken: String?,
        noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            if (!shareLinkToken.isNullOrBlank())
            {
                shareLinkValidationService.validateForNoAuth(
                    rawToken = shareLinkToken,
                    exchangeId = RequestGuards.uuid(exchangeId, "Invalid Exchange id"),
                )
            }

            val exchange = exchangeRetrievalService.getNoAuthExchange(
                exchangeId,
                noAuthAccessToken,
                shareLinkTokenValidated = !shareLinkToken.isNullOrBlank(),
            )

            Response.ok(exchange).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                {
                    logger.warn("No-auth Exchange access denied ({}): {}", exchangeId, exception.message)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is ExchangeNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting no-auth Exchange", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting Exchange"))
                        .build()
                }
            }
        }
    }

    override fun issueNoAuthExchangeOtp(
        exchangeId: String,
        noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(OTP_RESPONSE_FLOOR_MILLIS) {
                exchangeUpdateService.issueRecipientOtp(exchangeId, noAuthAccessToken)
            }
            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                {
                    logger.warn("OTP request denied", exception)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is ExchangeNotFoundException ->
                {
                    logger.warn("OTP request for missing Exchange", exception)
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()
                }

                is NoAuthOtpException ->
                {
                    logger.warn(
                        "OTP request rejected: reasonCode={} retryAfter={}",
                        exception.reasonCode,
                        exception.retryAfterSeconds
                    )
                    NoAuthExchangeHttp.otpRefused(exception)
                }

                is IllegalArgumentException ->
                {
                    logger.warn("OTP request rejected", exception)
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                }

                else ->
                {
                    logger.error("Error issuing no-auth Exchange OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while issuing the verification code"))
                        .build()
                }
            }
        }
    }

    override fun verifyNoAuthAccessCode(
        exchangeId: String,
        noAuthAccessToken: String?,
        request: UpdateNoAuthExchange,
    ): Response
    {
        return try
        {
            val updatedExchange =
                exchangeUpdateService.verifyNoAuthAccessCode(exchangeId, request.otp, noAuthAccessToken)
            Response.ok(updatedExchange).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is NoAuthOtpException -> NoAuthExchangeHttp.otpRefused(exception)

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ExchangeNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error verifying no-auth access code", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while verifying access code"))
                        .build()
                }
            }
        }
    }

    override fun updateNoAuthExchange(
        exchangeId: String,
        noAuthAccessToken: String?,
        request: UpdateNoAuthExchange
    ): Response
    {
        return try
        {
            val updatedExchange = exchangeUpdateService.updateNoAuthExchange(
                exchangeId,
                request.status,
                request.otp,
                request.rejectReason ?: request.rejectionReason,
                noAuthAccessToken,
            )

            Response.ok(updatedExchange).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeRecipientEligibilityException ->
                {
                    logger.warn("No-auth Exchange acceptance eligibility changed")
                    Response.status(CONFLICT)
                        .entity(ResponseError("This Exchange can no longer be accepted"))
                        .build()
                }

                is NoAuthOtpException ->
                {
                    logger.warn(
                        "No-auth Exchange update rejected: reasonCode={} retryAfter={}",
                        exception.reasonCode,
                        exception.retryAfterSeconds
                    )
                    NoAuthExchangeHttp.otpRefused(exception)
                }

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is ExchangeNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating no-auth Exchange", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while updating Exchange"))
                        .build()
                }
            }
        }
    }

    override fun uploadSessionDocument(
        file: File?,
        extension: String?,
        encryptionMode: DocumentEncryptionMode?,
        exchangeId: String?,
        documentId: String?,
        noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            handler.uploadNoAuth(file, extension, exchangeId, documentId, encryptionMode, noAuthAccessToken)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is NoAuthExchangeAccessExpiredException ->
                {
                    logger.warn("No-auth upload blocked by a lapsed access window: {}", exception.message)
                    NoAuthExchangeHttp.accessVerificationRequired(exception)
                }

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error uploading no-auth Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while uploading Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun downloadDocument(
        exchangeId: String,
        documentId: String,
        noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            handler.downloadNoAuth(exchangeId, documentId, noAuthAccessToken)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                {
                    logger.warn("No-auth download denied for document: {}", exception.message)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is NoAuthExchangeAccessExpiredException ->
                {
                    logger.warn("No-auth download blocked by a lapsed access window: {}", exception.message)
                    NoAuthExchangeHttp.accessVerificationRequired(exception)
                }

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error downloading no-auth Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while downloading Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun getNoAuthDocumentThumbnail(
        exchangeId: String,
        documentId: String,
        noAuthAccessToken: String?,
        ifNoneMatch: String?,
    ): Response
    {
        return try
        {
            handler.thumbnailNoAuth(exchangeId, documentId, noAuthAccessToken, ifNoneMatch)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                {
                    logger.warn("No-auth document thumbnail access denied: {}", exception.message)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is DocumentThumbnailUnavailableException ->
                {
                    logger.warn("No-auth document thumbnail is unavailable: {}", exception.message)
                    Response.status(SERVICE_UNAVAILABLE).entity(ResponseError(exception.message)).build()
                }

                is NoAuthExchangeAccessExpiredException ->
                {
                    logger.warn("No-auth thumbnail blocked by a lapsed access window: {}", exception.message)
                    NoAuthExchangeHttp.accessVerificationRequired(exception)
                }

                is IllegalArgumentException ->
                {
                    logger.warn("No-auth document thumbnail request rejected: {}", exception.message)
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                }

                else ->
                {
                    logger.error("Error loading no-auth document thumbnail", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while loading the document thumbnail"))
                        .build()
                }
            }
        }
    }
}
