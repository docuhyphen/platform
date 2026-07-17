package com.docuhyphen.app.api.resource

import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path as FilePath

class OrganizationTrustResourceContractTest
{
    @Test
    fun `trust administration exposes REST resource paths and removes obsolete link resource`()
    {
        assertEquals(
            "organization-trust-relationships",
            OrganizationTrustRelationshipResource::class.java.getAnnotation(Path::class.java).value,
        )
        val methods = OrganizationTrustRelationshipResource::class.java.declaredMethods
        assertTrue(methods.any { it.isAnnotationPresent(GET::class.java) && it.name == "list" })
        assertTrue(methods.any { it.isAnnotationPresent(POST::class.java) && it.name == "create" })
        assertTrue(methods.any { it.isAnnotationPresent(PATCH::class.java) && it.name == "updatePolicy" })
        assertTrue(methods.any { it.isAnnotationPresent(DELETE::class.java) && it.name == "resume" })
        assertFalse(
            Files.exists(
                FilePath.of(
                    "src/main/kotlin/com/docuhyphen/app/api/resource/OrganizationExchangeLinkResource.kt",
                ),
            ),
        )
    }

    @Test
    fun `organization discovery uses a body based search resource`()
    {
        assertEquals(
            "organization-directory-searches",
            OrganizationDirectorySearchResource::class.java.getAnnotation(Path::class.java).value,
        )
        assertTrue(
            OrganizationDirectorySearchResource::class.java.declaredMethods
                .single { it.name == "search" }
                .isAnnotationPresent(POST::class.java),
        )
    }

    @Test
    fun `trusted member resolution uses a body and resource based path`()
    {
        assertEquals(
            "organizations/{targetOrganizationId}/external-identity-resolutions",
            ExternalIdentityResolutionResource::class.java.getAnnotation(Path::class.java).value,
        )
        assertTrue(
            ExternalIdentityResolutionResource::class.java.declaredMethods
                .single { it.name == "resolve" }
                .isAnnotationPresent(POST::class.java),
        )
    }
}
