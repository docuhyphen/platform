package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.PublishedSchemaAssignmentCommand
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateMaterializationTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var materializer: InformationRequestTemplateMaterializer
    @Inject lateinit var schemaAssignmentService: SchemaAssignmentService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter)
    }

    @Test
    fun `the Exchange owner who creates a request from a Template with a Field Requirement gets its Schema assigned`()
    {
        val draft = draftRequest()

        val materialized = QuarkusTransaction.requiringNew().call {
            materializer.materialize(requireNotNull(requestRepository.findById(draft.requestId)), draft.ownerAccess)
        }

        assertEquals(1, materialized.requirementCount)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM schema_assignment WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND schema_version_id = ?",
                    draft.requestId,
                    draft.schemaVersionId,
                ),
            )
        }
    }

    @Test
    fun `a caller assigning a Schema through the public Fields command still needs the request's edit capability`()
    {
        val draft = draftRequest()

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                schemaAssignmentService.assignPublishedSchemaVersion(
                    PublishedSchemaAssignmentCommand(
                        resource = FieldsResourceRef("INFORMATION_REQUEST", draft.requestId),
                        access = draft.ownerAccess,
                        schemaVersionId = draft.schemaVersionId,
                    ),
                )
            }
        }
    }

    private fun draftRequest(): DraftRequest
    {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-note")
        val schemaVersionId = store.insertSchemaVersion(owner, listOf(field))
        val template = store.insertDraft(owner, "single-field-request")
        store.publish(
            template,
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "answers",
                        title = "Answers",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = "recorded-note",
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Record the note",
                                responseMode = InformationRequestResponseMode.PROVIDE,
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                collectedFieldDefinitionId = field.fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val exchangeId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                     description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect a note', 'Please respond', 'Note collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                exchangeId,
                owner,
                owner,
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                exchangeId,
                owner,
            )
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
                template.versionId,
                owner,
            )
        }
        return DraftRequest(
            requestId = requestId,
            schemaVersionId = schemaVersionId,
            ownerAccess = FieldsAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session")),
        )
    }

    private data class DraftRequest(
        val requestId: UUID,
        val schemaVersionId: UUID,
        val ownerAccess: FieldsAccessContext,
    )
}
