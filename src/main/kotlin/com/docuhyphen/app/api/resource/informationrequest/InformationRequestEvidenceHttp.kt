package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestEvidenceRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.slf4j.Logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

object InformationRequestEvidenceHttp
{
    const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    const val EVIDENCE_ETAG_HEADER = "X-Evidence-ETag"

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: throw InformationRequestEvidenceRequestException("Invalid $name")

    fun idempotencyKey(raw: String?): String =
        raw?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InformationRequestEvidenceRequestException("Idempotency-Key is required")

    fun evidenceFile(upload: FileUpload?, encryptionMode: String?): InformationRequestEvidenceFile
    {
        val file = upload ?: throw InformationRequestEvidenceRequestException("An evidence file is required")
        val declaredName = file.fileName()?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
        if (declaredName.isNullOrEmpty())
        {
            throw InformationRequestEvidenceRequestException("An evidence file states its file name")
        }
        val mode = encryptionMode?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            DocumentEncryptionMode.entries.firstOrNull { it.name == value.uppercase() }
                ?: throw InformationRequestEvidenceRequestException("Unsupported encryption mode")
        } ?: DocumentEncryptionMode.INTERNAL

        return requestValue {
            InformationRequestEvidenceFile(
                file = file.uploadedFile().toFile(),
                declaredFileName = declaredName,
                declaredMediaType = file.contentType()?.trim()?.takeIf { it.isNotEmpty() },
                encryptionMode = mode,
            )
        }
    }

    fun attributes(form: InformationRequestEvidenceAttributeForm): InformationRequestEvidenceAttributes =
        requestValue {
            val coverageStartsOn = date(form.coverageStartsOn, "coverage start")
            val coverageEndsOn = date(form.coverageEndsOn, "coverage end")
            if ((coverageStartsOn == null) != (coverageEndsOn == null))
            {
                throw InformationRequestEvidenceRequestException("A coverage period states both its start and its end")
            }
            InformationRequestEvidenceAttributes(
                issuer = text(form.issuer),
                jurisdiction = text(form.jurisdiction),
                language = text(form.language),
                issuedOn = date(form.issuedOn, "issue date"),
                expiresOn = date(form.expiresOn, "expiry date"),
                coverage = coverageStartsOn?.let { InformationRequestEvidenceCoverage(it, requireNotNull(coverageEndsOn)) },
                certificationReference = text(form.certificationReference),
                signatureReference = text(form.signatureReference),
            )
        }

    fun content(content: InformationRequestEvidenceContent): Response =
        Response.ok(content.file.inputStream(), content.mediaType)
            .header("Content-Disposition", contentDisposition(content))
            .header("Content-Length", content.file.length())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .header("Cache-Control", "no-store")
            .build()

    fun refused(logger: Logger, message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is InformationRequestEvidenceRequestException -> error(BAD_REQUEST, exception.message)
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> error(CONFLICT, exception.message, exception.reasonCode)
            is InformationRequestLifecycleException ->
                if (exception.reasonCode == InformationRequestErrorCatalog.NOT_FOUND)
                    error(NOT_FOUND, exception.message, exception.reasonCode)
                else
                    error(CONFLICT, exception.message, exception.reasonCode)
            is ForbiddenException -> error(FORBIDDEN, exception.message)
            is UnauthorizedException -> error(UNAUTHORIZED, exception.message)
            else ->
            {
                logger.error(message, exception)
                error(INTERNAL_SERVER_ERROR, "Request failed")
            }
        }
    }

    fun notFound(): Response = error(NOT_FOUND, "Information Request not found")

    private fun error(status: Response.Status, message: String?, reasonCode: String? = null): Response =
        Response.status(status).entity(ResponseError(message, reasonCode)).build()

    private fun contentDisposition(content: InformationRequestEvidenceContent): String
    {
        val kind = if (content.inline) "inline" else "attachment"
        val fallback = content.fileName.map { if (it.code in 0x20..0x7e && it != '"' && it != '\\') it else '_' }
            .joinToString("")
        val encoded = URLEncoder.encode(content.fileName, StandardCharsets.UTF_8).replace("+", "%20")
        return "$kind; filename=\"$fallback\"; filename*=UTF-8''$encoded"
    }

    private fun text(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun date(value: String?, name: String): LocalDate? =
        text(value)?.let { stated ->
            try
            {
                LocalDate.parse(stated)
            }
            catch (_: DateTimeParseException)
            {
                throw InformationRequestEvidenceRequestException("The $name is not an ISO date")
            }
        }

    private fun <T> requestValue(block: () -> T): T =
        try
        {
            block()
        }
        catch (exception: IllegalArgumentException)
        {
            throw InformationRequestEvidenceRequestException(exception.message ?: "Invalid evidence request")
        }
}
