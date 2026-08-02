package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateNoAuthExchange
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentService
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import com.docuhyphen.app.api.service.exchange.ExchangeUpdateService
import com.docuhyphen.app.api.service.exchange.ShareLinkValidationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

@Path("no-auth/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NoAuthExchangeResource @Inject constructor(
    private val exchangeRetrievalService: ExchangeRetrievalService,
    private val exchangeUpdateService: ExchangeUpdateService,
    private val exchangeDocumentService: ExchangeDocumentService,
    private val fileStorageService: FileStorageService,
    private val shareLinkValidationService: ShareLinkValidationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NoAuthExchangeResource::class.java)
    }

    /**
     * Returns basic exchange metadata for participant and public-link access.
     *
     * When an X-Share-Link-Token header is present the token is validated against the
     * exchange before any data is returned. An invalid, revoked, exhausted, or mismatched
     * link results in 403. When no token is present the endpoint serves the OTP-based
     * participant flow (participants have a named Share grant validated at OTP time).
     */
    @GET
    @Path("/{exchangeId}")
    fun getNoAuthExchange(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam("X-Share-Link-Token") shareLinkToken: String?,
        @HeaderParam("x-no-auth-access-token") noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            if (!shareLinkToken.isNullOrBlank())
            {
                val exchangeUuid = try
                {
                    UUID.fromString(exchangeId)
                }
                catch (_: Exception)
                {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError("Invalid exchange id"))
                        .build()
                }
                shareLinkValidationService.validateForNoAuth(
                    rawToken = shareLinkToken,
                    exchangeId = exchangeUuid,
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
                    logger.warn("No-auth exchange access denied ({}): {}", exchangeId, exception.message)
                    Response
                        .status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is ExchangeNotFoundException ->
                {
                    logger.error("Error getting exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting exchange", exception)

                    val responseError = ResponseError("An error occurred while getting exchange")
                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @POST
    @Path("/{exchangeId}/otp")
    fun issueNoAuthExchangeOtp(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam("x-no-auth-access-token") noAuthAccessToken: String?,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1000) { try
        {
            exchangeUpdateService.issueRecipientOtp(exchangeId, noAuthAccessToken)
            Response.status(Response.Status.NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                {
                    logger.warn("OTP request denied", exception)
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }
                is ExchangeNotFoundException ->
                {
                    logger.warn("OTP request for missing session", exception)
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message))
                        .build()
                }
                is NoAuthOtpException ->
                {
                    logger.warn("OTP request rejected: reasonCode={} retryAfter={}", exception.reasonCode, exception.retryAfterSeconds)
                    val response = Response.status(
                        if (exception.reasonCode == "OTP_RATE_LIMITED" || exception.reasonCode == "OTP_LOCKED")
                            TOO_MANY_REQUESTS
                        else
                            Response.Status.BAD_REQUEST,
                    )
                        .entity(
                            ResponseError(
                                errorMessage = exception.message,
                                reasonCode = exception.reasonCode,
                                retryAfterSeconds = exception.retryAfterSeconds,
                            )
                        )
                    if (exception.retryAfterSeconds != null)
                    {
                        response.header("Retry-After", exception.retryAfterSeconds)
                    }
                    response.build()
                }
                is IllegalArgumentException ->
                {
                    logger.warn("OTP request rejected", exception)
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }
                else ->
                {
                    logger.error("Error issuing exchange OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while issuing the verification code"))
                        .build()
                }
            }
        }
        }
    }

    @POST
    @Path("/{exchangeId}/verify-access-code")
    fun verifyNoAuthAccessCode(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam("x-no-auth-access-token") noAuthAccessToken: String?,
        request: UpdateNoAuthExchange,
    ): Response
    {
        return try
        {
            val updatedSession = exchangeUpdateService.verifyNoAuthAccessCode(exchangeId, request.otp, noAuthAccessToken)
            Response.ok(updatedSession).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is NoAuthOtpException ->
                {
                    val response = Response
                        .status(
                            if (exception.reasonCode == "OTP_RATE_LIMITED" || exception.reasonCode == "OTP_LOCKED")
                                TOO_MANY_REQUESTS
                            else
                                Response.Status.BAD_REQUEST,
                        )
                        .entity(
                            ResponseError(
                                errorMessage = exception.message,
                                reasonCode = exception.reasonCode,
                                retryAfterSeconds = exception.retryAfterSeconds,
                            )
                        )

                    if (exception.retryAfterSeconds != null)
                    {
                        response.header("Retry-After", exception.retryAfterSeconds)
                    }

                    response.build()
                }

                is ForbiddenException ->
                {
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is ExchangeNotFoundException ->
                {
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }

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

    @PUT
    @Path("/{exchangeId}")
    fun updateNoAuthExchange(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam("x-no-auth-access-token") noAuthAccessToken: String?,
        request: UpdateNoAuthExchange
    ): Response
    {
        return try
        {
            val updatedSession = with(request) {
                exchangeUpdateService.updateNoAuthExchange(
                    exchangeId,
                    status,
                    otp,
                    rejectReason ?: rejectionReason,
                    noAuthAccessToken,
                )
            }

            Response.ok(updatedSession).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeRecipientEligibilityException ->
                {
                    logger.warn("No-auth Exchange acceptance eligibility changed")
                    Response.status(Response.Status.CONFLICT)
                        .entity(ResponseError("This Exchange can no longer be accepted"))
                        .build()
                }

                is NoAuthOtpException ->
                {
                    logger.error("Error updating exchange: reasonCode={} retryAfter={}", exception.reasonCode, exception.retryAfterSeconds)

                    val response = Response
                        .status(
                            if (exception.reasonCode == "OTP_RATE_LIMITED" || exception.reasonCode == "OTP_LOCKED")
                                TOO_MANY_REQUESTS
                            else
                                Response.Status.BAD_REQUEST,
                        )
                        .entity(
                            ResponseError(
                                errorMessage = exception.message,
                                reasonCode = exception.reasonCode,
                                retryAfterSeconds = exception.retryAfterSeconds,
                            )
                        )

                    if (exception.retryAfterSeconds != null)
                    {
                        response.header("Retry-After", exception.retryAfterSeconds)
                    }

                    response.build()
                }

                is ForbiddenException ->
                {
                    logger.error("Error updating exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.FORBIDDEN)
                        .entity(responseError)
                        .build()
                }

                is ExchangeNotFoundException ->
                {
                    logger.error("Error adding exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating exchange", exception)

                    val responseError = ResponseError("An error occurred while updating exchange")
                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @POST
    @Path("{exchangeId}/documents/{documentId}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadSessionDocument(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
        @RestForm("encryptionMode") encryptionMode: DocumentEncryptionMode?,
        @PathParam("exchangeId") exchangeId: String?,
        @PathParam("documentId") documentId: String?,
        @HeaderParam("x-no-auth-access-token") noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            val document = exchangeDocumentService.uploadNoAuthDocument(
                file,
                extension,
                exchangeId,
                documentId,
                encryptionMode,
                noAuthAccessToken,
            )

            val dto = DetailedEntityToDtoTransformer.toDto(document)
            Response.ok(enrichDocumentWithFileSize(dto)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                {
                    logger.error("Error uploading exchange document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error uploading exchange document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error uploading exchange document", exception)
                    val responseError = ResponseError("An error occurred while uploading exchange document")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    @Path("{exchangeId}/documents/{documentId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocument(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @HeaderParam("x-no-auth-access-token") noAuthAccessToken: String?,
    ): Response
    {
        return try
        {
            val file = exchangeDocumentService.downloadNoAuthSessionDocument(exchangeId, documentId, noAuthAccessToken)
            Response.ok(file.inputStream())
                .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                .header("Content-Length", file.length())
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                {
                    logger.error("Error downloading exchange document", exception)

                    val responseError = ResponseError(exception.message)
                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is ForbiddenException ->
                {
                    logger.warn("No-auth download denied for document: {}", exception.message)
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error downloading exchange document", exception)

                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error downloading exchange document", exception)

                    val responseError = ResponseError("An error occurred while downloading exchange document")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    private fun enrichDocumentWithFileSize(document: DocumentDetailedDto?): DocumentDetailedDto?
    {
        if (document == null || document.uploadDate == null) return document

        val documentId = document.id ?: return document
        val documentType = document.type
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let { runCatching { DocumentType.valueOf(it) }.getOrNull() }
            ?: return document

        val storageKey = "$documentId${DocumentType.toFileExtension(documentType)}"
        val fileSize = runCatching { fileStorageService.getDocumentSizeBytes(storageKey) }.getOrNull()

        return document.copy(fileSize = fileSize)
    }
}
