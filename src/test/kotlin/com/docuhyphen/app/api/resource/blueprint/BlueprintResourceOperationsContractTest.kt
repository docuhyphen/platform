package com.docuhyphen.app.api.resource.blueprint

import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlueprintResourceOperationsContractTest {
    @Test
    fun `the blueprint resource routes through an operations interface and carries no JAX-RS annotation itself`() {
        val resource = BlueprintDefinitionResource::class.java
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

    private fun isJaxRs(annotation: Annotation): Boolean =
        annotation.annotationClass.java.packageName.let {
            it.startsWith("jakarta.ws.rs") || it.startsWith("org.jboss.resteasy.reactive")
        }
}
