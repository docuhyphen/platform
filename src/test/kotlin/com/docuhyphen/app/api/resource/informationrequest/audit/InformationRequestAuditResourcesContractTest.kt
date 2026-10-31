package com.docuhyphen.app.api.resource.informationrequest.audit

import com.docuhyphen.app.api.model.audit.AuditTargetRecord
import com.docuhyphen.app.api.model.dto.InformationRequestAuditPageDto
import com.docuhyphen.app.api.model.dto.InformationRequestAuditReconciliationDto
import com.docuhyphen.app.api.model.dto.InformationRequestReconciliationGapDto
import com.docuhyphen.app.api.model.dto.InformationRequestReconciliationStrayDto
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.*
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditEventResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditReconciliationResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditSearchResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditTargets
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

class InformationRequestAuditResourcesContractTest {
    private val requestId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "operator"))
    private val audit: InformationRequestAuditService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock {
        on { currentAuthenticated() } doReturn access
    }
    private val searchResource = InformationRequestAuditSearchResource(audit)
    private val eventResource = InformationRequestAuditEventResource(audit, accessContextFactory)
    private val reconciliationResource = InformationRequestAuditReconciliationResource(audit, accessContextFactory)

    @Test
    fun `audit routes are read-only collections declared on their operations interfaces`() {
        val search = InformationRequestAuditSearchResourceOperations::class.java
        val events = InformationRequestAuditEventResourceOperations::class.java
        val reconciliation = InformationRequestAuditReconciliationResourceOperations::class.java
        val filters = listOf("eventClass", "eventType", "actorId", "occurredAfter", "occurredBefore", "limit", "offset")

        assertEquals("/information-request-audit-events", search.getAnnotation(Path::class.java).value)
        assertEquals("/information-requests/{id}/audit-events", events.getAnnotation(Path::class.java).value)
        assertEquals(
            "/information-requests/{id}/audit-reconciliation",
            reconciliation.getAnnotation(Path::class.java).value
        )
        listOf(search, events, reconciliation).forEach { operations ->
            assertTrue(operations.declaredMethods.single().isAnnotationPresent(GET::class.java), operations.simpleName)
        }
        assertEquals(listOf("requestId") + filters, queryParamsOf(search.declaredMethods.single()))
        assertEquals(filters, queryParamsOf(events.declaredMethods.single()))
        assertEquals(
            "id",
            events.declaredMethods.single().parameters.first().getAnnotation(PathParam::class.java).value
        )
        assertEquals(
            "id",
            reconciliation.declaredMethods.single().parameters.single().getAnnotation(PathParam::class.java).value
        )
        listOf(
            InformationRequestAuditSearchResource::class.java,
            InformationRequestAuditEventResource::class.java,
            InformationRequestAuditReconciliationResource::class.java,
        ).forEach { resource ->
            assertTrue(resource.declaredMethods.none {
                it.isAnnotationPresent(GET::class.java) || it.isAnnotationPresent(
                    Path::class.java
                )
            })
        }
    }

    @Test
    fun `owner search parses every filter, defaults paging, and names the request only for request targets`() {
        val requestEvent = event(InformationRequestAuditTargets.REQUEST, requestId.toString())
        val templateEvent = event(InformationRequestAuditTargets.TEMPLATE_DEFINITION, UUID.randomUUID().toString())
        whenever(audit.search(any())).thenReturn(
            InformationRequestAuditPage(
                listOf(requestEvent, templateEvent),
                2,
                100,
                0
            )
        )

        val response = searchResource.search(
            requestId.toString(),
            " Lifecycle_Transition ",
            " information_request.issued ",
            actorId.toString(),
            "2026-10-01T00:00:00Z",
            "2026-10-02T00:00:00Z",
            null,
            null,
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(audit).search(
            InformationRequestAuditSearch(
                requestId = requestId,
                eventClass = "lifecycle_transition",
                eventTypeKey = "information_request.issued",
                actorId = actorId,
                occurredAfter = Instant.parse("2026-10-01T00:00:00Z"),
                occurredBefore = Instant.parse("2026-10-02T00:00:00Z"),
                limit = 100,
                offset = 0,
            ),
        )
        val page = response.entity as InformationRequestAuditPageDto
        assertEquals(2, page.total)
        assertEquals(requestId.toString(), page.items[0].requestId)
        assertEquals(mapOf("from" to "DRAFT"), page.items[0].payload)
        assertEquals(1, page.items[0].withheldKeyCount)
        assertNull(page.items[1].requestId)
        assertEquals(InformationRequestAuditTargets.TEMPLATE_DEFINITION, page.items[1].targetType)
    }

    @Test
    fun `request history searches inside the path request with the caller's access`() {
        whenever(audit.events(any(), any(), any())).thenReturn(InformationRequestAuditPage(emptyList(), 0, 25, 50))

        val response = eventResource.list(requestId.toString(), null, null, null, null, null, 25, 50)

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(audit).events(requestId, access, InformationRequestAuditSearch(limit = 25, offset = 50))
        assertEquals(InformationRequestAuditPageDto(emptyList(), 0, 25, 50), response.entity)
    }

    @Test
    fun `malformed filters are refused before any audit read`() {
        val refusals = listOf(
            searchResource.search("not-a-uuid", null, null, null, null, null, null, null),
            searchResource.search(null, "Bad-Class", null, null, null, null, null, null),
            searchResource.search(null, null, null, "not-a-uuid", null, null, null, null),
            searchResource.search(null, null, null, null, "yesterday", null, null, null),
            searchResource.search(null, null, null, null, "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z", null, null),
            searchResource.search(null, null, null, null, null, null, 0, null),
            searchResource.search(null, null, null, null, null, null, 501, null),
            searchResource.search(null, null, null, null, null, null, null, -1),
            eventResource.list("not-a-uuid", null, null, null, null, null, null, null),
            eventResource.list(requestId.toString(), null, null, null, null, null, 501, null),
            reconciliationResource.get("not-a-uuid"),
        )

        assertTrue(
            refusals.all { it.status == Response.Status.BAD_REQUEST.statusCode },
            refusals.map { it.status }.toString()
        )
        verify(audit, never()).search(any())
        verify(audit, never()).events(any(), any(), any())
        verify(audit, never()).reconciliation(any(), any())
    }

    @Test
    fun `reconciliation reports matched counts, missing transitions, and stray events`() {
        val transitionId = UUID.randomUUID()
        val strayId = UUID.randomUUID()
        whenever(audit.reconciliation(requestId, access)).thenReturn(
            InformationRequestAuditReconciliation(
                requestId = requestId,
                auditedTransitionCount = 3,
                matchedTransitionCount = 2,
                missing = listOf(
                    InformationRequestReconciliationGap(
                        transitionId,
                        3,
                        "ISSUE",
                        "information_request.issued"
                    )
                ),
                unmatched = listOf(
                    InformationRequestReconciliationStray(
                        strayId,
                        "information_request.closed",
                        "transaction-9"
                    )
                ),
                unsealedEventCount = 1,
            ),
        )

        val response = reconciliationResource.get(requestId.toString())

        assertEquals(
            InformationRequestAuditReconciliationDto(
                requestId = requestId,
                reconciled = false,
                auditedTransitionCount = 3,
                matchedTransitionCount = 2,
                unsealedEventCount = 1,
                missing = listOf(
                    InformationRequestReconciliationGapDto(
                        transitionId,
                        3,
                        "ISSUE",
                        "information_request.issued"
                    )
                ),
                unmatched = listOf(
                    InformationRequestReconciliationStrayDto(
                        strayId,
                        "information_request.closed",
                        "transaction-9"
                    )
                ),
            ),
            response.entity,
        )
    }

    @Test
    fun `refusals keep their status and reason on every audit route`() {
        whenever(audit.search(any())).thenThrow(ForbiddenException("denied"))
        whenever(audit.events(any(), any(), any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )
        whenever(audit.reconciliation(any(), any())).thenThrow(ForbiddenException("denied"))

        val search = searchResource.search(null, null, null, null, null, null, null, null)
        val events = eventResource.list(requestId.toString(), null, null, null, null, null, null, null)
        val reconciliation = reconciliationResource.get(requestId.toString())

        assertEquals(Response.Status.FORBIDDEN.statusCode, search.status)
        assertEquals(InformationRequestErrorCatalog.FORBIDDEN, (search.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.NOT_FOUND.statusCode, events.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (events.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.FORBIDDEN.statusCode, reconciliation.status)
    }

    private fun queryParamsOf(method: java.lang.reflect.Method): List<String> =
        method.parameters.mapNotNull { it.getAnnotation(QueryParam::class.java)?.value }

    private fun event(targetType: String, targetId: String) = InformationRequestAuditEvent(
        record = AuditTargetRecord(
            eventId = UUID.randomUUID(),
            eventTypeKey = "information_request.issued",
            category = "LIFECYCLE",
            outcome = "SUCCESS",
            occurredAt = Instant.parse("2026-10-01T10:00:00Z"),
            recordedAt = Instant.parse("2026-10-01T10:00:01Z"),
            actorKind = "USER",
            actorId = actorId,
            targetType = targetType,
            targetId = targetId,
            businessTransactionId = "transaction-1",
            payload = mapOf("from" to "DRAFT", "secret" to "withheld"),
            sealed = true,
            eventHash = "hash",
            streamSequence = 4,
        ),
        eventClass = "lifecycle_transition",
        payload = mapOf("from" to "DRAFT"),
        withheldKeyCount = 1,
    )
}
