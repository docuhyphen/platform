package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import com.docuhyphen.app.api.service.informationrequest.template.singleFieldConfiguration
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestPersonalOwnerTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var instantiation: InformationRequestTemplateInstantiationService
    @Inject lateinit var lifecycle: InformationRequestLifecycleService
    @Inject lateinit var parties: InformationRequestPartyService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @Test
    fun `a personally owned Exchange creates and issues a request from a personal Template on a platform Schema`()
    {
        val owner = store.insertOwner()
        subscribe(owner)
        val field = store.insertTextField("recorded-note")
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        val template = store.insertDraft(owner, "personal-collection")
        store.publish(template, singleFieldConfiguration(schemaVersionId, field))
        val exchangeId = insertExchange(owner)

        val created = QuarkusTransaction.requiringNew().call {
            instantiation.createFromTemplateVersion(
                CreateInformationRequestFromTemplateVersionCommand(
                    templateVersionId = template.versionId,
                    exchangeId = exchangeId,
                    access = access(owner),
                    idempotencyKey = "create-personal",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            parties.assign(
                AssignInformationRequestPartyCommand(
                    requestId = created.request.id,
                    roleKey = InformationRequestShareRoleKey.DECISION_MAKER,
                    principal = PrincipalRef.user(owner),
                    access = access(owner),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.partiesOf(requireNotNull(requestRepository.findById(created.request.id))),
                    ),
                    idempotencyKey = "name-decision-maker",
                ),
            )
        }
        val issued = QuarkusTransaction.requiringNew().call {
            lifecycle.issue(
                IssueInformationRequestCommand(
                    requestId = created.request.id,
                    access = access(owner),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(created.request.id))),
                    ),
                    idempotencyKey = "issue-personal",
                ),
            )
        }

        assertEquals(InformationRequestState.ISSUED, issued.request.state)
        dataSource.connection.use { connection ->
            assertEquals(
                "USER|$owner|PERSONAL|10|100|${250L * 1024L * 1024L}",
                queryString(
                    connection,
                    """
                    SELECT owner_type || '|' || owner_user_id || '|' || plan_code || '|' || acting_party_cap || '|' ||
                           evidence_file_allowance || '|' || evidence_byte_allowance
                    FROM request_execution_grant WHERE request_id = ?
                    """.trimIndent(),
                    created.request.id,
                ),
            )
        }
    }

    @Test
    fun `a platform Version is refused on a personally owned Exchange until it is copied`()
    {
        val owner = store.insertOwner()
        subscribe(owner)
        val field = store.insertTextField("platform-note")
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        val platform = insertPlatformDraft(owner)
        store.publish(platform, singleFieldConfiguration(schemaVersionId, field))
        val exchangeId = insertExchange(owner)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            QuarkusTransaction.requiringNew().call {
                instantiation.createFromTemplateVersion(
                    CreateInformationRequestFromTemplateVersionCommand(
                        templateVersionId = platform.versionId,
                        exchangeId = exchangeId,
                        access = access(owner),
                        idempotencyKey = "create-from-platform",
                    ),
                )
            }
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.PLATFORM_COPY_REQUIRED, refusal.code)
    }

    private fun access(owner: UUID) =
        RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session"))

    private fun insertPlatformDraft(actorId: UUID): WalkingSkeletonDraft =
        QuarkusTransaction.requiringNew().call {
            val definition = definitionRepository.save(
                InformationRequestTemplateDefinition().apply {
                    scopeKind = InformationRequestTemplateScopeKind.PLATFORM
                    namespace = "process-${UUID.randomUUID().toString().take(8)}"
                    templateKey = "platform-collection"
                    displayName = "Platform collection"
                    createdByAppUserId = actorId
                },
            )
            val version = versionRepository.save(
                InformationRequestTemplateVersion().apply {
                    templateDefinitionId = definition.id
                    versionNumber = 1
                    createdByAppUserId = actorId
                },
            )
            WalkingSkeletonDraft(definition.id, version.id, actorId, "platform-collection")
        }

    private fun subscribe(owner: UUID)
    {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner,
            )
        }
    }

    private fun insertExchange(owner: UUID): UUID
    {
        val id = UUID.randomUUID()
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
                id,
                owner,
                owner,
            )
            execute(
                connection,
                """
                INSERT INTO share
                    (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                id,
                owner,
            )
        }
        return id
    }
}
