package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeFieldBindingPolicy
import com.docuhyphen.app.api.service.exchange.ExchangeFieldResourceAdapter
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Audience filtering and capability rules for a resource's Schema Assignment and typed values.
 *
 * The projection returned by a value write must hide exactly what the read projection hides, an
 * external caller must not be able to address a binding their audience cannot see, and changing
 * which Schema is assigned must require more than the resource's ordinary write capability.
 *
 * The Exchange adapter is exercised for real so the classification of a caller as external and the
 * capability actually demanded of them are both covered. Participant and public-link principals are
 * supplied explicitly: no production endpoint emits them yet, so these are contract tests for the
 * service and adapter boundary rather than proof of an exposed unauthenticated surface.
 */
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

    private data class Fixture(
        val service: SchemaAssignmentService,
        val valueRepository: FieldValueRepository,
        val assignmentRepository: SchemaAssignmentRepository,
        val access: FieldsAccessContext,
    )

    private val resource by lazy { FieldsResourceRef(resourceType, exchangeId) }

    /** Reads the answers the Exchange gives as itself. */
    private fun Fixture.read() = service.getAssignment(FieldValueReadCommand(resource, access))

    /** Saves answers into the set the Exchange answers with as itself. */
    private fun Fixture.save(entries: List<FieldValueEntry>) =
        service.setValues(FieldValueWriteCommand(resource, access, entries))

    /** Chooses the published Schema for the Exchange. */
    private fun Fixture.assign() = requireNotNull(
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.ASSIGN, schemaDefinitionId),
        ),
    ) { "Choosing a Schema always answers with the assignment it created" }

    /** Removes the Schema governing the Exchange. */
    private fun Fixture.unassign()
    {
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.UNASSIGN),
        )
    }

    /**
     * @param principal the caller identity handed to the service.
     * @param callerIsOrgMember whether the caller is an active member of the Exchange's owner org,
     *   which is what makes a registered User an internal rather than an external caller.
     * @param allowedActions the Actions the authorization service allows for this caller.
     * @param assigned whether the Exchange already has a Schema Assignment.
     * @param allowEveryAction grants every Action, so a test can describe a fully privileged caller
     *   without naming the Action a Schema Assignment change happens to demand.
     */
    private fun fixture(
        principal: PrincipalRef,
        callerIsOrgMember: Boolean,
        vararg allowedActions: Action,
        assigned: Boolean = true,
        allowEveryAction: Boolean = false,
    ): Fixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = this@SchemaAssignmentAudienceAuthorizationTest.ownerOrganizationId
            status = ExchangeStatus.INITIATED
            isDeleted = false
        }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)

        val membershipRepository = mock<OrganizationMembershipRepository>()
        whenever(membershipRepository.findActiveByUserAndOrg(any(), any())).thenReturn(
            if (callerIsOrgMember) mock() else null,
        )

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
                organizationMembershipRepository = membershipRepository,
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
        whenever(valueSetRepository.findByAssignment(assignmentId)).thenReturn(listOf(rootValueSet))
        whenever(valueSetRepository.save(any())).thenAnswer { it.getArgument<FieldValueSet>(0) }

        val selectionRepository = mock<FieldValueSelectionRepository>()
        whenever(selectionRepository.findByValue(any())).thenReturn(emptyList())

        return Fixture(
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

    // ── Read projection ───────────────────────────────────────────────────────

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

        // One projection is authoritative for both, so a caller can never learn from the description
        // of a binding that a value is being withheld from them.
        assertEquals(external?.fields?.map { it.fieldContractId }, external?.bindings?.map { it.fieldContractId })
        assertEquals(internal?.fields?.map { it.fieldContractId }, internal?.bindings?.map { it.fieldContractId })
    }

    // ── Write projection ──────────────────────────────────────────────────────

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

    // ── Writes to invisible bindings ──────────────────────────────────────────

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
            error.message?.contains("not part of the assigned schema") == true,
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

    // ── Assignment changes ────────────────────────────────────────────────────

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
