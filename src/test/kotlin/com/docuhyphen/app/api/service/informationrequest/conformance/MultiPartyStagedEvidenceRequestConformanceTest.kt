package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.informationrequest.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestConnectorCall
import com.docuhyphen.app.api.model.informationrequest.InformationRequestConnectorContract
import com.docuhyphen.app.api.model.informationrequest.InformationRequestConnectorOutcome
import com.docuhyphen.app.api.model.informationrequest.InformationRequestConnectorResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestConnectorValue
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReconciliationOutcome
import com.docuhyphen.app.api.model.informationrequest.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.model.informationrequest.RequestInformationRequestConnectorExchangeCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConnector
import com.docuhyphen.app.api.service.informationrequest.InformationRequestDelegatedAuthorityService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.RevokeInformationRequestDelegatedAuthorityCommand
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class MultiPartyStagedEvidenceRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var authorizationService: AuthorizationService
    @Inject lateinit var delegatedAuthorities: InformationRequestDelegatedAuthorityService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `distinct parties act only within their assignments, delegated authority lapses on expiry and revocation, and stages submit separately`()
    {
        lateinit var people: Parties
        val request = support.fieldRequest(staged = true, prepare = { connection, prepared -> people = parties(connection, prepared) })
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val field = request.answers.requirementId
        val document = request.runtime.documentRequirementId
        val attestation = request.runtime.attestationRequirementId

        assertAllowed(support.contributor(request).principal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        assertDenied(support.contributor(request).principal, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, attestation)
        assertAllowed(support.attestor(request).principal, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, attestation)
        assertDenied(support.attestor(request).principal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        assertDenied(people.reviewer, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        assertDenied(people.delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        assertDenied(people.outsider, Action.INFORMATION_REQUEST_VIEW, null, request.requestId)
        assertNotEquals(people.subjectIdentityRefId, request.runtime.contributorUserId)

        val now = Instant.now()
        grant(request, people, field, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), "grant-expired")
        assertDenied(people.delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        val current = grant(request, people, field, null, null, "grant-current")
        assertAllowed(people.delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        assertDenied(people.delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, document)

        support.answerField(services, request, "Answered by the delegate", "delegate-answer", RequestAccessContext(people.delegate, AuthorizationContext(sessionRef = "delegate-session")))
        QuarkusTransaction.requiringNew().run {
            delegatedAuthorities.revoke(
                RevokeInformationRequestDelegatedAuthorityCommand(
                    requestId = request.requestId,
                    authorityId = current,
                    reason = "authority ended",
                    access = support.owner(request),
                    precondition = CommandPrecondition.ExpectedRevision(partiesETag(request)),
                    idempotencyKey = "revoke-current",
                ),
            )
        }
        assertDenied(people.delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, field)
        assertThrows(ForbiddenException::class.java) {
            support.answerField(services, request, "Late delegate answer", "late-delegate", RequestAccessContext(people.delegate, AuthorizationContext(sessionRef = "delegate-session")))
        }

        val firstStage = support.submit(services, request, "submit-record-stage", RECORD_STAGE)
        assertEquals(RECORD_STAGE, firstStage.submission.submissionPackage.stageKey)
        assertEquals(setOf(field, document), firstStage.submission.items.map { it.informationRequestRequirementId }.toSet())
        assertNotEquals(InformationRequestState.CLOSED, firstStage.request.state)
        val locked = assertThrows(InformationRequestLifecycleException::class.java) {
            support.answerField(services, request, "Changed after the stage", "after-stage")
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, locked.reasonCode)

        support.assent(services, request, "assent-confirmation-stage", CONFIRMATION_STAGE)
        val secondStage = support.submit(services, request, "submit-confirmation-stage", CONFIRMATION_STAGE)
        assertEquals(CONFIRMATION_STAGE, secondStage.submission.submissionPackage.stageKey)
        assertEquals(listOf(attestation), secondStage.submission.items.map { it.informationRequestRequirementId })
        assertEquals(InformationRequestState.CLOSED, secondStage.request.state)
    }

    @Test
    fun `an external verification of a contributed answer arrives untrusted, stays with the requesting side, and is decided by the reviewer`()
    {
        lateinit var people: Parties
        val request = support.fieldRequest(staged = true, prepare = { connection, prepared -> people = parties(connection, prepared) })
        val verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofMinutes(10))
        val connector = VerificationConnector(
            InformationRequestConnectorOutcome.Completed(
                "verification-7",
                InformationRequestConnectorResult(
                    source = "record-verification-service",
                    confidence = InformationRequestSourceConfidence.VERIFIED,
                    verifiedAt = verifiedAt,
                    expiresAt = verifiedAt.plus(Duration.ofDays(30)),
                    provenanceReference = "verification-run-7",
                    values = listOf(InformationRequestConnectorValue("recorded-value", FieldValueType.SHORT_TEXT, JsonPrimitive("Answered by the contributor"))),
                ),
            ),
        )
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService, connectors = listOf(connector))
        val field = request.answers.requirementId
        support.answerField(services, request, "Answered by the contributor", "contributor-answer")
        val owner = support.owner(request)
        val reviewer = RequestAccessContext(people.reviewer, AuthorizationContext(sessionRef = "reviewer-session"))

        val exchange = QuarkusTransaction.requiringNew().call {
            services.connectorExchanges.request(
                RequestInformationRequestConnectorExchangeCommand(request.requestId, field, connector.contract.key, "record-17", owner, "verify-answer"),
            )
        }
        assertEquals(InformationRequestConnectorExchangeState.COMPLETED, services.connectorWorker.process(exchange.id))
        assertEquals("record-17", connector.lookupReferences.single())

        listOf(support.contributor(request), support.attestor(request)).forEach { respondingSide ->
            assertThrows(ForbiddenException::class.java) {
                QuarkusTransaction.requiringNew().call { services.importedValues.values(request.requestId, respondingSide) }
            }
        }
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.importedValues.values(request.requestId, RequestAccessContext(people.outsider, AuthorizationContext(sessionRef = "outsider")))
            }
        }
        val imported = QuarkusTransaction.requiringNew().call { services.importedValues.values(request.requestId, reviewer) }.single().value
        assertEquals(InformationRequestImportedValueSource.CONNECTOR, imported.sourceKind)
        assertEquals(verifiedAt, imported.verifiedAt?.toInstant())
        assertEquals("verification-run-7", imported.provenanceReference)

        val outcome = QuarkusTransaction.requiringNew().call {
            services.importedValues.reconcile(ReconcileInformationRequestImportedValuesCommand(request.requestId, reviewer, "reconcile-answer"))
        }.single()
        assertEquals(InformationRequestReconciliationOutcome.MATCHES, outcome.outcome)
        val decided = QuarkusTransaction.requiringNew().call {
            services.importedValues.decide(
                DecideInformationRequestImportedValueCommand(request.requestId, imported.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "record confirmed", reviewer, "accept-verification"),
            )
        }
        assertEquals(InformationRequestImportedValueDecisionKind.ACCEPTED, decided.decision?.decision)
        dataSource.connection.use { connection ->
            assertEquals(
                "Answered by the contributor",
                queryString(connection, "SELECT text_value FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?", request.requestId),
            )
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM information_request_accepted_fact WHERE source_information_request_id = ?", request.requestId))
        }
    }

    private class VerificationConnector(private val completed: InformationRequestConnectorOutcome) : InformationRequestConnector
    {
        val lookupReferences = mutableListOf<String?>()

        override val contract = InformationRequestConnectorContract(
            key = "record-verification",
            kind = InformationRequestConnectorKind.EXTERNAL_VERIFICATION,
            contractVersion = 1,
            resultKeys = setOf("recorded-value"),
            maximumResultAge = Duration.ofDays(1),
        )

        override fun request(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome
        {
            lookupReferences.add(call.lookupReference)
            return completed
        }

        override fun poll(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome = completed
    }

    private fun grant(request: ConformanceRequest, people: Parties, requirementId: UUID, effective: Instant?, expires: Instant?, key: String): UUID =
        QuarkusTransaction.requiringNew().call {
            delegatedAuthorities.grant(
                GrantInformationRequestDelegatedAuthorityCommand(
                    requestId = request.requestId,
                    assignedPartyId = request.runtime.contributorPartyId,
                    delegatePrincipal = people.delegate,
                    requirementId = requirementId,
                    authorityInstrumentRef = "instrument-$key",
                    effectiveAt = effective?.let(Timestamp::from),
                    expiresAt = expires?.let(Timestamp::from),
                    access = support.owner(request),
                    precondition = CommandPrecondition.ExpectedRevision(partiesETag(request)),
                    idempotencyKey = key,
                ),
            ).authority.id
        }

    private fun partiesETag(request: ConformanceRequest): String =
        QuarkusTransaction.requiringNew().call { InformationRequestETag.partiesOf(requireNotNull(requestRepository.findById(request.requestId))) }

    private fun decide(principal: PrincipalRef, action: Action, requirementId: UUID?, requestId: UUID? = null): Decision =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                principal,
                action,
                requirementId?.let(ResourceRef::informationRequestRequirement) ?: ResourceRef.informationRequest(requireNotNull(requestId)),
                AuthorizationContext(sessionRef = "decision-session"),
            )
        }

    private fun assertAllowed(principal: PrincipalRef, action: Action, requirementId: UUID?, requestId: UUID? = null)
    {
        val decision = decide(principal, action, requirementId, requestId)
        assertTrue(decision !is Decision.Deny, "$action for ${principal.id} was denied: $decision")
    }

    private fun assertDenied(principal: PrincipalRef, action: Action, requirementId: UUID?, requestId: UUID? = null)
    {
        val decision = decide(principal, action, requirementId, requestId)
        assertTrue(decision is Decision.Deny, "$action for ${principal.id} was allowed")
    }

    private fun parties(connection: java.sql.Connection, request: ConformanceRequest): Parties
    {
        val subjectId = UUID.randomUUID()
        request.runtime.insertSubject(subjectId, UUID.randomUUID())
        val reviewerId = UUID.randomUUID()
        val reviewerPartyId = UUID.randomUUID()
        request.runtime.insertActingParty(reviewerPartyId, "REVIEWER", reviewerId)
        val reviewerShare = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'REVIEWER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            reviewerShare,
            request.requestId,
            reviewerId,
        )
        execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", reviewerShare, reviewerPartyId)
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            request.runtime.exchangeId,
            request.runtime.template.userId,
        )
        val delegateId = UUID.randomUUID()
        val outsiderId = UUID.randomUUID()
        request.runtime.insertUser(delegateId)
        request.runtime.insertUser(outsiderId)
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            request.requestId,
            delegateId,
        )
        return Parties(subjectId, PrincipalRef.user(reviewerId), PrincipalRef.user(delegateId), PrincipalRef.user(outsiderId))
    }

    private data class Parties(
        val subjectIdentityRefId: UUID,
        val reviewer: PrincipalRef,
        val delegate: PrincipalRef,
        val outsider: PrincipalRef,
    )

    private companion object
    {
        const val RECORD_STAGE = "record-stage"
        const val CONFIRMATION_STAGE = "confirmation-stage"
    }
}
