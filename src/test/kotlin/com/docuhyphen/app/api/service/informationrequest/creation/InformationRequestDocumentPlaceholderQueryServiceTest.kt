package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.creation.InformationRequestDocumentPlaceholderRepository
import com.docuhyphen.app.api.service.auth.authz.*
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestDocumentPlaceholderQueryServiceTest {
    private val requests = mock<InformationRequestRepository>()
    private val placeholders = mock<InformationRequestDocumentPlaceholderRepository>()
    private val authorization = mock<AuthorizationService>()
    private val service = InformationRequestDocumentPlaceholderQueryService(requests, placeholders, authorization)
    private val request = InformationRequest().apply { id = UUID.randomUUID() }
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)

    @Test
    fun `a manager reads the request's frozen document placeholders in their stored order`() {
        val stored = listOf(placeholder("Signed record", 0), placeholder("Supporting statement", 1))
        whenever(requests.findById(request.id)).thenReturn(request)
        whenever(placeholders.findForRequest(request.id)).thenReturn(stored)
        whenever(
            authorization.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_VIEW_OPERATIONS),
                eq(ResourceRef.informationRequest(request.id)),
                any()
            )
        )
            .thenReturn(Decision.Allow())

        val read = service.listForRequest(request.id, access)

        assertEquals(stored.map { it.id }, read.map { it.id })
    }

    @Test
    fun `a caller who may not manage the request is refused before any placeholder is read`() {
        whenever(requests.findById(request.id)).thenReturn(request)
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(
            Decision.Deny(
                Decision.REASON_NO_GRANT,
                "denied"
            )
        )

        assertThrows(ForbiddenException::class.java) { service.listForRequest(request.id, access) }

        verify(placeholders, never()).findForRequest(any())
    }

    @Test
    fun `an unknown request is refused like a request hidden from the caller, before any request is read`() {
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(
            Decision.Deny(
                Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED,
                "unresolved"
            )
        )

        assertThrows(ForbiddenException::class.java) { service.listForRequest(UUID.randomUUID(), access) }

        verify(requests, never()).findById(any())
    }

    private fun placeholder(title: String, order: Int) = InformationRequestDocumentPlaceholder().apply {
        informationRequestId = request.id
        this.title = title
        displayOrder = order
    }
}
