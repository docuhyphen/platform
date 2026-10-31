package com.docuhyphen.app.api.service.fields

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FieldsServiceLayeringContractTest {
    @Test
    fun `the Fields services hold no request bodies, value data classes or DTO mapping`() {
        listOf(
            "CanonicalFieldValue",
            "ExchangeFieldSnapshot",
            "FieldConstraints",
            "FieldOption",
            "ValidatedSchemaDefault",
            "FieldContractRequest",
            "CreateFieldDefinitionRequest",
            "BindingRequest",
            "CreateSchemaRequest",
            "PublishSchemaRequest",
            "UpdateBindingsRequest",
        ).forEach { name ->
            assertThrows(ClassNotFoundException::class.java, { Class.forName("${javaClass.packageName}.$name") }, name)
        }
        listOf(FieldDefinitionService::class.java, SchemaDefinitionService::class.java).forEach { service ->
            assertTrue(service.declaredMethods.none { it.name == "toDto" }, "${service.simpleName} maps its own DTOs")
        }
    }
}
