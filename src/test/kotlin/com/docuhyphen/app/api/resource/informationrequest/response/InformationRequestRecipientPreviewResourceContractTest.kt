package com.docuhyphen.app.api.resource.informationrequest.response

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.dto.InformationRequestRecipientPreviewDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestRecipientPreview
import com.docuhyphen.app.api.resource.informationrequest.response.operations.InformationRequestRecipientPreviewResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestRecipientPreviewService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.util.*

class InformationRequestRecipientPreviewResourceContractTest {
    private val previews = mock<InformationRequestRecipientPreviewService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestRecipientPreviewResource(previews, accessContextFactory)
    private val requestId = UUID.randomUUID()
    private val partyId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    @Test
    fun `the recipient preview is a read-only sub-resource of a request party`() {
        val operations = InformationRequestRecipientPreviewResourceOperations::class.java

        assertEquals(
            "/information-requests/{id}/parties/{partyId}/recipient-preview",
            operations.getAnnotation(Path::class.java).value,
        )
        assertEquals(listOf("preview"), operations.declaredMethods.map { it.name })
        assertTrue(operations.declaredMethods.single().isAnnotationPresent(GET::class.java))
        assertTrue(resource.javaClass.declaredMethods.none { method -> method.annotations.any { it is GET || it is Path } })
    }

    @Test
    fun `an authorized read returns the nominated party's projection`() {
        val templateVersion = InformationRequestTemplateVersionDto(
            id = UUID.randomUUID(),
            templateDefinitionId = UUID.randomUUID(),
            versionNumber = 2,
            status = InformationRequestTemplateStatus.PUBLISHED,
            createdAt = Timestamp(0),
        )
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(previews.preview(requestId, partyId, access)).thenReturn(
            InformationRequestRecipientPreview(partyId, InformationRequestShareRoleKey.PREPARER, templateVersion),
        )

        val response = resource.preview(requestId.toString(), partyId.toString())

        assertEquals(200, response.status)
        assertEquals(
            InformationRequestRecipientPreviewDto(partyId, InformationRequestShareRoleKey.PREPARER, templateVersion),
            response.entity,
        )
    }

    @Test
    fun `refusals map to their HTTP statuses and invalid ids never reach the service`() {
        val forbidden = UUID.randomUUID()
        val missing = UUID.randomUUID()
        val silent = UUID.randomUUID()
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(previews.preview(requestId, forbidden, access)).thenThrow(ForbiddenException("denied"))
        whenever(previews.preview(requestId, missing, access)).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request party not found"
            ),
        )
        whenever(previews.preview(requestId, silent, access)).thenThrow(
            InformationRequestCommandRequestException("Preview a party who answers this request"),
        )

        assertEquals(403, resource.preview(requestId.toString(), forbidden.toString()).status)
        assertEquals(404, resource.preview(requestId.toString(), missing.toString()).status)
        assertEquals(400, resource.preview(requestId.toString(), silent.toString()).status)
        val invalidRequest = resource.preview("not-a-request", partyId.toString())
        val invalidParty = resource.preview(requestId.toString(), "not-a-party")
        assertEquals(400, invalidRequest.status)
        assertEquals("Invalid information request id", (invalidRequest.entity as ResponseError).errorMessage)
        assertEquals(400, invalidParty.status)
        assertEquals("Invalid party id", (invalidParty.entity as ResponseError).errorMessage)
        verify(previews, times(3)).preview(any(), any(), any())
    }
}
