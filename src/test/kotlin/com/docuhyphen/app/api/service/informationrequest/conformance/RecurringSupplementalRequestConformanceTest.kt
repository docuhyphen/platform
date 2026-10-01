package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.NextSubmissionVersion
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCarryForwardPlanner
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class RecurringSupplementalRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    @Test
    fun `a linked follow-up keeps its source package, carries forward or invalidates explicitly, rechecks reuse, reconfirms amendments, and keeps cancellation and supersession history`()
    {
        val scenario = scenario()
        val source = scenario.runtime
        val sourceServices = runtime.build(source.requestId, denies = respondingSide(scenario))
        val sourcePackage = submitWhole(sourceServices, scenario)
        assertEquals(InformationRequestState.CLOSED, state(source.requestId))
        val fact = QuarkusTransaction.requiringNew().call {
            sourceServices.acceptedFacts.promote(
                PromoteInformationRequestAcceptedFactCommand(
                    requestId = source.requestId,
                    packageId = sourcePackage,
                    submissionItemId = runtime.packageReader.view(source.requestId, sourcePackage).items
                        .single { it.informationRequestRequirementId == scenario.answers.requirementId }.id,
                    purposeKey = REUSE_PURPOSE,
                    policyBasisKey = "policy.reuse",
                    visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
                    validTo = Instant.now().plus(Duration.ofDays(365)),
                    access = owner(scenario),
                    idempotencyKey = "promote-source-answer",
                ),
            )
        }
        insertLapsedNewerFact(scenario, sourcePackage)

        val supplement = QuarkusTransaction.requiringNew().call {
            sourceServices.successors.create(successor(source.requestId, InformationRequestLineageKind.SUPPLEMENT, scenario, "supplement"))
        }
        assertEquals(sourcePackage, supplement.lineage.sourcePackageId)
        val sourceKeys = QuarkusTransaction.requiringNew().call {
            runtime.packageReader.view(source.requestId, sourcePackage).items.associate { it.id to it.requirementKey }
        }
        assertEquals(InformationRequestState.CLOSED, supplement.source.state)
        assertEquals(
            setOf(
                "recorded-note" to InformationRequestCarryForwardDecision.OFFERED,
                "supporting-record" to InformationRequestCarryForwardDecision.INVALIDATED,
                "recorded-assertion" to InformationRequestCarryForwardDecision.INVALIDATED,
            ),
            supplement.carryForwards.map { sourceKeys.getValue(it.sourceItemId) to it.decision }.toSet(),
        )
        assertTrue(
            supplement.carryForwards.any { it.reasonCode == InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION },
        )
        QuarkusTransaction.requiringNew().run { assertEquals(1, runtime.packageReader.views(source.requestId).size) }

        val supplementId = supplement.successor.id
        val supplementServices = runtime.build(supplementId, denies = respondingSide(scenario))
        val offers = QuarkusTransaction.requiringNew().call { supplementServices.acceptedFactQueries.offers(supplementId, contributor(scenario)) }
        assertEquals(listOf(fact.fact.id), offers.map { it.fact.fact.id })
        assertTrue(offers.single().reconfirmationRequired)

        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?", supplementId)
        }
        val supplementDocument = QuarkusTransaction.requiringNew().call {
            runtime.requirementRepository.findForRequest(supplementId).single { it.sourceTemplateBindingId == source.documentBindingId }.id
        }
        patch(supplementServices, supplementId, supplementDocument, "record-supplement-document")
        val amended = QuarkusTransaction.requiringNew().call {
            supplementServices.amendments.amend(
                AmendInformationRequestCommand(
                    requestId = supplementId,
                    targetTemplateVersionId = scenario.next.versionId,
                    reasonCode = "revised-collection",
                    access = owner(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(supplementId)),
                    idempotencyKey = "amend-supplement",
                ),
            )
        }
        val documentChange = amended.amendment.changes.single { it.requirementKey == "supporting-record" }
        assertEquals(InformationRequestAmendmentChangeKind.MEANING_CHANGED, documentChange.changeKind)
        assertTrue(documentChange.reconfirmationRequired)
        assertEquals(InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED, problems(supplementServices, supplementId)[supplementDocument])
        patch(supplementServices, supplementId, supplementDocument, "reconfirm-supplement-document")
        assertEquals(null, problems(supplementServices, supplementId)[supplementDocument])

        val recurrence = QuarkusTransaction.requiringNew().call {
            sourceServices.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = source.requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(1)),
                    maximumOccurrences = 2,
                    access = owner(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(source.requestId)),
                    idempotencyKey = "define-recurrence",
                ),
            )
        }
        val occurrence = QuarkusTransaction.requiringNew().call {
            sourceServices.followUps.createNextOccurrence(CreateNextInformationRequestOccurrenceCommand(source.requestId, recurrence.id, owner(scenario), "first-occurrence"))
        }
        assertEquals(InformationRequestLineageKind.RECURRENCE, occurrence.lineage.lineageKind)
        assertEquals(1, occurrence.lineage.recurrenceSequence)
        assertEquals(sourcePackage, occurrence.lineage.sourcePackageId)
        val notDue = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                sourceServices.followUps.createNextOccurrence(CreateNextInformationRequestOccurrenceCommand(source.requestId, recurrence.id, owner(scenario), "second-occurrence"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.RECURRENCE_NOT_DUE, notDue.reasonCode)

        val superseding = QuarkusTransaction.requiringNew().call {
            supplementServices.successors.create(successor(supplementId, InformationRequestLineageKind.SUPERSEDING, scenario, "supersede-supplement"))
        }
        assertEquals(InformationRequestState.SUPERSEDED, superseding.source.state)
        assertEquals(superseding.successor.id, superseding.source.supersededByRequestId)
        val cancelledId = occurrence.successor.id
        val cancelled = QuarkusTransaction.requiringNew().call {
            runtime.build(cancelledId).lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = cancelledId,
                    reasonCode = "no-longer-needed",
                    access = owner(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(cancelledId)),
                    idempotencyKey = "cancel-occurrence",
                ),
            )
        }
        assertEquals(InformationRequestState.CANCELLED, cancelled.request.state)
        QuarkusTransaction.requiringNew().run {
            assertTrue(InformationRequestMutation.SUPERSEDE in transitionRepository.findForRequest(supplementId).map { it.mutation })
            assertTrue(InformationRequestMutation.CANCEL in transitionRepository.findForRequest(cancelledId).map { it.mutation })
        }
        val history = QuarkusTransaction.requiringNew().call { sourceServices.lineageQueries.lineage(source.requestId, owner(scenario)) }
        assertEquals(setOf(supplementId, cancelledId), history.successors.map { it.successorRequestId }.toSet())
        assertNotNull(history.recurrence)
    }

    private fun submitWhole(services: InformationRequestRuntimeServices, scenario: Scenario): UUID
    {
        val source = scenario.runtime
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = source.requestId,
                    requirementId = source.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = RequestAccessContext(PrincipalRef.user(source.attestorUserId), AuthorizationContext(sessionRef = "attestor-session")),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(source.requestId)),
                    idempotencyKey = "source-assent",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = source.requestId,
                    access = contributor(scenario),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "source-submit",
                ),
            ).submission.submissionPackage.id
        }
    }

    private fun patch(services: InformationRequestRuntimeServices, requestId: UUID, requirementId: UUID, key: String) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = requestId,
                    access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "respondent-session")),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(requestId))),
                    ),
                    idempotencyKey = key,
                    patches = listOf(InformationRequestResponsePatch(requirementId = requirementId, disposition = InformationRequestResponseDisposition.PROVIDED)),
                ),
            )
        }

    private fun problems(services: InformationRequestRuntimeServices, requestId: UUID) =
        QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(requestId, null, RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())).readiness.problems
                .associate { it.requirementId to it.code }
        }

    private fun successor(sourceId: UUID, kind: InformationRequestLineageKind, scenario: Scenario, key: String) =
        CreateInformationRequestSuccessorCommand(
            sourceRequestId = sourceId,
            kind = kind,
            reasonCode = "additional-information",
            access = owner(scenario),
            precondition = CommandPrecondition.ExpectedRevision(aggregateETag(sourceId)),
            idempotencyKey = key,
        )

    private fun insertLapsedNewerFact(scenario: Scenario, packageId: UUID)
    {
        val item = QuarkusTransaction.requiringNew().call {
            runtime.packageReader.view(scenario.runtime.requestId, packageId).items.single { it.informationRequestRequirementId == scenario.answers.requirementId }
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request_accepted_fact
                    (id, owner_type, owner_organization_id, subject_identity_ref_id, purpose_key, policy_basis_key, field_definition_id,
                     value_type, canonical_value, source_information_request_id, source_package_id, source_submission_item_id,
                     source_requirement_id, source_response_id, source_response_revision, source_field_value_revision_id,
                     visibility, confidence, valid_from, valid_to, conflict_state, promoted_by_principal_kind,
                     promoted_by_principal_id, promoted_at)
                VALUES (?, 'ORGANIZATION', ?, ?, ?, 'policy.reuse', ?, 'SHORT_TEXT', '"Lapsed answer"', ?, ?, ?, ?, ?, ?, ?,
                        'RESPONDING_PARTIES', 'DECLARED', now() - INTERVAL '2 days', now() - INTERVAL '1 hour', 'NONE', 'USER', ?,
                        now() + INTERVAL '1 minute')
                """.trimIndent(),
                UUID.randomUUID(),
                scenario.runtime.template.organizationId,
                scenario.subjectId,
                REUSE_PURPOSE,
                scenario.answers.fieldDefinitionId,
                scenario.runtime.requestId,
                packageId,
                item.id,
                scenario.answers.requirementId,
                requireNotNull(item.responseId),
                requireNotNull(item.responseRevision),
                requireNotNull(item.fieldValueRevisionId),
                scenario.runtime.template.userId,
            )
        }
    }

    private fun scenario(): Scenario =
        dataSource.connection.use { connection ->
            lateinit var answers: FieldAnswerSqlFixture
            val source = SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template, reusePurpose = REUSE_PURPOSE)
            })
            answers.materialize(source)
            val subjectId = UUID.randomUUID()
            source.insertSubject(subjectId, UUID.randomUUID())
            val next = source.publishNextVersion(adjust = { version ->
                execute(connection, "UPDATE information_request_template_requirement_binding SET requiredness = 'OPTIONAL' WHERE id = ?", version.documentBindingId)
                execute(
                    connection,
                    "UPDATE information_request_template_version SET schema_version_id = ?, fact_reuse_purpose_key = ? WHERE id = ?",
                    answers.schemaVersionId,
                    REUSE_PURPOSE,
                    version.versionId,
                )
                val fieldBinding = UUID.randomUUID()
                execute(
                    connection,
                    """
                    INSERT INTO information_request_template_requirement_binding
                        (id, template_version_id, template_definition_id, template_requirement_id, template_section_id,
                         display_order, prompt, response_mode, requiredness, contributor_role, review_policy,
                         collected_field_definition_id)
                    VALUES (?, ?, ?, ?, ?, 9, 'Record the note', 'PROVIDE', 'REQUIRED', 'CONTRIBUTOR', 'NOT_REQUIRED', ?)
                    """.trimIndent(),
                    fieldBinding,
                    version.versionId,
                    source.template.definitionId,
                    answers.templateRequirementId,
                    version.sectionId,
                    answers.fieldDefinitionId,
                )
                source.template.insertDisposition(fieldBinding, version.versionId, "PROVIDED")
            })
            Scenario(source, answers, subjectId, next)
        }

    private fun respondingSide(scenario: Scenario): (PrincipalRef, Action) -> Boolean = { principal, action ->
        principal.id != scenario.runtime.template.userId && action == Action.INFORMATION_REQUEST_PROMOTE_FACT
    }

    private fun state(requestId: UUID): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(requestId)).state }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun submissionETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun owner(scenario: Scenario) =
        RequestAccessContext(PrincipalRef.user(scenario.runtime.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(scenario: Scenario) =
        RequestAccessContext(PrincipalRef.user(scenario.runtime.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    private data class Scenario(
        val runtime: SubmissionRuntimeSqlFixture,
        val answers: FieldAnswerSqlFixture,
        val subjectId: UUID,
        val next: NextSubmissionVersion,
    )

    private companion object
    {
        const val REUSE_PURPOSE = "profile.reuse"
    }
}
