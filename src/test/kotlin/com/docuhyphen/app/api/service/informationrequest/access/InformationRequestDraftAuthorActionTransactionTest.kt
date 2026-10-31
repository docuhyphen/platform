package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestAuthorActions
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.model.DraftAuthorActionScenario
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestTemplateInstantiationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.*
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyAssignmentService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.singleFieldConfiguration
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestDraftAuthorActionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var instantiation: InformationRequestTemplateInstantiationService

    @Inject
    lateinit var authorActions: InformationRequestAuthorActionService

    @Inject
    lateinit var lifecycle: InformationRequestLifecycleService

    @Inject
    lateinit var completionGates: InformationRequestCompletionGateService

    @Inject
    lateinit var followUps: InformationRequestFollowUpService

    @Inject
    lateinit var successors: InformationRequestSuccessorService

    @Inject
    lateinit var parties: InformationRequestPartyAssignmentService

    @Inject
    lateinit var appUsers: AppUserService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @ParameterizedTest(name = "{0} Exchange")
    @ValueSource(strings = ["ACCEPTED_STARTED", "INITIATED"])
    fun `the owner of a draft is offered supersession and follow-up scheduling, issuing once it names a Decision Maker, and no follow-up or outcome`(
        parentStatus: String,
    ) {
        val scenario = drafts(parentStatus)

        val unnamed = projectionOf(scenario)
        nameDecisionMaker(scenario)
        val named = workspaceOf(scenario, scenario.draftId)

        assertEquals(DRAFT_ACTIONS.copy(issue = false), unnamed)
        assertEquals(200, named.statusCode, named.asString())
        assertEquals("ACTIVE", named.jsonPath().getString("executionStanding.kind"))
        assertEquals(flags(DRAFT_ACTIONS), named.jsonPath().getMap<String, Boolean>("authorActions"))
    }

    @Test
    fun `a draft on a deleted Exchange offers no author action`() {
        val scenario = drafts()
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE exchange SET is_deleted = TRUE WHERE id = ?", scenario.exchangeId)
        }

        assertEquals(InformationRequestAuthorActions(), projectionOf(scenario))
    }

    @Test
    fun `the owner supersedes a draft with another draft of its Exchange, as the projection offers`() {
        val scenario = drafts()

        val superseded = supersede(scenario)
        val after = workspaceOf(scenario, scenario.draftId)

        assertEquals(InformationRequestState.SUPERSEDED, superseded.request.state)
        assertEquals(scenario.replacementId, superseded.request.supersededByRequestId)
        assertEquals(InformationRequestState.DRAFT, stateOf(scenario.replacementId))
        assertEquals(200, after.statusCode, after.asString())
        assertEquals(false, after.jsonPath().getBoolean("authorActions.supersede"))
        assertEquals(false, after.jsonPath().getBoolean("authorActions.scheduleFollowUps"))
    }

    @Test
    fun `the owner schedules a draft's recurrence, as the projection offers, and its first follow-up waits until the draft is issued`() {
        val scenario = drafts()

        val recurrence = schedule(scenario).recurrence
        val nextBeforeIssue =
            assertThrows<InformationRequestLifecycleException> { nextOccurrence(scenario, recurrence.id) }
        val supplementBeforeIssue = assertThrows<InformationRequestLifecycleException> { supplement(scenario) }
        nameDecisionMaker(scenario)
        issue(scenario)
        val next = nextOccurrence(scenario, recurrence.id)

        assertEquals(scenario.draftId, recurrence.originRequestId)
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, nextBeforeIssue.reasonCode)
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, supplementBeforeIssue.reasonCode)
        assertEquals(InformationRequestState.DRAFT, next.successor.state)
        assertEquals(scenario.exchangeId, next.successor.exchangeId)
    }

    @Test
    fun `a draft whose owner may not start new work refuses supersession and scheduling, as the standing its page applies says`() {
        val scenario = drafts()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE user_subscription_policy SET subscription_status = 'CANCELED' WHERE app_user_id = ?",
                scenario.ownerId,
            )
        }

        assertThrows<SubscriptionDenialException> { supersede(scenario) }
        assertThrows<SubscriptionDenialException> { schedule(scenario) }
        val workspace = workspaceOf(scenario, scenario.draftId)

        assertEquals(200, workspace.statusCode, workspace.asString())
        assertEquals("NEW_WORK_UNAVAILABLE", workspace.jsonPath().getString("executionStanding.kind"))
        assertEquals(InformationRequestState.DRAFT, stateOf(scenario.draftId))
    }

    @Test
    fun `a draft whose owner may not start new work can still be cancelled`() {
        val scenario = drafts()
        endPlan(scenario)

        val cancelled = cancel(scenario)

        assertEquals(InformationRequestState.CANCELLED, cancelled.request.state)
    }

    @Test
    fun `a draft whose owner may not start new work can stop holding back its Exchange's end but not start again`() {
        val scenario = drafts()
        endPlan(scenario)

        val released = changeCompletionGate(scenario, gatesExchangeClosure = false)

        assertEquals(false, released.request.gatesExchangeClosure)
        assertThrows<SubscriptionDenialException> { changeCompletionGate(scenario, gatesExchangeClosure = true) }
    }

    private fun endPlan(scenario: DraftAuthorActionScenario) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE user_subscription_policy SET subscription_status = 'CANCELED' WHERE app_user_id = ?",
                scenario.ownerId,
            )
        }
    }

    private fun cancel(scenario: DraftAuthorActionScenario): InformationRequestLifecycleResult =
        QuarkusTransaction.requiringNew().call {
            lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = scenario.draftId,
                    reasonCode = "no-longer-needed",
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.draftId)),
                    idempotencyKey = "cancel-draft-${UUID.randomUUID()}",
                ),
            )
        }

    private fun changeCompletionGate(
        scenario: DraftAuthorActionScenario,
        gatesExchangeClosure: Boolean,
    ): InformationRequestLifecycleResult =
        QuarkusTransaction.requiringNew().call {
            completionGates.change(
                ChangeInformationRequestCompletionGateCommand(
                    requestId = scenario.draftId,
                    gatesExchangeClosure = gatesExchangeClosure,
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.draftId)),
                    idempotencyKey = "completion-gate-${UUID.randomUUID()}",
                ),
            )
        }

    private fun drafts(parentStatus: String = "ACCEPTED_STARTED"): DraftAuthorActionScenario {
        val owner = store.insertOwner()
        subscribe(owner)
        val field = store.insertTextField(FIELD_KEY)
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE schema_version_id = ?",
                schemaVersionId,
            )
        }
        val template = store.insertDraft(owner, "draft-management")
        store.publish(template, singleFieldConfiguration(schemaVersionId, field))
        val exchangeId = insertExchange(owner, parentStatus)
        val create = { label: String ->
            QuarkusTransaction.requiringNew().call {
                instantiation.createFromTemplateVersion(
                    CreateInformationRequestFromTemplateVersionCommand(
                        templateVersionId = template.versionId,
                        exchangeId = exchangeId,
                        access = access(owner),
                        idempotencyKey = "create-$label-${UUID.randomUUID()}",
                    ),
                )
            }.request.id
        }
        return DraftAuthorActionScenario(owner, exchangeId, create("draft"), create("replacement"))
    }

    private fun projectionOf(scenario: DraftAuthorActionScenario): InformationRequestAuthorActions =
        QuarkusTransaction.requiringNew().call {
            authorActions.actionsFor(requireNotNull(requests.findById(scenario.draftId)), access(scenario))
        }

    private fun workspaceOf(scenario: DraftAuthorActionScenario, requestId: UUID): Response =
        InformationRequestSignedInCaller.signIn(appUsers, scenario.ownerId)
            .authorized()
            .get("/information-requests/$requestId/response-workspace")

    private fun supersede(scenario: DraftAuthorActionScenario): InformationRequestLifecycleResult =
        QuarkusTransaction.requiringNew().call {
            lifecycle.supersede(
                SupersedeInformationRequestCommand(
                    requestId = scenario.draftId,
                    supersededByRequestId = scenario.replacementId,
                    reasonCode = "requirements-changed",
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.draftId)),
                    idempotencyKey = "supersede-draft-${UUID.randomUUID()}",
                ),
            )
        }

    private fun schedule(scenario: DraftAuthorActionScenario): InformationRequestRecurrenceDefinition =
        QuarkusTransaction.requiringNew().call {
            followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = scenario.draftId,
                    intervalUnit = InformationRequestRecurrenceUnit.DAY,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(30)),
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.draftId)),
                    idempotencyKey = "schedule-draft-${UUID.randomUUID()}",
                ),
            )
        }

    private fun nextOccurrence(
        scenario: DraftAuthorActionScenario,
        recurrenceId: UUID
    ): InformationRequestSuccessorResult =
        QuarkusTransaction.requiringNew().call {
            followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    requestId = scenario.draftId,
                    recurrenceId = recurrenceId,
                    access = access(scenario),
                    idempotencyKey = "next-occurrence-${UUID.randomUUID()}",
                ),
            )
        }

    private fun supplement(scenario: DraftAuthorActionScenario): InformationRequestSuccessorResult =
        QuarkusTransaction.requiringNew().call {
            successors.create(
                CreateInformationRequestSuccessorCommand(
                    sourceRequestId = scenario.draftId,
                    kind = InformationRequestLineageKind.SUPPLEMENT,
                    reasonCode = "missing-item",
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.draftId)),
                    idempotencyKey = "supplement-draft-${UUID.randomUUID()}",
                ),
            )
        }

    private fun nameDecisionMaker(scenario: DraftAuthorActionScenario) {
        QuarkusTransaction.requiringNew().run {
            parties.assign(
                AssignInformationRequestPartyCommand(
                    requestId = scenario.draftId,
                    roleKey = InformationRequestShareRoleKey.DECISION_MAKER,
                    principal = PrincipalRef.user(scenario.ownerId),
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.partiesOf(requireNotNull(requests.findById(scenario.draftId))),
                    ),
                    idempotencyKey = "decision-maker-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun issue(scenario: DraftAuthorActionScenario): InformationRequestLifecycleResult =
        QuarkusTransaction.requiringNew().call {
            lifecycle.issue(
                IssueInformationRequestCommand(
                    requestId = scenario.draftId,
                    access = access(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.draftId)),
                    idempotencyKey = "issue-draft-${UUID.randomUUID()}",
                ),
            )
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requests.findById(requestId)))
        }

    private fun stateOf(requestId: UUID): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requests.findById(requestId)).state }

    private fun access(scenario: DraftAuthorActionScenario) = access(scenario.ownerId)

    private fun access(owner: UUID) =
        RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session"))

    private fun subscribe(owner: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner,
            )
        }
    }

    private fun insertExchange(owner: UUID, status: String): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                     description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process information', 'Please respond',
                        'Process collection', ?)
                """.trimIndent(),
                id,
                owner,
                owner,
                status,
            )
            execute(
                connection,
                """
                INSERT INTO share
                    (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                id,
                owner,
            )
        }
        return id
    }

    private fun flags(actions: InformationRequestAuthorActions): Map<String, Boolean> =
        mapOf(
            "issue" to actions.issue,
            "cancel" to actions.cancel,
            "supersede" to actions.supersede,
            "changeCompletionGate" to actions.changeCompletionGate,
            "manageParties" to actions.manageParties,
            "previewRecipients" to actions.previewRecipients,
            "manageClocks" to actions.manageClocks,
            "scheduleFollowUps" to actions.scheduleFollowUps,
            "createFollowUps" to actions.createFollowUps,
            "promoteFacts" to actions.promoteFacts,
            "revokeFacts" to actions.revokeFacts,
            "recordDecisions" to actions.recordDecisions,
            "issueAccessLinks" to actions.issueAccessLinks,
        )

    private companion object {
        const val FIELD_KEY = "recorded-note"

        val DRAFT_ACTIONS = InformationRequestAuthorActions(
            issue = true,
            cancel = true,
            supersede = true,
            changeCompletionGate = true,
            manageParties = true,
            previewRecipients = true,
            manageClocks = true,
            scheduleFollowUps = true,
            issueAccessLinks = true,
        )
    }
}
