package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestCarryForwardDto
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.InformationRequestCarryForwardResource
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCarryForwardDisclosureFixture.Companion.EARLIER_NARRATIVE
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCarryForwardDisclosureFixture.Companion.EARLIER_VALUE
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.CarryForwardDisclosureScenario
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCarryForwardPlanner
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestCarryForwardDisclosureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var responseRepository: InformationRequestResponseRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    private val fixture by lazy {
        InformationRequestCarryForwardDisclosureFixture(dataSource, runtime, requestRepository, authorizationService)
    }

    @Test
    fun `a respondent who may read the source and successor Requirements is offered the earlier answer while files and assent are asked afresh`() {
        val scenario = fixture.scenario()

        val contributorOffers = offers(scenario, fixture.access(scenario.contributor))
        val attestorOffers = offers(scenario, fixture.access(scenario.attestor))

        val offered = contributorOffers.single { it.decision == InformationRequestCarryForwardDecision.OFFERED }
        assertEquals(scenario.successorFieldRequirementId, offered.requirementId)
        assertEquals(EARLIER_NARRATIVE, offered.priorNarrative)
        assertEquals(JsonPrimitive(EARLIER_VALUE), offered.priorFieldValue)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, offered.priorDisposition)
        assertTrue(
            contributorOffers.any { it.reasonCode == InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION },
            "$contributorOffers",
        )
        assertTrue(
            attestorOffers.any { it.reasonCode == InformationRequestCarryForwardPlanner.ATTESTATION_REQUIRES_FRESH_ASSENT },
            "$attestorOffers",
        )
        (contributorOffers + attestorOffers).filter { it.decision == InformationRequestCarryForwardDecision.INVALIDATED }
            .forEach {
                assertEquals(null, it.priorNarrative)
                assertEquals(null, it.priorFieldValue)
            }
    }

    @Test
    fun `a successor contributor who is not assigned the source Requirement is not shown its earlier answer and can still respond`() {
        val scenario = fixture.scenario()
        fixture.assignSuccessorContributor(scenario, scenario.attestor)
        assertAllowed(fixture.decision(scenario.attestor, scenario.successorFieldRequirementId, verified = false))
        assertDenied(
            fixture.decision(scenario.attestor, scenario.sourceFieldRequirementId, verified = false),
            InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED,
        )

        val offers = offers(scenario, fixture.access(scenario.attestor))

        assertNoEarlierAnswer(offers)
        assertTrue(offers.none { it.requirementId == scenario.successorFieldRequirementId }, "$offers")
        respond(scenario, scenario.attestor, "Fresh successor narrative")
        assertEquals(
            listOf("Fresh successor narrative"),
            QuarkusTransaction.requiringNew().call {
                responseRepository.findAllForRequest(scenario.successorId)
                    .filter { it.informationRequestRequirementId == scenario.successorFieldRequirementId }
                    .map { it.narrative }
            },
        )
    }

    @Test
    fun `a protected source answer is offered only while the reader is recently verified on either access path`() {
        val scenario = fixture.scenario(protectedSource = true)
        assertAllowed(fixture.decision(scenario.contributor, scenario.successorFieldRequirementId, verified = false))
        assertDenied(
            fixture.decision(scenario.contributor, scenario.sourceFieldRequirementId, verified = false),
            InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
        )

        val unverified = offers(scenario, fixture.access(scenario.contributor, verified = false))
        val verified = offers(scenario, fixture.access(scenario.contributor, verified = true))
        val unverifiedParticipant = offers(scenario, fixture.access(scenario.participant, verified = false))
        val resourceResponse = InformationRequestCarryForwardResource(
            scenario.services.lineageQueries,
            accessFactory(fixture.access(scenario.contributor, verified = false)),
        ).list(scenario.successorId.toString())

        assertNoEarlierAnswer(unverified)
        assertNoEarlierAnswer(unverifiedParticipant)
        assertEquals(
            EARLIER_NARRATIVE,
            verified.single { it.decision == InformationRequestCarryForwardDecision.OFFERED }.priorNarrative
        )
        assertEquals(200, resourceResponse.status)
        @Suppress("UNCHECKED_CAST")
        assertNoEarlierAnswer((resourceResponse.entity as Array<InformationRequestCarryForwardDto>).toList())
    }

    @Test
    fun `withdrawing the source Share withholds the earlier answer while the successor Share remains`() {
        val scenario = fixture.scenario()
        val before = offers(scenario, fixture.access(scenario.contributor))

        fixture.withdrawSourceAccess(scenario, scenario.contributor)

        assertAllowed(fixture.decision(scenario.contributor, scenario.successorFieldRequirementId, verified = false))
        assertTrue(
            fixture.decision(
                scenario.contributor,
                scenario.sourceFieldRequirementId,
                verified = false
            ) is Decision.Deny
        )
        assertEquals(
            EARLIER_NARRATIVE,
            before.single { it.decision == InformationRequestCarryForwardDecision.OFFERED }.priorNarrative
        )
        assertNoEarlierAnswer(offers(scenario, fixture.access(scenario.contributor)))
    }

    @Test
    fun `leaving the group that held the source Requirement withholds its earlier answer from a directly assigned successor contributor`() {
        val scenario = fixture.scenario()
        fixture.assignSuccessorContributor(scenario, scenario.groupMember)
        val before = offers(scenario, fixture.access(scenario.groupMember))

        fixture.removeGroupMember(scenario)

        assertAllowed(fixture.decision(scenario.groupMember, scenario.successorFieldRequirementId, verified = false))
        assertTrue(
            fixture.decision(
                scenario.groupMember,
                scenario.sourceFieldRequirementId,
                verified = false
            ) is Decision.Deny
        )
        assertEquals(
            EARLIER_NARRATIVE,
            before.single { it.decision == InformationRequestCarryForwardDecision.OFFERED }.priorNarrative
        )
        assertNoEarlierAnswer(offers(scenario, fixture.access(scenario.groupMember)))
    }

    private fun offers(
        scenario: CarryForwardDisclosureScenario,
        access: RequestAccessContext
    ): List<InformationRequestCarryForwardDto> =
        QuarkusTransaction.requiringNew().call {
            scenario.services.lineageQueries.carryForwards(scenario.successorId, access)
                .map(InformationRequestLineageDtoMapper::toDto)
        }

    private fun respond(scenario: CarryForwardDisclosureScenario, principal: PrincipalRef, narrative: String) {
        QuarkusTransaction.requiringNew().run {
            scenario.services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.successorId,
                    access = fixture.access(principal),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(scenario.successorId))),
                    ),
                    idempotencyKey = "successor-response-${UUID.randomUUID()}",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = scenario.successorFieldRequirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            narrative = ResponseNarrativePatch.Set(narrative),
                        ),
                    ),
                ),
            )
        }
    }

    private fun accessFactory(access: RequestAccessContext) =
        mock<InformationRequestAccessContextFactory> { on { currentAuthenticated() } doReturn access }

    private fun assertNoEarlierAnswer(offers: List<InformationRequestCarryForwardDto>) {
        val serialized = Json.encodeToString(ListSerializer(InformationRequestCarryForwardDto.serializer()), offers)
        assertFalse(serialized.contains(EARLIER_NARRATIVE), serialized)
        assertFalse(serialized.contains(EARLIER_VALUE), serialized)
        assertTrue(offers.none { it.decision == InformationRequestCarryForwardDecision.OFFERED }, serialized)
    }

    private fun assertAllowed(decision: Decision) {
        assertTrue(decision is Decision.Allow, "$decision")
    }

    private fun assertDenied(decision: Decision, reasonCode: String) {
        assertEquals(reasonCode, (decision as? Decision.Deny)?.reasonCode, "$decision")
    }
}
