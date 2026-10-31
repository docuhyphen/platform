package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.review.model.AssignmentChangeSetup
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewAssignmentChangeTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a recusal states its reason, is the assigned reviewer's alone, and leaves the stage to another reviewer`() {
        val setup = setUp()
        val assigned = support.assign(setup.services, setup.fixture, setup.review)
        val assignment = assigned.assignment!!

        assertThrows(ForbiddenException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assignment, RECUSAL, support.owner(setup.fixture)))
        }
        assertThrows(ForbiddenException::class.java) {
            change(
                setup,
                support.changeCommand(setup.fixture, assignment, RECUSAL, support.access(setup.colleagueUser))
            )
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    RECUSAL,
                    support.reviewer(setup.fixture),
                    reasonCode = " "
                )
            )
        }
        val recused = change(
            setup,
            support.changeCommand(setup.fixture, assignment, RECUSAL, support.reviewer(setup.fixture), key = "recuse")
        )
        assertEquals(assignment.id, recused.assignment!!.id)
        assertEquals(InformationRequestReviewAssignmentState.RECUSED, recused.assignment.state)
        assertEquals("reviewer.unavailable", recused.assignment.changeReasonCode)
        assertEquals(InformationRequestReviewState.IN_REVIEW, recused.review.state)

        val worksheet = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.reviewDecisions.saveDraft(
                    SaveInformationRequestReviewDraftCommand(
                        requestId = setup.fixture.requestId,
                        reviewId = setup.review.id,
                        assignmentId = assignment.id,
                        patches = listOf(
                            InformationRequestReviewDraftPatch(
                                setup.documentItem,
                                InformationRequestReviewOutcome.SATISFIED
                            )
                        ),
                        access = support.reviewer(setup.fixture),
                        precondition = CommandPrecondition.ExpectedRevision(requireNotNull(assigned.draftETag)),
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE, worksheet.reasonCode)
        val again = assertThrows(InformationRequestLifecycleException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    RECUSAL,
                    support.reviewer(setup.fixture),
                    key = "recuse-again"
                )
            )
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE, again.reasonCode)
        assertTrue(
            QuarkusTransaction.requiringNew()
                .call { setup.services.reviewQueries.queue(support.reviewer(setup.fixture)) }.isEmpty()
        )

        val replacement = support.assign(setup.services, setup.fixture, setup.review, partyId = setup.colleagueParty)
        val colleague = support.access(setup.colleagueUser)
        val settled = support.record(
            setup.services,
            setup.fixture,
            replacement,
            support.draft(
                setup.services,
                setup.fixture,
                replacement,
                InformationRequestReviewOutcome.SATISFIED,
                reviewerAccess = colleague
            ),
            "accept-after-recusal",
            reviewerAccess = colleague,
        )
        assertEquals(InformationRequestReviewState.SATISFIED, settled.review.state)
    }

    @Test
    fun `a delegation states its reason and hands the stage to another eligible reviewer`() {
        val setup = setUp()
        val assignment = support.assign(setup.services, setup.fixture, setup.review).assignment!!
        val reviewer = support.reviewer(setup.fixture)

        assertThrows(InformationRequestCommandRequestException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    DELEGATION,
                    reviewer,
                    reasonCode = null,
                    delegatePartyId = setup.colleagueParty
                )
            )
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    DELEGATION,
                    reviewer,
                    reasonCode = "  ",
                    delegatePartyId = setup.colleagueParty
                )
            )
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assignment, DELEGATION, reviewer))
        }
        val ineligible = assertThrows(InformationRequestLifecycleException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    DELEGATION,
                    reviewer,
                    delegatePartyId = setup.fixture.runtime.contributorPartyId
                )
            )
        }
        assertEquals(InformationRequestErrorCatalog.REVIEWER_NOT_ELIGIBLE, ineligible.reasonCode)
        val alreadyAssigned = assertThrows(InformationRequestLifecycleException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    DELEGATION,
                    reviewer,
                    delegatePartyId = setup.fixture.reviewerPartyId
                )
            )
        }
        assertEquals(InformationRequestErrorCatalog.REVIEWER_ALREADY_ASSIGNED, alreadyAssigned.reasonCode)
        assertThrows(ForbiddenException::class.java) {
            change(
                setup,
                support.changeCommand(
                    setup.fixture,
                    assignment,
                    DELEGATION,
                    support.access(setup.colleagueUser),
                    delegatePartyId = setup.colleagueParty
                ),
            )
        }

        val command = support.changeCommand(
            setup.fixture,
            assignment,
            DELEGATION,
            reviewer,
            delegatePartyId = setup.colleagueParty,
            key = "delegate"
        )
        val delegated = change(setup, command)
        val delegate = delegated.assignment!!
        assertEquals(setup.colleagueParty, delegate.reviewerPartyId)
        assertEquals(assignment.id, delegate.delegatedFromAssignmentId)
        assertEquals(InformationRequestReviewAssignmentState.ACTIVE, delegate.state)
        val handedOver = support.assignmentOf(assignment.id)
        assertEquals(InformationRequestReviewAssignmentState.DELEGATED, handedOver.state)
        assertEquals("reviewer.unavailable", handedOver.changeReasonCode)
        assertEquals(delegate.id, change(setup, command).assignment!!.id)

        val returned = change(
            setup,
            support.changeCommand(
                setup.fixture,
                delegate,
                DELEGATION,
                support.owner(setup.fixture),
                delegatePartyId = setup.fixture.reviewerPartyId
            ),
        )
        assertEquals(setup.fixture.reviewerPartyId, returned.assignment!!.reviewerPartyId)
        assertEquals(delegate.id, returned.assignment.delegatedFromAssignmentId)
    }

    @Test
    fun `only an administrator revokes an assignment and a revocation states its reason`() {
        val setup = setUp()
        val assignment = support.assign(setup.services, setup.fixture, setup.review).assignment!!
        val owner = support.owner(setup.fixture)

        assertThrows(ForbiddenException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assignment, REVOCATION, support.reviewer(setup.fixture)))
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assignment, REVOCATION, owner, reasonCode = null))
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assignment, REVOCATION, owner, reasonCode = ""))
        }
        val revoked = change(
            setup,
            support.changeCommand(
                setup.fixture,
                assignment,
                REVOCATION,
                owner,
                reasonCode = "assignment.withdrawn",
                key = "revoke"
            )
        )
        assertEquals(InformationRequestReviewAssignmentState.REVOKED, revoked.assignment!!.state)
        assertEquals("assignment.withdrawn", revoked.assignment.changeReasonCode)
        val again = assertThrows(InformationRequestLifecycleException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assignment, REVOCATION, owner, key = "revoke-again"))
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE, again.reasonCode)
        assertTrue(
            QuarkusTransaction.requiringNew()
                .call { setup.services.reviewQueries.queue(support.reviewer(setup.fixture)) }.isEmpty()
        )
    }

    @Test
    fun `an assignment names a known stage, an active reviewer party, a reviewer not yet on the stage, and a future due instant`() {
        val setup = setUp()
        val owner = support.owner(setup.fixture)
        fun assignment(partyId: UUID, stageKey: String = "review", dueAt: Instant? = null) =
            support.assignment(setup.fixture, setup.review, partyId, stageKey, "assign-${UUID.randomUUID()}", dueAt)

        fun assign(command: AssignInformationRequestReviewerCommand) =
            QuarkusTransaction.requiringNew().call { setup.services.reviewAssignments.assign(command) }

        val unknown = assertThrows(InformationRequestLifecycleException::class.java) {
            assign(assignment(setup.fixture.reviewerPartyId, stageKey = "missing-stage"))
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_STAGE_UNKNOWN, unknown.reasonCode)
        val ineligible = assertThrows(InformationRequestLifecycleException::class.java) {
            assign(assignment(setup.fixture.runtime.contributorPartyId))
        }
        assertEquals(InformationRequestErrorCatalog.REVIEWER_NOT_ELIGIBLE, ineligible.reasonCode)
        assertThrows(InformationRequestCommandRequestException::class.java) {
            assign(assignment(setup.fixture.reviewerPartyId, dueAt = Instant.now().minusSeconds(60)))
        }
        assertThrows(ForbiddenException::class.java) {
            assign(assignment(setup.fixture.reviewerPartyId).copy(access = support.reviewer(setup.fixture)))
        }
        val assigned = assign(assignment(setup.fixture.reviewerPartyId))
        val duplicate = assertThrows(InformationRequestLifecycleException::class.java) {
            assign(assignment(setup.fixture.reviewerPartyId))
        }
        assertEquals(InformationRequestErrorCatalog.REVIEWER_ALREADY_ASSIGNED, duplicate.reasonCode)

        support.finding(
            setup.services,
            setup.fixture,
            setup.review,
            setup.documentItem,
            InformationRequestFindingCorrectionScope.NONE
        )
        val rejected = support.record(
            setup.services,
            setup.fixture,
            assigned,
            support.draft(setup.services, setup.fixture, assigned, InformationRequestReviewOutcome.REJECTED),
            "reject",
        )
        assertEquals(InformationRequestReviewState.REJECTED, rejected.review.state)
        val settled = assertThrows(InformationRequestLifecycleException::class.java) {
            assign(assignment(setup.colleagueParty))
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SETTLED, settled.reasonCode)
        val settledChange = assertThrows(InformationRequestLifecycleException::class.java) {
            change(setup, support.changeCommand(setup.fixture, assigned.assignment!!, REVOCATION, owner))
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SETTLED, settledChange.reasonCode)
    }

    private fun setUp(): AssignmentChangeSetup {
        val colleagueParty = UUID.randomUUID()
        val colleagueUser = UUID.randomUUID()
        val fixture = support.fixture(prepare = { runtime ->
            runtime.insertActingParty(
                colleagueParty,
                "REVIEWER",
                colleagueUser
            )
        })
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueUser)),
        )
        val packageId = support.submitWhole(services, fixture)
        return AssignmentChangeSetup(
            fixture = fixture,
            services = services,
            review = support.reviewsOf(fixture).single(),
            documentItem = support.item(fixture, packageId, fixture.documentRequirementId),
            colleagueParty = colleagueParty,
            colleagueUser = colleagueUser,
        )
    }

    private fun change(setup: AssignmentChangeSetup, command: ChangeInformationRequestReviewAssignmentCommand) =
        QuarkusTransaction.requiringNew().call { setup.services.reviewAssignments.change(command) }

    private companion object {
        val RECUSAL = InformationRequestReviewAssignmentChange.RECUSAL
        val DELEGATION = InformationRequestReviewAssignmentChange.DELEGATION
        val REVOCATION = InformationRequestReviewAssignmentChange.REVOCATION
    }
}
