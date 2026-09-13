package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SchemaAssignmentExactVersionTest
{
    private val requestId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val schemaDefinitionId = UUID.randomUUID()
    private val exactVersionId = UUID.randomUUID()
    private val newerVersionId = UUID.randomUUID()
    private val access = FieldsAccessContext(PrincipalRef.user(actorId), AuthorizationContext())

    @Test
    fun `assigning an exact published version does not resolve latest or recheck the live plan`()
    {
        val fixture = fixture()

        val assigned = fixture.service.assignPublishedSchemaVersion(
            PublishedSchemaAssignmentCommand(
                resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                access = access,
                schemaVersionId = exactVersionId,
                source = SchemaAssignmentSource.API,
            ),
        )

        assertEquals(exactVersionId, assigned.schemaVersionId)
        assertEquals(
            exactVersionId,
            fixture.savedAssignments.single().schemaVersionId,
        )
        assertEquals(
            FieldValueSetKind.ROOT,
            fixture.savedValueSets.single().setKind,
        )
        verify(fixture.schemaVersionRepository, never()).findLatestPublished(schemaDefinitionId)
        verify(fixture.subscriptionGuard, never()).requireResourceMutation(any())
    }

    private data class Fixture(
        val service: SchemaAssignmentService,
        val savedAssignments: MutableList<SchemaAssignment>,
        val savedValueSets: MutableList<FieldValueSet>,
        val schemaVersionRepository: SchemaVersionRepository,
        val subscriptionGuard: BusinessFieldsSubscriptionGuard,
    )

    private fun fixture(): Fixture
    {
        val adapter = mock<FieldResourceAdapter>()
        whenever(adapter.exists(requestId)).thenReturn(true)
        whenever(adapter.schemaAssignmentMutable(requestId)).thenReturn(true)
        whenever(adapter.valuesEditable(requestId)).thenReturn(true)
        whenever(adapter.bindingPolicy).thenReturn(InternalCallerBindingPolicy)
        whenever(adapter.ownerScope(requestId)).thenReturn(ScopeReference.Organization(organizationId))
        whenever(adapter.subscriptionContext(requestId)).thenReturn(null)

        val adapterRegistry = mock<FieldResourceAdapterRegistry>()
        whenever(adapterRegistry.adapterFor(ResourceType.INFORMATION_REQUEST.name)).thenReturn(adapter)

        val savedAssignments = mutableListOf<SchemaAssignment>()
        val assignmentRepository = mock<SchemaAssignmentRepository>()
        whenever(assignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, requestId))
            .thenReturn(null)
        whenever(assignmentRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SchemaAssignment>(0).also { savedAssignments += it }
        }

        val definition = SchemaDefinition().apply {
            id = schemaDefinitionId
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = organizationId
            namespace = "process"
            schemaKey = "request-data"
            displayName = "Request data"
            targetResourceType = ResourceType.INFORMATION_REQUEST.name
            status = FieldLifecycleStatus.PUBLISHED
        }
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(schemaDefinitionId)).thenReturn(definition)

        val exactVersion = SchemaVersion().apply {
            id = exactVersionId
            this.schemaDefinitionId = this@SchemaAssignmentExactVersionTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.PUBLISHED
        }
        val newerVersion = SchemaVersion().apply {
            id = newerVersionId
            this.schemaDefinitionId = this@SchemaAssignmentExactVersionTest.schemaDefinitionId
            versionNumber = 2
            status = FieldLifecycleStatus.PUBLISHED
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findById(exactVersionId)).thenReturn(exactVersion)
        whenever(versionRepository.findById(newerVersionId)).thenReturn(newerVersion)
        whenever(versionRepository.findLatestPublished(schemaDefinitionId)).thenReturn(newerVersion)

        val bindingRepository = mock<SchemaFieldBindingRepository>()
        whenever(bindingRepository.findByVersion(exactVersionId)).thenReturn(emptyList())

        val savedValueSets = mutableListOf<FieldValueSet>()
        val valueSetRepository = mock<FieldValueSetRepository>()
        whenever(valueSetRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<FieldValueSet>(0).also { savedValueSets += it }
        }

        val valueRepository = mock<FieldValueRepository>()
        val selectionRepository = mock<FieldValueSelectionRepository>()
        val contractRepository = mock<FieldContractRepository>()
        val definitionFieldRepository = mock<FieldDefinitionRepository>()
        val subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>()

        return Fixture(
            service = SchemaAssignmentService(
                adapterRegistry = adapterRegistry,
                assignmentRepository = assignmentRepository,
                schemaDefinitionRepository = definitionRepository,
                schemaVersionRepository = versionRepository,
                bindingRepository = bindingRepository,
                fieldContractRepository = contractRepository,
                fieldValueRepository = valueRepository,
                fieldValueSetRepository = valueSetRepository,
                selectionRepository = selectionRepository,
                projectionLoader = FieldsProjectionLoader(
                    bindingRepository,
                    contractRepository,
                    definitionFieldRepository,
                    valueRepository,
                    selectionRepository,
                ),
                validator = FieldValueValidator(FieldTypeRegistry()),
                subscriptionGuard = subscriptionGuard,
                revisionRecorder = mock(),
                auditTrail = mock(),
            ),
            savedAssignments = savedAssignments,
            savedValueSets = savedValueSets,
            schemaVersionRepository = versionRepository,
            subscriptionGuard = subscriptionGuard,
        )
    }
}
