package com.docuhyphen.app.api.resource.informationrequest.creation

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestDocumentPlaceholderDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.creation.operations.InformationRequestDocumentPlaceholderResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestDocumentPlaceholderQueryService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestDocumentPlaceholderResource @Inject constructor(
    private val placeholderQueryService: InformationRequestDocumentPlaceholderQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestDocumentPlaceholderResourceOperations {
    override fun list(id: String): Response {
        return try {
            val requestId = runCatching { UUID.fromString(id) }.getOrNull()
                ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid information request id")).build()
            val placeholders =
                placeholderQueryService.listForRequest(requestId, accessContextFactory.currentAuthenticated())
            Response.ok(placeholders.map(InformationRequestDocumentPlaceholderDtoMapper::toDto).toTypedArray()).build()
        } catch (exception: Exception) {
            when (exception) {
                is InformationRequestCommandRequestException -> Response.status(BAD_REQUEST)
                    .entity(ResponseError(exception.message, exception.reasonCode)).build()

                is IllegalArgumentException -> Response.status(NOT_FOUND).entity(ResponseError(exception.message))
                    .build()

                is ForbiddenException -> Response.status(FORBIDDEN)
                    .entity(ResponseError(exception.message, InformationRequestErrorCatalog.FORBIDDEN)).build()

                is UnauthorizedException -> Response.status(UNAUTHORIZED).entity(ResponseError(exception.message))
                    .build()

                else -> {
                    logger.error("Information Request document placeholder list failed", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
                }
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestDocumentPlaceholderResource::class.java)
    }
}
