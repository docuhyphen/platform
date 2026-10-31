package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.model.AmendedFieldOccurrenceFixture
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAmendedFieldOccurrenceTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var requirements: InformationRequestRequirementRepository

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var definitions: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versions: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilities: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var writer: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var authorization: AuthorizationService

    private val published by lazy {
        PublishedRequestSupport(
            dataSource,
            requests,
            requirements,
            definitions,
            versions,
            capabilities,
            writer,
            materializer
        )
    }
    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitions, versions, capabilities, writer)
    }

    @Test
    fun `a raised minimum on a Field group provisions a value set that accepts answers`() {
        val fixture = issue(minimum = 1, withField = true)

        amendTo(fixture, minimum = 2, withField = true, versionNumber = 2)

        assertEquals(listOf("entries[0]", "entries[1]"), activePaths(fixture.request))
        assertEquals(setOf("entries[0]", "entries[1]"), occurrenceValueSets(fixture.request))
        completeAndSubmit(fixture, expectedItems = 4)
    }

    @Test
    fun `an entry provisioned while a Version asks for no Field answers takes them once they return`() {
        val fixture = issue(minimum = 1, withField = true)

        amendTo(fixture, minimum = 2, withField = false, versionNumber = 2)
        assertEquals(listOf("entries[0]", "entries[1]"), activePaths(fixture.request))
        assertEquals(setOf("entries[0]", "entries[1]"), occurrenceValueSets(fixture.request))
        amendTo(fixture, minimum = 2, withField = true, versionNumber = 3)

        assertEquals(setOf("entries[0]", "entries[1]"), occurrenceValueSets(fixture.request))
        completeAndSubmit(fixture, expectedItems = 4)
    }

    @Test
    fun `an amendment provides a value set an existing entry is missing`() {
        val fixture = issue(minimum = 2, withField = true)
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                DELETE FROM field_value_set
                WHERE occurrence_path = 'entries[1]'
                  AND schema_assignment_id IN (SELECT id FROM schema_assignment WHERE resource_id = ?)
                """.trimIndent(),
                fixture.request.requestId,
            )
        }
        assertEquals(setOf("entries[0]"), occurrenceValueSets(fixture.request))

        amendTo(fixture, minimum = 2, withField = true, versionNumber = 2)

        assertEquals(setOf("entries[0]", "entries[1]"), occurrenceValueSets(fixture.request))
        completeAndSubmit(fixture, expectedItems = 4)
    }

    @Test
    fun `a first Field Requirement in an entry group is refused on a request without a Schema`() {
        val fixture = issue(minimum = 1, withField = false)
        val issuedVersion = currentVersion(fixture.request)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            amendTo(fixture, minimum = 2, withField = true, versionNumber = 2)
        }

        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SCHEMA_CHANGED, refusal.reasonCode)
        assertEquals(issuedVersion, currentVersion(fixture.request))
        assertEquals(listOf("entries[0]"), activePaths(fixture.request))
        assertEquals(emptySet<String>(), occurrenceValueSets(fixture.request))
    }

    @Test
    fun `a first Field Requirement at the root is refused on a request without a Schema`() {
        val fixture = issue(minimum = 0, withField = false, atRoot = true)
        val issuedVersion = currentVersion(fixture.request)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            amendTo(fixture, minimum = 0, withField = true, versionNumber = 2, atRoot = true)
        }

        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SCHEMA_CHANGED, refusal.reasonCode)
        assertEquals(issuedVersion, currentVersion(fixture.request))
    }

    @Test
    fun `an amendment is refused while an outside contributor could not answer a Field its target collects`() {
        val fixture = issue(minimum = 1, withField = true)
        val issuedVersion = currentVersion(fixture.request)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE schema_field_binding SET visibility = 'INTERNAL' WHERE schema_version_id = ?",
                fixture.schemaVersionId,
            )
        }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            amendTo(fixture, minimum = 2, withField = true, versionNumber = 2)
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refusal.reasonCode)
        assertEquals(issuedVersion, currentVersion(fixture.request))
        assertEquals(listOf("entries[0]"), activePaths(fixture.request))
    }

    @Test
    fun `a request without a Schema still takes amendments that ask for no Field answers`() {
        val fixture = issue(minimum = 1, withField = false)

        amendTo(fixture, minimum = 2, withField = false, versionNumber = 2)

        assertEquals(listOf("entries[0]", "entries[1]"), activePaths(fixture.request))
        assertEquals(emptySet<String>(), occurrenceValueSets(fixture.request))
        answerRecords(fixture.request, services(fixture.request), "answer-records")
        assertTrue(preview(fixture.request, services(fixture.request)).readiness.ready)
    }

    private fun issue(minimum: Int, withField: Boolean, atRoot: Boolean = false): AmendedFieldOccurrenceFixture {
        var schemaVersionId: UUID? = null
        var label: WalkingSkeletonField? = null
        val request = published.issue(
            "amended-field-${UUID.randomUUID().toString().take(8)}",
            listOf("entry-label"),
            listOf("CONTRIBUTOR", "DECISION_MAKER"),
        ) { schema, fields ->
            schemaVersionId = schema
            label = fields.getValue("entry-label")
            configuration(schema, fields.getValue("entry-label"), minimum, withField, atRoot)
        }
        return AmendedFieldOccurrenceFixture(request, requireNotNull(schemaVersionId), requireNotNull(label))
    }

    @Suppress("LongParameterList")
    private fun amendTo(
        fixture: AmendedFieldOccurrenceFixture,
        minimum: Int,
        withField: Boolean,
        versionNumber: Int,
        atRoot: Boolean = false
    ) {
        val request = fixture.request
        val draft =
            WalkingSkeletonDraft(request.definitionId, currentVersion(request), request.ownerId, "amended-field")
        val next = store.insertNextDraft(draft, versionNumber)
        store.publish(next, configuration(fixture.schemaVersionId, fixture.label, minimum, withField, atRoot))
        QuarkusTransaction.requiringNew().run {
            services(request).amendments.amend(
                AmendInformationRequestCommand(
                    requestId = request.requestId,
                    targetTemplateVersionId = next.versionId,
                    reasonCode = "collection-revised",
                    access = request.access("DECISION_MAKER"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requests.findById(request.requestId)!!),
                    ),
                    idempotencyKey = "amend-to-$versionNumber",
                ),
            )
        }
    }

    private fun completeAndSubmit(fixture: AmendedFieldOccurrenceFixture, expectedItems: Int) {
        val services = services(fixture.request)
        answerRecords(fixture.request, services, "answer-records")
        answerLabels(fixture, services, "answer-labels")
        assertTrue(preview(fixture.request, services).readiness.ready)
        val submitted = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.request.requestId,
                    access = fixture.request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        preview(
                            fixture.request,
                            services
                        ).submissionETag
                    ),
                    idempotencyKey = "submit-whole",
                ),
            )
        }
        assertEquals(expectedItems, submitted.submission.items.size)
    }

    private fun answerRecords(
        request: PublishedRequest,
        services: InformationRequestRuntimeServices,
        idempotencyKey: String
    ) {
        val labels = labelRequirementIds(request)
        patch(request, services, idempotencyKey) {
            requirements.findForRequest(request.requestId).filter { it.sourceTemplateRequirementId !in labels }.map {
                InformationRequestResponsePatch(
                    requirementId = it.id,
                    disposition = InformationRequestResponseDisposition.UNAVAILABLE,
                    narrative = ResponseNarrativePatch.Set("No recorded material is available for this item"),
                )
            }
        }
    }

    private fun answerLabels(
        fixture: AmendedFieldOccurrenceFixture,
        services: InformationRequestRuntimeServices,
        idempotencyKey: String
    ) {
        val labels = labelRequirementIds(fixture.request)
        patch(fixture.request, services, idempotencyKey) {
            requirements.findForRequest(fixture.request.requestId).filter { it.sourceTemplateRequirementId in labels }
                .map {
                    InformationRequestResponsePatch(
                        requirementId = it.id,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                        fieldValues = ResponseFieldValuesPatch(
                            entries = listOf(
                                FieldValueEntry(
                                    fixture.label.fieldContractId,
                                    JsonPrimitive("label ${it.occurrencePath}")
                                )
                            ),
                            precondition = FieldsPrecondition.Unconditioned,
                        ),
                    )
                }
        }
    }

    private fun patch(
        request: PublishedRequest,
        services: InformationRequestRuntimeServices,
        idempotencyKey: String,
        patches: () -> List<InformationRequestResponsePatch>,
    ) {
        QuarkusTransaction.requiringNew().run {
            val selected = patches()
            assertTrue(selected.isNotEmpty(), "No Requirement selected for $idempotencyKey")
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requests.findById(request.requestId)!!),
                    ),
                    idempotencyKey = idempotencyKey,
                    patches = selected,
                ),
            )
        }
    }

    private fun labelRequirementIds(request: PublishedRequest): Set<UUID> = dataSource.connection.use { connection ->
        queryStrings(
            connection,
            "SELECT id::text FROM information_request_template_requirement WHERE template_definition_id = ? AND requirement_key = 'entry-label'",
            request.definitionId,
        )
    }.map(UUID::fromString).toSet()

    private fun occurrenceValueSets(request: PublishedRequest): Set<String> = dataSource.connection.use { connection ->
        queryStrings(
            connection,
            """
            SELECT value_set.occurrence_path FROM field_value_set value_set
            JOIN schema_assignment assignment ON assignment.id = value_set.schema_assignment_id
            WHERE assignment.resource_id = ? AND value_set.set_kind = 'OCCURRENCE'
            """.trimIndent(),
            request.requestId,
        )
    }.toSet()

    private fun currentVersion(request: PublishedRequest): UUID =
        QuarkusTransaction.requiringNew().call { requests.findById(request.requestId)!!.templateVersionId }

    private fun activePaths(request: PublishedRequest): List<String> = QuarkusTransaction.requiringNew().call {
        occurrences.findForRequest(request.requestId).map { it.occurrencePath }.sorted()
    }

    private fun services(request: PublishedRequest) =
        runtime.build(request.requestId, centralAuthorization = authorization)

    private fun preview(request: PublishedRequest, services: InformationRequestRuntimeServices) =
        QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(request.requestId, null, request.access("CONTRIBUTOR"))
        }

    @Suppress("LongParameterList")
    private fun configuration(
        schemaVersionId: UUID,
        label: WalkingSkeletonField,
        minimum: Int,
        withField: Boolean,
        atRoot: Boolean,
    ): InformationRequestTemplateConfigurationRequest {
        val anchor = if (atRoot) null else "entries"
        return InformationRequestTemplateConfigurationRequest(
            schemaVersionId = schemaVersionId,
            groups = if (atRoot) emptyList()
            else listOf(
                InformationRequestTemplateGroupRequest(
                    "entries",
                    minOccurrences = minimum,
                    maxOccurrences = 3
                )
            ),
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "entries",
                    title = "Entries",
                    requirements = listOfNotNull(
                        InformationRequestTemplateRequirementRequest(
                            requirementKey = "entry-record",
                            requirementType = InformationRequestRequirementType.DOCUMENT,
                            prompt = "Provide this record or explain its absence",
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            occurrenceAnchorKey = anchor,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.UNAVAILABLE),
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(minimumFileCount = 1),
                        ),
                        if (withField) InformationRequestTemplateRequirementRequest(
                            requirementKey = "entry-label",
                            requirementType = InformationRequestRequirementType.FIELD,
                            prompt = "Name the entry",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            occurrenceAnchorKey = anchor,
                            collectedFieldDefinitionId = label.fieldDefinitionId,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        ) else null,
                    ),
                ),
            ),
        )
    }
}
