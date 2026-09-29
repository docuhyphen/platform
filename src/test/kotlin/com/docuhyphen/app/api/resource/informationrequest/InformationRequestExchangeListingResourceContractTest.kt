package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestExchangeListingDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.InformationRequestCallerStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestExchangeListing
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNextAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSummary
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSummaryPermissions
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestExchangeSummaryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestExchangeListingResourceContractTest
{
    private val summaryService = mock<InformationRequestExchangeSummaryService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestExchangeListingResource(summaryService, accessContextFactory)
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val exchangeId = UUID.randomUUID()

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `an Exchange's requests are listed under the Exchange with the creation offer`()
    {
        val request = InformationRequest().apply {
            exchangeId = this@InformationRequestExchangeListingResourceContractTest.exchangeId
            templateVersionId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
            createdAt = Timestamp.from(Instant.now())
        }
        whenever(summaryService.listForExchange(exchangeId, access)).thenReturn(
            InformationRequestExchangeListing(
                requests = listOf(
                    InformationRequestSummary(
                        request = request,
                        title = "Periodic records request",
                        nextDueAt = Instant.parse("2026-10-01T08:00:00Z"),
                        completedCount = 1,
                        requiredCount = 4,
                        standing = InformationRequestCallerStanding(
                            roles = listOf(InformationRequestShareRoleKey.CONTRIBUTOR),
                            permissions = InformationRequestSummaryPermissions(canManage = false, canRespond = true, canReview = false),
                            nextAction = InformationRequestNextAction.RESPOND,
                        ),
                    ),
                ),
                canCreate = true,
            ),
        )

        val listed = resource.list(exchangeId.toString())
        val invalid = resource.list("not-an-exchange")

        assertEquals("/exchanges/{exchangeId}/information-requests", InformationRequestExchangeListingResource::class.java.getAnnotation(Path::class.java).value)
        assertTrue(InformationRequestExchangeListingResource::class.java.declaredMethods.single { it.name == "list" }.isAnnotationPresent(GET::class.java))
        assertEquals(Response.Status.OK.statusCode, listed.status)
        val body = listed.entity as InformationRequestExchangeListingDto
        assertEquals(true, body.canCreate)
        val summary = body.requests.single()
        assertEquals(request.id, summary.id)
        assertEquals("Periodic records request", summary.title)
        assertEquals(Timestamp.from(Instant.parse("2026-10-01T08:00:00Z")), summary.nextDueAt)
        assertEquals(1, summary.completedCount)
        assertEquals(4, summary.requiredCount)
        assertEquals(listOf(InformationRequestShareRoleKey.CONTRIBUTOR), summary.callerRoles)
        assertEquals(true, summary.permissions.canRespond)
        assertEquals(InformationRequestNextAction.RESPOND, summary.nextAction)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalid.status)
    }

    @Test
    fun `an unknown Exchange and a caller without access keep their statuses`()
    {
        whenever(summaryService.listForExchange(eq(exchangeId), any()))
            .thenThrow(IllegalArgumentException("Exchange not found"))
            .thenThrow(ForbiddenException("Access denied to list Information Requests"))

        val missing = resource.list(exchangeId.toString())
        val denied = resource.list(exchangeId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, missing.status)
        assertEquals("Exchange not found", (missing.entity as ResponseError).errorMessage)
        assertEquals(Response.Status.FORBIDDEN.statusCode, denied.status)
    }
}
