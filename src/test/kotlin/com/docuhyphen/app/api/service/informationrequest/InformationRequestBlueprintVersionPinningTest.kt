package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestBlueprintVersionPinningTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var blueprintInstantiation: InformationRequestBlueprintInstantiationService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @Test
    fun `re-pointing a Blueprint changes only later requests while a created request keeps its pinned Version`()
    {
        val owner = store.insertOwner()
        subscribe(owner)
        val field = store.insertTextField("recorded-note")
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        val first = store.insertDraft(owner, "collection-pattern")
        store.publish(first, configuration(schemaVersionId, field, "recorded-note", "Record the note"))
        val second = store.insertDraft(owner, "revised-collection-pattern")
        store.publish(second, configuration(schemaVersionId, field, "revised-note", "Record the revised note"))
        val blueprintId = insertBlueprint(owner, first.versionId)
        val exchangeId = insertExchange(owner)

        val earlier = create(blueprintId, exchangeId, owner, "earlier")
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE blueprint_definition SET information_request_template_version_id = ? WHERE id = ?",
                second.versionId,
                blueprintId,
            )
        }
        val later = create(blueprintId, exchangeId, owner, "later")

        dataSource.connection.use { connection ->
            assertEquals(first.versionId.toString(), pinnedVersionOf(connection, earlier))
            assertEquals(setOf(first.versionId.toString()), requirementVersionsOf(connection, earlier))
            assertEquals(setOf("recorded-note"), requirementKeysOf(connection, earlier))
            assertEquals(second.versionId.toString(), pinnedVersionOf(connection, later))
            assertEquals(setOf(second.versionId.toString()), requirementVersionsOf(connection, later))
            assertEquals(setOf("revised-note"), requirementKeysOf(connection, later))
        }
    }

    private fun create(blueprintId: UUID, exchangeId: UUID, owner: UUID, key: String): UUID =
        QuarkusTransaction.requiringNew().call {
            blueprintInstantiation.createFromBlueprint(
                CreateInformationRequestFromBlueprintCommand(
                    blueprintDefinitionId = blueprintId,
                    exchangeId = exchangeId,
                    access = RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session")),
                    idempotencyKey = key,
                ),
            ).request.id
        }

    private fun pinnedVersionOf(connection: java.sql.Connection, requestId: UUID): String? =
        queryString(connection, "SELECT template_version_id::text FROM information_request WHERE id = ?", requestId)

    private fun requirementVersionsOf(connection: java.sql.Connection, requestId: UUID): Set<String> =
        queryStrings(
            connection,
            "SELECT source_template_version_id::text FROM information_request_requirement_revision WHERE information_request_id = ?",
            requestId,
        )

    private fun requirementKeysOf(connection: java.sql.Connection, requestId: UUID): Set<String> =
        queryStrings(
            connection,
            """
            SELECT template_requirement.requirement_key
            FROM information_request_requirement requirement
            JOIN information_request_template_requirement template_requirement
                ON template_requirement.id = requirement.source_template_requirement_id
            WHERE requirement.information_request_id = ?
            """.trimIndent(),
            requestId,
        )

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

    private fun insertBlueprint(owner: UUID, templateVersionId: UUID): UUID
    {
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
                templateVersionId,
            )
        }
        return id
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

    private fun configuration(
        schemaVersionId: UUID,
        field: WalkingSkeletonField,
        requirementKey: String,
        prompt: String,
    ) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "records",
                title = "Records",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = requirementKey,
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = prompt,
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                        collectedFieldDefinitionId = field.fieldDefinitionId,
                    ),
                ),
            ),
        ),
    )
}
