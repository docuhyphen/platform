package com.docuhyphen.app.api.resource.informationrequest.creation

import com.docuhyphen.app.api.model.dto.InformationRequestDocumentPlaceholderDto
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestDocumentPlaceholderQueryService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestDocumentPlaceholderResourceContractTest {
    private val queryService = mock<InformationRequestDocumentPlaceholderQueryService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestDocumentPlaceholderResource(queryService, accessContextFactory)
    private val requestId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    @Test
    fun `placeholders are a read-only sub-resource of the request`() {
        val operations = Class.forName(
            "com.docuhyphen.app.api.resource.informationrequest.creation.operations.InformationRequestDocumentPlaceholderResourceOperations",
        )

        assertEquals(
            "/information-requests/{id}/document-placeholders",
            operations.getAnnotation(Path::class.java).value
        )
        assertEquals(listOf("list"), operations.declaredMethods.map { it.name })
        assertTrue(operations.declaredMethods.single().isAnnotationPresent(GET::class.java))
    }

    @Test
    fun `an authorized read returns each frozen placeholder with its library snapshot`() {
        val placeholder = InformationRequestDocumentPlaceholder().apply {
            informationRequestId = requestId
            title = "Signed record"
            required = true
            restrictType = true
            restrictedType = "PDF"
            libraryDocumentId = UUID.randomUUID()
            libraryTitle = "Record template"
            libraryFileName = "record-template.pdf"
            libraryFileSizeBytes = 2048
            libraryContentHash = "a".repeat(64)
            displayOrder = 3
        }
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(queryService.listForRequest(requestId, access)).thenReturn(listOf(placeholder))

        val response = resource.list(requestId.toString())

        assertEquals(200, response.status)
        val dto = (response.entity as Array<*>).single() as InformationRequestDocumentPlaceholderDto
        assertEquals(placeholder.id, dto.id)
        assertEquals("Signed record", dto.title)
        assertTrue(dto.required)
        assertEquals("PDF", dto.restrictedType)
        assertEquals(placeholder.libraryDocumentId, dto.libraryDocumentId)
        assertEquals("record-template.pdf", dto.libraryFileName)
        assertEquals(2048L, dto.libraryFileSizeBytes)
        assertEquals(3, dto.displayOrder)
    }

    @Test
    fun `refusals map to their HTTP statuses without reaching the service for an invalid id`() {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(queryService.listForRequest(requestId, access)).thenThrow(ForbiddenException("denied"))
        val missing = UUID.randomUUID()
        whenever(
            queryService.listForRequest(
                missing,
                access
            )
        ).thenThrow(IllegalArgumentException("Information Request not found"))

        assertEquals(403, resource.list(requestId.toString()).status)
        assertEquals(404, resource.list(missing.toString()).status)
        val invalid = resource.list("not-a-request")
        assertEquals(400, invalid.status)
        assertEquals("Invalid information request id", (invalid.entity as ResponseError).errorMessage)
        verify(queryService, times(2)).listForRequest(any(), any())
    }
}
