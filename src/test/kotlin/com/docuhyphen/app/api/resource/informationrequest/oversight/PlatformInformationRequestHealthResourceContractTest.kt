package com.docuhyphen.app.api.resource.informationrequest.oversight

import com.docuhyphen.app.api.model.dto.InformationRequestHealthReportDto
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicator
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicatorKey
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthReport
import com.docuhyphen.app.api.resource.informationrequest.oversight.operations.PlatformInformationRequestHealthResourceOperations
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestHealthService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class PlatformInformationRequestHealthResourceContractTest
{
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val healthService = mock<InformationRequestHealthService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = PlatformInformationRequestHealthResource(healthService, accessContextFactory)

    @Test
    fun `a platform administrator reads the health report as one platform resource`()
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(healthService.reportFor(access.principal, "request-1")).thenReturn(
            InformationRequestHealthReport(
                Instant.parse("2026-09-30T10:00:00Z"),
                listOf(InformationRequestHealthIndicator(InformationRequestHealthIndicatorKey.REQUESTS_WITHOUT_EXECUTION_GRANT, 1, 0)),
            ),
        )

        val response = resource.get("request-1")

        assertEquals("/platform/information-request-health", PlatformInformationRequestHealthResourceOperations::class.java.getAnnotation(Path::class.java).value)
        assertTrue(PlatformInformationRequestHealthResourceOperations::class.java.declaredMethods.single { it.name == "get" }.isAnnotationPresent(GET::class.java))
        val body = response.entity as InformationRequestHealthReportDto
        assertEquals(false, body.healthy)
        assertEquals(true, body.indicators.single().breached)
    }

    @Test
    fun `anyone else is refused`()
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(healthService.reportFor(access.principal, null)).thenThrow(ForbiddenException("Platform administrators only"))

        assertEquals(Response.Status.FORBIDDEN.statusCode, resource.get(null).status)
    }
}
