package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.resource.model.BindingRequest
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class SchemaBindingConfigurationTest {
    private val organizationId = UUID.randomUUID()
    private val schemaDefinitionId = UUID.randomUUID()
    private val publishedVersionId = UUID.randomUUID()

    private val referenceContract = contract(FieldValueType.SHORT_TEXT, """{"maxLength":12}""")
    private val amountContract = contract(FieldValueType.DECIMAL, """{"scale":2}""")
    private val flagContract = contract(FieldValueType.BOOLEAN, "{}")

    private val bindingsByVersion = mutableMapOf<UUID, MutableList<SchemaFieldBinding>>()
    private var draft: SchemaVersion? = null

    private val service: SchemaDefinitionService

    init {
        val principal = PrincipalRef.user(UUID.randomUUID())
        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(AuthorizationContext(activeOrgId = organizationId))
        val userRoleService = mock<UserRoleService>()
        whenever(
            userRoleService.orgRolesIn(
                principal.id,
                organizationId
            )
        ).thenReturn(setOf(OrganizationRoleName.ORG_ADMIN))
        val authorizationService = mock<AuthorizationService>()
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())

        val definition = SchemaDefinition().apply {
            id = schemaDefinitionId
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = organizationId
            namespace = "process"
            schemaKey = "process-data"
            displayName = "Process data"
            targetResourceType = "EXCHANGE"
            status = FieldLifecycleStatus.PUBLISHED
        }
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(schemaDefinitionId)).thenReturn(definition)
        whenever(definitionRepository.findByIdForUpdate(schemaDefinitionId)).thenReturn(definition)
        whenever(definitionRepository.update(any())).thenAnswer { it.getArgument(0) }

        val published = SchemaVersion().apply {
            id = publishedVersionId
            schemaDefinitionId = this@SchemaBindingConfigurationTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.PUBLISHED
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findLatestPublished(schemaDefinitionId)).thenReturn(published)
        whenever(versionRepository.findDraft(schemaDefinitionId)).thenAnswer { draft }
        whenever(versionRepository.findMaxVersion(schemaDefinitionId)).thenReturn(1)
        whenever(versionRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SchemaVersion>(0).also { draft = it }
        }

        val bindingRepository = mock<SchemaFieldBindingRepository>()
        whenever(bindingRepository.findByVersion(any())).thenAnswer { invocation ->
            bindingsByVersion[invocation.getArgument(0)].orEmpty().toList()
        }
        whenever(bindingRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SchemaFieldBinding>(0).also { binding ->
                bindingsByVersion.getOrPut(binding.schemaVersionId) { mutableListOf() } += binding
            }
        }
        whenever(bindingRepository.deleteByVersion(any())).thenAnswer { invocation ->
            bindingsByVersion.remove(invocation.getArgument<UUID>(0))?.size ?: 0
        }

        val contracts = listOf(referenceContract, amountContract, flagContract)
        val contractRepository = mock<FieldContractRepository>()
        contracts.forEach { whenever(contractRepository.findById(it.id)).thenReturn(it) }
        whenever(contractRepository.findByIds(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<UUID>>(0).toSet()
            contracts.filter { it.id in ids }
        }
        val fieldDefinitionRepository = mock<FieldDefinitionRepository>()
        contracts.forEach { contract ->
            whenever(fieldDefinitionRepository.findById(contract.fieldDefinitionId)).thenReturn(
                FieldDefinition().apply {
                    id = contract.fieldDefinitionId
                    scopeKind = FieldScopeKind.ORGANIZATION
                    scopeOrgId = organizationId
                    namespace = "process"
                    fieldKey = "field-${contract.valueType.name.lowercase().replace('_', '-')}"
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
        }

        service = SchemaDefinitionService(
            schemaDefinitionRepository = definitionRepository,
            schemaVersionRepository = versionRepository,
            bindingRepository = bindingRepository,
            fieldContractRepository = contractRepository,
            fieldDefinitionRepository = fieldDefinitionRepository,
            fieldValueValidator = FieldValueValidator(FieldTypeRegistry()),
            authorizationService = authorizationService,
            authorizationContextFactory = contextFactory,
            userRoleService = userRoleService,
            auditRecorder = mock<AuditRecorder>(),
            subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
            projectionLoader = FieldsProjectionLoader(
                bindingRepository,
                contractRepository,
                fieldDefinitionRepository,
                mock(),
                mock()
            ),
            schemaTargets = SchemaTargetRegistry(),
        )
    }

    @Test
    fun `a new version keeps each field's default, visibility and section through a save of what it was opened with`() {
        bindingsByVersion[publishedVersionId] = mutableListOf(
            published(
                referenceContract,
                0,
                section = "Identity",
                default = "\"REF-001\"",
                visibility = FieldDataClassification.INTERNAL
            ),
            published(flagContract, 1, section = null, default = "true", visibility = FieldDataClassification.PUBLIC),
        )

        val opened = service.createDraftVersion(schemaDefinitionId)
        val roundTripped = requireNotNull(opened.draftVersion).bindings.map {
            BindingRequest(
                fieldContractId = it.fieldContractId,
                displayOrder = it.displayOrder,
                section = it.section,
                isRequired = it.isRequired,
                isReadOnly = it.isReadOnly,
                defaultValueJson = it.defaultValueJson,
                visibility = it.visibility,
            )
        }
        service.updateDraftBindings(schemaDefinitionId, roundTripped)

        val saved = draftBindings()
        assertEquals(listOf("Identity", null), saved.map { it.section })
        assertEquals(listOf("\"REF-001\"", "true"), saved.map { it.defaultValueJson })
        assertEquals(
            listOf(FieldDataClassification.INTERNAL, FieldDataClassification.PUBLIC),
            saved.map { it.visibility })
        assertEquals(listOf(true, true), saved.map { it.isReadOnly })
    }

    @Test
    fun `a default is stored in the canonical form of its field type`() {
        openDraft()

        service.updateDraftBindings(
            schemaDefinitionId,
            listOf(
                BindingRequest(
                    fieldContractId = referenceContract.id,
                    displayOrder = 0,
                    defaultValueJson = "\"  REF-001  \""
                ),
                BindingRequest(fieldContractId = amountContract.id, displayOrder = 1, defaultValueJson = "\"12.5\""),
                BindingRequest(fieldContractId = flagContract.id, displayOrder = 2, defaultValueJson = "\"yes\""),
            ),
        )

        assertEquals(listOf("\"REF-001\"", "\"12.50\"", "true"), draftBindings().map { it.defaultValueJson })
    }

    @Test
    fun `an empty default stores no default`() {
        openDraft()

        service.updateDraftBindings(
            schemaDefinitionId,
            listOf(BindingRequest(fieldContractId = referenceContract.id, displayOrder = 0, defaultValueJson = "null")),
        )

        assertNull(draftBindings().single().defaultValueJson)
    }

    @Test
    fun `a default that does not fit its field type is refused when the draft is saved`() {
        openDraft()

        assertThrows<FieldValidationException> {
            service.updateDraftBindings(
                schemaDefinitionId,
                listOf(BindingRequest(fieldContractId = amountContract.id, defaultValueJson = "\"not a number\"")),
            )
        }
    }

    @Test
    fun `a default that breaks its field's limits is refused when the draft is saved`() {
        openDraft()

        assertThrows<FieldValidationException> {
            service.updateDraftBindings(
                schemaDefinitionId,
                listOf(
                    BindingRequest(
                        fieldContractId = referenceContract.id,
                        defaultValueJson = "\"REFERENCE-TOO-LONG\""
                    )
                ),
            )
        }
    }

    @Test
    fun `a default that is not JSON is refused when the draft is saved`() {
        openDraft()

        assertThrows<FieldValidationException> {
            service.updateDraftBindings(
                schemaDefinitionId,
                listOf(BindingRequest(fieldContractId = referenceContract.id, defaultValueJson = "REF-001")),
            )
        }
    }

    @Test
    fun `a section longer than a binding can hold is refused`() {
        openDraft()

        assertThrows<FieldValidationException> {
            service.updateDraftBindings(
                schemaDefinitionId,
                listOf(BindingRequest(fieldContractId = referenceContract.id, section = "s".repeat(129))),
            )
        }
    }

    private fun openDraft() {
        draft = SchemaVersion().apply {
            id = UUID.randomUUID()
            schemaDefinitionId = this@SchemaBindingConfigurationTest.schemaDefinitionId
            versionNumber = 2
            status = FieldLifecycleStatus.DRAFT
        }
    }

    private fun draftBindings(): List<SchemaFieldBinding> =
        bindingsByVersion[requireNotNull(draft).id].orEmpty().sortedBy { it.displayOrder }

    private fun contract(type: FieldValueType, constraints: String) = FieldContract().apply {
        id = UUID.randomUUID()
        fieldDefinitionId = UUID.randomUUID()
        contractVersion = 1
        valueType = type
        label = "Recorded ${type.name.lowercase()}"
        constraintsJson = constraints
        dataClassification = FieldDataClassification.PUBLIC
    }

    private fun published(
        contract: FieldContract,
        order: Int,
        section: String?,
        default: String?,
        visibility: FieldDataClassification,
    ) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = publishedVersionId
        fieldContractId = contract.id
        fieldDefinitionId = contract.fieldDefinitionId
        displayOrder = order
        this.section = section
        isRequired = false
        isReadOnly = true
        defaultValueJson = default
        this.visibility = visibility
    }
}
