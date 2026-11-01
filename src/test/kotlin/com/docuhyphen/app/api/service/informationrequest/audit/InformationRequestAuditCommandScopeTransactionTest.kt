package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditReconciliation.assertReconciled
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditReconciliation.grantRequestShare
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCompletionGateService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAuditCommandScopeTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorization: AuthorizationService

    @Test
    fun `two respondents saving under the same client key each keep their own audit event`() {
        saveAsTwoRespondents(SHARED_KEY, SHARED_KEY)
    }

    @Test
    fun `two respondents saving under different client keys each keep their own audit event`() {
        saveAsTwoRespondents("first-save-key", "second-save-key")
    }

    @Test
    fun `a respondent retrying a save under its client key keeps exactly one audit event`() {
        val (fixture, _) = twoParties(CONTRIBUTOR)
        val services = runtime.build(fixture.requestId, centralAuthorization = authorization)
        val save = saveCommand(fixture, fixture.contributorUserId, SHARED_KEY, "First saved note")

        repeat(2) { QuarkusTransaction.requiringNew().run { services.responses.patch(save) } }

        assertReconciled(dataSource, fixture.requestId, SAVE_RESPONSE, RESPOND_EVENT, 1)
    }

    @Test
    fun `two managers changing the completion gate under the same client key each keep their own audit event`() {
        changeGateAsTwoManagers(SHARED_KEY, SHARED_KEY)
    }

    @Test
    fun `two managers changing the completion gate under different client keys each keep their own audit event`() {
        changeGateAsTwoManagers("first-gate-key", "second-gate-key")
    }

    @Test
    fun `a manager retrying a completion gate change under its client key keeps exactly one audit event`() {
        val (fixture, _) = twoParties(DECISION_MAKER)
        val gates = gateService(fixture)
        val change = gateCommand(fixture, fixture.contributorUserId, SHARED_KEY, false)

        repeat(2) { QuarkusTransaction.requiringNew().run { gates.change(change) } }

        assertReconciled(dataSource, fixture.requestId, CHANGE_COMPLETION_GATE, GATE_EVENT, 1)
    }

    @Test
    fun `one manager changing the completion gate of two requests under the same client key keeps an audit event on each`() {
        val (first, _) = twoParties(DECISION_MAKER)
        val secondParty = UUID.randomUUID()
        val second = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also {
                it.insertPartyForUser(secondParty, DECISION_MAKER, first.contributorUserId)
            }
        }
        grantRequestShare(dataSource, second, secondParty, DECISION_MAKER, first.contributorUserId)

        listOf(first, second).forEach { fixture ->
            val change = gateCommand(fixture, first.contributorUserId, SHARED_KEY, false)
            QuarkusTransaction.requiringNew().run { gateService(fixture).change(change) }
        }

        listOf(first, second).forEach { fixture ->
            assertReconciled(dataSource, fixture.requestId, CHANGE_COMPLETION_GATE, GATE_EVENT, 1)
        }
    }

    private fun saveAsTwoRespondents(firstKey: String, secondKey: String) {
        val (fixture, colleague) = twoParties(CONTRIBUTOR)
        val services = runtime.build(fixture.requestId, centralAuthorization = authorization)
        val first = saveCommand(fixture, fixture.contributorUserId, firstKey, "First saved note")
        QuarkusTransaction.requiringNew().run { services.responses.patch(first) }
        val second = saveCommand(fixture, colleague, secondKey, "Second saved note")
        QuarkusTransaction.requiringNew().run { services.responses.patch(second) }

        assertReconciled(dataSource, fixture.requestId, SAVE_RESPONSE, RESPOND_EVENT, 2)
    }

    private fun changeGateAsTwoManagers(firstKey: String, secondKey: String) {
        val (fixture, colleague) = twoParties(DECISION_MAKER)
        val gates = gateService(fixture)
        val first = gateCommand(fixture, fixture.contributorUserId, firstKey, false)
        QuarkusTransaction.requiringNew().run { gates.change(first) }
        val second = gateCommand(fixture, colleague, secondKey, true)
        QuarkusTransaction.requiringNew().run { gates.change(second) }

        assertReconciled(dataSource, fixture.requestId, CHANGE_COMPLETION_GATE, GATE_EVENT, 2)
    }

    private fun gateService(fixture: SubmissionRuntimeSqlFixture) = InformationRequestCompletionGateService(
        runtime.build(fixture.requestId, centralAuthorization = authorization).gate,
        requests,
        runtime.commandReceiptService,
        runtime.transitionHistory,
        runtime.clock,
    )

    private fun saveCommand(fixture: SubmissionRuntimeSqlFixture, user: UUID, key: String, note: String) =
        PatchInformationRequestResponsesCommand(
            requestId = fixture.requestId,
            access = access(user),
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew().call {
                    InformationRequestETag.responsesOf(requireNotNull(requests.findById(fixture.requestId)))
                },
            ),
            idempotencyKey = key,
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.documentRequirementId,
                    narrative = ResponseNarrativePatch.Set(note),
                ),
            ),
        )

    private fun gateCommand(fixture: SubmissionRuntimeSqlFixture, user: UUID, key: String, gatesClosure: Boolean) =
        ChangeInformationRequestCompletionGateCommand(
            requestId = fixture.requestId,
            gatesExchangeClosure = gatesClosure,
            access = access(user),
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew().call {
                    InformationRequestETag.aggregateOf(requireNotNull(requests.findById(fixture.requestId)))
                },
            ),
            idempotencyKey = key,
        )

    private fun access(user: UUID) =
        RequestAccessContext(PrincipalRef.user(user), AuthorizationContext(sessionRef = "session-$user"))

    private fun twoParties(role: String): Pair<SubmissionRuntimeSqlFixture, UUID> {
        val colleague = UUID.randomUUID()
        val colleagueParty = UUID.randomUUID()
        val managerParty = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also {
                it.insertActingParty(colleagueParty, role, colleague)
                if (role != CONTRIBUTOR) it.insertPartyForUser(managerParty, role, it.contributorUserId)
            }
        }
        val firstParty = if (role == CONTRIBUTOR) fixture.contributorPartyId else managerParty
        grantRequestShare(dataSource, fixture, firstParty, role, fixture.contributorUserId)
        grantRequestShare(dataSource, fixture, colleagueParty, role, colleague)
        return fixture to colleague
    }

    private companion object {
        const val SHARED_KEY = "shared-client-key"
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val DECISION_MAKER = "DECISION_MAKER"
        const val SAVE_RESPONSE = "SAVE_RESPONSE"
        const val CHANGE_COMPLETION_GATE = "CHANGE_COMPLETION_GATE"
        val RESPOND_EVENT = AuditEventType.INFORMATION_REQUEST_REQUIREMENT_RESPOND.key
        val GATE_EVENT = AuditEventType.INFORMATION_REQUEST_COMPLETION_GATE_CHANGE.key
    }
}
