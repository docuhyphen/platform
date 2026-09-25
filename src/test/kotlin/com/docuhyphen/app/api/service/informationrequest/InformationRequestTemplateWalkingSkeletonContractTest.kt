package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestTemplatePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonContractTest
{
    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var projectionLoader: InformationRequestTemplateProjectionLoader

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `the basic walking skeleton publishes as the current fixture contract`()
    {
        val field = insertTextField("basic-recorded-summary")
        val actorId = insertOwner()
        val schemaVersionId = insertInformationRequestSchemaVersion(actorId, listOf(field))
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures
            .basicFieldDocumentResponseAttestationRequest(schemaVersionId, field.fieldDefinitionId)
        val stored = insertDraft(actorId, fixture.templateKey)

        publish(stored, fixture)

        val projected = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(stored.versionId)!!)
        }

        assertEquals(1, fixture.fixtureContractVersion)
        assertEquals("basic_field_document_response_attestation_request", fixture.fixtureKey)
        assertEquals(fixture.templateKey, stored.templateKey)
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, projected.status)
        assertEquals(schemaVersionId, projected.schemaVersionId)
        assertEquals(fixture.expectedRequirementKeys, projected.sections.flatMap { section ->
            section.requirements.map { requirement -> requirement.requirementKey }
        })
        assertEquals(
            listOf(InformationRequestRequirementType.FIELD),
            projected.sections
                .flatMap { it.requirements }
                .filter { it.collectedFieldDefinitionId == field.fieldDefinitionId }
                .map { it.requirementType },
        )
        assertCapabilities(stored.versionId, fixture.expectedCapabilities)
    }

    @Test
    fun `the stress walking skeleton publishes every current template capability contract`()
    {
        val subjectField = insertTextField("stress-subject-status")
        val delegateField = insertTextField("stress-delegate-note")
        val actorId = insertOwner()
        val schemaVersionId = insertInformationRequestSchemaVersion(
            actorId,
            listOf(subjectField, delegateField),
        )
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures.multiPartyStagedEvidenceRequest(
            schemaVersionId,
            subjectField.fieldDefinitionId,
            delegateField.fieldDefinitionId,
        )
        val stored = insertDraft(actorId, fixture.templateKey)

        publish(stored, fixture)

        val projected = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(stored.versionId)!!)
        }

        assertEquals(1, fixture.fixtureContractVersion)
        assertEquals("multi_party_staged_evidence_request", fixture.fixtureKey)
        assertEquals(fixture.templateKey, stored.templateKey)
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, projected.status)
        assertEquals(fixture.expectedRequirementKeys, projected.sections.flatMap { section ->
            section.requirements.map { requirement -> requirement.requirementKey }
        })
        assertEquals(
            setOf(subjectField.fieldDefinitionId, delegateField.fieldDefinitionId),
            projected.sections
                .flatMap { it.requirements }
                .mapNotNull { it.collectedFieldDefinitionId }
                .toSet(),
        )
        assertCapabilities(stored.versionId, fixture.expectedCapabilities)
    }

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource,
            definitionRepository,
            versionRepository,
            capabilityRepository,
            configurationWriter,
        )
    }

    private fun publish(stored: WalkingSkeletonDraft, fixture: InformationRequestTemplateWalkingSkeletonFixture) =
        store.publish(stored, fixture.configuration)

    private fun assertCapabilities(versionId: UUID, expected: List<InformationRequestCapability>)
    {
        val recorded = QuarkusTransaction.requiringNew().call {
            capabilityRepository.findForVersion(versionId)
        }
        assertEquals(expected.toSet(), recorded.map { it.capabilityKey }.toSet())
        assertTrue(
            recorded.all { it.requiredContractVersion == it.capabilityKey.contractVersion },
            "Every recorded capability must use its current contract version",
        )
    }

    private fun insertDraft(actorId: UUID, templateKey: String) = store.insertDraft(actorId, templateKey)

    private fun insertOwner() = store.insertOwner()

    private fun insertTextField(key: String) = store.insertTextField(key)

    private fun insertInformationRequestSchemaVersion(actorId: UUID, fields: List<WalkingSkeletonField>) =
        store.insertSchemaVersion(actorId, fields)
}
