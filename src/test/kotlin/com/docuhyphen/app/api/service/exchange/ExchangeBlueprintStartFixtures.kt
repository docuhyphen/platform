package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.exchange.model.BlueprintStartOwner
import com.docuhyphen.app.api.service.exchange.model.BlueprintStartTemplate
import com.docuhyphen.app.api.service.exchange.model.StartedRequestRow
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import io.quarkus.narayana.jta.QuarkusTransaction
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class ExchangeBlueprintStartFixtures(
    private val dataSource: DataSource,
    private val store: InformationRequestTemplateWalkingSkeletonStore,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
) {
    fun personalOwner(): BlueprintStartOwner {
        val userId = store.insertOwner()
        subscribePersonally(userId)
        return BlueprintStartOwner(userId, store.insertOwner(), null)
    }

    fun organizationOwner(): BlueprintStartOwner {
        val userId = store.insertOwner()
        subscribePersonally(userId)
        val organizationId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        write { connection ->
            execute(
                connection,
                """
                INSERT INTO organization
                    (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Process owner organization', ?, TRUE, TRUE, now())
                """.trimIndent(),
                organizationId,
                "REG-${organizationId.toString().take(8)}",
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', 'ACTIVE', 5, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                organizationId,
            )
            execute(
                connection,
                """
                INSERT INTO organization_membership
                    (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
                VALUES (?, ?, ?, 'ACTIVE', TRUE, now(), now())
                """.trimIndent(),
                membershipId,
                userId,
                organizationId,
            )
            execute(
                connection,
                "INSERT INTO organization_membership_role (organization_membership_id, role_name) VALUES (?, 'ORG_MEMBER')",
                membershipId,
            )
        }
        return BlueprintStartOwner(userId, store.insertOwner(), organizationId)
    }

    fun personalTemplate(owner: BlueprintStartOwner): BlueprintStartTemplate =
        publish(owner, store.insertDraft(owner.userId, "collection-pattern"))

    fun organizationTemplate(owner: BlueprintStartOwner): BlueprintStartTemplate {
        val organizationId = requireNotNull(owner.organizationId)
        val draft = QuarkusTransaction.requiringNew().call {
            val definition = definitionRepository.save(
                InformationRequestTemplateDefinition().apply {
                    scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
                    scopeOrgId = organizationId
                    namespace = "process-${UUID.randomUUID().toString().take(8)}"
                    templateKey = "organization-collection-pattern"
                    displayName = "Organization collection pattern"
                    createdByPrincipalKind = PrincipalKind.USER
                    createdByPrincipalId = owner.userId
                },
            )
            val version = versionRepository.save(
                InformationRequestTemplateVersion().apply {
                    templateDefinitionId = definition.id
                    versionNumber = 1
                    createdByPrincipalKind = PrincipalKind.USER
                    createdByPrincipalId = owner.userId
                },
            )
            WalkingSkeletonDraft(definition.id, version.id, owner.userId, definition.templateKey)
        }
        return publish(owner, draft)
    }

    fun blueprint(
        owner: BlueprintStartOwner,
        template: BlueprintStartTemplate?,
        organizationScoped: Boolean = false,
        fieldDefault: String? = null,
        documentTitles: List<String> = emptyList(),
        participants: List<Pair<UUID, String>> = emptyList(),
    ): UUID {
        val blueprintId = UUID.randomUUID()
        write { connection ->
            execute(
                connection,
                """
                INSERT INTO blueprint_definition
                    (id, name, scope, organization_id, created_by_app_user_id, config_json, created_at,
                     updated_at, information_request_template_version_id)
                VALUES (?, 'Collection blueprint', ?, ?, ?, '{}', now(), now(), ?)
                """.trimIndent(),
                blueprintId,
                if (organizationScoped) "ORG" else "PERSONAL",
                if (organizationScoped) owner.organizationId else null,
                owner.userId,
                template?.versionId,
            )
            if (fieldDefault != null && template != null) {
                execute(
                    connection,
                    """
                    INSERT INTO blueprint_field_default
                        (id, blueprint_definition_id, field_definition_id, value_type, value_json, display_order)
                    VALUES (?, ?, ?, 'SHORT_TEXT', ?, 0)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    blueprintId,
                    template.fieldDefinitionId,
                    "\"$fieldDefault\"",
                )
            }
            participants.forEachIndexed { index, (userId, role) ->
                execute(
                    connection,
                    """
                    INSERT INTO blueprint_participant_default
                        (id, blueprint_definition_id, principal_kind, principal_id, role_name, display_order)
                    VALUES (?, ?, 'USER', ?, ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    blueprintId,
                    userId.toString(),
                    role,
                    index,
                )
            }
            documentTitles.forEachIndexed { index, title ->
                execute(
                    connection,
                    """
                    INSERT INTO blueprint_document_default
                        (id, blueprint_definition_id, title, restrict_type, required, display_order)
                    VALUES (?, ?, ?, FALSE, TRUE, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    blueprintId,
                    title,
                    index,
                )
            }
        }
        return blueprintId
    }

    fun retire(template: BlueprintStartTemplate, actorId: UUID) {
        write { connection ->
            execute(
                connection,
                """
                UPDATE information_request_template_version
                SET status = 'RETIRED', retired_at = now(), retired_by_principal_kind = 'USER', retired_by_principal_id = ?
                WHERE id = ?
                """.trimIndent(),
                actorId,
                template.versionId,
            )
        }
    }

    fun withdrawInformationRequests(owner: BlueprintStartOwner) {
        write { connection ->
            execute(
                connection,
                """
                INSERT INTO subscription_feature_entitlement
                    (id, owner_type, app_user_id, feature_code, is_enabled, updated_by_app_user_id)
                VALUES (?, 'USER', ?, 'INFORMATION_REQUESTS', FALSE, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                owner.userId,
                owner.userId,
            )
        }
    }

    fun fillOpenRequestCapacity(owner: BlueprintStartOwner, template: BlueprintStartTemplate, openRequests: Int) {
        val exchangeId = UUID.randomUUID()
        write { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date,
                     last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Earlier collection', 'Please respond',
                        'Earlier collection', 'INITIATED')
                """.trimIndent(),
                exchangeId,
                owner.userId,
                owner.userId,
            )
            repeat(openRequests) {
                execute(
                    connection,
                    """
                    INSERT INTO information_request
                        (id, exchange_id, template_version_id, owner_type, owner_user_id, state,
                         gates_exchange_closure, aggregate_revision, party_revision, created_by_principal_kind,
                         created_by_principal_id, created_at, updated_at)
                    VALUES (?, ?, ?, 'USER', ?, 'DRAFT', TRUE, 1, 1, 'USER', ?, now(), now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    exchangeId,
                    template.versionId,
                    owner.userId,
                    owner.userId,
                )
            }
        }
    }

    fun exchangesNamed(name: String): Int =
        read { connection -> count(connection, "SELECT COUNT(*) FROM exchange WHERE name = ?", name) }

    fun documentCount(exchangeId: UUID): Int =
        read { connection ->
            count(
                connection,
                "SELECT COUNT(*) FROM exchange_document WHERE exchange_id = ?",
                exchangeId
            )
        }

    fun requestsFor(exchangeId: UUID): List<StartedRequestRow> =
        read { connection ->
            connection.prepareStatement(
                """
                SELECT id, state, template_version_id, owner_organization_id, owner_user_id
                FROM information_request
                WHERE exchange_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, exchangeId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                StartedRequestRow(
                                    id = rows.getObject(1) as UUID,
                                    state = rows.getString(2),
                                    templateVersionId = rows.getObject(3) as UUID,
                                    ownerOrganizationId = rows.getObject(4) as UUID?,
                                    ownerUserId = rows.getObject(5) as UUID?,
                                ),
                            )
                        }
                    }
                }
            }
        }

    fun requestsOwnedBy(owner: BlueprintStartOwner): Int =
        read { connection ->
            if (owner.organizationId != null)
                count(
                    connection,
                    "SELECT COUNT(*) FROM information_request WHERE owner_organization_id = ?",
                    owner.organizationId
                )
            else
                count(connection, "SELECT COUNT(*) FROM information_request WHERE owner_user_id = ?", owner.userId)
        }

    fun personalRequestsOf(userId: UUID): Int =
        read { connection ->
            count(
                connection,
                "SELECT COUNT(*) FROM information_request WHERE owner_user_id = ?",
                userId
            )
        }

    fun registeredUser(): UUID = store.insertOwner()

    fun partyUsers(requestId: UUID, roleKey: String): Set<String> =
        read { connection ->
            queryStrings(
                connection,
                """
                SELECT principal_id::text
                FROM information_request_party
                WHERE information_request_id = ? AND role_key = ? AND revoked_at IS NULL
                """.trimIndent(),
                requestId,
                roleKey,
            )
        }

    fun decisionMakers(requestId: UUID): Set<String> =
        read { connection ->
            queryStrings(
                connection,
                """
                SELECT principal_id::text
                FROM information_request_party
                WHERE information_request_id = ? AND role_key = 'DECISION_MAKER' AND revoked_at IS NULL
                """.trimIndent(),
                requestId,
            )
        }

    fun requestTextValue(requestId: UUID, template: BlueprintStartTemplate): String? =
        read { connection ->
            queryString(
                connection,
                """
                SELECT text_value
                FROM field_value
                WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND field_contract_id = ?
                """.trimIndent(),
                requestId,
                template.fieldContractId,
            )
        }

    fun placeholderTitles(requestId: UUID): Set<String> =
        read { connection ->
            queryStrings(
                connection,
                "SELECT title FROM information_request_document_placeholder WHERE information_request_id = ?",
                requestId,
            )
        }

    fun blueprintDocumentTitles(blueprintId: UUID): Set<String> =
        read { connection ->
            queryStrings(
                connection,
                "SELECT title FROM blueprint_document_default WHERE blueprint_definition_id = ?",
                blueprintId,
            )
        }

    private fun publish(owner: BlueprintStartOwner, draft: WalkingSkeletonDraft): BlueprintStartTemplate {
        val field = store.insertTextField("recorded-note")
        val schemaVersionId = store.insertSchemaVersion(owner.userId, listOf(field))
        store.publish(draft, configuration(schemaVersionId, field))
        return BlueprintStartTemplate(draft.versionId, field.fieldDefinitionId, field.fieldContractId)
    }

    private fun subscribePersonally(userId: UUID) {
        write { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                userId,
            )
        }
    }

    private fun configuration(schemaVersionId: UUID, field: WalkingSkeletonField) =
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
        )

    private fun count(connection: Connection, sql: String, value: Any): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, value)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun write(block: (Connection) -> Unit) = dataSource.connection.use(block)

    private fun <T> read(block: (Connection) -> T): T = dataSource.connection.use(block)
}
