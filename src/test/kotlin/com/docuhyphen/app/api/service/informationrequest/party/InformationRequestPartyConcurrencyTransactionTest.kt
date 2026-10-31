package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityRefRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.RequestTemplatePostgreSQLResource
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.informationrequest.party.model.PartyConcurrencyFixture
import com.docuhyphen.app.api.service.informationrequest.response.*
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestPartyConcurrencyTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeRepository: ExchangeRepository

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

    @Test
    fun `reassignment lets an opposing response save finish before taking the parent lock`()
    {
        val fixture = createFixture()
        val gate = ReassignmentParentLockGate(exchangeLookup)
        val services = services(fixture, gate.lookup)
        val pool = Executors.newFixedThreadPool(2) { task -> Thread(task, "information-request-concurrency") }
        try
        {
            val reassignment = pool.submit(Callable { services.reassign() })
            assertTrue(gate.reassignmentReachedParent.await(10, TimeUnit.SECONDS))

            val save = pool.submit(Callable { services.saveResponse() })
            try
            {
                assertEquals(2L, save.get(10, TimeUnit.SECONDS))
            }
            finally
            {
                gate.releaseReassignment.countDown()
            }

            assertEquals("reassigned", reassignment.get(10, TimeUnit.SECONDS))
        }
        finally
        {
            gate.releaseReassignment.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `reassignment observes parent termination after waiting for the parent lock`()
    {
        val fixture = createFixture()
        val gate = ReassignmentParentLockGate(exchangeLookup)
        val services = services(fixture, gate.lookup)
        val pool = Executors.newFixedThreadPool(2) { task -> Thread(task, "information-request-concurrency") }
        try
        {
            val reassignment = pool.submit(Callable { services.reassign() })
            assertTrue(gate.reassignmentReachedParent.await(10, TimeUnit.SECONDS))

            val termination = pool.submit(Callable { services.terminateParent() })
            try
            {
                assertEquals("terminated", termination.get(10, TimeUnit.SECONDS))
            }
            finally
            {
                gate.releaseReassignment.countDown()
            }

            assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, reassignment.get(10, TimeUnit.SECONDS))
        }
        finally
        {
            gate.releaseReassignment.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `reassignment preserves response content and original principal provenance`()
    {
        val fixture = createFixture()
        val services = services(fixture, exchangeLookup)

        assertEquals(2L, services.saveResponse())
        val before = services.responseSnapshot()
        val originalId = before.id
        val originalRevision = before.responseRevision
        assertEquals("reassigned", services.reassign())
        val after = services.responseSnapshot()

        assertEquals(originalId, after.id)
        assertEquals(fixture.requestId, after.informationRequestId)
        assertEquals(fixture.requirementId, after.informationRequestRequirementId)
        assertEquals("Current process details", after.narrative)
        assertEquals(PrincipalKind.USER, after.recordedByPrincipalKind)
        assertEquals(fixture.userId, after.recordedByPrincipalId)
        assertEquals(originalRevision, after.responseRevision)
    }

    private fun services(
        fixture: PartyConcurrencyFixture,
        gatedExchangeLookup: ExchangeLookupService,
    ): ConcurrentServices
    {
        val authorizationService = mock<AuthorizationService>()
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())

        val party = InformationRequestParty().apply {
            id = fixture.partyId
            informationRequestId = fixture.requestId
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.USER
            principalId = fixture.userId
        }
        val partyRepository = mock<InformationRequestPartyRepository>()
        whenever(partyRepository.findByIdForUpdate(fixture.partyId)).thenReturn(party)
        whenever(partyRepository.update(any())).thenAnswer { it.getArgument(0) }

        val shareService = mock<ShareService>()
        whenever(
            shareService.grantAdditionalRoleKeyWithPrincipalProvenance(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(Share())

        val history = mock<InformationRequestTransitionHistoryService>()
        val receipts = CommandReceiptService(ConcurrentCommandReceiptStore(), CommandReceiptScope())
        val partySupport = InformationRequestPartyCommandSupport(
            requestRepository,
            partyRepository,
            mock<SubjectIdentityRefRepository>(),
            mock<ExchangeRecipientService>(),
            shareService,
            authorizationService,
            receipts,
            gatedExchangeLookup,
            history,
            mock(),
        )
        val partyService = InformationRequestPartyRevocationService(
            partyRepository,
            partySupport,
            InformationRequestPartyCapacity(
                mock<InformationRequestExecutionGrantService>(),
                mock<InformationRequestExecutionUsageReservationService>(),
            ),
            mock<InformationRequestMutationGate>(),
            mock<InformationRequestPartyEligibilityService>(),
            mock<InformationRequestTrustedRecipientEligibilityService>(),
            mock<InformationRequestDelegatedAuthorityService>(),
            mock<InformationRequestBootstrapShareLinkService>(),
            history,
            mock(),
        )

        val requirement = InformationRequestRequirement().apply {
            id = fixture.requirementId
            informationRequestId = fixture.requestId
            sourceTemplateVersionId = fixture.versionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "root"
        }
        val revision = InformationRequestRequirementRevision().apply {
            informationRequestRequirementId = requirement.id
            informationRequestId = fixture.requestId
            sourceTemplateVersionId = fixture.versionId
            sourceTemplateRequirementId = requirement.sourceTemplateRequirementId
            sourceTemplateBindingId = requirement.sourceTemplateBindingId
            occurrencePath = requirement.occurrencePath
            configurationHashSha256 = "0".repeat(64)
        }
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(fixture.requestId)).thenReturn(listOf(requirement))
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        whenever(occurrenceRepository.findForRequest(fixture.requestId)).thenReturn(emptyList())
        val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        whenever(revisionRepository.findCurrentForRequest(fixture.requestId)).thenReturn(listOf(revision))
        val responses = mutableListOf<InformationRequestResponse>()
        val responseStore = mock<InformationRequestResponseStore>()
        whenever(responseStore.findCurrentForRequest(fixture.requestId)).thenAnswer { responses.toList() }
        whenever(responseStore.findAllForRequest(fixture.requestId)).thenAnswer { responses.toList() }
        whenever(responseStore.findCurrentForUpdate(fixture.requestId, requirement.id)).thenReturn(null)
        whenever(responseStore.save(any())).thenAnswer {
            it.getArgument<InformationRequestResponse>(0).also(responses::add)
        }
        val conditionEvaluationService = mock<InformationRequestConditionEvaluationService>()
        whenever(conditionEvaluationService.evaluate(fixture.requestId)).thenReturn(emptyList())
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val activeResponses = InformationRequestActiveResponseReader(
            requirementRepository,
            occurrenceRepository,
            bindingRepository,
            responseStore,
            conditionEvaluationService,
        )
        val responseService = InformationRequestResponseDraftService(
            requestRepository,
            requirementRepository,
            occurrenceRepository,
            revisionRepository,
            responseStore,
            InformationRequestResponseMutationGuard(
                requestRepository,
                exchangeLookup,
                InformationRequestContinuationGuard(
                    mock<InformationRequestExecutionGrantService>(),
                    mock<InformationRequestEntitlementGuard>(),
                ),
            ),
            InformationRequestResponsePatchValidator(
                mock<InformationRequestTemplateBindingDispositionRepository>(),
                responseStore
            ),
            InformationRequestResponseFieldWriter(
                mock<SchemaAssignmentService>(),
                mock<FieldsReadFacade>(),
                bindingRepository,
                activeResponses
            ),
            activeResponses,
            authorizationService,
            receipts,
            history,
            mock<InformationRequestStructuredResponseValidationService>(),
            mock<InformationRequestStructuredResponseValidationContextLoader>(),
            mock<InformationRequestSubmissionLockService>(),
            InformationRequestHiddenResponsePolicyEnforcer(
                conditionEvaluationService,
                mock<InformationRequestTemplateRequirementBindingRepository>(),
                revisionRepository,
                responseStore,
                mock<SchemaAssignmentService>(),
                authorizationService,
                mock<InformationRequestHiddenResponseArchiver>(),
                mock<FieldsReadFacade>(),
                mock<InformationRequestHiddenResponseFiles>(),
                mock<InformationRequestEvidenceCollectionService>(),
                mock<InformationRequestSubmissionLockService>(),
                InformationRequestConditionVisibility(mock(), mock()),
            ),
            mock<InformationRequestConditionEffectRecorder>(),
            InformationRequestNoAuthAccessDoubles.current(),
        )
        val parentLifecycle = InformationRequestParentLifecycleService(
            requestRepository,
            mock<RequestAccessSessionService>(),
            history,
            shareService,
            mock(),
            mock(),
            mock(),
            mock(),
        )
        return ConcurrentServices(fixture, partyService, responseService, parentLifecycle, responses)
    }

    private inner class ConcurrentServices(
        private val fixture: PartyConcurrencyFixture,
        private val partyService: InformationRequestPartyRevocationService,
        private val responseService: InformationRequestResponseDraftService,
        private val parentLifecycle: InformationRequestParentLifecycleService,
        private val responses: List<InformationRequestResponse>,
    )
    {
        private val actor = PrincipalRef.user(fixture.userId)
        private val access = RequestAccessContext(actor, AuthorizationContext.ANONYMOUS)

        fun reassign(): String = try
        {
            QuarkusTransaction.requiringNew().call {
                partyService.reassign(
                    ReassignInformationRequestPartyCommand(
                        requestId = fixture.requestId,
                        partyId = fixture.partyId,
                        principal = PrincipalRef.user(UUID.randomUUID()),
                        access = access,
                        precondition = CommandPrecondition.Unconditioned,
                        idempotencyKey = UUID.randomUUID().toString(),
                    ),
                )
                "reassigned"
            }
        }
        catch (exception: InformationRequestLifecycleException)
        {
            exception.reasonCode
        }

        fun saveResponse(): Long = QuarkusTransaction.requiringNew().call {
            responseService.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.requestId,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = UUID.randomUUID().toString(),
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirementId,
                            narrative = ResponseNarrativePatch.Set("Current process details"),
                        ),
                    ),
                ),
            )
            assertEquals(1, responses.size)
            requestRepository.findById(fixture.requestId)!!.responseRevision
        }

        fun responseSnapshot(): InformationRequestResponse = responses.single()

        fun terminateParent(): String = QuarkusTransaction.requiringNew().call {
            exchangeRepository.findByIdForUpdate(fixture.exchangeId)
                ?: error("Exchange not found")
            exchangeRepository.updateStatus(fixture.exchangeId, ExchangeStatus.RESCINDED)
            parentLifecycle.apply(
                fixture.exchangeId,
                ExchangeStatus.RESCINDED,
                false,
                InformationRequestAuditActor.of(access)
            )
            "terminated"
        }
    }

    private class ReassignmentParentLockGate(realLookup: ExchangeLookupService)
    {
        val reassignmentReachedParent = CountDownLatch(1)
        val releaseReassignment = CountDownLatch(1)
        val lookup = mock<ExchangeLookupService>()

        init
        {
            whenever(lookup.lockExchangeForUpdate(any())).thenAnswer { invocation ->
                reassignmentReachedParent.countDown()
                check(releaseReassignment.await(20, TimeUnit.SECONDS)) { "Reassignment parent lock was not released" }
                realLookup.lockExchangeForUpdate(invocation.getArgument(0))
            }
        }
    }

    private fun createFixture(): PartyConcurrencyFixture
    {
        val fixture = PartyConcurrencyFixture()
        dataSource.connection.use { connection ->
            insertOrganization(connection, fixture)
            insertUser(connection, fixture)
            insertExchange(connection, fixture)
            insertDefinition(connection, fixture)
            insertVersion(connection, fixture)
            insertRequest(connection, fixture)
        }
        return fixture
    }

    private fun insertOrganization(connection: Connection, fixture: PartyConcurrencyFixture)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, 'Party concurrency organization', ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.organizationId)
            statement.setString(2, "REG-${fixture.organizationId.toString().take(8)}")
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertUser(connection: Connection, fixture: PartyConcurrencyFixture)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.userId)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.setString(3, "party-concurrency-${fixture.userId.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertExchange(connection: Connection, fixture: PartyConcurrencyFixture)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                    'Party concurrency collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.exchangeId)
            statement.setObject(2, fixture.organizationId)
            statement.setObject(3, fixture.userId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(connection: Connection, fixture: PartyConcurrencyFixture)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                 status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Party concurrency pattern',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.definitionId)
            statement.setObject(2, fixture.organizationId)
            statement.setString(3, "party-concurrency-${fixture.definitionId.toString().take(8)}")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertVersion(connection: Connection, fixture: PartyConcurrencyFixture)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, created_at,
                 published_at, published_by_principal_kind, published_by_principal_id)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?, 'USER', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.versionId)
            statement.setObject(2, fixture.definitionId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.setObject(5, fixture.userId)
            statement.executeUpdate()
        }
    }

    private fun insertRequest(connection: Connection, fixture: PartyConcurrencyFixture)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                 state, gates_exchange_closure, aggregate_revision, party_revision,
                 response_revision, issued_at, created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, 1, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.requestId)
            statement.setObject(2, fixture.exchangeId)
            statement.setObject(3, fixture.versionId)
            statement.setObject(4, fixture.organizationId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, now)
            statement.executeUpdate()
        }
    }
}

private class ConcurrentCommandReceiptStore : CommandReceiptStore
{
    private val receipts = ConcurrentHashMap<String, CommandReceipt>()

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? = receipts[key(request)]

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts[key(receipt)] = receipt
        return receipt
    }

    private fun key(request: CommandReceiptRequest): String =
        "${request.resource.type}|${request.resource.id}|${request.operation}|${request.actor.kind}|" +
                "${request.actor.id}|${request.idempotencyKey}"

    private fun key(receipt: CommandReceipt): String =
        "${receipt.resourceType}|${receipt.resourceId}|${receipt.operationName}|${receipt.actorKind}|" +
                "${receipt.actorId}|${receipt.idempotencyKey}"
}
