package com.docuhyphen.app.api.resource.documentlibrary

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.CloneDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.CreateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        return try
        {
            val items = documentLibraryService.listEntries(scope, tag)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Listing document library entries was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.title.isBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("title is required")).build()
        }

        return try
        {
            val dto = documentLibraryService.createEntry(request)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Creating a document library entry was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            val dto = documentLibraryService.getEntry(entryId)
            Response.ok(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Reading a document library entry was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            val dto = documentLibraryService.updateEntry(entryId, request)
            Response.ok(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Updating a document library entry was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            val dto = documentLibraryService.patchStatus(entryId, request)
            Response.ok(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Changing a document library entry status was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            val dto = documentLibraryService.patchPublished(entryId, request)
            Response.ok(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Publishing a document library entry was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            documentLibraryService.deleteEntry(entryId)
            Response.noContent().build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Deleting a document library entry was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
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

        return try
        {
            val dto = documentLibraryService.uploadFile(entryId, file, extension)
            Response.ok(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Uploading a document library file was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            val file = documentLibraryService.downloadFile(entryId)
            Response.ok(file.inputStream())
                .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                .header("Content-Length", file.length())
                .build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Downloading a document library file was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val entryId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid document library entry id")).build()
        }

        return try
        {
            val dto = documentLibraryService.cloneEntry(entryId, request)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn(
                "Cloning a document library entry was refused by the subscription plan check: plan={}",
                e.denial.planCode,
            )
            throw e
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
