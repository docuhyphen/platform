package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExternalIdentityLookupGuardServiceTest
{
    private val rateLimitService = mock<AuthRateLimitService>()
    private val incidentService = mock<SecurityIncidentService>()
    private val configurationService = mock<ConfigurationService>()
    private val service = ExternalIdentityLookupGuardService(
        rateLimitService,
        incidentService,
        configurationService,
    )

    @Test
    fun `rate limits actor caller target relationship and keyed email without raw email keys`()
    {
        whenever(configurationService.getJwtSecret()).thenReturn("test-secret-that-is-at-least-32-characters")
        whenever(configurationService.getAuthRateLimitDirectoryPerMinute()).thenReturn(20)
        whenever(rateLimitService.isLimited(any(), eq(20))).thenReturn(false)
        val email = "member@example.test"

        service.enforce(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            email,
            "trace-id",
        )

        val keys = argumentCaptor<String>()
        verify(rateLimitService, org.mockito.kotlin.times(5)).isLimited(keys.capture(), eq(20))
        assertTrue(keys.allValues.any { it.contains(":actor:") })
        assertTrue(keys.allValues.any { it.contains(":caller:") })
        assertTrue(keys.allValues.any { it.contains(":target:") })
        assertTrue(keys.allValues.any { it.contains(":relationship:") })
        assertTrue(keys.allValues.any { it.contains(":email:") })
        assertFalse(keys.allValues.any { it.contains(email) })
    }

    @Test
    fun `limited lookup records a redacted security incident`()
    {
        whenever(configurationService.getJwtSecret()).thenReturn("test-secret-that-is-at-least-32-characters")
        whenever(configurationService.getAuthRateLimitDirectoryPerMinute()).thenReturn(20)
        whenever(rateLimitService.isLimited(any(), eq(20))).thenReturn(true)
        whenever(incidentService.formatDetails(any(), any(), any(), any(), any())).thenReturn("safe-details")
        val actorId = UUID.randomUUID()

        assertThrows<com.docuhyphen.app.api.exception.OrganizationTrustRateLimitException> {
            service.enforce(
                actorId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "member@example.test",
                "trace-id",
            )
        }

        verify(incidentService).record(
            incidentType = eq(SecurityIncidentType.AUTH_RATE_LIMIT_EXTERNAL_IDENTITY_RESOLUTION),
            severity = any(),
            actorId = eq(actorId),
            requestId = eq("trace-id"),
            details = eq("safe-details"),
            reasonCode = any(),
        )
    }
}
