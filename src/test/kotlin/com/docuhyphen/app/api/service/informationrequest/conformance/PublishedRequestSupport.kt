package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import java.util.UUID
import javax.sql.DataSource

internal data class PublishedRequest(
    val requestId: UUID,
    val exchangeId: UUID,
    val definitionId: UUID,
    val templateVersionId: UUID,
    val ownerId: UUID,
    val users: Map<String, UUID>,
    val parties: Map<String, UUID>,
    val fields: Map<String, WalkingSkeletonField>,
    val participants: Set<String> = emptySet(),
)
{
    fun access(party: String): RequestAccessContext =
        RequestAccessContext(principal(party), AuthorizationContext(sessionRef = "${party.lowercase()}-session"))

    fun principal(party: String): PrincipalRef =
        if (party in participants) PrincipalRef.participant(users.getValue(party)) else PrincipalRef.user(users.getValue(party))

    val owner: RequestAccessContext get() = RequestAccessContext(PrincipalRef.user(ownerId), AuthorizationContext(sessionRef = "owner-session"))
}

internal class PublishedRequestSupport(
    private val dataSource: DataSource,
    private val requestRepository: InformationRequestRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    definitionRepository: InformationRequestTemplateDefinitionRepository,
    versionRepository: InformationRequestTemplateVersionRepository,
    capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val materializer: InformationRequestTemplateMaterializer,
)
{
    private val store = InformationRequestTemplateWalkingSkeletonStore(
        dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
    )

    fun issue(
        templateKey: String,
        fieldKeys: List<String>,
        partyRoles: List<String>,
        participants: Set<String> = emptySet(),
        configuration: (UUID, Map<String, WalkingSkeletonField>) -> InformationRequestTemplateConfigurationRequest,
    ): PublishedRequest
    {
        val owner = store.insertOwner()
        val fields = fieldKeys.associateWith { store.insertTextField(it) }
        val schemaVersionId = store.insertSchemaVersion(owner, fields.values.toList())
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE schema_field_binding SET visibility = 'PUBLIC', is_required = FALSE WHERE schema_version_id = ?", schemaVersionId)
        }
        val draft = store.insertDraft(owner, templateKey)
        store.publish(draft, configuration(schemaVersionId, fields))
        val exchangeId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                     description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process information', 'Please respond',
                        'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                exchangeId,
                owner,
                owner,
            )
            share(connection, "EXCHANGE", exchangeId, owner, "OWNER")
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_user_id, state, gates_exchange_closure,
                     aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                requestId,
                exchangeId,
                draft.versionId,
                owner,
            )
        }
        QuarkusTransaction.requiringNew().run {
            materializer.materialize(
                requireNotNull(requestRepository.findById(requestId)),
                FieldsAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session")),
            )
        }
        val users = partyRoles.associateWith { party -> if (party in participants) UUID.randomUUID() else store.insertOwner() }
        val parties = partyRoles.associateWith { UUID.randomUUID() }
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?", requestId)
            users.forEach { (party, principalId) ->
                val role = party.substringBefore('#')
                val kind = if (party in participants) "PARTICIPANT" else "USER"
                if (party in participants) insertParticipant(connection, principalId, owner)
                val shareId = share(connection, "INFORMATION_REQUEST", requestId, principalId, role, kind)
                execute(
                    connection,
                    """
                    INSERT INTO information_request_party
                        (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                         assigned_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE, 1, now(), now(), now())
                    """.trimIndent(),
                    parties.getValue(party),
                    requestId,
                    role,
                    kind,
                    principalId,
                    shareId,
                )
            }
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_user_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at)
                VALUES (?, ?, 'USER', ?, 'PERSONAL', 'ACTIVE', 'ENFORCE', 5, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                requestId,
                owner,
            )
        }
        return PublishedRequest(requestId, exchangeId, draft.definitionId, draft.versionId, owner, users, parties, fields, participants)
    }

    fun requirement(request: PublishedRequest, key: String, path: String = "root"): InformationRequestRequirement =
        QuarkusTransaction.requiringNew().call {
            val templateRequirementIds = dataSource.connection.use { connection ->
                queryStrings(
                    connection,
                    "SELECT id::text FROM information_request_template_requirement WHERE template_definition_id = ? AND requirement_key = ?",
                    request.definitionId,
                    key,
                )
            }.map(UUID::fromString).toSet()
            requirementRepository.findForRequest(request.requestId)
                .single { it.sourceTemplateRequirementId in templateRequirementIds && it.occurrencePath == path }
        }

    fun addOccurrence(
        occurrences: InformationRequestGroupOccurrenceService,
        request: PublishedRequest,
        access: RequestAccessContext,
        groupKey: String,
        parentOccurrenceId: UUID?,
        key: String,
    ): InformationRequestGroupOccurrence =
        QuarkusTransaction.requiringNew().call {
            val before = QuarkusTransaction.requiringNew().call { groupOccurrenceIds(request.requestId) }
            occurrences.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = request.requestId,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(request)),
                    idempotencyKey = key,
                    groupKey = groupKey,
                    parentOccurrenceId = parentOccurrenceId,
                ),
            ).occurrences.single { it.removedAt == null && it.id !in before }
        }

    private fun groupOccurrenceIds(requestId: UUID): Set<UUID> =
        dataSource.connection.use { connection ->
            queryStrings(connection, "SELECT id::text FROM information_request_group_occurrence WHERE information_request_id = ?", requestId)
        }.map(UUID::fromString).toSet()

    fun responseETag(request: PublishedRequest): String =
        QuarkusTransaction.requiringNew().call { InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(request.requestId))) }

    @Suppress("LongParameterList")
    private fun share(
        connection: java.sql.Connection,
        resourceType: String,
        resourceId: UUID,
        principalId: UUID,
        role: String,
        principalKind: String = "USER",
    ): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, ?, ?, ?, ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            id,
            resourceType,
            resourceId,
            principalKind,
            principalId,
            role,
        )
        return id
    }

    private fun insertParticipant(connection: java.sql.Connection, participantId: UUID, ownerId: UUID)
    {
        val email = "participant-${participantId.toString().take(8)}@process.test"
        execute(
            connection,
            """
            INSERT INTO external_participant (id, owner_app_user_id, email, email_lower, is_active, created_date)
            VALUES (?, ?, ?, LOWER(?), TRUE, now())
            """.trimIndent(),
            participantId,
            ownerId,
            email,
            email,
        )
    }
}
