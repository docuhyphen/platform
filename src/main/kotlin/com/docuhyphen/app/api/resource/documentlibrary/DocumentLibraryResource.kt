package com.docuhyphen.app.api.resource.documentlibrary

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.documentlibrary.operations.DocumentLibraryResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.io.File

class DocumentLibraryResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val documentLibraryService: DocumentLibraryService,
) : DocumentLibraryResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(DocumentLibraryResource::class.java)

        private const val INVALID_ENTRY_ID = "Invalid document library entry id"
    }

    override fun listEntries(
        scope: String?,
        tag: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = documentLibraryService.listEntries(scope, tag)
            Response.ok(items.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Listing document library entries was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to list document library entries", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to list document library entries"))
                        .build()
                }
            }
        }
    }

    override fun createEntry(request: CreateDocumentLibraryEntryRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            RequestGuards.requireNotBlank(request.title, "title is required")
            val dto = documentLibraryService.createEntry(request)
            Response.status(CREATED).entity(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Creating a document library entry was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to create document library entry", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to create document library entry"))
                        .build()
                }
            }
        }
    }

    override fun getEntry(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val dto = documentLibraryService.getEntry(entryId)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Reading a document library entry was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to get document library entry {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to get document library entry"))
                        .build()
                }
            }
        }
    }

    override fun updateEntry(id: String, request: UpdateDocumentLibraryEntryRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val dto = documentLibraryService.updateEntry(entryId, request)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Updating a document library entry was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to update document library entry {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to update document library entry"))
                        .build()
                }
            }
        }
    }

    override fun patchStatus(id: String, request: PatchDocumentLibraryStatusRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val dto = documentLibraryService.patchStatus(entryId, request)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Changing a document library entry status was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to patch document library entry status {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch document library entry status"))
                        .build()
                }
            }
        }
    }

    override fun patchPublished(id: String, request: PatchDocumentLibraryPublishedRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val dto = documentLibraryService.patchPublished(entryId, request)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Publishing a document library entry was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to patch document library entry published {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch document library entry published"))
                        .build()
                }
            }
        }
    }

    override fun deleteEntry(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            documentLibraryService.deleteEntry(entryId)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Deleting a document library entry was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to delete document library entry {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to delete document library entry"))
                        .build()
                }
            }
        }
    }

    override fun uploadFile(
        file: File?,
        extension: String?,
        id: String,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val uploadedFile = file ?: throw RequestValidationException("file is required")
            val statedExtension = RequestGuards.requireNotBlank(extension, "extension is required")
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val dto = documentLibraryService.uploadFile(entryId, uploadedFile, statedExtension)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Uploading a document library file was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to upload file for document library entry {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to upload file")).build()
                }
            }
        }
    }

    override fun downloadFile(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val file = documentLibraryService.downloadFile(entryId)
            Response.ok(file.inputStream())
                .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                .header("Content-Length", file.length())
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Downloading a document library file was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to download file for document library entry {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to download file")).build()
                }
            }
        }
    }

    override fun cloneEntry(id: String, request: CloneDocumentLibraryEntryRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val entryId = RequestGuards.uuid(id, INVALID_ENTRY_ID)
            val dto = documentLibraryService.cloneEntry(entryId, request)
            Response.status(CREATED).entity(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Cloning a document library entry was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to clone document library entry {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to clone document library entry"))
                        .build()
                }
            }
        }
    }
}
