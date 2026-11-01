package com.docuhyphen.app.api.resource.audit

import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditResourceOperationsContractTest
{
    @Test
    fun `audit resources route through operations interfaces and carry no JAX-RS annotation themselves`()
    {
        listOf(
            AuditApplicationEventsResource::class.java,
            AuditExchangeDocumentEventsResource::class.java,
            AuditExchangeEventsResource::class.java,
            AuditExportResource::class.java,
            AuditGovernanceResource::class.java,
            AuditOrganizationAnalyticsResource::class.java,
            AuditOrganizationEngagementResource::class.java,
            AuditOrganizationEventsResource::class.java,
            AuditOrganizationExportsResource::class.java,
            AuditOrganizationIntegrityResource::class.java,
            AuditOrganizationLegalHoldResource::class.java,
            AuditOrganizationRetentionPolicyResource::class.java,
            AuditProjectionResource::class.java,
            AuditSecurityIncidentEventsResource::class.java,
            AuditWorkflowDefinitionEventsResource::class.java,
        ).forEach { resource ->
            assertTrue(
                resource.annotations.none(::isJaxRs),
                "${resource.simpleName} carries JAX-RS annotations on the class",
            )
            resource.declaredMethods.forEach { method ->
                assertTrue(
                    method.annotations.none(::isJaxRs) && method.parameterAnnotations.flatten().none(::isJaxRs),
                    "${resource.simpleName}.${method.name} carries JAX-RS annotations",
                )
            }
            val operations = resource.interfaces.singleOrNull { it.simpleName == "${resource.simpleName}Operations" }
            assertNotNull(operations, "${resource.simpleName} implements no ${resource.simpleName}Operations interface")
            assertEquals("${resource.packageName}.operations", operations!!.packageName)
            assertNotNull(operations.getAnnotation(Path::class.java), "${operations.simpleName} declares no @Path")
        }
    }

    private fun isJaxRs(annotation: Annotation): Boolean =
        annotation.annotationClass.java.packageName.let {
            it.startsWith("jakarta.ws.rs") || it.startsWith("org.jboss.resteasy.reactive")
        }
}
