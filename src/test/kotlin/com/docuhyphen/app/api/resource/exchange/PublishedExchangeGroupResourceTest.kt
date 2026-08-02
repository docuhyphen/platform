package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.model.dto.PublishedExchangeGroupDto
import com.docuhyphen.app.api.service.organization.TrustedExternalGroupQueryService
import jakarta.ws.rs.core.GenericEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class PublishedExchangeGroupResourceTest
{
    private val queryService = mock<TrustedExternalGroupQueryService>()
    private val resource = PublishedExchangeGroupResource(queryService)

    @Test
    fun `empty response preserves its element type and serializes`()
    {
        val organizationId = UUID.randomUUID()
        whenever(queryService.listPublishedGroups(organizationId)).thenReturn(emptyList())

        val response = resource.list(organizationId.toString())
        val entity = response.entity as GenericEntity<*>

        assertEquals(200, response.status)
        assertEquals(emptyList<PublishedExchangeGroupDto>(), entity.entity)
        assertTrue(entity.type.typeName.contains("PublishedExchangeGroupDto"))
        @Suppress("UNCHECKED_CAST")
        assertEquals("[]", Json.encodeToString(entity.entity as List<PublishedExchangeGroupDto>))
    }

    @Test
    fun `populated response preserves its element type and serializes group metadata`()
    {
        val organizationId = UUID.randomUUID()
        val group = PublishedExchangeGroupDto(
            id = UUID.randomUUID(),
            name = "Published Reviewers",
            description = "External decision makers",
            organizationId = organizationId,
        )
        whenever(queryService.listPublishedGroups(organizationId)).thenReturn(listOf(group))

        val response = resource.list(organizationId.toString())
        val entity = response.entity as GenericEntity<*>

        assertEquals(200, response.status)
        assertEquals(listOf(group), entity.entity)
        assertTrue(entity.type.typeName.contains("PublishedExchangeGroupDto"))
        @Suppress("UNCHECKED_CAST")
        val json = Json.encodeToString(entity.entity as List<PublishedExchangeGroupDto>)
        assertTrue(json.contains("Published Reviewers"))
        assertTrue(json.contains(organizationId.toString()))
    }

    @Test
    fun `malformed target organization id returns a client error without querying`()
    {
        val response = resource.list("not-an-organization-id")

        assertEquals(400, response.status)
        verifyNoInteractions(queryService)
    }
}
