package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateSection
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestTemplatePostgreSQLResource
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestTemplatePublicationContractTest
{
    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var sectionRepository: InformationRequestTemplateSectionRepository

    @Inject
    lateinit var requirementRepository: InformationRequestTemplateRequirementRepository

    @Inject
    lateinit var bindingRepository: InformationRequestTemplateRequirementBindingRepository

    @Inject
    lateinit var dispositionRepository: InformationRequestTemplateBindingDispositionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var projectionLoader: InformationRequestTemplateProjectionLoader

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `a draft freezes with the capability set derived by storage`()
    {
        val fixture = insertDraft(InformationRequestRequirementType.RESPONSE_ATTESTATION)
        val service = publicationService(fixture)

        QuarkusTransaction.requiringNew().run {
            service.publishTemplate(fixture.definitionId)
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.PUBLISHED,
                versionRepository.findById(fixture.versionId)?.status,
            )
            assertEquals(
                InformationRequestTemplateStatus.PUBLISHED,
                definitionRepository.findById(fixture.definitionId)?.status,
            )
            assertEquals(
                listOf(
                    InformationRequestCapability.RESPONSE_ATTESTATION,
                    InformationRequestCapability.RESPONSE_SUBMISSION,
                ),
                capabilityRepository.findForVersion(fixture.versionId).map { it.capabilityKey },
            )
            assertEquals(
                listOf(1, 1),
                capabilityRepository.findForVersion(fixture.versionId)
                    .map { it.requiredContractVersion },
            )
        }
    }

    @Test
    fun `a refused freeze rolls capability rows and both statuses back together`()
    {
        val fixture = insertDraft(InformationRequestRequirementType.FIELD)
        val service = publicationService(fixture)

        assertThrows<RuntimeException> {
            QuarkusTransaction.requiringNew().run {
                service.publishTemplate(fixture.definitionId)
            }
        }

        QuarkusTransaction.requiringNew().run {
            val version = versionRepository.findById(fixture.versionId)
            assertEquals(InformationRequestTemplateStatus.DRAFT, version?.status)
            assertNull(version?.publishedAt)
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                definitionRepository.findById(fixture.definitionId)?.status,
            )
            assertEquals(emptyList<InformationRequestCapability>(),
                capabilityRepository.findForVersion(fixture.versionId).map { it.capabilityKey })
        }
    }

    @Test
    fun `a published configuration becomes a fresh draft without copied capability rows`()
    {
        val fixture = insertDraft(InformationRequestRequirementType.RESPONSE_ATTESTATION)
        QuarkusTransaction.requiringNew().run {
            publicationService(fixture).publishTemplate(fixture.definitionId)
        }

        QuarkusTransaction.requiringNew().run {
            lifecycleService(fixture).createDraftVersion(fixture.definitionId, 1)
        }

        QuarkusTransaction.requiringNew().run {
            val versions = versionRepository.findForDefinitions(listOf(fixture.definitionId))
            val source = versions.single { it.versionNumber == 1 }
            val draft = versions.single { it.versionNumber == 2 }

            assertEquals(InformationRequestTemplateStatus.PUBLISHED, source.status)
            assertEquals(InformationRequestTemplateStatus.DRAFT, draft.status)
            assertEquals(
                InformationRequestTemplateConfigurationMapper.toRequest(
                    projectionLoader.loadVersion(source),
                ),
                InformationRequestTemplateConfigurationMapper.toRequest(
                    projectionLoader.loadVersion(draft),
                ),
            )
            assertEquals(emptyList<InformationRequestCapability>(),
                capabilityRepository.findForVersion(draft.id).map { it.capabilityKey })
        }
    }

    private fun publicationService(fixture: StoredDraft): InformationRequestTemplatePublicationService
    {
        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.findById(fixture.definitionId)
        } ?: error("Stored definition not found")
        val authoringService = mock<InformationRequestTemplateAuthoringService>()
        whenever(authoringService.requireMutationContext(fixture.definitionId)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(fixture.actorId)),
        )
        whenever(authoringService.projectTemplate(any())).thenReturn(mock<InformationRequestTemplateDto>())
        return InformationRequestTemplatePublicationService(
            authoringService,
            definitionRepository,
            versionRepository,
            capabilityRepository,
        )
    }

    private fun lifecycleService(fixture: StoredDraft): InformationRequestTemplateLifecycleService
    {
        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.findById(fixture.definitionId)
        } ?: error("Stored definition not found")
        val authoringService = mock<InformationRequestTemplateAuthoringService>()
        whenever(authoringService.requireMutationContext(fixture.definitionId)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(fixture.actorId)),
        )
        whenever(authoringService.projectTemplate(any())).thenReturn(mock<InformationRequestTemplateDto>())
        return InformationRequestTemplateLifecycleService(
            authoringService,
            definitionRepository,
            versionRepository,
            configurationWriter,
            projectionLoader,
        )
    }

    private fun insertDraft(type: InformationRequestRequirementType): StoredDraft
    {
        val actorId = insertOwner()
        return QuarkusTransaction.requiringNew().call {
            val definition = definitionRepository.save(
                InformationRequestTemplateDefinition().apply {
                    scopeKind = InformationRequestTemplateScopeKind.PERSONAL
                    scopeUserId = actorId
                    namespace = "process-${UUID.randomUUID().toString().take(8)}"
                    templateKey = "collection-pattern"
                    displayName = "Collection pattern"
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
            val section = sectionRepository.save(
                InformationRequestTemplateSection().apply {
                    templateVersionId = version.id
                    sectionKey = "collected-data"
                    displayOrder = 1
                    title = "Collected data"
                },
            )
            val requirement = requirementRepository.save(
                InformationRequestTemplateRequirement().apply {
                    templateDefinitionId = definition.id
                    requirementKey = "recorded-assertion"
                    requirementType = type
                },
            )
            val binding = bindingRepository.save(
                InformationRequestTemplateRequirementBinding().apply {
                    templateVersionId = version.id
                    templateDefinitionId = definition.id
                    templateRequirementId = requirement.id
                    templateSectionId = section.id
                    displayOrder = 1
                    prompt = "State the recorded assertion"
                    collectedFieldDefinitionId =
                        if (type == InformationRequestRequirementType.FIELD) insertFieldDefinition() else null
                },
            )
            // The authored path stores the plain provided answer for a requirement whose party can
            // answer, so the fixture states it too and a copy of this version reads back unchanged.
            dispositionRepository.save(
                InformationRequestTemplateBindingDisposition().apply {
                    templateBindingId = binding.id
                    templateVersionId = version.id
                    disposition = InformationRequestResponseDisposition.PROVIDED
                },
            )
            StoredDraft(definition.id, version.id, actorId)
        }
    }

    /**
     * A typed requirement has to name the field it collects before it can be stored at all, which
     * is a separate rule from the publication rules these tests are about.
     */
    private fun insertFieldDefinition(): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO field_definition
                    (id, scope_kind, namespace, field_key, status, created_at, updated_at)
                VALUES (?, 'PLATFORM', 'process', ?, 'PUBLISHED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setObject(1, id)
                statement.setString(2, "recorded-note-${id.toString().take(8)}")
                statement.setTimestamp(3, now)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        }
        return id
    }

    private fun insertOwner(): UUID
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
                statement.setString(3, "publication-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }
        return id
    }

    private data class StoredDraft(
        val definitionId: UUID,
        val versionId: UUID,
        val actorId: UUID,
    )
}
