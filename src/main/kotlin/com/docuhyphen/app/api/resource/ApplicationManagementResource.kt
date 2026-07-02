package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.ApplicationNotFoundException
import com.docuhyphen.app.api.model.entity.Application
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.ApplicationManagementService
import com.docuhyphen.app.api.service.application.CreateApplicationRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.NO_CONTENT
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/admin/applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ApplicationManagementResource @Inject constructor(
    private val applicationManagementService: ApplicationManagementService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationManagementResource::class.java)
    }

    @GET
    fun listApplications(): Response = guarded {
        val apps = applicationManagementService.listAll(principal(), context())
        Response.ok(apps.map { it.toDto() }).build()
    }

    @POST
    fun createApplication(body: CreateApplicationRequestBody): Response = guarded {
        require(!body.name.isNullOrBlank()) { "name is required" }
        val appType = body.applicationType?.trim()?.uppercase()
            ?.let { runCatching { com.docuhyphen.app.api.model.entity.ApplicationType.valueOf(it) }.getOrNull() }
            ?: com.docuhyphen.app.api.model.entity.ApplicationType.SERVICE
        val request = CreateApplicationRequest(
            name = body.name,
            description = body.description,
            applicationType = appType,
            ownerOrganizationId = body.ownerOrganizationId?.let { UUID.fromString(it) },
            grantedCapabilities = body.grantedCapabilities ?: emptyList(),
        )
        val (app, rawSecret) = applicationManagementService.create(request, principal(), context())
        Response.status(CREATED).entity(app.toCreatedDto(rawSecret)).build()
    }

    @GET
    @Path("/{id}")
    fun getApplication(@PathParam("id") id: String): Response = guarded {
        val appId = parseUuid(id)
        val app = applicationManagementService.getById(appId, principal(), context())
        Response.ok(app.toDto()).build()
    }

    @POST
    @Path("/{id}/credentials/rotate")
    fun rotateCredentials(@PathParam("id") id: String): Response = guarded {
        val appId = parseUuid(id)
        val result = applicationManagementService.rotateCredentials(appId, principal(), context())
        Response.ok(RotateCredentialsResponse(result.apiKey, result.rawSecret)).build()
    }

    @PUT
    @Path("/{id}/capabilities")
    fun updateCapabilities(
        @PathParam("id") id: String,
        body: UpdateCapabilitiesRequestBody
    ): Response = guarded {
        val appId = parseUuid(id)
        applicationManagementService.updateGrantedCapabilities(appId, body.capabilities ?: emptyList(), principal(), context())
        Response.ok().build()
    }

    @DELETE
    @Path("/{id}")
    fun deactivateApplication(@PathParam("id") id: String): Response = guarded {
        val appId = parseUuid(id)
        applicationManagementService.deactivate(appId, principal(), context())
        Response.status(NO_CONTENT).build()
    }

    // -------------------------------------------------------------------------

    private fun principal() = authorizationContextFactory.currentPrincipal()
        ?: throw SecurityException("Authentication required")

    private fun context() = authorizationContextFactory.currentContext()

    private fun parseUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw IllegalArgumentException("Invalid id") }

    private fun guarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: Exception)
        {
            logger.error("Error in application management endpoint", e)
            when (e)
            {
                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
                is ApplicationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
                else ->
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred")).build()
            }
        }
    }

    private fun Application.toDto() = ApplicationDto(
        id = id.toString(),
        name = name,
        description = description,
        applicationType = applicationType.name,
        ownerOrganizationId = ownerOrganizationId?.toString(),
        isActive = isActive,
        grantedCapabilities = grantedCapabilitiesJson,
        createdDate = createdDate.toInstant().toString(),
        lastAccessDate = lastAccessDate?.toInstant()?.toString(),
    )

    private fun Application.toCreatedDto(rawSecret: String) = ApplicationCreatedDto(
        id = id.toString(),
        name = name,
        description = description,
        applicationType = applicationType.name,
        ownerOrganizationId = ownerOrganizationId?.toString(),
        apiKey = apiKey,
        rawSecret = rawSecret,
        grantedCapabilities = grantedCapabilitiesJson,
        createdDate = createdDate.toInstant().toString(),
    )
}

@Serializable
data class ApplicationDto(
    val id: String,
    val name: String,
    val description: String?,
    val applicationType: String,
    val ownerOrganizationId: String?,
    val isActive: Boolean,
    val grantedCapabilities: String,
    val createdDate: String,
    val lastAccessDate: String?,
)

@Serializable
data class ApplicationCreatedDto(
    val id: String,
    val name: String,
    val description: String?,
    val applicationType: String,
    val ownerOrganizationId: String?,
    val apiKey: String,
    val rawSecret: String,
    val grantedCapabilities: String,
    val createdDate: String,
)

@Serializable
data class RotateCredentialsResponse(val apiKey: String, val rawSecret: String)

@Serializable
data class CreateApplicationRequestBody(
    val name: String?,
    val description: String?,
    val applicationType: String?,
    val ownerOrganizationId: String?,
    val grantedCapabilities: List<String>?,
)

@Serializable
data class UpdateCapabilitiesRequestBody(val capabilities: List<String>?)
