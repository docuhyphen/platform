package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.CancelInformationRequestRequest
import com.docuhyphen.app.api.resource.model.CreateInformationRequestDraftRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SupersedeInformationRequestRequest
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.CancelInformationRequestCommand
import com.docuhyphen.app.api.service.informationrequest.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAdHocCreationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCreationResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.SupersedeInformationRequestCommand
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
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
 * REST adapter for the owner-facing slice of the runtime Information Request lifecycle: listing an
 * Exchange's requests, creating an ad hoc draft, cancelling one, and superseding one with a
 * replacement. Issuance and every respondent-facing action are deliberately not exposed here; those
 * need the dual-access authorization surface and runtime executors this resource does not depend on.
 */
@Path("/information-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestResource @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val creationService: InformationRequestAdHocCreationService,
    private val lifecycleService: InformationRequestLifecycleService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
    private val responseWorkspaceService: InformationRequestResponseWorkspaceService,
)
{
    @GET
    fun list(@QueryParam("exchangeId") exchangeIdParam: String?): Response
    {
        return try
        {
            val exchangeId = exchangeIdParam?.let(::parseUuid)
                ?: return badRequest("exchangeId is required")
            val requests = queryService.listForExchange(exchangeId, accessContextFactory.currentAuthenticated())
            Response.ok(requests.map(InformationRequestDtoMapper::toDto)).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request list failed", exception)
        }
    }

    @GET
    @Path("/{id}")
    fun get(@PathParam("id") id: String): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            Response.ok(responseWorkspaceService.loadRequest(requestId, accessContextFactory.currentAuthenticated())).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request lookup failed", exception)
        }
    }

    @GET
    @Path("/{id}/response-workspace")
    fun responseWorkspace(@PathParam("id") id: String): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            Response.ok(
                responseWorkspaceService.load(requestId, accessContextFactory.currentAuthenticated()),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request response workspace lookup failed", exception)
        }
    }

    @POST
    fun create(
        request: CreateInformationRequestDraftRequest,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            created(
                creationService.createAdHoc(
                    CreateAdHocInformationRequestCommand(
                        exchangeId = request.exchangeId,
                        displayName = request.displayName,
                        description = request.description,
                        configuration = request.configuration,
                        gatesExchangeClosure = request.gatesExchangeClosure,
                        access = accessContextFactory.currentAuthenticated(),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request draft creation failed", exception)
        }
    }

    @POST
    @Path("/{id}/cancellation")
    fun cancel(
        @PathParam("id") id: String,
        request: CancelInformationRequestRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            ok(
                lifecycleService.cancel(
                    CancelInformationRequestCommand(
                        requestId = requestId,
                        reasonCode = request?.reasonCode,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request cancellation failed", exception)
        }
    }

    @POST
    @Path("/{id}/supersession")
    fun supersede(
        @PathParam("id") id: String,
        request: SupersedeInformationRequestRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            ok(
                lifecycleService.supersede(
                    SupersedeInformationRequestCommand(
                        requestId = requestId,
                        supersededByRequestId = request.supersededByRequestId,
                        reasonCode = request.reasonCode,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request supersession failed", exception)
        }
    }

    private fun created(result: InformationRequestCreationResult): Response =
        Response.status(CREATED)
            .entity(InformationRequestDtoMapper.toDto(result.request))
            .header("ETag", result.requestETag)
            .build()

    private fun ok(result: InformationRequestLifecycleResult): Response =
        Response.ok(InformationRequestDtoMapper.toDto(result.request))
            .header("ETag", result.requestETag)
            .build()

    private fun requiredIdempotencyKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()
            is InformationRequestLifecycleException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()
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
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        val logger = LoggerFactory.getLogger(InformationRequestResource::class.java)
    }
}
