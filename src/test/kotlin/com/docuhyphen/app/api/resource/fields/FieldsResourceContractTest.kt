package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.resource.exchange.ExchangeFieldsResource
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldDefinitionService
import com.docuhyphen.app.api.service.fields.FieldsAccessContextFactory
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class FieldsResourceContractTest {
    private val id = UUID.randomUUID().toString()

    @Test
    fun `the Fields resources route through operations interfaces and carry no JAX-RS annotation themselves`() {
        listOf(
            ExchangeFieldsResource::class.java,
            FieldDefinitionResource::class.java,
            SchemaResource::class.java,
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

    @Test
    fun `each Exchange Fields endpoint reports its own failure`() {
        val resource = ExchangeFieldsResource(authenticated(), failing<SchemaAssignmentService>(), accessFactory())

        assertEachFailureIsItsOwn(
            ExchangeFieldsResource::class.java.name,
            listOf(
                { resource.getSchema(id) },
                { resource.assignSchema(id, AssignSchemaRequest(UUID.randomUUID()), null, "*") },
                { resource.unassignSchema(id, "*") },
                { resource.patchValues(id, SetFieldValuesRequest(), "*") },
            ),
        )
    }

    @Test
    fun `each Field Definition endpoint reports its own failure`() {
        val resource = FieldDefinitionResource(authenticated(), failing<FieldDefinitionService>())
        val contract = FieldContractRequest(valueType = FieldValueType.SHORT_TEXT, label = "Recorded note")

        assertEachFailureIsItsOwn(
            FieldDefinitionResource::class.java.name,
            listOf(
                { resource.listTypes() },
                { resource.listDefinitions(null) },
                {
                    resource.createDefinition(
                        CreateFieldDefinitionRequest(
                            "process",
                            "recorded-note",
                            contract = contract
                        )
                    )
                },
                { resource.getDefinition(id) },
                { resource.listContracts(id) },
                { resource.addContract(id, contract) },
                { resource.patchStatus(id, FieldStatusRequest(FieldLifecycleStatus.RETIRED)) },
            ),
        )
    }

    @Test
    fun `each Schema endpoint reports its own failure`() {
        val resource = SchemaResource(authenticated(), failing<SchemaDefinitionService>())

        assertEachFailureIsItsOwn(
            SchemaResource::class.java.name,
            listOf(
                { resource.listSchemas(null) },
                { resource.createSchema(CreateSchemaRequest("process", "process-data", "Process data")) },
                { resource.getSchema(id) },
                { resource.getVersion(id) },
                { resource.getResolved(id) },
                { resource.updateDraftBindings(id, UpdateBindingsRequest()) },
                { resource.publishDraft(id, null) },
                { resource.createDraftVersion(id) },
                { resource.patchStatus(id, FieldStatusRequest(FieldLifecycleStatus.RETIRED)) },
            ),
        )
    }

    private fun assertEachFailureIsItsOwn(loggerName: String, calls: List<() -> Response>) {
        val messages = calls.map { call ->
            CapturedErrors(loggerName).use { captured ->
                val response = call()
                assertEquals(500, response.status, "An unexpected failure answers 500")
                captured.messages.singleOrNull()
                    ?: throw AssertionError("Expected one logged failure, got ${captured.messages}")
            }
        }
        assertEquals(messages.size, messages.toSet().size, "Each endpoint must log its own failure: $messages")
    }

    private fun isJaxRs(annotation: Annotation): Boolean =
        annotation.annotationClass.java.packageName.startsWith("jakarta.ws.rs")

    private inline fun <reified T : Any> failing(): T =
        mock<T>(defaultAnswer = Answer<Any> { throw IllegalCallerException("unexpected failure") })

    private fun authenticated(): AuthTokenContext = AuthTokenContext().apply {
        authToken = AuthToken().apply { appUser = AppUser() }
    }

    private fun accessFactory(): FieldsAccessContextFactory = mock<FieldsAccessContextFactory>().also {
        whenever(it.current()).thenReturn(
            FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext()),
        )
    }

    private class CapturedErrors(loggerName: String) : Handler(), AutoCloseable {
        private val logger: Logger = Logger.getLogger(loggerName)
        val messages = mutableListOf<String>()

        init {
            logger.addHandler(this)
        }

        override fun publish(record: LogRecord) {
            if (record.level.intValue() >= Level.SEVERE.intValue()) messages += record.message
        }

        override fun flush() = Unit

        override fun close() {
            logger.removeHandler(this)
        }
    }
}
