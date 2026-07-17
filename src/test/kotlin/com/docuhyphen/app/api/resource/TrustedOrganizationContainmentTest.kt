package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.PublishedExchangeGroupDto
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TrustedOrganizationContainmentTest
{
    @Test
    fun `organization resources do not expose a trusted member enumeration endpoint`()
    {
        val paths = OrganizationResource::class.java.declaredMethods
            .mapNotNull { it.getAnnotation(Path::class.java)?.value }

        assertFalse(paths.any { it.contains("app-users") })
    }

    @Test
    fun `published Exchange group summary does not expose members`()
    {
        val fields = PublishedExchangeGroupDto::class.java.declaredFields.map { it.name }

        assertFalse(fields.contains("members"))
    }

    @Test
    fun `published group resource uses the trusted organization endpoint`()
    {
        assertEquals(
            "organizations/{targetOrganizationId}/published-exchange-groups",
            PublishedExchangeGroupResource::class.java.getAnnotation(Path::class.java).value,
        )
    }

    @Test
    fun `trusted group initiation selection is discriminated by type`()
    {
        val request = Json.decodeFromString<ExchangeInitiationDto>(
            """{"primaryRecipient":{"type":"TRUSTED_GROUP","organizationId":"${java.util.UUID.randomUUID()}","groupId":"${java.util.UUID.randomUUID()}"}}""",
        )

        assertEquals(TrustedGroupRecipientSelectionRequest::class, request.primaryRecipient!!::class)
    }
}
