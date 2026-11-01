package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLifecycleResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestTemplateInstantiationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyAssignmentService
import com.docuhyphen.app.api.service.informationrequest.response.model.FieldAudienceDraft
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.singleFieldConfiguration
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestFieldAudienceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var instantiation: InformationRequestTemplateInstantiationService

    @Inject
    lateinit var lifecycle: InformationRequestLifecycleService

    @Inject
    lateinit var parties: InformationRequestPartyAssignmentService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @Test
    fun `a contributor from outside the owner is refused while the collected Field is not Public`() {
        val draft = draftRequest(FieldDataClassification.INTERNAL)

        val refusal = assertThrows<InformationRequestLifecycleException> {
            assign(draft, InformationRequestShareRoleKey.CONTRIBUTOR, store.insertOwner(), "assign-outside-contributor")
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refusal.reasonCode)
        assertTrue(refusal.message.contains(FIELD_KEY)) { refusal.message }
        assertEquals("0", activeParties(draft))
    }

    @Test
    fun `a Public Field lets the same outside contributor be assigned and the request issue`() {
        val draft = draftRequest(FieldDataClassification.PUBLIC)
        assign(draft, InformationRequestShareRoleKey.CONTRIBUTOR, store.insertOwner(), "assign-public-contributor")

        val issued = issue(draft, "issue-public-field")

        assertEquals(InformationRequestState.ISSUED, issued.request.state)
    }

    @Test
    fun `an outside contributor recorded while the Field was Public keeps the request from issuing once it is not`() {
        val draft = draftRequest(FieldDataClassification.PUBLIC)
        assign(draft, InformationRequestShareRoleKey.CONTRIBUTOR, store.insertOwner(), "assign-before-restriction")
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE schema_field_binding SET visibility = 'INTERNAL' WHERE schema_version_id = ?",
                draft.schemaVersionId,
            )
        }

        val refusal = assertThrows<InformationRequestLifecycleException> { issue(draft, "issue-restricted-field") }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refusal.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestState.DRAFT,
                requireNotNull(requestRepository.findById(draft.requestId)).state
            )
        }
    }

    private fun draftRequest(classification: FieldDataClassification): FieldAudienceDraft {
        val owner = store.insertOwner()
        subscribe(owner)
        val field = store.insertTextField(FIELD_KEY)
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE schema_field_binding SET visibility = ? WHERE schema_version_id = ?",
                classification.name,
                schemaVersionId,
            )
        }
        val template = store.insertDraft(owner, "audience-collection")
        store.publish(template, singleFieldConfiguration(schemaVersionId, field))
        val exchangeId = insertExchange(owner)
        val created = QuarkusTransaction.requiringNew().call {
            instantiation.createFromTemplateVersion(
                CreateInformationRequestFromTemplateVersionCommand(
                    templateVersionId = template.versionId,
                    exchangeId = exchangeId,
                    access = access(owner),
                    idempotencyKey = "create-audience-${UUID.randomUUID()}",
                ),
            )
        }
        val draft = FieldAudienceDraft(created.request.id, owner, schemaVersionId)
        assign(draft, InformationRequestShareRoleKey.DECISION_MAKER, owner, "name-decision-maker")
        return draft
    }

    private fun assign(draft: FieldAudienceDraft, roleKey: InformationRequestShareRoleKey, userId: UUID, key: String) {
        QuarkusTransaction.requiringNew().run {
            parties.assign(
                AssignInformationRequestPartyCommand(
                    requestId = draft.requestId,
                    roleKey = roleKey,
                    principal = PrincipalRef.user(userId),
                    access = access(draft.ownerId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.partiesOf(requireNotNull(requestRepository.findById(draft.requestId))),
                    ),
                    idempotencyKey = "$key-${draft.requestId}",
                ),
            )
        }
    }

    private fun issue(draft: FieldAudienceDraft, key: String): InformationRequestLifecycleResult =
        QuarkusTransaction.requiringNew().call {
            lifecycle.issue(
                IssueInformationRequestCommand(
                    requestId = draft.requestId,
                    access = access(draft.ownerId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(draft.requestId))),
                    ),
                    idempotencyKey = "$key-${draft.requestId}",
                ),
            )
        }

    private fun activeParties(draft: FieldAudienceDraft): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT count(*)::text FROM information_request_party WHERE information_request_id = ? AND role_key = 'CONTRIBUTOR'",
                draft.requestId,
            )
        }

    private fun access(owner: UUID) =
        RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session"))

    private fun subscribe(owner: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner,
            )
        }
    }

    private fun insertExchange(owner: UUID): UUID {
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

    private companion object {
        const val FIELD_KEY = "recorded-note"
    }
}
