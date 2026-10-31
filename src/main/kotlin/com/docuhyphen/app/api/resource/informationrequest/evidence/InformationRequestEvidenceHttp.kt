package com.docuhyphen.app.api.resource.informationrequest.evidence

import com.docuhyphen.app.api.exception.InformationRequestEvidenceRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceFile
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import jakarta.ws.rs.core.StreamingOutput
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.slf4j.Logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.*

object InformationRequestEvidenceHttp
{
    const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    const val EVIDENCE_ETAG_HEADER = "X-Evidence-ETag"
    private const val REASON_LIMIT = 512

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: throw InformationRequestEvidenceRequestException("Invalid $name")

    fun idempotencyKey(raw: String?): String =
        InformationRequestCommandHttp.idempotencyKey(
            CommandIdempotencyKeyHeader.stated(raw)
                ?: throw InformationRequestEvidenceRequestException("Idempotency-Key is required"),
        )

    fun reason(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.also { reason ->
            if (reason.length > REASON_LIMIT) {
                throw InformationRequestEvidenceRequestException("A reason is at most $REASON_LIMIT characters")
            }
        }

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
                coverage = coverageStartsOn?.let {
                    InformationRequestEvidenceCoverage(
                        it,
                        requireNotNull(coverageEndsOn)
                    )
                },
                certificationReference = text(form.certificationReference),
                signatureReference = text(form.signatureReference),
            )
        }

    fun content(content: InformationRequestEvidenceContent): Response =
        try {
            Response.ok(StreamingOutput(content::transferTo), content.mediaType)
                .header("Content-Disposition", contentDisposition(content))
                .header("Content-Length", content.file.length())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header("Cache-Control", "no-store")
                .build()
        } catch (failure: Exception) {
            content.close()
            throw failure
        }

    fun refused(logger: Logger, message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is InformationRequestEvidenceRequestException -> error(
                BAD_REQUEST,
                exception.message,
                InformationRequestErrorCatalog.EVIDENCE_REQUEST_INVALID
            )
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> error(CONFLICT, exception.message, exception.reasonCode)
            is InformationRequestLifecycleException -> InformationRequestCommandHttp.lifecycleRefused(exception)
            is ForbiddenException -> error(FORBIDDEN, exception.message, InformationRequestErrorCatalog.FORBIDDEN)
            is UnauthorizedException -> error(UNAUTHORIZED, exception.message)
            is com.docuhyphen.app.api.exception.InformationRequestCommandRequestException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is IllegalArgumentException -> error(NOT_FOUND, exception.message)
            else -> InformationRequestCommandHttp.storageRuleRefused(logger, message, exception) ?: run {
                logger.error(message, exception)
                error(INTERNAL_SERVER_ERROR, "Request failed")
            }
        }
    }

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
