package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.externalsource.InformationRequestImportedValueReconciliationResource
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.model.AmendedExternalValueFixture
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueDecisionService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueIntakeService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueReconciliationService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueRecords
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAmendedExternalValueTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var requirements: InformationRequestRequirementRepository

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
    lateinit var amendments: InformationRequestAmendmentService

    @Inject
    lateinit var responses: InformationRequestResponseDraftService

    @Inject
    lateinit var intake: InformationRequestImportedValueIntakeService

    @Inject
    lateinit var decisions: InformationRequestImportedValueDecisionService

    @Inject
    lateinit var reconciliation: InformationRequestImportedValueReconciliationService

    @Inject
    lateinit var records: InformationRequestImportedValueRecords

    private val support by lazy {
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
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource,
            definitions,
            versions,
            capabilities,
            writer
        )
    }

    @Test
    fun `a value on a Document Requirement the request still asks for reconciles as not comparable`() {
        val fixture = issue()
        val value = propose(fixture, SOURCE_RECORD, "External process record")

        assertEquals(
            listOf(value to InformationRequestReconciliationOutcome.NOT_COMPARABLE),
            outcomes(reconcile(fixture, "current"))
        )
    }

    @Test
    fun `a value whose Requirement an amendment removed is kept with its provenance and left out of reconciliation`() {
        val fixture = issue()
        val removedRequirementId = support.requirement(fixture.request, SOURCE_RECORD).id
        val value = propose(fixture, SOURCE_RECORD, "External process record")

        removeSourceRecord(fixture)

        assertEquals(emptyList<InformationRequestImportedValueReconciliation>(), reconcile(fixture, "after-removal"))
        val kept = QuarkusTransaction.requiringNew().call {
            records.values(fixture.request.requestId, fixture.request.access(REVIEWER))
        }.single()
        assertEquals(value, kept.value.id)
        assertEquals(removedRequirementId, kept.value.informationRequestRequirementId)
        assertEquals("source-system", kept.value.sourceReference)
        assertEquals("source-record-reference", kept.value.provenanceReference)
        assertEquals(1, importedValues(fixture))
    }

    @Test
    fun `the reconciliation command answers through its REST adapter after a Requirement with a value was removed`() {
        val fixture = issue()
        answerSummary(fixture, "Recorded summary", "answer")
        propose(fixture, SOURCE_RECORD, "External process record")
        val matching = propose(fixture, SUMMARY, "Recorded summary")
        removeSourceRecord(fixture)
        val accessContexts = mock<InformationRequestAccessContextFactory>()
        whenever(accessContexts.currentAuthenticated()).thenReturn(fixture.request.access(REVIEWER))

        val response = InformationRequestImportedValueReconciliationResource(reconciliation, accessContexts)
            .reconcile(fixture.request.requestId.toString(), "reconcile-over-http")

        assertEquals(200, response.status)
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Array<InformationRequestImportedValueReconciliationDto>
        assertEquals(
            listOf(matching to InformationRequestReconciliationOutcome.MATCHES),
            body.map { it.importedValueId to it.outcome },
        )
    }

    @Test
    fun `values the request still asks for reconcile beside a value whose Requirement was removed`() {
        val fixture = issue()
        answerSummary(fixture, "Recorded summary", "answer")
        propose(fixture, SOURCE_RECORD, "External process record")
        val differing = propose(fixture, SUMMARY, "Another summary")
        removeSourceRecord(fixture)

        val first = reconcile(fixture, "mixed").single()
        val repeated = reconcile(fixture, "mixed-again").single()

        assertEquals(differing, first.importedValueId)
        assertEquals(InformationRequestReconciliationOutcome.DIFFERS, first.outcome)
        assertEquals("\"Recorded summary\"", first.responseCanonicalValue)
        assertEquals(requireNotNull(first.discrepancyId), repeated.discrepancyId)
        assertEquals(1, discrepancies(fixture))
        assertEquals("Recorded summary", summaryAnswer(fixture))
    }

    @Test
    fun `a reconciliation recorded before the amendment replays its stored outcomes afterwards`() {
        val fixture = issue()
        val value = propose(fixture, SOURCE_RECORD, "External process record")
        val recorded = outcomes(reconcile(fixture, "before-removal"))

        removeSourceRecord(fixture)

        assertEquals(listOf(value to InformationRequestReconciliationOutcome.NOT_COMPARABLE), recorded)
        assertEquals(recorded, outcomes(reconcile(fixture, "before-removal")))
        assertEquals(emptyList<InformationRequestImportedValueReconciliation>(), reconcile(fixture, "after-removal"))
    }

    @Test
    fun `a value rejected before its Requirement was removed keeps its decision and stays out of reconciliation`() {
        val fixture = issue()
        val value = propose(fixture, SOURCE_RECORD, "External process record")
        QuarkusTransaction.requiringNew().run {
            decisions.decide(
                DecideInformationRequestImportedValueCommand(
                    fixture.request.requestId, value, InformationRequestImportedValueDecisionKind.REJECTED,
                    "source-not-used", fixture.request.access(REVIEWER), UUID.randomUUID().toString(),
                )
            )
        }

        removeSourceRecord(fixture)

        assertEquals(emptyList<InformationRequestImportedValueReconciliation>(), reconcile(fixture, "after-removal"))
        val kept = QuarkusTransaction.requiringNew().call {
            records.values(fixture.request.requestId, fixture.request.access(REVIEWER))
        }.single()
        assertEquals(InformationRequestImportedValueDecisionKind.REJECTED, kept.decision?.decision)
    }

    private fun issue(): AmendedExternalValueFixture {
        var schemaVersionId: UUID? = null
        var summary: WalkingSkeletonField? = null
        val request = support.issue(
            "amended-external-value-${UUID.randomUUID().toString().take(8)}",
            listOf(SUMMARY),
            listOf(CONTRIBUTOR, REVIEWER, DECISION_MAKER),
        ) { schema, fields ->
            schemaVersionId = schema
            summary = fields.getValue(SUMMARY)
            configuration(schema, fields.getValue(SUMMARY), withSourceRecord = true)
        }
        return AmendedExternalValueFixture(request, requireNotNull(schemaVersionId), requireNotNull(summary))
    }

    private fun configuration(
        schemaVersionId: UUID,
        summary: WalkingSkeletonField,
        withSourceRecord: Boolean,
    ) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "records", "Records",
                requirements = listOfNotNull(
                    if (withSourceRecord) InformationRequestTemplateRequirementRequest(
                        SOURCE_RECORD, InformationRequestRequirementType.DOCUMENT, "Provide the source record",
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(maximumFileCount = 1),
                    ) else null,
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = SUMMARY,
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = "Summarize the records",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        collectedFieldDefinitionId = summary.fieldDefinitionId,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                ),
            )
        ),
    )

    private fun propose(fixture: AmendedExternalValueFixture, requirementKey: String, value: String): UUID =
        QuarkusTransaction.requiringNew().call {
            intake.propose(
                ProposeInformationRequestImportedValueCommand(
                    fixture.request.requestId,
                    support.requirement(fixture.request, requirementKey).id,
                    "record-label",
                    FieldValueType.SHORT_TEXT,
                    JsonPrimitive(value),
                    "source-system",
                    InformationRequestSourceConfidence.ASSERTED,
                    null,
                    null,
                    "source-record-reference",
                    fixture.request.owner,
                    UUID.randomUUID().toString(),
                )
            )
        }.value.id

    private fun answerSummary(fixture: AmendedExternalValueFixture, answer: String, idempotencyKey: String) {
        val requirementId = support.requirement(fixture.request, SUMMARY).id
        QuarkusTransaction.requiringNew().run {
            responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.requestId,
                    access = fixture.request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requests.findById(fixture.request.requestId))),
                    ),
                    idempotencyKey = idempotencyKey,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(
                                    FieldValueEntry(
                                        fixture.summary.fieldContractId,
                                        JsonPrimitive(answer)
                                    )
                                ),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        )
                    ),
                )
            )
        }
    }

    private fun removeSourceRecord(fixture: AmendedExternalValueFixture) {
        val request = fixture.request
        val removed = support.requirement(request, SOURCE_RECORD).id
        val next = store.insertNextDraft(
            WalkingSkeletonDraft(
                request.definitionId,
                request.templateVersionId,
                request.ownerId,
                "amended-external-value"
            ),
            2,
        )
        store.publish(next, configuration(fixture.schemaVersionId, fixture.summary, withSourceRecord = false))
        QuarkusTransaction.requiringNew().run {
            amendments.amend(
                AmendInformationRequestCommand(
                    requestId = request.requestId,
                    targetTemplateVersionId = next.versionId,
                    reasonCode = "scope-reduced",
                    access = request.access(DECISION_MAKER),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requireNotNull(requests.findById(request.requestId))),
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            )
        }
        assertTrue(QuarkusTransaction.requiringNew().call { requirements.findForRequest(request.requestId) }
            .none { it.id == removed })
    }

    private fun reconcile(
        fixture: AmendedExternalValueFixture,
        idempotencyKey: String
    ): List<InformationRequestImportedValueReconciliation> =
        QuarkusTransaction.requiringNew().call {
            reconciliation.reconcile(
                ReconcileInformationRequestImportedValuesCommand(
                    fixture.request.requestId, fixture.request.access(REVIEWER), idempotencyKey,
                )
            )
        }

    private fun outcomes(results: List<InformationRequestImportedValueReconciliation>) =
        results.map { it.importedValueId to it.outcome }

    private fun importedValues(fixture: AmendedExternalValueFixture): Int =
        count("information_request_imported_value", fixture)

    private fun discrepancies(fixture: AmendedExternalValueFixture): Int =
        count("information_request_imported_value_discrepancy", fixture)

    private fun count(table: String, fixture: AmendedExternalValueFixture): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM $table WHERE information_request_id = ?",
                fixture.request.requestId
            )
        }

    private fun summaryAnswer(fixture: AmendedExternalValueFixture): String? = dataSource.connection.use { connection ->
        queryString(
            connection,
            "SELECT string_agg(text_value, ',') FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
            fixture.request.requestId,
        )
    }

    private companion object {
        const val SOURCE_RECORD = "source-record"
        const val SUMMARY = "summary"
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val REVIEWER = "REVIEWER"
        const val DECISION_MAKER = "DECISION_MAKER"
    }
}
