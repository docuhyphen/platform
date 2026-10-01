package com.docuhyphen.app.api.resource.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestConfigurationBundleValidationDto
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.resource.informationrequest.template.operations.InformationRequestConfigurationBundleResourceOperations
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestConfigurationBundleValidator
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestConfigurationBundleResourceContractTest
{
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val resource = InformationRequestConfigurationBundleResource(InformationRequestConfigurationBundleValidator(), accessContextFactory)
    private val bundle = """
        {"formatVersion":1,"bundleKey":"neutral-bundle","bundleVersion":1,"displayName":"Neutral bundle",
         "retentionDefaults":[{"retentionKey":"standard-retention","minimumRetentionDays":30}]}
    """.trimIndent()

    @Test
    fun `validation is a sub-resource of configuration bundles and answers valid bundles`()
    {
        whenever(accessContextFactory.currentAuthenticated())
            .thenReturn(RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext()))
        assertEquals(
            "/information-request-configuration-bundles/validations",
            InformationRequestConfigurationBundleResourceOperations::class.java.getAnnotation(Path::class.java).value,
        )

        val response = resource.validate(bundle)

        assertEquals(200, response.status)
        assertTrue((response.entity as InformationRequestConfigurationBundleValidationDto).valid)
    }

    @Test
    fun `an invalid bundle lists its problems, an absent one is refused, and an anonymous caller is forbidden`()
    {
        whenever(accessContextFactory.currentAuthenticated())
            .thenReturn(RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext()))
        val invalid = resource.validate(bundle.replace("\"minimumRetentionDays\":30", "\"minimumRetentionDays\":-1"))
        val dto = invalid.entity as InformationRequestConfigurationBundleValidationDto
        assertEquals(200, invalid.status)
        assertFalse(dto.valid)
        assertEquals(listOf("retentionDefaults[0].minimumRetentionDays"), dto.problems.map { it.path })
        assertEquals(400, resource.validate(" ").status)

        whenever(accessContextFactory.currentAuthenticated()).thenThrow(ForbiddenException("Not authenticated"))
        assertEquals(403, resource.validate(bundle).status)
    }
}
