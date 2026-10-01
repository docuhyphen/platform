package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.FactSqlFixture
import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RevokeInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordAssembler
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestFactRecertificationTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var entityManager: EntityManager
    @Inject lateinit var assembler: InformationRequestRecordAssembler

    @Test
    fun `recertification writes the reused value with exact source provenance only after explicit assent`()
    {
        val fixture = fixture()
        val factId = insertFact(fixture)
        linkEvidence(fixture, factId)
        val services = laterServices(fixture)

        val command = recertify(fixture, factId, "recertify-once")
        val result = QuarkusTransaction.requiringNew().call { services.factRecertifications.recertify(command) }

        val recertification = result.view.recertification
        assertEquals(factId, recertification.factId)
        assertEquals(fixture.laterRequestId, recertification.informationRequestId)
        assertEquals(fixture.laterRequirementId, recertification.informationRequestRequirementId)
        assertEquals(fixture.requestId, recertification.sourceInformationRequestId)
        assertEquals(fixture.facts.packageId, recertification.sourcePackageId)
        assertEquals(fixture.facts.itemId, recertification.sourceSubmissionItemId)
        assertEquals(fixture.answers.requirementId, recertification.sourceRequirementId)
        assertEquals(fixture.answers.fieldValueRevisionId, recertification.sourceFieldValueRevisionId)
        assertEquals("\"Recorded answer\"", recertification.canonicalValue)
        assertEquals("policy.reuse", recertification.policyBasisKey)
        assertEquals(fixture.runtime.contributorUserId, recertification.assentedByPrincipalId)
        assertEquals(listOf(fixture.runtime.evidenceVersionId), result.view.evidenceVersionIds)
        assertEquals(currentResponseETag(fixture), result.responseETag)
        dataSource.connection.use { connection ->
            assertEquals(
                "Recorded answer",
                queryString(
                    connection,
                    "SELECT text_value FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
                    fixture.laterRequestId,
                ),
            )
            assertEquals(
                recertification.responseId.toString(),
                queryString(
                    connection,
                    "SELECT id::text FROM information_request_response WHERE information_request_requirement_id = ? AND disposition = 'PROVIDED'",
                    fixture.laterRequirementId,
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            val mutations = transitionRepository.findForRequest(fixture.laterRequestId).map { it.mutation }
            assertTrue(InformationRequestMutation.RECERTIFY_FACT in mutations, mutations.toString())
        }

        val replayed = QuarkusTransaction.requiringNew().call { services.factRecertifications.recertify(command) }
        assertEquals(recertification.id, replayed.view.recertification.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.factRecertifications.recertify(command.copy(requirementId = UUID.randomUUID()))
            }
        }
        assertEquals(1, recertifications(fixture))
    }

    @Test
    fun `the later request's record lists its recertification with the copied provenance`()
    {
        val fixture = fixture()
        val factId = insertFact(fixture)
        linkEvidence(fixture, factId)
        val services = laterServices(fixture)
        val result = QuarkusTransaction.requiringNew().call { services.factRecertifications.recertify(recertify(fixture, factId, "recertify-record")) }

        val record = QuarkusTransaction.requiringNew().call { assembler.assemble(requireNotNull(requestRepository.findById(fixture.laterRequestId))) }
        val listed = record.getValue("recertifications").jsonArray.single().jsonObject
        assertEquals(result.view.recertification.id.toString(), listed.getValue("recertificationId").jsonPrimitive.content)
        assertEquals(factId.toString(), listed.getValue("factId").jsonPrimitive.content)
        assertEquals(fixture.requestId.toString(), listed.getValue("sourceInformationRequestId").jsonPrimitive.content)
        assertEquals(fixture.facts.packageId.toString(), listed.getValue("sourcePackageId").jsonPrimitive.content)
        assertEquals("policy.reuse", listed.getValue("policyBasisKey").jsonPrimitive.content)
        assertEquals(
            listOf(fixture.runtime.evidenceVersionId.toString()),
            listed.getValue("evidenceVersionIds").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `a recertification without explicit assent writes nothing`()
    {
        val fixture = fixture()
        val factId = insertFact(fixture)
        val services = laterServices(fixture)

        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.factRecertifications.recertify(recertify(fixture, factId, "no-assent").copy(assented = false))
            }
        }
        assertNothingWritten(fixture)
    }

    @Test
    fun `a recertification against a stale response precondition writes nothing`()
    {
        val fixture = fixture()
        val factId = insertFact(fixture)
        val services = laterServices(fixture)

        assertThrows(CommandPreconditionException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.factRecertifications.recertify(
                    recertify(fixture, factId, "stale").copy(precondition = CommandPrecondition.ExpectedRevision("\"stale\"")),
                )
            }
        }
        assertNothingWritten(fixture)
    }

    @Test
    fun `revoked, expired, superseded, and other-purpose facts are refused as unavailable offers`()
    {
        val fixture = fixture()
        val revoked = insertFact(fixture)
        revoke(fixture, revoked)
        val expired = insertFact(fixture, expired = true)
        val superseded = insertFact(fixture)
        insertFact(fixture, supersedes = superseded)
        val otherPurpose = insertFact(fixture, purpose = "profile.other")
        val services = laterServices(fixture)

        listOf(revoked, expired, superseded, otherPurpose).forEach { factId ->
            val refused = assertThrows(InformationRequestLifecycleException::class.java) {
                QuarkusTransaction.requiringNew().call {
                    services.factRecertifications.recertify(recertify(fixture, factId, "refused-$factId"))
                }
            }
            assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_OFFER_UNAVAILABLE, refused.reasonCode)
        }
        assertNothingWritten(fixture)
    }

    @Test
    fun `a fact kept to the requesting side is never written into a response even by a caller who can see it`()
    {
        val fixture = fixture()
        val factId = insertFact(fixture, visibility = "REQUESTING_SIDE")
        val services = runtime.build(fixture.laterRequestId)
        val offered = QuarkusTransaction.requiringNew().call {
            services.acceptedFactQueries.offers(fixture.laterRequestId, contributor(fixture))
        }
        assertEquals(listOf(factId), offered.map { it.fact.fact.id })

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.factRecertifications.recertify(recertify(fixture, factId, "requesting-side"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_OFFER_UNAVAILABLE, refused.reasonCode)
        assertNothingWritten(fixture)
    }

    @Test
    fun `a revocation committed while a recertification waits on the fact refuses the recertification`()
    {
        val fixture = fixture()
        val factId = insertFact(fixture)
        val source = runtime.build(fixture.requestId, denies = respondentDenials(fixture))
        val services = laterServices(fixture)
        val revoked = CountDownLatch(1)
        val release = CountDownLatch(1)

        val revocation = CompletableFuture.runAsync {
            withRequestContext {
                QuarkusTransaction.requiringNew().run {
                    source.acceptedFacts.revoke(
                        RevokeInformationRequestAcceptedFactCommand(
                            requestId = fixture.requestId,
                            factId = factId,
                            reasonCode = "no-longer-accurate",
                            access = owner(fixture),
                            idempotencyKey = "revoke-during-recertification",
                        ),
                    )
                    entityManager.flush()
                    revoked.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
            }
        }
        while (!revoked.await(100, TimeUnit.MILLISECONDS))
        {
            if (revocation.isDone) revocation.get()
        }
        val command = recertify(fixture, factId, "recertify-during-revocation")
        val recertification = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().call {
                        services.factRecertifications.recertify(command)
                    }
                }
            }
        }
        awaitLockWaiterOrCompletion(recertification)
        release.countDown()
        revocation.get(30, TimeUnit.SECONDS)

        val outcome = recertification.get(30, TimeUnit.SECONDS).exceptionOrNull()
        assertTrue(outcome is InformationRequestLifecycleException, outcome.toString())
        assertEquals(
            InformationRequestErrorCatalog.ACCEPTED_FACT_OFFER_UNAVAILABLE,
            (outcome as InformationRequestLifecycleException).reasonCode,
        )
        assertNothingWritten(fixture)
    }

    private fun <T> withRequestContext(block: () -> T): T
    {
        val context = Arc.container().requestContext()
        context.activate()
        try
        {
            return block()
        }
        finally
        {
            context.terminate()
        }
    }

    private fun awaitLockWaiterOrCompletion(pending: CompletableFuture<*>)
    {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!pending.isDone && System.nanoTime() < deadline)
        {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting > 0) return
            Thread.onSpinWait()
        }
    }

    private fun assertNothingWritten(fixture: RecertificationFixture)
    {
        assertEquals(0, recertifications(fixture))
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(connection, "SELECT COUNT(*) FROM information_request_response WHERE information_request_id = ?", fixture.laterRequestId),
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
                    fixture.laterRequestId,
                ),
            )
        }
    }

    private fun recertifications(fixture: RecertificationFixture): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM information_request_fact_recertification WHERE information_request_id = ?",
                fixture.laterRequestId,
            )
        }

    private fun currentResponseETag(fixture: RecertificationFixture): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.laterRequestId)))
        }

    private fun recertify(fixture: RecertificationFixture, factId: UUID, key: String) =
        RecertifyInformationRequestAcceptedFactCommand(
            requestId = fixture.laterRequestId,
            factId = factId,
            requirementId = fixture.laterRequirementId,
            assented = true,
            precondition = CommandPrecondition.ExpectedRevision(currentResponseETag(fixture)),
            access = contributor(fixture),
            idempotencyKey = key,
        )

    private fun laterServices(fixture: RecertificationFixture) =
        runtime.build(fixture.laterRequestId, denies = respondentDenials(fixture))

    private fun revoke(fixture: RecertificationFixture, factId: UUID) =
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request_accepted_fact_revocation
                    (id, fact_id, reason_code, revoked_by_principal_kind, revoked_by_principal_id, revoked_at)
                VALUES (?, ?, 'no-longer-accurate', 'USER', ?, now())
                """.trimIndent(),
                UUID.randomUUID(),
                factId,
                fixture.runtime.template.userId,
            )
        }

    @Suppress("LongParameterList")
    private fun insertFact(
        fixture: RecertificationFixture,
        visibility: String = "RESPONDING_PARTIES",
        expired: Boolean = false,
        supersedes: UUID? = null,
        purpose: String = "profile.reuse",
    ): UUID =
        dataSource.connection.use { connection ->
            val id = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request_accepted_fact
                    (id, owner_type, owner_organization_id, subject_identity_ref_id, purpose_key, policy_basis_key,
                     field_definition_id, value_type, canonical_value, source_information_request_id, source_package_id,
                     source_submission_item_id, source_requirement_id, source_response_id, source_response_revision,
                     source_field_value_revision_id, visibility, confidence, valid_from, valid_to, expires_at,
                     supersedes_fact_id, conflict_state, promoted_by_principal_kind, promoted_by_principal_id, promoted_at)
                VALUES (?, 'ORGANIZATION', ?, ?, ?, 'policy.reuse', ?, 'SHORT_TEXT', '"Recorded answer"', ?, ?, ?, ?, ?, 2, ?,
                        ?, 'DECLARED', now() - INTERVAL '2 days', now() + INTERVAL '30 days',
                        CASE WHEN ? THEN now() - INTERVAL '1 hour' END, ?, 'NONE', 'USER', ?, now() - INTERVAL '1 day')
                """.trimIndent(),
                id,
                fixture.runtime.template.organizationId,
                fixture.facts.subjectId,
                purpose,
                fixture.answers.fieldDefinitionId,
                fixture.requestId,
                fixture.facts.packageId,
                fixture.facts.itemId,
                fixture.answers.requirementId,
                fixture.answers.responseId,
                fixture.answers.fieldValueRevisionId,
                visibility,
                expired,
                supersedes,
                fixture.runtime.template.userId,
            )
            id
        }

    private fun linkEvidence(fixture: RecertificationFixture, factId: UUID) =
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO information_request_accepted_fact_evidence (id, fact_id, source_submission_evidence_id, evidence_version_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                factId,
                fixture.submittedEvidenceId,
                fixture.runtime.evidenceVersionId,
            )
        }

    private fun fixture(): RecertificationFixture =
        dataSource.connection.use { connection ->
            lateinit var answers: FieldAnswerSqlFixture
            val templateLinkId = UUID.randomUUID()
            val source = SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template, reusePurpose = "profile.reuse")
                execute(
                    connection,
                    "INSERT INTO information_request_template_binding_evidence_link (id, template_binding_id, supporting_template_binding_id, template_version_id) VALUES (?, ?, ?, ?)",
                    templateLinkId,
                    answers.bindingId,
                    configured.documentBindingId,
                    configured.template.versionId,
                )
            })
            answers.materialize(source)
            execute(connection, "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE id = ?", answers.schemaFieldBindingId)
            val facts = FactSqlFixture(connection, source, answers)
            val submittedEvidenceId = supportingEvidence(connection, source, answers, facts, templateLinkId)
            val later = LaterRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            insertLaterRequest(connection, source, answers, facts, later)
            RecertificationFixture(source, facts, answers, later.requestId, later.requirementId, submittedEvidenceId)
        }

    private fun supportingEvidence(
        connection: java.sql.Connection,
        source: SubmissionRuntimeSqlFixture,
        answers: FieldAnswerSqlFixture,
        facts: FactSqlFixture,
        templateLinkId: UUID,
    ): UUID
    {
        val documentItemId = UUID.randomUUID()
        val submittedEvidenceId = UUID.randomUUID()
        val requestLinkId = UUID.randomUUID()
        source.insertItem(
            documentItemId,
            facts.packageId,
            source.documentRequirementId,
            source.documentRevisionId,
            source.documentBindingId,
            "DOCUMENT",
            source.documentResponseId,
        )
        source.insertEvidenceMember(submittedEvidenceId, facts.packageId, documentItemId)
        execute(
            connection,
            "INSERT INTO information_request_supporting_evidence_link (id, information_request_id, supported_requirement_id, supporting_requirement_id, template_evidence_link_id) VALUES (?, ?, ?, ?, ?)",
            requestLinkId,
            source.requestId,
            answers.requirementId,
            source.documentRequirementId,
            templateLinkId,
        )
        execute(
            connection,
            "INSERT INTO information_request_submission_supporting_link (id, package_id, information_request_id, supporting_evidence_link_id, supported_requirement_id, supporting_requirement_id) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            facts.packageId,
            source.requestId,
            requestLinkId,
            answers.requirementId,
            source.documentRequirementId,
        )
        return submittedEvidenceId
    }

    private fun insertLaterRequest(
        connection: java.sql.Connection,
        source: SubmissionRuntimeSqlFixture,
        answers: FieldAnswerSqlFixture,
        facts: FactSqlFixture,
        later: LaterRequest,
    )
    {
        val template = source.template
        val laterInitiatorId = UUID.randomUUID()
        source.insertUser(laterInitiatorId)
        execute(
            connection,
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect later records', 'Please respond',
                    'Later collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
            later.exchangeId,
            template.organizationId,
            laterInitiatorId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state, gates_exchange_closure,
                 aggregate_revision, party_revision, created_at, updated_at, issued_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, now(), now(), now())
            """.trimIndent(),
            later.requestId,
            later.exchangeId,
            template.versionId,
            template.organizationId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at, created_at, updated_at)
            VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            later.requestId,
            facts.subjectId,
        )
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share
                (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            later.requestId,
            source.contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'CONTRIBUTOR', 'USER', ?, ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            later.requestId,
            source.contributorUserId,
            shareId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path, created_at)
            VALUES (?, ?, ?, ?, ?, 'root', now())
            """.trimIndent(),
            later.requirementId,
            later.requestId,
            template.versionId,
            answers.templateRequirementId,
            answers.bindingId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_revision
                (id, information_request_requirement_id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                 effective_from, configuration_hash_sha256, optimistic_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, 'root', now(), ?, 1, now())
            """.trimIndent(),
            later.revisionId,
            later.requirementId,
            later.requestId,
            template.versionId,
            answers.templateRequirementId,
            answers.bindingId,
            "0".repeat(64),
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_current
                (information_request_requirement_id, current_revision_id, current_revision_number, updated_at)
            VALUES (?, ?, 1, now())
            """.trimIndent(),
            later.requirementId,
            later.revisionId,
        )
        execute(
            connection,
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind, scope_org_id,
                                           assignment_source, assigned_by_principal_kind, assigned_by_principal_id, assigned_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, ?, 'ORGANIZATION', ?, 'MANUAL', 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            later.requestId,
            answers.schemaVersionId,
            template.organizationId,
            template.userId,
        )
        execute(
            connection,
            """
            INSERT INTO request_execution_grant
                (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                 enforcement_mode, acting_party_cap, issued_at, created_at)
            VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 5, now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            later.requestId,
            template.organizationId,
        )
    }

    private fun respondentDenials(fixture: RecertificationFixture): (PrincipalRef, Action) -> Boolean = { principal, action ->
        principal.id != fixture.runtime.template.userId && action == Action.INFORMATION_REQUEST_PROMOTE_FACT
    }

    private fun owner(fixture: RecertificationFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(fixture: RecertificationFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    private data class LaterRequest(
        val exchangeId: UUID,
        val requestId: UUID,
        val requirementId: UUID,
        val revisionId: UUID,
    )

    internal data class RecertificationFixture(
        val runtime: SubmissionRuntimeSqlFixture,
        val facts: FactSqlFixture,
        val answers: FieldAnswerSqlFixture,
        val laterRequestId: UUID,
        val laterRequirementId: UUID,
        val submittedEvidenceId: UUID,
    )
    {
        val requestId: UUID get() = runtime.requestId
    }
}
