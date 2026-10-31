package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeFieldBindingPolicy
import com.docuhyphen.app.api.service.exchange.ExchangeFieldResourceAdapter
import com.docuhyphen.app.api.service.fields.model.AudienceAuthorizationFixture
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class SchemaAssignmentAudienceAuthorizationTest
{
    private val resourceType = "EXCHANGE"
    private val exchangeId = UUID.randomUUID()
    private val ownerOrganizationId = UUID.randomUUID()
    private val schemaDefinitionId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val rootValueSetId = UUID.randomUUID()

    private val publicContractId = UUID.randomUUID()
    private val internalContractId = UUID.randomUUID()

    private val resource by lazy { FieldsResourceRef(resourceType, exchangeId) }

    private fun AudienceAuthorizationFixture.read() = service.getAssignment(FieldValueReadCommand(resource, access))

    private fun AudienceAuthorizationFixture.save(entries: List<FieldValueEntry>) =
        service.setValues(FieldValueWriteCommand(resource, access, entries))

    private fun AudienceAuthorizationFixture.assign() = requireNotNull(
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.ASSIGN, schemaDefinitionId),
        ),
    ) { "Choosing a Schema always answers with the assignment it created" }

    private fun AudienceAuthorizationFixture.unassign()
    {
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.UNASSIGN),
        )
    }

    private fun fixture(
        principal: PrincipalRef,
        callerIsOrgMember: Boolean,
        vararg allowedActions: Action,
        assigned: Boolean = true,
        allowEveryAction: Boolean = false,
    ): AudienceAuthorizationFixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = this@SchemaAssignmentAudienceAuthorizationTest.ownerOrganizationId
            status = ExchangeStatus.INITIATED
            isDeleted = false
        }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)

        val memberships = mock<OrganizationMembershipService>()
        whenever(memberships.isMember(any(), any())).thenReturn(callerIsOrgMember)

        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer {
            if (allowEveryAction || it.getArgument<Action>(1) in allowedActions)
                Decision.Allow()
            else
                Decision.Deny(Decision.REASON_NO_GRANT, "Denied in test")
        }

        val adapter = ExchangeFieldResourceAdapter(
            exchangeRepository = exchangeRepository,
            authorizationService = authorization,
            bindingPolicy = ExchangeFieldBindingPolicy(
                exchangeRepository = exchangeRepository,
                organizationMembershipService = memberships,
            ),
        )
        val registry = mock<FieldResourceAdapterRegistry>()
        whenever(registry.adapterFor(resourceType)).thenReturn(adapter)

        val assignment = SchemaAssignment().apply {
            id = assignmentId
            resourceType = this@SchemaAssignmentAudienceAuthorizationTest.resourceType
            resourceId = exchangeId
            schemaVersionId = this@SchemaAssignmentAudienceAuthorizationTest.schemaVersionId
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = ownerOrganizationId
        }
        val assignmentRepository = mock<SchemaAssignmentRepository>()
        whenever(assignmentRepository.findByResource(resourceType, exchangeId))
            .thenReturn(if (assigned) assignment else null)
        whenever(assignmentRepository.save(any())).thenAnswer { it.getArgument<SchemaAssignment>(0) }

        val definition = SchemaDefinition().apply {
            id = schemaDefinitionId
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = ownerOrganizationId
            namespace = "process"
            schemaKey = "process-data"
            displayName = "Process data"
            targetResourceType = resourceType
            status = FieldLifecycleStatus.PUBLISHED
        }
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(schemaDefinitionId)).thenReturn(definition)

        val version = SchemaVersion().apply {
            id = schemaVersionId
            schemaDefinitionId = this@SchemaAssignmentAudienceAuthorizationTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.PUBLISHED
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findById(schemaVersionId)).thenReturn(version)
        whenever(versionRepository.findLatestPublished(schemaDefinitionId)).thenReturn(version)

        val bindingRepository = mock<SchemaFieldBindingRepository>()
        whenever(bindingRepository.findByVersion(schemaVersionId)).thenReturn(
            listOf(
                binding(publicContractId, FieldDataClassification.PUBLIC, 0),
                binding(internalContractId, FieldDataClassification.INTERNAL, 1),
            ),
        )

        val contracts = listOf(
            contract(publicContractId, "Shared note"),
            contract(internalContractId, "Internal note"),
        )
        val contractRepository = mock<FieldContractRepository>()
        whenever(contractRepository.findByIds(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<UUID>>(0).toSet()
            contracts.filter { it.id in ids }
        }
        contracts.forEach { whenever(contractRepository.findById(it.id)).thenReturn(it) }

        val fieldDefinitionRepository = mock<FieldDefinitionRepository>()
        contracts.forEach { c ->
            whenever(fieldDefinitionRepository.findById(c.fieldDefinitionId)).thenReturn(
                FieldDefinition().apply {
                    id = c.fieldDefinitionId
                    namespace = "process"
                    fieldKey = "note-${c.id}"
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
        }

        val valueRepository = mock<FieldValueRepository>()
        whenever(valueRepository.findByAssignment(assignmentId)).thenReturn(emptyList())
        whenever(valueRepository.findByValueSet(any())).thenReturn(emptyList())
        whenever(valueRepository.findByValueSetAndContract(any(), any())).thenReturn(null)
        whenever(valueRepository.save(any())).thenAnswer { it.getArgument<FieldValue>(0) }
        whenever(valueRepository.update(any())).thenAnswer { it.getArgument<FieldValue>(0) }

        val rootValueSet = FieldValueSet().apply {
            id = rootValueSetId
            schemaAssignmentId = assignmentId
            setKind = FieldValueSetKind.ROOT
        }
        val valueSetRepository = mock<FieldValueSetRepository>()
        whenever(valueSetRepository.findRoot(assignmentId)).thenReturn(rootValueSet)
        whenever(valueSetRepository.findRootForUpdate(assignmentId)).thenReturn(rootValueSet)
        whenever(valueSetRepository.findByAssignment(assignmentId)).thenReturn(listOf(rootValueSet))
        whenever(valueSetRepository.save(any())).thenAnswer { it.getArgument<FieldValueSet>(0) }

        val selectionRepository = mock<FieldValueSelectionRepository>()
        whenever(selectionRepository.findByValue(any())).thenReturn(emptyList())

        return AudienceAuthorizationFixture(
            SchemaAssignmentService(
                adapterRegistry = registry,
                assignmentRepository = assignmentRepository,
                schemaDefinitionRepository = definitionRepository,
                schemaVersionRepository = versionRepository,
                bindingRepository = bindingRepository,
                fieldContractRepository = contractRepository,
                fieldValueRepository = valueRepository,
                fieldValueSetRepository = valueSetRepository,
                selectionRepository = selectionRepository,
                projectionLoader = FieldsProjectionLoader(
                    bindingRepository, contractRepository, fieldDefinitionRepository,
                    valueRepository, selectionRepository,
                ),
                validator = FieldValueValidator(FieldTypeRegistry()),
                subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
                revisionRecorder = mock<FieldValueRevisionRecorder>(),
                auditTrail = mock<SchemaAssignmentAuditTrail>(),
            ),
            valueRepository,
            assignmentRepository,
            FieldsAccessContext(principal, AuthorizationContext()),
        )
    }

    private fun binding(contractId: UUID, classification: FieldDataClassification, order: Int) =
        SchemaFieldBinding().apply {
            id = UUID.randomUUID()
            schemaVersionId = this@SchemaAssignmentAudienceAuthorizationTest.schemaVersionId
            fieldContractId = contractId
            displayOrder = order
            visibility = classification
        }

    private fun contract(contractId: UUID, contractLabel: String) = FieldContract().apply {
        id = contractId
        fieldDefinitionId = UUID.randomUUID()
        valueType = FieldValueType.SHORT_TEXT
        label = contractLabel
    }

    private fun entry(contractId: UUID) = FieldValueEntry(contractId, JsonPrimitive("supplied"))

    @Test
    fun `external registered user read omits bindings outside their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
        )

        val result = fixture.read()

        assertEquals(listOf(publicContractId), result?.fields?.map { it.fieldContractId })
    }

    @Test
    fun `internal organization member read includes every binding`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
        )

        val result = fixture.read()

        assertEquals(
            listOf(publicContractId, internalContractId),
            result?.fields?.map { it.fieldContractId },
        )
    }

    @Test
    fun `external registered user read describes only the bindings in their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
        )

        val result = fixture.read()

        assertEquals(listOf(publicContractId), result?.bindings?.map { it.fieldContractId })
    }

    @Test
    fun `internal organization member read describes every binding`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
        )

        val result = fixture.read()

        assertEquals(
            listOf(publicContractId, internalContractId),
            result?.bindings?.map { it.fieldContractId },
        )
    }

    @Test
    fun `the bindings a caller is described are exactly the ones their values cover`()
    {
        val external = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
        ).read()
        val internal = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
        ).read()

        assertEquals(external?.fields?.map { it.fieldContractId }, external?.bindings?.map { it.fieldContractId })
        assertEquals(internal?.fields?.map { it.fieldContractId }, internal?.bindings?.map { it.fieldContractId })
    }

    @Test
    fun `external registered user value write response omits bindings outside their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        val result = fixture.save(listOf(entry(publicContractId)))

        assertEquals(listOf(publicContractId), result.fields.map { it.fieldContractId })
    }

    @Test
    fun `participant value write response omits bindings outside their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.participant(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        val result = fixture.save(listOf(entry(publicContractId)))

        assertEquals(listOf(publicContractId), result.fields.map { it.fieldContractId })
    }

    @Test
    fun `external registered user value write response describes only the bindings in their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        val result = fixture.save(listOf(entry(publicContractId)))

        assertEquals(listOf(publicContractId), result.bindings.map { it.fieldContractId })
    }

    @Test
    fun `internal organization member value write response includes every binding`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        val result = fixture.save(listOf(entry(internalContractId)))

        assertEquals(
            listOf(publicContractId, internalContractId),
            result.fields.map { it.fieldContractId },
        )
    }

    @Test
    fun `external registered user cannot write a binding outside their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        val error = assertThrows<FieldValidationException> {
            fixture.save(listOf(entry(internalContractId)))
        }

        assertTrue(
            error.message.contains("not part of the assigned schema") == true,
            "An invisible binding must be refused without confirming that it exists: ${error.message}",
        )
        verify(fixture.valueRepository, never()).save(any())
        verify(fixture.valueRepository, never()).update(any())
    }

    @Test
    fun `public link caller cannot write a binding outside their audience`()
    {
        val fixture = fixture(
            principal = PrincipalRef.publicLink(UUID.randomUUID()),
            callerIsOrgMember = false,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        assertThrows<FieldValidationException> {
            fixture.save(listOf(entry(internalContractId)))
        }

        verify(fixture.valueRepository, never()).save(any())
    }

    @Test
    fun `value write still requires the resource write capability`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
        )

        assertThrows<ForbiddenException> {
            fixture.save(listOf(entry(publicContractId)))
        }
    }

    @Test
    fun `resource write capability alone cannot assign a schema`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
            assigned = false,
        )

        assertThrows<ForbiddenException> {
            fixture.assign()
        }
        verify(fixture.assignmentRepository, never()).save(any())
    }

    @Test
    fun `resource write capability alone cannot unassign a schema`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            Action.EXCHANGE_VIEW,
            Action.EXCHANGE_EDIT,
        )

        assertThrows<ForbiddenException> {
            fixture.unassign()
        }
        verify(fixture.assignmentRepository, never()).delete(any())
    }

    @Test
    fun `a fully privileged caller assigns a schema`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = true,
            assigned = false,
            allowEveryAction = true,
        )

        val result = fixture.assign()

        assertEquals(schemaVersionId, result.schemaVersionId)
        verify(fixture.assignmentRepository).save(any())
    }

    @Test
    fun `external caller permitted to assign a schema receives an audience filtered assignment`()
    {
        val fixture = fixture(
            principal = PrincipalRef.user(UUID.randomUUID()),
            callerIsOrgMember = false,
            assigned = false,
            allowEveryAction = true,
        )

        val result = fixture.assign()

        assertEquals(listOf(publicContractId), result.fields.map { it.fieldContractId })
    }
}
