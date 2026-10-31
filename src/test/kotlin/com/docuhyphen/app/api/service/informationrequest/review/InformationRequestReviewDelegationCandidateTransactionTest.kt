package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.InformationRequestReviewDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestReviewDto
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewAssignmentChange
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewDelegationScenario
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewDelegationCandidateTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    private val support by lazy {
        InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository)
    }

    @Test
    fun `a reviewer who does not manage the request tells the other reviewers apart when delegating`() {
        val setup = setUp()
        val assigned = support.assign(setup.services, setup.fixture, setup.review).assignment!!

        val own = read(setup, support.reviewer(setup.fixture)).assignments.single { it.id == assigned.id }

        assertEquals(
            mapOf(
                setup.firstColleagueParty to email(setup.firstColleagueUser),
                setup.secondColleagueParty to email(setup.secondColleagueUser)
            ),
            own.delegationCandidates.associate { it.partyId to it.label },
        )
    }

    @Test
    fun `delegation candidates leave out the assignment's own reviewer, reviewers already on its stage, and parties that do not review`() {
        val setup = setUp()
        val assigned = support.assign(setup.services, setup.fixture, setup.review).assignment!!
        support.assign(setup.services, setup.fixture, setup.review, partyId = setup.firstColleagueParty)

        val candidates = read(setup, support.reviewer(setup.fixture)).assignments
            .single { it.id == assigned.id }
            .delegationCandidates
            .map { it.partyId }

        assertEquals(listOf(setup.secondColleagueParty), candidates)
        val responding = setOf(setup.fixture.runtime.contributorPartyId, setup.fixture.runtime.attestorPartyId)
        assertTrue(candidates.none { it in responding })
    }

    @Test
    fun `a reviewer is offered no delegation candidates on an assignment they do not hold`() {
        val setup = setUp()
        support.assign(setup.services, setup.fixture, setup.review)
        val colleagueAssignment = support.assign(
            setup.services,
            setup.fixture,
            setup.review,
            partyId = setup.firstColleagueParty
        ).assignment!!

        val other = read(setup, support.reviewer(setup.fixture)).assignments.single { it.id == colleagueAssignment.id }

        assertEquals(emptyList<Any>(), other.delegationCandidates)
    }

    @Test
    fun `a manager is offered the candidates of every assignment that can still be delegated`() {
        val setup = setUp()
        val reviewerAssignment = support.assign(setup.services, setup.fixture, setup.review).assignment!!
        val colleagueAssignment = support.assign(
            setup.services,
            setup.fixture,
            setup.review,
            partyId = setup.firstColleagueParty
        ).assignment!!

        val assignments = read(setup, support.owner(setup.fixture)).assignments.associateBy { it.id }

        assertEquals(
            listOf(setup.secondColleagueParty),
            assignments.getValue(reviewerAssignment.id).delegationCandidates.map { it.partyId },
        )
        assertEquals(
            listOf(setup.secondColleagueParty),
            assignments.getValue(colleagueAssignment.id).delegationCandidates.map { it.partyId },
        )
    }

    @Test
    fun `a decided assignment offers no delegation candidates`() {
        val setup = setUp()
        val assigned = support.assign(setup.services, setup.fixture, setup.review)
        support.record(
            setup.services,
            setup.fixture,
            assigned,
            support.draft(setup.services, setup.fixture, assigned, InformationRequestReviewOutcome.SATISFIED),
            "decide-before-delegation",
        )

        val own = read(setup, support.reviewer(setup.fixture)).assignments.single { it.id == assigned.assignment!!.id }

        assertEquals(emptyList<Any>(), own.delegationCandidates)
    }

    @Test
    fun `delegating to an offered candidate hands the assignment to that reviewer`() {
        val setup = setUp()
        val assigned = support.assign(setup.services, setup.fixture, setup.review).assignment!!
        val candidate = read(setup, support.reviewer(setup.fixture)).assignments
            .single { it.id == assigned.id }
            .delegationCandidates
            .first()

        val delegated = QuarkusTransaction.requiringNew().call {
            setup.services.reviewAssignments.change(
                support.changeCommand(
                    setup.fixture,
                    assigned,
                    InformationRequestReviewAssignmentChange.DELEGATION,
                    support.reviewer(setup.fixture),
                    delegatePartyId = candidate.partyId,
                ),
            )
        }

        assertEquals(candidate.partyId, delegated.assignment!!.reviewerPartyId)
    }

    private fun read(setup: ReviewDelegationScenario, access: RequestAccessContext): InformationRequestReviewDto =
        QuarkusTransaction.requiringNew().call {
            InformationRequestReviewDtoMapper.toDto(
                setup.services.reviewQueries.review(setup.fixture.requestId, setup.review.id, access),
                access.principal,
            )
        }

    private fun email(userId: UUID): String = "submission-party-${userId.toString().take(8)}@process.test"

    private fun setUp(): ReviewDelegationScenario {
        val firstColleagueParty = UUID.randomUUID()
        val firstColleagueUser = UUID.randomUUID()
        val secondColleagueParty = UUID.randomUUID()
        val secondColleagueUser = UUID.randomUUID()
        val fixture = support.fixture(prepare = { runtime ->
            runtime.insertActingParty(firstColleagueParty, "REVIEWER", firstColleagueUser)
            runtime.insertActingParty(secondColleagueParty, "REVIEWER", secondColleagueUser)
        })
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(
                fixture,
                setOf(fixture.reviewerUserId, firstColleagueUser, secondColleagueUser)
            ),
        )
        support.submitWhole(services, fixture)
        return ReviewDelegationScenario(
            fixture = fixture,
            services = services,
            review = support.reviewsOf(fixture).single(),
            firstColleagueParty = firstColleagueParty,
            firstColleagueUser = firstColleagueUser,
            secondColleagueParty = secondColleagueParty,
            secondColleagueUser = secondColleagueUser,
        )
    }

}
