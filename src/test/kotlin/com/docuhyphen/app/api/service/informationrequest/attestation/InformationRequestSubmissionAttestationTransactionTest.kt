package com.docuhyphen.app.api.service.informationrequest.attestation

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionAttestationResult
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionIncompleteException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubmissionAttestationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Test
    fun `only an assertion Requirement takes a submission attestation`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)

        val refusal = refused(
            services,
            command(
                fixture,
                InformationRequestAttestationDecision.ASSENTED
            ).copy(requirementId = fixture.documentRequirementId)
        )

        assertEquals(InformationRequestErrorCatalog.ATTESTATION_NOT_AN_ASSERTION, refusal.reasonCode)
    }

    @Test
    fun `a refusal states a reason within its bound and an assent states none`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)

        listOf(
            command(fixture, InformationRequestAttestationDecision.REFUSED),
            command(fixture, InformationRequestAttestationDecision.REFUSED, refusalReason = "   "),
            command(fixture, InformationRequestAttestationDecision.REFUSED, refusalReason = "x".repeat(2_001)),
            command(
                fixture,
                InformationRequestAttestationDecision.ASSENTED,
                refusalReason = "An assent that gives a reason"
            ),
        ).forEach { invalid ->
            assertEquals(
                InformationRequestErrorCatalog.ATTESTATION_REFUSAL_REASON_INVALID,
                refused(services, invalid).reasonCode
            )
        }
        assertTrue(recordedAttestations(fixture).isEmpty())
    }

    @Test
    fun `a reasoned refusal is recorded and blocks submission until the same party later assents`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)

        val refusal = recorded(
            services,
            command(
                fixture,
                InformationRequestAttestationDecision.REFUSED,
                refusalReason = "The recorded items are incomplete"
            ),
        )
        assertEquals(InformationRequestAttestationState.REFUSED, refusal.evaluation?.evaluation?.state)
        assertEquals("The recorded items are incomplete", refusal.attestation.refusalReason)
        assertEquals(
            listOf(fixture.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_REFUSED),
            blockedSubmission(services, fixture, "refused-submission"),
        )

        val assent = recorded(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestAttestationState.SATISFIED, assent.evaluation?.evaluation?.state)
        val submitted = QuarkusTransaction.requiringNew()
            .call { services.submissions.submit(submit(fixture, "assented-submission")) }
        assertEquals(listOf(assent.attestation.id), submitted.submission.attestations.map { it.id })
    }

    @Test
    fun `assertions made in role order refuse a later role until every earlier role has assented`() {
        val preparerPartyId = UUID.randomUUID()
        val preparerUserId = UUID.randomUUID()
        val fixture = fixture(
            configure = {
                policy(
                    it,
                    ordering = "ROLE_SEQUENCE",
                    minimumAssents = 2,
                    roles = listOf("PREPARER", "ATTESTOR")
                )
            },
            prepare = { it.insertActingParty(preparerPartyId, "PREPARER", preparerUserId) },
        )
        val services = runtime.build(fixture.requestId)
        val waiting = attestationStanding(services, fixture, user(fixture.attestorUserId))
        assertTrue(waiting.callerCanAttest)
        assertEquals(listOf(InformationRequestContributorRole.PREPARER), waiting.waitingForRoles)
        assertEquals(
            emptyList<InformationRequestContributorRole>(),
            attestationStanding(services, fixture, user(preparerUserId)).waitingForRoles
        )

        val early = refused(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestErrorCatalog.ATTESTATION_ORDER_UNMET, early.reasonCode)

        val preparer = recorded(
            services,
            command(fixture, InformationRequestAttestationDecision.ASSENTED, access = user(preparerUserId))
        )
        assertEquals(InformationRequestAttestationState.PENDING, preparer.evaluation?.evaluation?.state)
        assertEquals(
            emptyList<InformationRequestContributorRole>(),
            attestationStanding(services, fixture, user(fixture.attestorUserId)).waitingForRoles
        )
        val attestor = recorded(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestAttestationState.SATISFIED, attestor.evaluation?.evaluation?.state)
        assertEquals(
            listOf(InformationRequestContributorRole.PREPARER, InformationRequestContributorRole.ATTESTOR),
            attestor.evaluation?.evaluation?.counted?.map { it.partyRole },
        )
    }

    @Test
    fun `an assertion that needs a stronger proof refuses a caller who proved less`() {
        val fixture = fixture(configure = { policy(it, strength = "MULTI_FACTOR") })
        val services = runtime.build(fixture.requestId)

        val weak = refused(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestErrorCatalog.ATTESTATION_STRENGTH_INSUFFICIENT, weak.reasonCode)

        val strong = recorded(
            services,
            command(
                fixture,
                InformationRequestAttestationDecision.ASSENTED,
                access = RequestAccessContext(
                    PrincipalRef.user(fixture.attestorUserId),
                    AuthorizationContext(mfaSatisfied = true, sessionRef = "attestor-session"),
                ),
            ),
        )
        assertEquals(InformationRequestAuthenticationStrength.MULTI_FACTOR, strong.attestation.authenticationStrength)
        assertEquals(InformationRequestAttestationState.SATISFIED, strong.evaluation?.evaluation?.state)
    }

    @Test
    fun `only a party the policy names attests and a caller acting for two named parties names the one it acts for`() {
        val preparerPartyId = UUID.randomUUID()
        val fixture = fixture(
            configure = { policy(it, minimumAssents = 2, roles = listOf("ATTESTOR", "PREPARER")) },
            prepare = { it.insertPartyForUser(preparerPartyId, "PREPARER", it.attestorUserId) },
        )
        val services = runtime.build(fixture.requestId)

        val ineligible = refused(
            services,
            command(fixture, InformationRequestAttestationDecision.ASSENTED, access = contributor(fixture))
        )
        assertEquals(InformationRequestErrorCatalog.ATTESTATION_PARTY_NOT_ELIGIBLE, ineligible.reasonCode)
        val ambiguous = refused(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestErrorCatalog.ATTESTATION_PARTY_AMBIGUOUS, ambiguous.reasonCode)
        assertEquals(
            setOf(
                fixture.attestorPartyId to InformationRequestContributorRole.ATTESTOR,
                preparerPartyId to InformationRequestContributorRole.PREPARER,
            ),
            attestationStanding(services, fixture, user(fixture.attestorUserId))
                .attestingParties.map { it.party.id to it.role }.toSet(),
        )
        assertTrue(attestationStanding(services, fixture, contributor(fixture)).attestingParties.isEmpty())

        val named = recorded(
            services,
            command(fixture, InformationRequestAttestationDecision.ASSENTED, partyId = preparerPartyId)
        )
        assertEquals(preparerPartyId, named.attestation.partyId)
        assertEquals(InformationRequestContributorRole.PREPARER, named.attestation.partyRole)
    }

    @Test
    fun `an external signature reference is required or refused as the assertion's policy states`() {
        val unaccepted = fixture()
        val refusedReference = refused(
            runtime.build(unaccepted.requestId),
            command(unaccepted, InformationRequestAttestationDecision.ASSENTED, reference = "signature-ref-1"),
        )
        assertEquals(
            InformationRequestErrorCatalog.ATTESTATION_SIGNATURE_REFERENCE_NOT_ACCEPTED,
            refusedReference.reasonCode
        )

        val required = fixture(configure = { policy(it, signatureReference = "REQUIRED") })
        val services = runtime.build(required.requestId)
        val missing = refused(services, command(required, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestErrorCatalog.ATTESTATION_SIGNATURE_REFERENCE_REQUIRED, missing.reasonCode)

        val referenced = recorded(
            services,
            command(required, InformationRequestAttestationDecision.ASSENTED, reference = "signature-ref-1"),
        )
        assertEquals("signature-ref-1", referenced.attestation.externalSignatureReference)
        assertEquals(InformationRequestAttestationState.SATISFIED, referenced.evaluation?.evaluation?.state)
    }

    @Test
    fun `an assertion needing a quorum is satisfied only once enough named parties assent`() {
        val secondPartyId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        val fixture = fixture(
            configure = { policy(it, minimumAssents = 2) },
            prepare = { it.insertActingParty(secondPartyId, "ATTESTOR", secondUserId) },
        )
        val services = runtime.build(fixture.requestId)

        val first = recorded(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestAttestationState.PENDING, first.evaluation?.evaluation?.state)
        assertEquals(1, first.evaluation?.evaluation?.assentCount)
        assertEquals(2, first.evaluation?.evaluation?.requiredAssentCount)
        assertEquals(
            listOf(fixture.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING),
            blockedSubmission(services, fixture, "below-quorum"),
        )

        val second = recorded(
            services,
            command(fixture, InformationRequestAttestationDecision.ASSENTED, access = user(secondUserId))
        )
        assertEquals(InformationRequestAttestationState.SATISFIED, second.evaluation?.evaluation?.state)
        val submitted =
            QuarkusTransaction.requiringNew().call { services.submissions.submit(submit(fixture, "quorum-reached")) }
        assertEquals(
            setOf(first.attestation.id, second.attestation.id),
            submitted.submission.attestations.map { it.id }.toSet()
        )
    }

    @Test
    fun `an assent past the validity its policy states no longer counts`() {
        val fixture = fixture(configure = { policy(it, validityHours = 1) })
        val services = runtime.build(fixture.requestId)

        val lapsed = QuarkusTransaction.requiringNew().call {
            attestationsRecordedAt(services, Clock.offset(Clock.systemUTC(), Duration.ofHours(-2)))
                .record(command(fixture, InformationRequestAttestationDecision.ASSENTED))
        }
        assertTrue(requireNotNull(lapsed.attestation.expiresAt).toInstant().isBefore(Instant.now()))
        assertEquals(InformationRequestAttestationState.PENDING, lapsed.evaluation?.evaluation?.state)
        assertEquals(1, lapsed.evaluation?.evaluation?.superseded)
        assertEquals(
            listOf(fixture.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING),
            blockedSubmission(services, fixture, "lapsed-assent"),
        )

        val current = recorded(services, command(fixture, InformationRequestAttestationDecision.ASSENTED))
        assertEquals(InformationRequestAttestationState.SATISFIED, current.evaluation?.evaluation?.state)
    }

    private fun fixture(
        configure: (SubmissionRuntimeSqlFixture) -> Unit = {},
        prepare: (SubmissionRuntimeSqlFixture) -> Unit = {},
    ): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, beforePublish = configure).also(prepare)
        }

    @Suppress("LongParameterList")
    private fun policy(
        fixture: SubmissionRuntimeSqlFixture,
        ordering: String = "ANY_ORDER",
        minimumAssents: Int = 1,
        strength: String = "VERIFIED_CONTACT",
        validityHours: Int? = null,
        signatureReference: String = "NOT_ACCEPTED",
        roles: List<String> = listOf("ATTESTOR"),
    ) {
        val policyId = UUID.randomUUID()
        fixture.template.insertAttestationPolicy(
            policyId,
            fixture.attestationBindingId,
            fixture.template.versionId,
            ordering,
            minimumAssents,
            strength,
            validityHours,
            signatureReference,
        )
        roles.forEachIndexed { index, role ->
            fixture.template.insertAttestationRole(policyId, fixture.template.versionId, role, index + 1)
        }
    }

    private fun currentETag(fixture: SubmissionRuntimeSqlFixture): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun user(userId: UUID) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "party-session"))

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) = user(fixture.contributorUserId)

    @Suppress("LongParameterList")
    private fun command(
        fixture: SubmissionRuntimeSqlFixture,
        decision: InformationRequestAttestationDecision,
        refusalReason: String? = null,
        reference: String? = null,
        partyId: UUID? = null,
        access: RequestAccessContext = user(fixture.attestorUserId),
    ) = RecordInformationRequestSubmissionAttestationCommand(
        requestId = fixture.requestId,
        requirementId = fixture.attestationRequirementId,
        decision = decision,
        refusalReason = refusalReason,
        externalSignatureReference = reference,
        partyId = partyId,
        access = access,
        precondition = CommandPrecondition.ExpectedRevision(currentETag(fixture)),
        idempotencyKey = "attestation-${UUID.randomUUID()}",
    )

    private fun submit(fixture: SubmissionRuntimeSqlFixture, key: String) =
        SubmitInformationRequestPackageCommand(
            requestId = fixture.requestId,
            access = contributor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(currentETag(fixture)),
            idempotencyKey = key,
        )

    private fun attestationStanding(
        services: InformationRequestRuntimeServices,
        fixture: SubmissionRuntimeSqlFixture,
        access: RequestAccessContext,
    ) = QuarkusTransaction.requiringNew().call { services.submissionQueries.preview(fixture.requestId, null, access) }
        .attestations
        .single()

    private fun recorded(
        services: InformationRequestRuntimeServices,
        command: RecordInformationRequestSubmissionAttestationCommand,
    ): InformationRequestSubmissionAttestationResult =
        QuarkusTransaction.requiringNew().call { services.attestations.record(command) }

    private fun refused(
        services: InformationRequestRuntimeServices,
        command: RecordInformationRequestSubmissionAttestationCommand,
    ): InformationRequestLifecycleException =
        assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.attestations.record(command) }
        }

    private fun blockedSubmission(
        services: InformationRequestRuntimeServices,
        fixture: SubmissionRuntimeSqlFixture,
        key: String,
    ) = assertThrows(InformationRequestSubmissionIncompleteException::class.java)
    {
        QuarkusTransaction.requiringNew().call { services.submissions.submit(submit(fixture, key)) }
    }.readiness.problems.map { it.requirementId to it.code }

    private fun recordedAttestations(fixture: SubmissionRuntimeSqlFixture) =
        QuarkusTransaction.requiringNew().call { runtime.attestationRepository.findForRequest(fixture.requestId) }

    private fun attestationsRecordedAt(services: InformationRequestRuntimeServices, clock: Clock) =
        InformationRequestSubmissionAttestationService(
            gate = services.gate,
            requirementRepository = runtime.requirementRepository,
            revisionRepository = runtime.revisionRepository,
            occurrenceRepository = runtime.occurrenceRepository,
            templateVersionRepository = runtime.templateVersionRepository,
            templateRequirementRepository = runtime.templateRequirementRepository,
            bindingRepository = runtime.bindingRepository,
            attestationRepository = runtime.attestationRepository,
            policyLoader = runtime.policyLoader,
            evaluationService = runtime.attestationEvaluationService,
            contentCollector = runtime.contentCollector,
            stages = runtime.stages,
            lockService = runtime.lockService,
            requirementContext = runtime.requirementContext,
            commandReceiptService = runtime.commandReceiptService,
            transitionHistory = mock(),
            clock = clock,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
            conditionVisibility = InformationRequestConditionVisibility(
                runtime.conditionEvaluationService,
                runtime.bindingRepository
            ),
        )
}
