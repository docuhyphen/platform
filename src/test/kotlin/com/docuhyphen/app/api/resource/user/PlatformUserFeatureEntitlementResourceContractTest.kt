package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformUserFeatureEntitlementDtoMapper
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.model.subscription.UserFeatureEntitlementResult
import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementDto
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsResponse
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.service.subscription.PlatformUserFeatureEntitlementService
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class PlatformUserFeatureEntitlementResourceContractTest
{
    private val service = mock<PlatformUserFeatureEntitlementService>()
    private val resource = PlatformUserFeatureEntitlementResource(
        service,
        PlatformUserFeatureEntitlementDtoMapper(),
    )
    private val appUserId: UUID = UUID.randomUUID()

    @Test
    fun `personal feature entitlement administration declares its resource based paths and verbs on an operations interface`() {
        val resourceClass = PlatformUserFeatureEntitlementResource::class.java
        val operations = resourceClass.interfaces.singleOrNull {
            it.simpleName == "${resourceClass.simpleName}Operations"
        }
        assertNotNull(operations, "${resourceClass.simpleName} implements no operations interface")
        assertEquals("${resourceClass.packageName}.operations", operations!!.packageName)
        assertEquals(
            "/platform/users/{appUserId}/feature-entitlements",
            operations.getAnnotation(Path::class.java).value
        )
        assertTrue(operations.declaredMethods.single { it.name == "get" }.isAnnotationPresent(GET::class.java))
        assertTrue(operations.declaredMethods.single { it.name == "replace" }.isAnnotationPresent(PUT::class.java))
        val requestIdHeaders = operations.declaredMethods
            .flatMap { method -> method.parameterAnnotations.flatMap { it.toList() } }
            .filterIsInstance<HeaderParam>()
            .map { it.value }
        assertEquals(listOf(RequestHeaders.REQUEST_ID, RequestHeaders.REQUEST_ID), requestIdHeaders)
    }

    @Test
    fun `the resource class carries no JAX-RS annotation of its own`() {
        val resourceClass = PlatformUserFeatureEntitlementResource::class.java

        assertTrue(resourceClass.annotations.none(::isJaxRs), "the class is annotated")
        resourceClass.declaredMethods.forEach { method ->
            assertTrue(
                method.annotations.none(::isJaxRs) && method.parameterAnnotations.flatten().none(::isJaxRs),
                "${method.name} is annotated",
            )
        }
    }

    private fun isJaxRs(annotation: Annotation): Boolean =
        annotation.annotationClass.java.packageName.startsWith("jakarta.ws.rs")

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
