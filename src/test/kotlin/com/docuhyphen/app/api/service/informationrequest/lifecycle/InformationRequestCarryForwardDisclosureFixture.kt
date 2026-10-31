package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.CarryForwardDisclosureScenario
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

internal class InformationRequestCarryForwardDisclosureFixture(
    private val dataSource: DataSource,
    private val runtime: InformationRequestRuntimeTestServices,
    private val requestRepository: InformationRequestRepository,
    private val authorizationService: AuthorizationService,
) {
    fun scenario(protectedSource: Boolean = false): CarryForwardDisclosureScenario {
        lateinit var answers: FieldAnswerSqlFixture
        val groupId = UUID.randomUUID()
        val groupMemberId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        var next: NextSubmissionVersion? = null
        val source = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template)
                if (protectedSource) {
                    execute(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = ? WHERE id = ?",
                        COMPARTMENT_KEY,
                        answers.bindingId,
                    )
                }
            }).also { source ->
                answers.materialize(source)
                execute(
                    connection,
                    "UPDATE information_request_response SET narrative = ? WHERE id = ?",
                    EARLIER_NARRATIVE,
                    answers.responseId
                )
                grantSourceShares(connection, source)
                addGroupContributor(connection, source, groupId, groupMemberId)
                addParticipantContributor(connection, source, participantId)
                if (protectedSource) {
                    next = source.publishNextVersion(adjust = { version ->
                        addUnprotectedFieldBinding(
                            connection,
                            source,
                            answers,
                            version
                        )
                    })
                }
            }
        }
        val sourceServices = runtime.build(source.requestId, centralAuthorization = authorizationService)
        submitWhole(sourceServices, source)
        val successorId = QuarkusTransaction.requiringNew().call {
            sourceServices.successors.create(
                CreateInformationRequestSuccessorCommand(
                    sourceRequestId = source.requestId,
                    kind = InformationRequestLineageKind.SUPPLEMENT,
                    targetTemplateVersionId = next?.versionId,
                    reasonCode = "additional-information",
                    access = owner(source),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(source.requestId)),
                    idempotencyKey = "supplement-${UUID.randomUUID()}",
                ),
            )
        }.successor.id
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?",
                successorId
            )
        }
        val successorFieldRequirementId = QuarkusTransaction.requiringNew().call {
            runtime.requirementRepository.findForRequest(successorId)
                .single { it.sourceTemplateRequirementId == answers.templateRequirementId }.id
        }
        return CarryForwardDisclosureScenario(
            source = source,
            answers = answers,
            successorId = successorId,
            successorFieldRequirementId = successorFieldRequirementId,
            groupId = groupId,
            groupMemberId = groupMemberId,
            participantId = participantId,
            services = runtime.build(successorId, centralAuthorization = authorizationService),
        )
    }

    fun assignSuccessorContributor(scenario: CarryForwardDisclosureScenario, principal: PrincipalRef) {
        dataSource.connection.use { connection ->
            val shareId = share(connection, scenario.successorId, principal, "CONTRIBUTOR")
            party(connection, scenario.successorId, principal, "CONTRIBUTOR", shareId)
        }
    }

    fun withdrawSourceAccess(scenario: CarryForwardDisclosureScenario, principal: PrincipalRef) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE share SET status = 'REVOKED', revoked_at = now()
                WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND principal_kind = ? AND principal_id = ? AND status = 'ACTIVE'
                """.trimIndent(),
                scenario.source.requestId,
                principal.kind.name,
                principal.id,
            )
            execute(
                connection,
                "UPDATE information_request_party SET active = FALSE WHERE information_request_id = ? AND principal_kind = ? AND principal_id = ?",
                scenario.source.requestId,
                principal.kind.name,
                principal.id,
            )
        }
    }

    fun removeGroupMember(scenario: CarryForwardDisclosureScenario) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE principal_group_member SET is_active = FALSE WHERE principal_group_id = ? AND principal_id = ?",
                scenario.groupId,
                scenario.groupMemberId,
            )
        }
    }

    fun successorPartyShareId(scenario: CarryForwardDisclosureScenario, principal: PrincipalRef): UUID =
        dataSource.connection.use { connection ->
            UUID.fromString(
                requireNotNull(
                    queryString(
                        connection,
                        """
                        SELECT share_id::text FROM information_request_party
                        WHERE information_request_id = ? AND principal_kind = ? AND principal_id = ? AND active
                        """.trimIndent(),
                        scenario.successorId,
                        principal.kind.name,
                        principal.id,
                    ),
                ),
            )
        }

    fun decision(principal: PrincipalRef, requirementId: UUID, verified: Boolean): Decision =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                ResourceRef.informationRequestRequirement(requirementId),
                AuthorizationContext(sessionRef = "carry-forward-session", mfaSatisfied = verified),
            )
        }

    fun owner(source: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(
            PrincipalRef.user(source.template.userId),
            AuthorizationContext(sessionRef = "owner-session")
        )

    fun access(principal: PrincipalRef, verified: Boolean = false) =
        RequestAccessContext(
            principal,
            AuthorizationContext(sessionRef = "carry-forward-session", mfaSatisfied = verified)
        )

    private fun submitWhole(services: InformationRequestRuntimeServices, source: SubmissionRuntimeSqlFixture) {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = source.requestId,
                    requirementId = source.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(PrincipalRef.user(source.attestorUserId), verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(source.requestId)),
                    idempotencyKey = "source-assent-${UUID.randomUUID()}",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = source.requestId,
                    access = access(PrincipalRef.user(source.contributorUserId), verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "source-submit-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun grantSourceShares(connection: Connection, source: SubmissionRuntimeSqlFixture) {
        val decisionMakerPartyId = UUID.randomUUID()
        source.insertPartyForUser(decisionMakerPartyId, "DECISION_MAKER", source.template.userId)
        listOf(
            Triple(source.contributorPartyId, source.contributorUserId, "CONTRIBUTOR"),
            Triple(source.attestorPartyId, source.attestorUserId, "ATTESTOR"),
            Triple(decisionMakerPartyId, source.template.userId, "DECISION_MAKER"),
        ).forEach { (partyId, userId, role) ->
            val shareId = share(connection, source.requestId, PrincipalRef.user(userId), role)
            execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
        }
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            source.exchangeId,
            source.template.userId,
        )
    }

    private fun addGroupContributor(
        connection: Connection,
        source: SubmissionRuntimeSqlFixture,
        groupId: UUID,
        memberId: UUID
    ) {
        source.insertUser(memberId)
        execute(
            connection,
            """
            INSERT INTO principal_group (id, name, scope, owner_organization_id, externally_published, is_active, created_date)
            VALUES (?, 'Respondent group', 'ORG', ?, FALSE, TRUE, now())
            """.trimIndent(),
            groupId,
            source.template.organizationId,
        )
        execute(
            connection,
            """
            INSERT INTO principal_group_member (id, principal_group_id, principal_kind, principal_id, group_role, added_at, is_active)
            VALUES (?, ?, 'USER', ?, 'MEMBER', now(), TRUE)
            """.trimIndent(),
            UUID.randomUUID(),
            groupId,
            memberId,
        )
        val group = PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, groupId)
        party(
            connection,
            source.requestId,
            group,
            "CONTRIBUTOR",
            share(connection, source.requestId, group, "CONTRIBUTOR")
        )
    }

    private fun addParticipantContributor(
        connection: Connection,
        source: SubmissionRuntimeSqlFixture,
        participantId: UUID
    ) {
        val email = "carry-forward-${participantId.toString().take(8)}@process.test"
        execute(
            connection,
            """
            INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date)
            VALUES (?, ?, ?, LOWER(?), TRUE, now())
            """.trimIndent(),
            participantId,
            source.template.organizationId,
            email,
            email,
        )
        val participant = PrincipalRef.participant(participantId)
        party(
            connection,
            source.requestId,
            participant,
            "CONTRIBUTOR",
            share(connection, source.requestId, participant, "CONTRIBUTOR")
        )
    }

    private fun addUnprotectedFieldBinding(
        connection: Connection,
        source: SubmissionRuntimeSqlFixture,
        answers: FieldAnswerSqlFixture,
        version: NextSubmissionVersion,
    ) {
        execute(
            connection,
            "UPDATE information_request_template_version SET schema_version_id = ? WHERE id = ?",
            answers.schemaVersionId,
            version.versionId
        )
        val bindingId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id, template_section_id,
                 display_order, prompt, response_mode, requiredness, contributor_role, review_policy,
                 collected_field_definition_id)
            VALUES (?, ?, ?, ?, ?, 9, 'Record the note', 'PROVIDE', 'REQUIRED', 'CONTRIBUTOR', 'NOT_REQUIRED', ?)
            """.trimIndent(),
            bindingId,
            version.versionId,
            source.template.definitionId,
            answers.templateRequirementId,
            version.sectionId,
            answers.fieldDefinitionId,
        )
        source.template.insertDisposition(bindingId, version.versionId, "PROVIDED")
    }

    private fun share(connection: Connection, requestId: UUID, principal: PrincipalRef, role: String): UUID {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, ?, ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            principal.kind.name,
            principal.id,
            role,
        )
        return shareId
    }

    private fun party(connection: Connection, requestId: UUID, principal: PrincipalRef, role: String, shareId: UUID) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            role,
            principal.kind.name,
            principal.id,
            shareId,
        )
    }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun submissionETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    companion object {
        const val EARLIER_NARRATIVE = "Earlier recorded narrative"
        const val EARLIER_VALUE = "Recorded answer"
        const val COMPARTMENT_KEY = "restricted-record"
    }
}
