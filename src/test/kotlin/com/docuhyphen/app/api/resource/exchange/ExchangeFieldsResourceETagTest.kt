package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.resource.model.AssignSchemaRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SetFieldValuesRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsAccessContextFactory
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * A client cannot condition its next write on the state it read unless the validator reaches it, so
 * the Fields read and every successful Fields mutation carry it as the response entity tag. The
 * resource only copies what the service resolved; it never derives a validator of its own.
 */
class ExchangeFieldsResourceETagTest
{
    private val schemaAssignmentService = mock<SchemaAssignmentService>()
    private val exchangeId: UUID = UUID.randomUUID()
    private val etag = "\"${UUID.randomUUID()}:7\""

    private val access = FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    private val resource = ExchangeFieldsResource(
        authTokenContext = authenticatedContext(),
        schemaAssignmentService = schemaAssignmentService,
        fieldsAccessContextFactory = mock<FieldsAccessContextFactory>().also {
            whenever(it.current()).thenReturn(access)
        },
    )

    @Test
    fun `reading the fields of an exchange carries the current validator`()
    {
        whenever(schemaAssignmentService.getAssignment(any())).thenReturn(assignment(etag))

        val response = resource.getSchema(exchangeId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(etag, response.getHeaderString("ETag"))
    }

    @Test
    fun `a successful save carries the validator of the state it produced`()
    {
        whenever(schemaAssignmentService.setValues(any())).thenReturn(assignment(etag))

        val response = resource.patchValues(exchangeId.toString(), SetFieldValuesRequest(), etag)

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(etag, response.getHeaderString("ETag"))
    }

    @Test
    fun `assigning a schema carries the validator of the set it created`()
    {
        whenever(schemaAssignmentService.applySchemaAssignment(any())).thenReturn(assignment(etag))

        val response = resource.assignSchema(
            exchangeId.toString(), AssignSchemaRequest(UUID.randomUUID()),
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(etag, response.getHeaderString("ETag"))
    }

    @Test
    fun `a projection with no set of answers to validate carries no validator`()
    {
        whenever(schemaAssignmentService.getAssignment(any())).thenReturn(assignment(null))

        val response = resource.getSchema(exchangeId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertNull(response.getHeaderString("ETag"))
    }

    @Test
    fun `a save whose state has moved on is refused as a failed precondition`()
    {
        whenever(schemaAssignmentService.setValues(any()))
            .thenThrow(FieldsPreconditionException.stale(etag))

        val response = resource.patchValues(exchangeId.toString(), SetFieldValuesRequest(), etag)

        assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, response.status)
        assertEquals("FIELDS_PRECONDITION_STALE", (response.entity as ResponseError).reasonCode)
        assertEquals(
            etag, response.getHeaderString("ETag"),
            "The refusal carries the state that is current, so the client can reload and retry",
        )
    }

    @Test
    fun `a save that named no state where one is required is refused as a missing precondition`()
    {
        whenever(schemaAssignmentService.setValues(any()))
            .thenThrow(FieldsPreconditionException.required(etag))

        val response = resource.patchValues(exchangeId.toString(), SetFieldValuesRequest(), etag)

        assertEquals(428, response.status, "A missing precondition is not the same answer as a stale one")
        assertEquals("FIELDS_PRECONDITION_REQUIRED", (response.entity as ResponseError).reasonCode)
    }

    private fun assignment(etag: String?) = SchemaAssignmentDto(
        id = UUID.randomUUID(),
        resourceType = "EXCHANGE",
        resourceId = exchangeId,
        schemaVersionId = UUID.randomUUID(),
        schemaDefinitionId = UUID.randomUUID(),
        schemaKey = "process-data",
        displayName = "Process data",
        versionNumber = 1,
        assignmentSource = SchemaAssignmentSource.MANUAL,
        assignedAt = Timestamp.from(Instant.parse("2026-01-05T10:15:30Z")),
        etag = etag,
    )

    private fun authenticatedContext(): AuthTokenContext = AuthTokenContext().apply {
        authToken = AuthToken().apply { appUser = AppUser() }
    }
}
