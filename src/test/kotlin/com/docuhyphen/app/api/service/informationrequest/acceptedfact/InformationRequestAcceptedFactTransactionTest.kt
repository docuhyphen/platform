package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConfidence
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConflictState
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RevokeInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAcceptedFactTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    @Test
    fun `promotion refuses an unstated reuse policy basis`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.acceptedFacts.promote(
                    promote(fixture, InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, "missing-basis")
                        .copy(policyBasisKey = null),
                )
            }
        }
    }

    @Test
    fun `promotion refuses evidence outside the exact supporting package`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.acceptedFacts.promote(
                    promote(fixture, InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, "unrelated-evidence")
                        .copy(evidenceVersionIds = listOf(UUID.randomUUID())),
                )
            }
        }
    }

    @Test
    fun `a promoted fact is offered to a later request of the same subject for reconfirmation until it is revoked`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        val exchangeValuesBefore = exchangeFieldValues(fixture)

        val command = promote(fixture, InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, "promote-once")
        val promoted = QuarkusTransaction.requiringNew().call { services.acceptedFacts.promote(command) }
        assertEquals(InformationRequestAcceptedFactConfidence.DECLARED, promoted.fact.confidence)
        assertEquals(InformationRequestAcceptedFactConflictState.NONE, promoted.fact.conflictState)
        assertEquals(InformationRequestAcceptedFactFreshness.CURRENT, promoted.freshness)
        assertEquals("\"Recorded answer\"", promoted.fact.canonicalValue)
        assertEquals(fixture.requestId, promoted.fact.sourceInformationRequestId)
        val replayed = QuarkusTransaction.requiringNew().call { services.acceptedFacts.promote(command) }
        assertEquals(promoted.fact.id, replayed.fact.id)
        assertThrows(com.docuhyphen.app.api.service.command.CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call { services.acceptedFacts.promote(command.copy(purposeKey = "profile.other")) }
        }

        val later = runtime.build(fixture.laterRequestId, denies = respondentDenials(fixture))
        val offered = QuarkusTransaction.requiringNew().call { later.acceptedFactQueries.offers(fixture.laterRequestId, contributor(fixture)) }
        assertEquals(listOf(fixture.laterFieldRequirementId), offered.map { it.requirementId })
        assertEquals(promoted.fact.id, offered.single().fact.fact.id)
        assertTrue(offered.single().reconfirmationRequired)
        assertEquals(exchangeValuesBefore, exchangeFieldValues(fixture))

        QuarkusTransaction.requiringNew().run {
            services.acceptedFacts.revoke(
                RevokeInformationRequestAcceptedFactCommand(
                    requestId = fixture.requestId,
                    factId = promoted.fact.id,
                    reasonCode = "no-longer-accurate",
                    access = owner(fixture),
                    idempotencyKey = "revoke-once",
                ),
            )
        }
        assertTrue(QuarkusTransaction.requiringNew().call { later.acceptedFactQueries.offers(fixture.laterRequestId, owner(fixture)) }.isEmpty())
        val again = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.acceptedFacts.revoke(
                    RevokeInformationRequestAcceptedFactCommand(
                        requestId = fixture.requestId,
                        factId = promoted.fact.id,
                        reasonCode = "no-longer-accurate",
                        access = owner(fixture),
                        idempotencyKey = "revoke-again",
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_REVOKED, again.reasonCode)
        QuarkusTransaction.requiringNew().run {
            val mutations = transitionRepository.findForRequest(fixture.requestId).map { it.mutation }
            assertTrue(mutations.containsAll(listOf(InformationRequestMutation.PROMOTE_FACT, InformationRequestMutation.REVOKE_FACT)))
        }
    }

    @Test
    fun `a fact the requesting side keeps to itself is not offered to responding parties`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        QuarkusTransaction.requiringNew().run {
            services.acceptedFacts.promote(promote(fixture, InformationRequestAcceptedFactVisibility.REQUESTING_SIDE, "promote-private"))
        }
        val later = runtime.build(fixture.laterRequestId, denies = respondentDenials(fixture))
        assertTrue(QuarkusTransaction.requiringNew().call { later.acceptedFactQueries.offers(fixture.laterRequestId, contributor(fixture)) }.isEmpty())
        assertEquals(1, QuarkusTransaction.requiringNew().call { later.acceptedFactQueries.offers(fixture.laterRequestId, owner(fixture)) }.size)
    }

    @Test
    fun `a differing current fact is recorded as a conflict and superseding it retires it from reuse`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        val existing = insertExistingFact(fixture, "\"Different answer\"")

        val conflicting = QuarkusTransaction.requiringNew().call {
            services.acceptedFacts.promote(promote(fixture, InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, "promote-conflicting"))
        }
        assertEquals(InformationRequestAcceptedFactConflictState.CONFLICTING, conflicting.fact.conflictState)
        assertEquals(existing, conflicting.fact.conflictingFactId)

        val superseding = QuarkusTransaction.requiringNew().call {
            services.acceptedFacts.promote(
                promote(fixture, InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, "promote-superseding")
                    .copy(supersedesFactId = existing),
            )
        }
        assertEquals(InformationRequestAcceptedFactConflictState.NONE, superseding.fact.conflictState)
        val later = runtime.build(fixture.laterRequestId, denies = respondentDenials(fixture))
        val offered = QuarkusTransaction.requiringNew().call { later.acceptedFactQueries.offers(fixture.laterRequestId, owner(fixture)) }
        assertEquals(superseding.fact.id, offered.single().fact.fact.id)
        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.acceptedFacts.promote(
                    promote(fixture, InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, "promote-stale-supersession")
                        .copy(supersedesFactId = existing),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_SUPERSESSION_INVALID, refused.reasonCode)
    }

    @Test
    fun `a business decision chain stays independent of the request and names the exact prior decision`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        val stateBefore = QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(fixture.requestId)).state }
        val original = QuarkusTransaction.requiringNew().call {
            services.businessDecisions.record(decision(fixture, InformationRequestBusinessDecisionKind.ORIGINAL, null, "decide-once"))
        }.decision
        assertEquals(1, original.decisionRevision)
        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            assertEquals(stateBefore, request.state)
            assertNull(request.satisfiedAt)
        }

        val duplicate = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.businessDecisions.record(decision(fixture, InformationRequestBusinessDecisionKind.ORIGINAL, null, "decide-twice"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.BUSINESS_DECISION_PRIOR_INVALID, duplicate.reasonCode)
        val appeal = QuarkusTransaction.requiringNew().call {
            services.businessDecisions.record(decision(fixture, InformationRequestBusinessDecisionKind.APPEAL, original.id, "appeal"))
        }.decision
        assertEquals(2, appeal.decisionRevision)
        assertEquals(original.id, appeal.priorDecisionId)
        val stalePrior = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.businessDecisions.record(decision(fixture, InformationRequestBusinessDecisionKind.RECONSIDERATION, original.id, "stale"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.BUSINESS_DECISION_PRIOR_INVALID, stalePrior.reasonCode)
        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.businessDecisions.record(
                    decision(fixture, InformationRequestBusinessDecisionKind.RECONSIDERATION, appeal.id, "future")
                        .copy(decidedAt = Instant.now().plus(Duration.ofDays(1))),
                )
            }
        }
        assertEquals(
            listOf(original.id, appeal.id),
            QuarkusTransaction.requiringNew().call { services.businessDecisions.decisions(fixture.requestId, owner(fixture)) }.map { it.id },
        )
    }

    private fun fixture(): FactFixture =
        dataSource.connection.use { connection ->
            lateinit var answers: FieldAnswerSqlFixture
            val runtimeFixture = SubmissionRuntimeSqlFixture(
                connection,
                beforePublish = { runtime -> answers = FieldAnswerSqlFixture(connection, runtime.template, reusePurpose = "profile.reuse") },
            )
            answers.materialize(runtimeFixture)
            val facts = com.docuhyphen.app.api.migration.FactSqlFixture(connection, runtimeFixture, answers)
            val laterRequestId = UUID.randomUUID()
            val laterRequirementId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state, gates_exchange_closure,
                     aggregate_revision, party_revision, created_at, updated_at, issued_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, now(), now(), now())
                """.trimIndent(),
                laterRequestId,
                runtimeFixture.exchangeId,
                runtimeFixture.template.versionId,
                runtimeFixture.template.organizationId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                laterRequestId,
                facts.subjectId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_requirement
                    (id, information_request_id, source_template_version_id, source_template_requirement_id,
                     source_template_binding_id, occurrence_path, created_at)
                VALUES (?, ?, ?, ?, ?, 'root', now())
                """.trimIndent(),
                laterRequirementId,
                laterRequestId,
                runtimeFixture.template.versionId,
                answers.templateRequirementId,
                answers.bindingId,
            )
            FactFixture(runtimeFixture, facts, answers, laterRequestId, laterRequirementId)
        }

    private fun insertExistingFact(fixture: FactFixture, canonical: String): UUID =
        dataSource.connection.use { connection ->
            val id = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request_accepted_fact
                    (id, owner_type, owner_organization_id, subject_identity_ref_id, purpose_key, policy_basis_key, field_definition_id, value_type,
                     canonical_value, source_information_request_id, source_package_id, source_submission_item_id,
                     source_requirement_id, source_response_id, source_response_revision, source_field_value_revision_id,
                     visibility, confidence, valid_from, conflict_state, promoted_by_principal_kind, promoted_by_principal_id,
                     promoted_at)
                VALUES (?, 'ORGANIZATION', ?, ?, 'profile.reuse', 'policy.reuse', ?, 'SHORT_TEXT', ?, ?, ?, ?, ?, ?, 2, ?,
                        'RESPONDING_PARTIES', 'DECLARED', now() - INTERVAL '1 day', 'NONE', 'USER', ?, now() - INTERVAL '1 day')
                """.trimIndent(),
                id,
                fixture.runtime.template.organizationId,
                fixture.facts.subjectId,
                fixture.answers.fieldDefinitionId,
                canonical,
                fixture.requestId,
                fixture.facts.packageId,
                fixture.facts.itemId,
                fixture.answers.requirementId,
                fixture.answers.responseId,
                fixture.answers.fieldValueRevisionId,
                fixture.runtime.template.userId,
            )
            id
        }

    private fun exchangeFieldValues(fixture: FactFixture): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM field_value WHERE resource_type = 'EXCHANGE' AND resource_id = ?",
                fixture.runtime.exchangeId,
            )
        }

    private fun promote(fixture: FactFixture, visibility: InformationRequestAcceptedFactVisibility, key: String) =
        PromoteInformationRequestAcceptedFactCommand(
            requestId = fixture.requestId,
            packageId = fixture.facts.packageId,
            submissionItemId = fixture.facts.itemId,
            purposeKey = "profile.reuse",
            policyBasisKey = "policy.reuse",
            visibility = visibility,
            validTo = Instant.now().plus(Duration.ofDays(365)),
            access = owner(fixture),
            idempotencyKey = key,
        )

    private fun decision(fixture: FactFixture, kind: InformationRequestBusinessDecisionKind, prior: UUID?, key: String) =
        RecordInformationRequestBusinessDecisionCommand(
            requestId = fixture.requestId,
            owningProcessKey = "process.outcome",
            outcomeCode = "outcome.recorded",
            reasonReference = "reference-1",
            kind = kind,
            priorDecisionId = prior,
            decidedAt = Instant.now().minus(Duration.ofMinutes(5)),
            access = owner(fixture),
            idempotencyKey = key,
        )

    private fun respondentDenials(fixture: FactFixture): (PrincipalRef, Action) -> Boolean = { principal, action ->
        principal.id != fixture.runtime.template.userId && action in setOf(
            Action.INFORMATION_REQUEST_PROMOTE_FACT,
            Action.INFORMATION_REQUEST_RECORD_DECISION,
        )
    }

    private fun owner(fixture: FactFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(fixture: FactFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    internal data class FactFixture(
        val runtime: SubmissionRuntimeSqlFixture,
        val facts: com.docuhyphen.app.api.migration.FactSqlFixture,
        val answers: FieldAnswerSqlFixture,
        val laterRequestId: UUID,
        val laterFieldRequirementId: UUID,
    )
    {
        val requestId: UUID get() = runtime.requestId
    }
}
