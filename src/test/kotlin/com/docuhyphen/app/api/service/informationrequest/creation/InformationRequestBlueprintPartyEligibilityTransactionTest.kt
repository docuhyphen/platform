package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromBlueprintCommand
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestBlueprintPartyEligibilityTransactionTest {
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
    lateinit var blueprintInstantiation: InformationRequestBlueprintInstantiationService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @Test
    fun `a Blueprint default naming a group its author may not share with refuses the request without creating it`() {
        val owner = store.insertOwner()
        val otherOwner = store.insertOwner()
        val blueprintId = blueprintFor(owner)
        defaultParticipant(blueprintId, personalGroupOf(otherOwner))
        val exchangeId = insertExchange(owner)

        val refusal =
            assertThrows(InformationRequestLifecycleException::class.java) { create(blueprintId, exchangeId, owner) }

        assertEquals(InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED, refusal.reasonCode)
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE exchange_id = ?", exchangeId)
            )
        }
    }

    @Test
    fun `a Blueprint default naming the author's own group becomes a party of the created request`() {
        val owner = store.insertOwner()
        val blueprintId = blueprintFor(owner)
        val groupId = personalGroupOf(owner)
        defaultParticipant(blueprintId, groupId)

        val requestId = create(blueprintId, insertExchange(owner), owner)

        dataSource.connection.use { connection ->
            assertEquals(
                "CONTRIBUTOR",
                queryString(
                    connection,
                    "SELECT role_key FROM information_request_party WHERE information_request_id = ? AND principal_kind = 'PRINCIPAL_GROUP' AND principal_id = ?",
                    requestId,
                    groupId,
                ),
            )
        }
    }

    private fun blueprintFor(owner: UUID): UUID {
        subscribe(owner)
        val field = store.insertTextField("recorded-note")
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        val draft = store.insertDraft(owner, "eligible-party-pattern")
        store.publish(
            draft,
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "records",
                        title = "Records",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = "recorded-note",
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Record the note",
                                responseMode = InformationRequestResponseMode.PROVIDE,
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                                collectedFieldDefinitionId = field.fieldDefinitionId,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO blueprint_definition
                    (id, name, scope, created_by_app_user_id, config_json, created_at, updated_at,
                     information_request_template_version_id)
                VALUES (?, 'Collection blueprint', 'PERSONAL', ?, '{}', now(), now(), ?)
                """.trimIndent(),
                id,
                owner,
                draft.versionId,
            )
        }
        return id
    }

    private fun personalGroupOf(owner: UUID): UUID = UUID.randomUUID().also { id ->
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO principal_group (id, name, scope, owner_app_user_id, externally_published, is_active, created_date)
                VALUES (?, 'Process contributors', 'PERSONAL', ?, FALSE, TRUE, now())
                """.trimIndent(),
                id,
                owner,
            )
        }
    }

    private fun defaultParticipant(blueprintId: UUID, groupId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO blueprint_participant_default (id, blueprint_definition_id, principal_kind, principal_id, role_name, display_order)
                VALUES (?, ?, 'PRINCIPAL_GROUP', ?, 'PARTICIPANT', 0)
                """.trimIndent(),
                UUID.randomUUID(),
                blueprintId,
                groupId.toString(),
            )
        }
    }

    private fun create(blueprintId: UUID, exchangeId: UUID, owner: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            blueprintInstantiation.createFromBlueprint(
                CreateInformationRequestFromBlueprintCommand(
                    blueprintDefinitionId = blueprintId,
                    exchangeId = exchangeId,
                    access = RequestAccessContext(
                        PrincipalRef.user(owner),
                        AuthorizationContext(sessionRef = "owner-session")
                    ),
                    idempotencyKey = "create-with-default-party",
                ),
            ).request.id
        }

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
                        'Process collection', 'INITIATED')
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
