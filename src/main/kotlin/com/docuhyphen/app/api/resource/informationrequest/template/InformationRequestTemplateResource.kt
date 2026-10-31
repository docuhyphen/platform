package com.docuhyphen.app.api.resource.informationrequest.template

import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.template.operations.InformationRequestTemplateResourceOperations
import com.docuhyphen.app.api.resource.model.CloneInformationRequestTemplateRequest
import com.docuhyphen.app.api.resource.model.InformationRequestTemplateNewVersionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateAuthoringService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateLifecycleService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplatePublicationService
import jakarta.inject.Inject
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CREATED
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestTemplateResource @Inject constructor(
    private val authoringService: InformationRequestTemplateAuthoringService,
    private val publicationService: InformationRequestTemplatePublicationService,
    private val lifecycleService: InformationRequestTemplateLifecycleService,
) : InformationRequestTemplateResourceOperations
{
    override fun list(scopeKindParam: String?): Response
    {
        return try
        {
            val scopeKind = if (scopeKindParam.isNullOrBlank())
                null
            else
                parseScopeKind(scopeKindParam) ?: return badRequest("Invalid scopeKind")
            Response.ok(authoringService.listTemplates(scopeKind).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template list failed", exception)
        }
    }

    override fun create(request: CreateInformationRequestTemplateRequest, idempotencyKey: String?): Response
    {
        return try
        {
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            tagged(Response.status(CREATED), authoringService.createTemplate(request, key))
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template create failed", exception)
        }
    }

    override fun get(id: String): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            tagged(Response.ok(), authoringService.getTemplate(templateId))
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template get failed", exception)
        }
    }

    override fun replaceDraftConfiguration(
        id: String,
        request: InformationRequestTemplateConfigurationRequest,
        ifMatch: String?,
    ): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            tagged(
                Response.ok(),
                authoringService.replaceDraftConfiguration(
                    templateId,
                    request,
                    CommandPreconditionHeader.requiredExact(ifMatch)
                ),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request Template configuration failed",
                exception
            )
        }
    }

    override fun publishDraft(id: String, ifMatch: String?, idempotencyKey: String?): Response
    {
        return try
        {
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            tagged(
                Response.ok(),
                publicationService.publishTemplate(templateId, CommandPreconditionHeader.requiredExact(ifMatch), key),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template publication failed", exception)
        }
    }

    override fun createDraftVersion(
        id: String,
        request: InformationRequestTemplateNewVersionRequest,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            if (request.sourceVersionNumber < 1)
            {
                return badRequest("sourceVersionNumber must be positive")
            }
            tagged(
                Response.status(CREATED),
                lifecycleService.createDraftVersion(templateId, request.sourceVersionNumber, key),
            )
        } catch (exception: Exception) {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request Template version creation failed",
                exception
            )
        }
    }

    override fun getVersion(id: String, versionNumber: String): Response {
        return try {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            val requestedVersion = parsePositiveInt(versionNumber)
                ?: return badRequest("versionNumber must be positive")
            Response.ok(authoringService.getVersion(templateId, requestedVersion)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template version read failed", exception)
        }
    }

    override fun retireVersion(
        id: String,
        versionNumber: String,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            val requestedVersion = parsePositiveInt(versionNumber)
                ?: return badRequest("versionNumber must be positive")
            tagged(Response.ok(), lifecycleService.retireVersion(templateId, requestedVersion, key))
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template retirement failed", exception)
        }
    }

    override fun clone(
        id: String,
        request: CloneInformationRequestTemplateRequest,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            if (request.sourceVersionNumber < 1)
            {
                return badRequest("sourceVersionNumber must be positive")
            }
            tagged(
                Response.status(CREATED),
                lifecycleService.cloneTemplate(templateId, request.sourceVersionNumber, request.target, key),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request Template clone failed", exception)
        }
    }

    private fun tagged(builder: Response.ResponseBuilder, template: InformationRequestTemplateDto): Response {
        template.draftETag?.let { builder.header(HttpHeaders.ETAG, it) }
        return builder.entity(template).build()
    }

    private fun parseScopeKind(raw: String?): InformationRequestTemplateScopeKind? =
        raw?.let { candidate ->
            runCatching {
                InformationRequestTemplateScopeKind.valueOf(candidate.trim().uppercase())
            }.getOrNull()
        }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun parsePositiveInt(raw: String): Int? =
        runCatching { raw.toInt() }.getOrNull()?.takeIf { it > 0 }

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestTemplateResource::class.java)
    }
}
