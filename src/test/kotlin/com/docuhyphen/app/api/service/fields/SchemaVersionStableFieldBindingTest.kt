package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.resource.model.BindingRequest
import com.docuhyphen.app.api.resource.model.CreateSchemaRequest
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.model.StableFieldBindingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class SchemaVersionStableFieldBindingTest
{
    private val organizationId = UUID.randomUUID()
    private val schemaDefinitionId = UUID.randomUUID()
    private val draftVersionId = UUID.randomUUID()
    private val publishedVersionId = UUID.randomUUID()

    private val referenceFieldId = UUID.randomUUID()
    private val referenceContractV1Id = UUID.randomUUID()
    private val referenceContractV2Id = UUID.randomUUID()
    private val reviewFieldId = UUID.randomUUID()
    private val reviewContractId = UUID.randomUUID()

    private fun fixture(
        hasOpenDraft: Boolean = true,
        publishedBindings: List<SchemaFieldBinding> = emptyList(),
    ): StableFieldBindingFixture
    {
        val principal = PrincipalRef.user(UUID.randomUUID())
        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(AuthorizationContext(activeOrgId = organizationId))

        val userRoleService = mock<UserRoleService>()
        whenever(userRoleService.orgRolesIn(principal.id, organizationId))
            .thenReturn(setOf(OrganizationRoleName.ORG_ADMIN))
        whenever(userRoleService.isAppAdmin(any())).thenReturn(false)

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
            status = FieldLifecycleStatus.DRAFT
        }
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(schemaDefinitionId)).thenReturn(definition)
        whenever(definitionRepository.findByIdForUpdate(schemaDefinitionId)).thenReturn(definition)
        whenever(definitionRepository.update(any())).thenAnswer { it.getArgument(0) }

        val draft = SchemaVersion().apply {
            id = draftVersionId
            schemaDefinitionId = this@SchemaVersionStableFieldBindingTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.DRAFT
        }
        val published = SchemaVersion().apply {
            id = publishedVersionId
            schemaDefinitionId = this@SchemaVersionStableFieldBindingTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.PUBLISHED
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findDraft(schemaDefinitionId)).thenReturn(if (hasOpenDraft) draft else null)
        whenever(versionRepository.findLatestPublished(schemaDefinitionId))
            .thenReturn(if (publishedBindings.isEmpty()) null else published)
        whenever(versionRepository.findMaxVersion(schemaDefinitionId)).thenReturn(1)

        val savedBindings = mutableListOf<SchemaFieldBinding>()
        val bindingRepository = mock<SchemaFieldBindingRepository>()
        whenever(bindingRepository.findByVersion(publishedVersionId)).thenReturn(publishedBindings)
        whenever(bindingRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SchemaFieldBinding>(0).also { savedBindings += it }
        }

        val contracts = listOf(
            contract(referenceContractV1Id, referenceFieldId, 1, "Reference code"),
            contract(referenceContractV2Id, referenceFieldId, 2, "Reference code"),
            contract(reviewContractId, reviewFieldId, 1, "Review note"),
        )
        val contractRepository = mock<FieldContractRepository>()
        contracts.forEach { whenever(contractRepository.findById(it.id)).thenReturn(it) }

        val fieldDefinitionRepository = mock<FieldDefinitionRepository>()
        whenever(fieldDefinitionRepository.findById(referenceFieldId))
            .thenReturn(fieldDefinition(referenceFieldId, "reference-code"))
        whenever(fieldDefinitionRepository.findById(reviewFieldId))
            .thenReturn(fieldDefinition(reviewFieldId, "review-note"))

        return StableFieldBindingFixture(
            SchemaDefinitionService(
                schemaDefinitionRepository = definitionRepository,
                schemaVersionRepository = versionRepository,
                bindingRepository = bindingRepository,
                fieldContractRepository = contractRepository,
                fieldDefinitionRepository = fieldDefinitionRepository,
                fieldValueValidator = mock(),
                authorizationService = authorizationService,
                authorizationContextFactory = contextFactory,
                userRoleService = userRoleService,
                auditRecorder = mock<AuditRecorder>(),
                subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
                projectionLoader = FieldsProjectionLoader(
                    bindingRepository, contractRepository, fieldDefinitionRepository, mock(), mock(),
                ),
                schemaTargets = SchemaTargetRegistry(),
            ),
            savedBindings,
        )
    }

    private fun contract(contractId: UUID, definitionId: UUID, version: Int, contractLabel: String) =
        FieldContract().apply {
            id = contractId
            fieldDefinitionId = definitionId
            contractVersion = version
            valueType = FieldValueType.SHORT_TEXT
            label = contractLabel
        }

    private fun fieldDefinition(definitionId: UUID, key: String) = FieldDefinition().apply {
        id = definitionId
        scopeKind = FieldScopeKind.ORGANIZATION
        scopeOrgId = organizationId
        namespace = "process"
        fieldKey = key
        status = FieldLifecycleStatus.PUBLISHED
    }

    private fun request(vararg contractIds: UUID) =
        contractIds.mapIndexed { index, id -> BindingRequest(fieldContractId = id, displayOrder = index) }

    @Test
    fun `a draft refuses two contract versions of the same stable field`()
    {
        val fixture = fixture()

        val refused = assertThrows<FieldValidationException> {
            fixture.service.updateDraftBindings(
                schemaDefinitionId,
                request(referenceContractV1Id, referenceContractV2Id),
            )
        }

        assertTrue(
            refused.message?.contains("process:reference-code") == true,
            "The refusal must name the field bound twice: ${refused.message}",
        )
    }

    @Test
    fun `a new schema refuses two contract versions of the same stable field`()
    {
        val fixture = fixture()

        assertThrows<FieldValidationException> {
            fixture.service.createSchema(
                CreateSchemaRequest(
                    namespace = "process",
                    schemaKey = "process-intake",
                    displayName = "Process intake",
                    scopeKind = FieldScopeKind.ORGANIZATION,
                    bindings = request(referenceContractV1Id, referenceContractV2Id),
                ),
            )
        }
    }

    @Test
    fun `a draft accepts one contract for each distinct stable field`()
    {
        val fixture = fixture()

        fixture.service.updateDraftBindings(
            schemaDefinitionId,
            request(referenceContractV2Id, reviewContractId),
        )

        assertEquals(
            listOf(referenceContractV2Id, reviewContractId),
            fixture.savedBindings.map { it.fieldContractId },
        )
    }

    @Test
    fun `a persisted binding records the stable field its contract belongs to`()
    {
        val fixture = fixture()

        fixture.service.updateDraftBindings(
            schemaDefinitionId,
            request(referenceContractV2Id, reviewContractId),
        )

        assertEquals(
            listOf(referenceFieldId, reviewFieldId),
            fixture.savedBindings.map { it.recordedStableFieldId() },
        )
    }

    @Test
    fun `a draft cloned from the published version carries the stable field of each binding`()
    {
        val source = SchemaFieldBinding().apply {
            id = UUID.randomUUID()
            schemaVersionId = publishedVersionId
            fieldContractId = referenceContractV2Id
            fieldDefinitionId = referenceFieldId
            displayOrder = 0
        }
        val fixture = fixture(hasOpenDraft = false, publishedBindings = listOf(source))

        fixture.service.createDraftVersion(schemaDefinitionId)

        assertEquals(listOf(referenceFieldId), fixture.savedBindings.map { it.recordedStableFieldId() })
    }
}

private fun SchemaFieldBinding.recordedStableFieldId(): UUID? =
    runCatching { fieldDefinitionId }.getOrNull()
