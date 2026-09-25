package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.SchemaCompatibility
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.narayana.jta.QuarkusTransaction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

data class WalkingSkeletonDraft(
    val definitionId: UUID,
    val versionId: UUID,
    val actorId: UUID,
    val templateKey: String,
)

data class WalkingSkeletonField(
    val fieldDefinitionId: UUID,
    val fieldContractId: UUID,
)

class InformationRequestTemplateWalkingSkeletonStore(
    private val dataSource: DataSource,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
)
{
    fun publish(draft: WalkingSkeletonDraft, configuration: InformationRequestTemplateConfigurationRequest)
    {
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(versionRepository.findById(draft.versionId)!!, configuration)
        }
        QuarkusTransaction.requiringNew().run {
            publicationService(draft).publishTemplate(draft.definitionId)
        }
    }

    fun insertDraft(actorId: UUID, templateKey: String): WalkingSkeletonDraft =
        QuarkusTransaction.requiringNew().call {
            val definition = definitionRepository.save(
                InformationRequestTemplateDefinition().apply {
                    scopeKind = InformationRequestTemplateScopeKind.PERSONAL
                    scopeUserId = actorId
                    namespace = "process-${UUID.randomUUID().toString().take(8)}"
                    this.templateKey = templateKey
                    displayName = templateKey.split("-").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
                    createdByAppUserId = actorId
                },
            )
            WalkingSkeletonDraft(definition.id, insertVersion(definition.id, 1, actorId), actorId, templateKey)
        }

    fun insertNextDraft(published: WalkingSkeletonDraft, versionNumber: Int): WalkingSkeletonDraft =
        QuarkusTransaction.requiringNew().call {
            published.copy(versionId = insertVersion(published.definitionId, versionNumber, published.actorId))
        }

    fun insertOwner(): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, "walking-skeleton-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }
        return id
    }

    fun insertTextField(key: String): WalkingSkeletonField
    {
        val field = WalkingSkeletonField(UUID.randomUUID(), UUID.randomUUID())
        dataSource.connection.use { connection ->
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO field_definition
                    (id, scope_kind, namespace, field_key, status, created_at, updated_at)
                VALUES (?, 'PLATFORM', 'process', ?, 'PUBLISHED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, field.fieldDefinitionId)
                statement.setString(2, "$key-${field.fieldDefinitionId.toString().take(8)}")
                statement.setTimestamp(3, now)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO field_contract
                    (id, field_definition_id, contract_version, value_type, type_contract_version,
                     label, constraints_json, options_json, data_classification, is_searchable,
                     is_filterable, is_sortable, is_reportable, external_aliases_json, created_at)
                VALUES (?, ?, 1, ?, 1, ?, '{}', '[]', ?, FALSE, FALSE, FALSE, FALSE, '[]', ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, field.fieldContractId)
                statement.setObject(2, field.fieldDefinitionId)
                statement.setString(3, FieldValueType.SHORT_TEXT.name)
                statement.setString(4, key.split("-").joinToString(" ") { it.replaceFirstChar(Char::titlecase) })
                statement.setString(5, FieldDataClassification.INTERNAL.name)
                statement.setTimestamp(6, now)
                statement.executeUpdate()
            }
        }
        return field
    }

    fun insertSchemaVersion(actorId: UUID, fields: List<WalkingSkeletonField>): UUID
    {
        val schemaDefinitionId = UUID.randomUUID()
        val schemaVersionId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO schema_definition
                    (id, scope_kind, namespace, schema_key, display_name, target_resource_type,
                     status, created_by_app_user_id, created_at, updated_at)
                VALUES (?, ?, 'process', ?, 'Walking skeleton data', 'INFORMATION_REQUEST',
                        ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, schemaDefinitionId)
                statement.setString(2, FieldScopeKind.PLATFORM.name)
                statement.setString(3, "walking-skeleton-${schemaDefinitionId.toString().take(8)}")
                statement.setString(4, FieldLifecycleStatus.PUBLISHED.name)
                statement.setObject(5, actorId)
                statement.setTimestamp(6, now)
                statement.setTimestamp(7, now)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO schema_version
                    (id, schema_definition_id, version_number, status, compatibility,
                     schema_rules_json, published_at, published_by_app_user_id, created_at)
                VALUES (?, ?, 1, ?, ?, '[]', ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, schemaVersionId)
                statement.setObject(2, schemaDefinitionId)
                statement.setString(3, FieldLifecycleStatus.PUBLISHED.name)
                statement.setString(4, SchemaCompatibility.ADDITIVE.name)
                statement.setTimestamp(5, now)
                statement.setObject(6, actorId)
                statement.setTimestamp(7, now)
                statement.executeUpdate()
            }
            fields.forEachIndexed { index, field ->
                connection.prepareStatement(
                    """
                    INSERT INTO schema_field_binding
                        (id, schema_version_id, field_contract_id, field_definition_id,
                         display_order, is_required, is_read_only, visibility)
                    VALUES (?, ?, ?, ?, ?, TRUE, FALSE, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, schemaVersionId)
                    statement.setObject(3, field.fieldContractId)
                    statement.setObject(4, field.fieldDefinitionId)
                    statement.setInt(5, index + 1)
                    statement.setString(6, FieldDataClassification.INTERNAL.name)
                    statement.executeUpdate()
                }
            }
        }
        return schemaVersionId
    }

    private fun insertVersion(definitionId: UUID, versionNumber: Int, actorId: UUID): UUID =
        versionRepository.save(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = definitionId
                this.versionNumber = versionNumber
                createdByAppUserId = actorId
            },
        ).id

    private fun publicationService(draft: WalkingSkeletonDraft): InformationRequestTemplatePublicationService
    {
        val definition = QuarkusTransaction.requiringNew().call { definitionRepository.findById(draft.definitionId) }
            ?: error("Stored definition not found")
        val authoringService = mock<InformationRequestTemplateAuthoringService>()
        whenever(authoringService.requireMutationContext(draft.definitionId)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(draft.actorId)),
        )
        whenever(authoringService.projectTemplate(any())).thenReturn(mock<InformationRequestTemplateDto>())
        return InformationRequestTemplatePublicationService(
            authoringService,
            definitionRepository,
            versionRepository,
            capabilityRepository,
        )
    }
}
