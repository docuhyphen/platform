package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromBlueprintCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.template.InformationRequestTemplateResource
import com.docuhyphen.app.api.resource.model.CloneInformationRequestTemplateRequest
import com.docuhyphen.app.api.resource.model.InformationRequestTemplateNewVersionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestAdHocCreationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestBlueprintInstantiationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestTemplateInstantiationService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestPrivateTemplateIsolationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitions: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versions: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilities: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var writer: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var creation: InformationRequestAdHocCreationService

    @Inject
    lateinit var instantiation: InformationRequestTemplateInstantiationService

    @Inject
    lateinit var blueprintInstantiation: InformationRequestBlueprintInstantiationService

    @Inject
    lateinit var blueprints: BlueprintDefinitionService

    @Inject
    lateinit var authoring: InformationRequestTemplateAuthoringService

    @Inject
    lateinit var publication: InformationRequestTemplatePublicationService

    @Inject
    lateinit var lifecycle: InformationRequestTemplateLifecycleService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitions, versions, capabilities, writer)
    }

    @ParameterizedTest
    @ValueSource(strings = ["template", "blueprint-create", "blueprint-update", "blueprint-resolve", "blueprint-snapshot", "blueprint-instantiate"])
    fun `private versions refuse reuse without changing requests or Blueprints`(operation: String) {
        val owner = store.insertOwner()
        subscribe(owner)
        val source = createPrivateRequest(owner)
        val targetExchangeId = insertExchange(owner)
        val blueprintId = insertBlueprint(owner, source.request.templateVersionId.takeIf {
            operation in setOf("blueprint-resolve", "blueprint-snapshot", "blueprint-instantiate")
        })
        actAs(owner)
        val before = snapshot(owner)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            when (operation) {
                "template" -> instantiation.createFromTemplateVersion(
                    CreateInformationRequestFromTemplateVersionCommand(
                        source.request.templateVersionId,
                        targetExchangeId,
                        access = access(owner),
                        idempotencyKey = "private-reuse",
                    )
                )

                "blueprint-create" -> blueprints.createBlueprint(
                    CreateBlueprintRequest(
                        name = "Collection blueprint", configJson = "{}", scope = "PERSONAL",
                        informationRequestTemplateVersionId = source.request.templateVersionId,
                    ), AdminApprovalContext()
                )

                "blueprint-update" -> blueprints.updateBlueprint(
                    blueprintId, UpdateBlueprintRequest(
                        informationRequestTemplateVersionId = source.request.templateVersionId,
                    ), AdminApprovalContext()
                )

                "blueprint-resolve" -> blueprints.resolveInformationRequestTemplateVersionForInstantiation(blueprintId)
                "blueprint-snapshot" -> blueprints.loadInformationRequestInstantiationSnapshot(
                    blueprintId, access(owner).principal, access(owner).authorization,
                )

                else -> blueprintInstantiation.createFromBlueprint(
                    CreateInformationRequestFromBlueprintCommand(
                        blueprintId, targetExchangeId, access = access(owner), idempotencyKey = "private-reuse",
                    )
                )
            }
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, refusal.code)
        assertEquals(before, snapshot(owner))
        dataSource.connection.use { connection ->
            assertEquals(
                "0", queryString(
                    connection,
                    "SELECT count(*)::text FROM command_receipt WHERE resource_id = ?", targetExchangeId
                )
            )
            assertEquals(
                "0", queryString(
                    connection,
                    "SELECT count(*)::text FROM information_request WHERE exchange_id = ?", targetExchangeId
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["read", "configure", "publish", "new-version", "retire", "clone"])
    fun `reusable Template routes return not found for a private definition without altering its record`(operation: String) {
        val owner = store.insertOwner()
        subscribe(owner)
        val source = createPrivateRequest(owner)
        val definitionId = QuarkusTransaction.requiringNew().call {
            requireNotNull(versions.findById(source.request.templateVersionId)).templateDefinitionId
        }
        actAs(owner)
        val before = snapshot(owner)
        val resource = InformationRequestTemplateResource(authoring, publication, lifecycle)

        val response = when (operation) {
            "read" -> resource.get(definitionId.toString())
            "configure" -> resource.replaceDraftConfiguration(definitionId.toString(), configuration(), "*")
            "publish" -> resource.publishDraft(definitionId.toString(), "*", "private-publish")
            "new-version" -> resource.createDraftVersion(
                definitionId.toString(),
                InformationRequestTemplateNewVersionRequest(1),
                "private-version"
            )

            "retire" -> resource.retireVersion(definitionId.toString(), "1", "private-retire")
            else -> resource.clone(
                definitionId.toString(), CloneInformationRequestTemplateRequest(
                    1,
                    CreateInformationRequestTemplateRequest(
                        "process",
                        "copied-collection",
                        "Copied collection",
                        scopeKind = InformationRequestTemplateScopeKind.PERSONAL
                    ),
                ), "private-clone"
            )
        }

        assertEquals(403, response.status)
        assertEquals(
            "Access denied to information request template configuration",
            (response.entity as ResponseError).errorMessage
        )
        assertEquals(before, snapshot(owner))
    }

    private fun createPrivateRequest(owner: UUID) = creation.createAdHoc(
        CreateAdHocInformationRequestCommand(
            insertExchange(owner),
            "Record collection",
            configuration = configuration(),
            access = access(owner),
            idempotencyKey = "private-source",
        )
    )

    private fun configuration() = InformationRequestTemplateConfigurationRequest(
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "records", "Records", requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        "supporting-record",
                        InformationRequestRequirementType.DOCUMENT,
                        "Provide the supporting record",
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest()
                    ),
                )
            ),
        )
    )

    private fun access(owner: UUID) =
        RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session"))

    private fun actAs(owner: UUID) {
        val context = mock<AuthTokenContext>()
        whenever(context.authToken).thenReturn(AuthToken().apply { appUser = AppUser().apply { id = owner } })
        QuarkusMock.installMockForType(context, AuthTokenContext::class.java)
        QuarkusMock.installMockForType(mock<AdminActionGuardService>(), AdminActionGuardService::class.java)
    }

    private fun subscribe(owner: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner
            )
        }
    }

    private fun insertExchange(owner: UUID): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection, """
                INSERT INTO exchange (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in,
                    created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect records', 'Please respond', 'Record collection', 'INITIATED')
            """.trimIndent(), id, owner, owner
            )
            execute(
                connection, """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(), UUID.randomUUID(), id, owner
            )
        }
        return id
    }

    private fun insertBlueprint(owner: UUID, versionId: UUID?): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection, """
                INSERT INTO blueprint_definition (id, name, scope, created_by_app_user_id, config_json,
                    created_at, updated_at, information_request_template_version_id)
                VALUES (?, 'Record blueprint', 'PERSONAL', ?, '{}', now(), now(), ?)
            """.trimIndent(), id, owner, versionId
            )
        }
        return id
    }

    private fun snapshot(owner: UUID): List<String?> = dataSource.connection.use { connection ->
        listOf(
            queryString(connection, "SELECT count(*)::text FROM information_request WHERE owner_user_id = ?", owner),
            queryString(
                connection,
                "SELECT count(*)::text FROM information_request_template_definition WHERE scope_user_id = ?",
                owner
            ),
            queryString(
                connection, """
                SELECT string_agg(v.id::text || ':' || v.status, ',' ORDER BY v.id)
                FROM information_request_template_version v JOIN information_request_template_definition d ON d.id = v.template_definition_id
                WHERE d.scope_user_id = ?
            """.trimIndent(), owner
            ),
            queryString(
                connection, """
                SELECT string_agg(id::text || ':' || COALESCE(information_request_template_version_id::text, ''), ',' ORDER BY id)
                FROM blueprint_definition WHERE created_by_app_user_id = ?
            """.trimIndent(), owner
            ),
        )
    }
}
