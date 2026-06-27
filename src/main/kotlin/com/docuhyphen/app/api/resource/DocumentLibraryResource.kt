package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CloneDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.CreateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

/**
 * REST endpoints for document library management.
 *
 *   GET    /document-library                  - list accessible entries
 *   POST   /document-library                  - create entry (metadata stub)
 *   GET    /document-library/{id}             - get full entry
 *   PUT    /document-library/{id}             - update entry
 *   PATCH  /document-library/{id}/status      - activate / deactivate
 *   PATCH  /document-library/{id}/published   - publish / unpublish
 *   DELETE /document-library/{id}             - soft-delete
 *   POST   /document-library/{id}/file        - upload file
 *   GET    /document-library/{id}/file        - download file
 *   POST   /document-library/{id}/clone       - clone to PERSONAL scope
 */
@Path("/document-library")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class DocumentLibraryResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val documentLibraryService: DocumentLibraryService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(DocumentLibraryResource::class.java)
    }

    @GET
    fun listEntries(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        return try
        {
            val items = documentLibraryService.listEntries(actor.id, callerOrgId, isOrgAdmin, isAppAdmin, scope, tag)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list document library entries", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list document library entries")).build()
        }
    }

    @POST
    fun createEntry(request: CreateDocumentLibraryEntryRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.title.isBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("title is required")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        return try
        {
            val dto = documentLibraryService.createEntry(request, actor.id, callerOrgId, isOrgAdmin, isAppAdmin)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to create document library entry", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create document library entry")).build()
        }
    }

    @GET
    @Path("/{id}")
    fun getEntry(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = documentLibraryService.getEntry(entryId, actor.id, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to get document library entry {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get document library entry")).build()
        }
    }

    @PUT
    @Path("/{id}")
    fun updateEntry(@PathParam("id") id: String, request: UpdateDocumentLibraryEntryRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = documentLibraryService.updateEntry(entryId, request, actor.id, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to update document library entry {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update document library entry")).build()
        }
    }

    @PATCH
    @Path("/{id}/status")
    fun patchStatus(@PathParam("id") id: String, request: PatchDocumentLibraryStatusRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = documentLibraryService.patchStatus(entryId, request, actor.id, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to patch document library entry status {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to patch document library entry status")).build()
        }
    }

    @PATCH
    @Path("/{id}/published")
    fun patchPublished(@PathParam("id") id: String, request: PatchDocumentLibraryPublishedRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        if (!isOrgAdmin && !isAppAdmin)
        {
            return Response.status(FORBIDDEN).entity(ResponseError("Org admin or app admin role required")).build()
        }

        return try
        {
            val dto = documentLibraryService.patchPublished(entryId, request, actor.id, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to patch document library entry published {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to patch document library entry published")).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteEntry(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            documentLibraryService.deleteEntry(entryId, actor.id, callerOrgId, isAppAdmin)
            Response.noContent().build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to delete document library entry {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete document library entry")).build()
        }
    }

    @POST
    @Path("/{id}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadFile(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
        @PathParam("id") id: String,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (file == null)
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("file is required")).build()
        }
        if (extension.isNullOrBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("extension is required")).build()
        }

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = documentLibraryService.uploadFile(entryId, file, extension, actor.id, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to upload file for document library entry {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to upload file")).build()
        }
    }

    @GET
    @Path("/{id}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadFile(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val file = documentLibraryService.downloadFile(entryId, actor.id, callerOrgId, isAppAdmin)
            Response.ok(file.inputStream())
                .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                .header("Content-Length", file.length())
                .build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to download file for document library entry {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to download file")).build()
        }
    }

    @POST
    @Path("/{id}/clone")
    fun cloneEntry(@PathParam("id") id: String, request: CloneDocumentLibraryEntryRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        return try
        {
            val dto = documentLibraryService.cloneEntry(entryId, request, actor.id, callerOrgId, isOrgAdmin, isAppAdmin)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to clone document library entry {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to clone document library entry")).build()
        }
    }
}
