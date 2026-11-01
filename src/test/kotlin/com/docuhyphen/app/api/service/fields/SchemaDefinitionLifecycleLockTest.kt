package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class SchemaDefinitionLifecycleLockTest {
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val schemaRepository = mock<SchemaDefinitionRepository>()
    private val versionRepository = mock<SchemaVersionRepository>()
    private val bindingRepository = mock<SchemaFieldBindingRepository>()
    private val contractRepository = mock<FieldContractRepository>()
    private val fieldRepository = mock<FieldDefinitionRepository>()
    private val schema = SchemaDefinition().apply {
        scopeKind = FieldScopeKind.PLATFORM
        namespace = "process"
        schemaKey = "collection"
        displayName = "Collection"
        targetResourceType = ResourceType.EXCHANGE.name
    }
    private val draft = SchemaVersion().apply {
        schemaDefinitionId = schema.id
        versionNumber = 2
        status = FieldLifecycleStatus.DRAFT
    }

    private val service = SchemaDefinitionService(
        schemaDefinitionRepository = schemaRepository,
        schemaVersionRepository = versionRepository,
        bindingRepository = bindingRepository,
        fieldContractRepository = contractRepository,
        fieldDefinitionRepository = fieldRepository,
        fieldValueValidator = mock<FieldValueValidator>(),
        authorizationService = mock<AuthorizationService>(),
        authorizationContextFactory = mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(principal)
            whenever(it.currentContext()).thenReturn(AuthorizationContext())
        },
        userRoleService = mock<UserRoleService>().also { whenever(it.isAppAdmin(principal.id)).thenReturn(true) },
        auditRecorder = mock<AuditRecorder>(),
        subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
        projectionLoader = FieldsProjectionLoader(
            bindingRepository,
            contractRepository,
            fieldRepository,
            mock(),
            mock()
        ),
        schemaTargets = SchemaTargetRegistry(),
    )

    @Test
    fun `every command that changes a Schema's versions locks the Schema before it reads the draft it changes`() {
        val reads = mutableListOf<String>()
        whenever(schemaRepository.findByIdForUpdate(schema.id)).thenAnswer {
            reads += "schema locked"
            schema
        }
        whenever(schemaRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(versionRepository.findDraft(schema.id)).thenAnswer {
            reads += "draft read"
            draft
        }
        whenever(bindingRepository.findByVersion(draft.id)).thenReturn(
            listOf(
                SchemaFieldBinding().apply {
                    schemaVersionId = draft.id
                    fieldContractId = UUID.randomUUID()
                    fieldDefinitionId = UUID.randomUUID()
                },
            ),
        )
        val commands = listOf<() -> Unit>(
            { service.updateDraftBindings(schema.id, emptyList()) },
            { service.publishDraft(schema.id, null) },
            { service.retireSchema(schema.id) },
        )

        commands.forEach { command ->
            reads.clear()
            command()

            assertEquals("schema locked", reads.firstOrNull())
            assertEquals(1, reads.count { it == "schema locked" })
        }
        verify(schemaRepository, never()).findById(any())
    }

    @Test
    fun `a draft save or a new draft that waited for a competing change acts on what that change committed`() {
        whenever(schemaRepository.findByIdForUpdate(schema.id)).thenReturn(schema)
        whenever(versionRepository.findDraft(schema.id)).thenReturn(null)

        val save = assertThrows<IllegalStateException> { service.updateDraftBindings(schema.id, emptyList()) }
        val publish = assertThrows<IllegalStateException> { service.publishDraft(schema.id, null) }

        assertEquals("Schema has no editable draft version", save.message)
        assertEquals("Schema has no draft version to publish", publish.message)
        verify(bindingRepository, never()).deleteByVersion(any())

        whenever(versionRepository.findDraft(schema.id)).thenReturn(draft)
        val second = assertThrows<IllegalStateException> { service.createDraftVersion(schema.id) }
        assertEquals("Schema already has an open draft version", second.message)
        verify(schemaRepository, never()).findById(any())
    }
}
