package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformUserFeatureEntitlementDtoMapper
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementDto
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsResponse
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.service.subscription.PlatformUserFeatureEntitlementService
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.UserFeatureEntitlementResult
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The personal feature entitlement endpoint is a thin adapter: it names the account in the path, it
 * uses the verb that matches whole-collection replacement, and every failure the service raises
 * reaches the caller as the status that describes it.
 */
class PlatformUserFeatureEntitlementResourceContractTest
{
    private val service = mock<PlatformUserFeatureEntitlementService>()
    private val resource = PlatformUserFeatureEntitlementResource(
        service,
        PlatformUserFeatureEntitlementDtoMapper(),
    )
    private val appUserId: UUID = UUID.randomUUID()

    @Test
    fun `personal feature entitlement administration uses resource based paths and verbs`()
    {
        val resourceClass = PlatformUserFeatureEntitlementResource::class.java
        assertEquals(
            "/platform/users/{appUserId}/feature-entitlements",
            resourceClass.getAnnotation(Path::class.java).value,
        )
        assertTrue(resourceClass.declaredMethods.single { it.name == "get" }.isAnnotationPresent(GET::class.java))
        assertTrue(resourceClass.declaredMethods.single { it.name == "replace" }.isAnnotationPresent(PUT::class.java))
    }

    @Test
    fun `stored decisions are returned for the account named in the path`()
    {
        whenever(service.get(eq(appUserId.toString()), anyOrNull())).thenReturn(result())

        val response = resource.get(appUserId.toString(), "request-id")

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as PlatformUserFeatureEntitlementsResponse
        assertEquals(appUserId.toString(), body.appUserId)
        assertEquals(listOf("PROCESS_CAPABILITY"), body.entitlements.map { it.featureCode })
    }

    @Test
    fun `a replacement returns the decisions the account now holds`()
    {
        whenever(service.replace(eq(appUserId.toString()), any(), any())).thenReturn(result())

        val response = resource.replace(
            appUserId.toString(),
            "request-id",
            PlatformUserFeatureEntitlementsUpdateRequest(
                entitlements = listOf(PlatformUserFeatureEntitlementDto("PROCESS_CAPABILITY", true)),
            ),
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as PlatformUserFeatureEntitlementsResponse
        assertTrue(body.entitlements.single().enabled)
    }

    @Test
    fun `an unauthenticated caller is answered with unauthorized`()
    {
        whenever(service.get(any(), anyOrNull())).thenThrow(UnauthorizedException("User is not authenticated"))

        assertEquals(Response.Status.UNAUTHORIZED.statusCode, resource.get(appUserId.toString(), null).status)
    }

    @Test
    fun `a caller without platform administration is answered with forbidden`()
    {
        whenever(service.get(any(), anyOrNull())).thenThrow(ForbiddenException("User does not have permission"))

        assertEquals(Response.Status.FORBIDDEN.statusCode, resource.get(appUserId.toString(), null).status)
    }

    @Test
    fun `a rejected request is answered with bad request`()
    {
        whenever(service.get(any(), anyOrNull())).thenThrow(IllegalArgumentException("Invalid app user ID format"))

        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.get(appUserId.toString(), null).status)
    }

    @Test
    fun `an unexpected failure is answered with an internal error`()
    {
        whenever(service.get(any(), anyOrNull())).thenThrow(IllegalStateException("boom"))

        assertEquals(
            Response.Status.INTERNAL_SERVER_ERROR.statusCode,
            resource.get(appUserId.toString(), null).status,
        )
    }

    private fun result(): UserFeatureEntitlementResult
    {
        val user = AppUser().apply {
            id = appUserId
            email = "process-owner@process.test"
        }
        val decision = SubscriptionFeatureEntitlement().apply {
            ownerType = SubscriptionOwnerType.USER.name
            this.appUserId = user.id
            featureCode = "PROCESS_CAPABILITY"
            isEnabled = true
            updatedByAppUserId = user.id
        }
        return UserFeatureEntitlementResult(user, listOf(decision))
    }
}
