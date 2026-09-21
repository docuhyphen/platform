package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.resource.model.CloneInformationRequestTemplateRequest
import com.docuhyphen.app.api.resource.model.InformationRequestTemplateNewVersionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateAuthoringService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateLifecycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplatePublicationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateValidationException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * REST adapter for reusable Information Request Template administration.
 *
 * The resource validates path and query shape, then delegates all owner, entitlement, lifecycle,
 * and persistence decisions to the Template services.
 */
@Path("/information-request-templates")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestTemplateResource @Inject constructor(
    private val authoringService: InformationRequestTemplateAuthoringService,
    private val publicationService: InformationRequestTemplatePublicationService,
    private val lifecycleService: InformationRequestTemplateLifecycleService,
)
{
    @GET
    fun list(@QueryParam("scopeKind") scopeKindParam: String?): Response
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
            handleException("Information Request Template list failed", exception)
        }
    }

    @POST
    fun create(request: CreateInformationRequestTemplateRequest): Response
    {
        return try
        {
            Response.status(CREATED).entity(authoringService.createTemplate(request)).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template create failed", exception)
        }
    }

    @GET
    @Path("/{id}")
    fun get(@PathParam("id") id: String): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            Response.ok(authoringService.getTemplate(templateId)).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template get failed", exception)
        }
    }

    @PUT
    @Path("/{id}/draft/configuration")
    fun replaceDraftConfiguration(
        @PathParam("id") id: String,
        request: InformationRequestTemplateConfigurationRequest,
    ): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            Response.ok(authoringService.replaceDraftConfiguration(templateId, request)).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template configuration failed", exception)
        }
    }

    @POST
    @Path("/{id}/draft/publication")
    fun publishDraft(@PathParam("id") id: String): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            Response.ok(publicationService.publishTemplate(templateId)).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template publication failed", exception)
        }
    }

    @POST
    @Path("/{id}/versions")
    fun createDraftVersion(
        @PathParam("id") id: String,
        request: InformationRequestTemplateNewVersionRequest,
    ): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            if (request.sourceVersionNumber < 1)
            {
                return badRequest("sourceVersionNumber must be positive")
            }
            Response.status(CREATED)
                .entity(lifecycleService.createDraftVersion(templateId, request.sourceVersionNumber))
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template version creation failed", exception)
        }
    }

    /**
     * Retirement is a subordinate lifecycle record of one exact Version. The Version is named in the
     * path rather than resolved as the latest published one, so a client can never retire a Version
     * that was published between the display it acted on and this call.
     */
    @POST
    @Path("/{id}/versions/{versionNumber}/retirement")
    fun retireVersion(
        @PathParam("id") id: String,
        @PathParam("versionNumber") versionNumber: String,
    ): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            val requestedVersion = parsePositiveInt(versionNumber)
                ?: return badRequest("versionNumber must be positive")
            Response.ok(lifecycleService.retireVersion(templateId, requestedVersion)).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template retirement failed", exception)
        }
    }

    @POST
    @Path("/{id}/clones")
    fun clone(
        @PathParam("id") id: String,
        request: CloneInformationRequestTemplateRequest,
    ): Response
    {
        return try
        {
            val templateId = parseUuid(id)
                ?: return badRequest("Invalid information request template id")
            if (request.sourceVersionNumber < 1)
            {
                return badRequest("sourceVersionNumber must be positive")
            }
            Response.status(CREATED)
                .entity(lifecycleService.cloneTemplate(templateId, request.sourceVersionNumber, request.target))
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request Template clone failed", exception)
        }
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

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is InformationRequestTemplateValidationException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()
            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(ResponseError(exception.message)).build()
            is UnauthorizedException -> Response.status(UNAUTHORIZED)
                .entity(ResponseError(exception.message)).build()
            else ->
            {
                logger.error(message, exception)
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
            }
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestTemplateResource::class.java)
    }
}

