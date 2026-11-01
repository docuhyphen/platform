package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.identity.ExternalIdentityResolutionResource
import com.docuhyphen.app.api.resource.identity.IdentityProviderResource
import com.docuhyphen.app.api.resource.security.SecurityIncidentResource
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthResourceOperationsContractTest
{
    @Test
    fun `auth, identity and security resources route through operations interfaces and carry no JAX-RS annotation themselves`()
    {
        listOf(
            ApplicationAuthResource::class.java,
            OAuthResource::class.java,
            PasswordResetResource::class.java,
            RealtimeTicketResource::class.java,
            SessionActivityResource::class.java,
            SignInResource::class.java,
            SignOutResource::class.java,
            SignUpResource::class.java,
            StepUpResource::class.java,
            TokenRefreshResource::class.java,
            UserSessionResource::class.java,
            ExternalIdentityResolutionResource::class.java,
            IdentityProviderResource::class.java,
            SecurityIncidentResource::class.java,
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
