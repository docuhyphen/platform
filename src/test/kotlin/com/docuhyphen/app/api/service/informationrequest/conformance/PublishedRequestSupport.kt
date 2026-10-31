package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import java.util.*
import javax.sql.DataSource

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
        fieldTypes: Map<String, FieldValueType> = emptyMap(),
        configuration: (UUID, Map<String, WalkingSkeletonField>) -> InformationRequestTemplateConfigurationRequest,
    ): PublishedRequest
    {
        val owner = store.insertOwner()
        val fields = fieldKeys.associateWith { store.insertField(it, fieldTypes[it] ?: FieldValueType.SHORT_TEXT) }
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
        val subjects = partyRoles.filter { it.substringBefore('#') == SUBJECT_ROLE }.associateWith { UUID.randomUUID() }
        val users =
            (partyRoles - subjects.keys).associateWith { party -> if (party in participants) UUID.randomUUID() else store.insertOwner() }
        val parties = partyRoles.associateWith { UUID.randomUUID() }
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?", requestId)
            subjects.forEach { (party, subjectId) ->
                insertSubjectParty(
                    connection,
                    requestId,
                    parties.getValue(party),
                    subjectId,
                    owner
                )
            }
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
                     enforcement_mode, acting_party_cap, granted_features, issued_at, created_at)
                VALUES (?, ?, 'USER', ?, 'PERSONAL', 'ACTIVE', 'ENFORCE', 5, ARRAY ['BUSINESS_FIELDS_AND_SCHEMAS', 'INFORMATION_REQUESTS'], now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                requestId,
                owner,
            )
        }
        return PublishedRequest(
            requestId,
            exchangeId,
            draft.definitionId,
            draft.versionId,
            owner,
            users,
            parties,
            fields,
            participants,
            subjects
        )
    }

    fun shareHolder(request: PublishedRequest, role: String): UUID {
        val userId = store.insertOwner()
        dataSource.connection.use { connection ->
            share(
                connection,
                "INFORMATION_REQUEST",
                request.requestId,
                userId,
                role
            )
        }
        return userId
    }

    fun outsider(): UUID = store.insertOwner()

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

    private fun insertSubjectParty(
        connection: java.sql.Connection,
        requestId: UUID,
        partyId: UUID,
        subjectId: UUID,
        ownerId: UUID
    ) {
        execute(
            connection,
            "INSERT INTO subject_identity_ref (id, owner_type, owner_user_id, subject_kind, created_at) VALUES (?, 'USER', ?, 'RECORD', now())",
            subjectId,
            ownerId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at, created_at, updated_at)
            VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            partyId,
            requestId,
            subjectId,
        )
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

    private companion object {
        const val SUBJECT_ROLE = "SUBJECT"
    }
}
