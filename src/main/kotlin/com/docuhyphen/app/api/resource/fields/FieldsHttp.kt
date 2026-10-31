package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.fields.SchemaAssignmentConflictException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import java.util.*

object FieldsHttp {
    fun requireUser(authTokenContext: AuthTokenContext) {
        authTokenContext.authToken.appUser ?: throw UnauthorizedException("Unauthorized")
    }

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw FieldValidationException("Invalid $name")

    fun scopeKind(raw: String?): FieldScopeKind? {
        val stated = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return FieldScopeKind.entries.firstOrNull { it.name == stated.uppercase() }
            ?: throw FieldValidationException(
                "Unknown scope kind; use one of ${FieldScopeKind.entries.joinToString(", ") { it.name }}",
            )
    }

    fun withETag(builder: Response.ResponseBuilder, etag: String?): Response {
        etag?.let { builder.header(HttpHeaders.ETAG, it) }
        return builder.build()
    }

    fun refusal(exception: Exception): Response? = when (exception) {
        is UnauthorizedException -> error(UNAUTHORIZED, "Unauthorized")
        is FieldsPreconditionException -> preconditionRefused(exception)
        is FieldValidationException -> error(BAD_REQUEST, exception.message)
        is SchemaAssignmentConflictException -> error(CONFLICT, exception.message, exception.reasonCode)
        is IllegalStateException -> error(CONFLICT, exception.message)
        is IllegalArgumentException -> error(NOT_FOUND, exception.message)
        is ForbiddenException -> error(FORBIDDEN, exception.message)
        else -> null
    }

    fun failed(): Response = error(INTERNAL_SERVER_ERROR, "Request failed")

    private fun preconditionRefused(exception: FieldsPreconditionException): Response {
        val status = when (exception.kind) {
            FieldsPreconditionException.Kind.REQUIRED -> PRECONDITION_REQUIRED
            FieldsPreconditionException.Kind.STALE -> PRECONDITION_FAILED
        }
        return withETag(
            Response.status(status).entity(ResponseError(exception.message, exception.reasonCode)),
            exception.currentETag,
        )
    }

    private fun error(status: Response.Status, message: String?, reasonCode: String? = null): Response =
        Response.status(status).entity(ResponseError(message, reasonCode)).build()
}
