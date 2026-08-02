package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.identity.ExternalIdentityResolutionResource
import com.docuhyphen.app.api.model.dto.OrganizationDirectoryEntryDto
import com.docuhyphen.app.api.model.dto.OrganizationTrustRelationshipDto
import com.docuhyphen.app.api.resource.model.OrganizationDirectorySearchRequest
import com.docuhyphen.app.api.service.organization.OrganizationDirectorySearchService
import com.docuhyphen.app.api.service.organization.OrganizationTrustCommandService
import com.docuhyphen.app.api.service.organization.OrganizationTrustQueryService
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.GenericEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path as FilePath

class OrganizationTrustResourceContractTest
{
    @Test
    fun `directory search response preserves its serializable element type`()
    {
        val searchService = mock<OrganizationDirectorySearchService>()
        val organization = mock<OrganizationDirectoryEntryDto>()
        whenever(searchService.search("receiver", "request-1")).thenReturn(listOf(organization))
        val resource = OrganizationDirectorySearchResource(searchService)

        val response = resource.search(
            OrganizationDirectorySearchRequest("receiver"),
            "request-1",
        )
        val entity = response.entity as GenericEntity<*>

        assertEquals(200, response.status)
        assertEquals(listOf(organization), entity.entity)
        assertTrue(entity.type.typeName.contains("OrganizationDirectoryEntryDto"))
    }

    @Test
    fun `relationship list response preserves its serializable element type`()
    {
        val queryService = mock<OrganizationTrustQueryService>()
        val relationship = mock<OrganizationTrustRelationshipDto>()
        whenever(queryService.listRelationships()).thenReturn(listOf(relationship))
        val resource = OrganizationTrustRelationshipResource(
            mock<OrganizationTrustCommandService>(),
            queryService,
        )

        val response = resource.list()
        val entity = response.entity as GenericEntity<*>

        assertEquals(200, response.status)
        assertEquals(listOf(relationship), entity.entity)
        assertTrue(entity.type.typeName.contains("OrganizationTrustRelationshipDto"))
        assertTrue(
            Files.readString(
                FilePath.of(
                    "src/main/kotlin/com/docuhyphen/app/api/resource/organization/OrganizationTrustRelationshipResource.kt",
                ),
            ).contains(
                "GenericEntity<List<OrganizationTrustRelationshipDto>>(relationships)",
            ),
        )
    }

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
                    "src/main/kotlin/com/docuhyphen/app/api/resource/organization/OrganizationExchangeLinkResource.kt",
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
