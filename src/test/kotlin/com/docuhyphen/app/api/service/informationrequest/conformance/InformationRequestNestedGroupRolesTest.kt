package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
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
class InformationRequestNestedGroupRolesTest {
    @Inject
    lateinit var dataSource: DataSource

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
    lateinit var occurrenceCommands: InformationRequestGroupOccurrenceService

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

    @Test
    fun `the party answering the outer group reorders and removes its entries while no nested entry holds another party's answers`() {
        val request = issue()
        val third = published.addOccurrence(
            occurrenceCommands,
            request,
            request.access(CONTRIBUTOR),
            "entries",
            null,
            "add-third"
        )
        val entries = activeEntries(request)

        QuarkusTransaction.requiringNew().run {
            occurrenceCommands.reorder(
                ReorderInformationRequestGroupOccurrencesCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = "reorder-entries",
                    groupKey = "entries",
                    parentOccurrenceId = null,
                    orderedOccurrenceIds = entries.map { it.id }.reversed(),
                ),
            )
        }
        remove(request, CONTRIBUTOR, third.id, "remove-third")

        assertEquals(listOf("entries[1]", "entries[0]"), activeEntries(request).map { it.occurrencePath })
    }

    @Test
    fun `an entry holding another party's nested answers is not removed by the party answering the entry`() {
        val request = issue()
        val third = published.addOccurrence(
            occurrenceCommands,
            request,
            request.access(CONTRIBUTOR),
            "entries",
            null,
            "add-third"
        )
        published.addOccurrence(occurrenceCommands, request, request.access(PREPARER), "notes", third.id, "add-note")

        assertThrows(ForbiddenException::class.java) { remove(request, CONTRIBUTOR, third.id, "remove-noted-third") }
        assertEquals(3, activeEntries(request).size)
    }

    private fun remove(request: PublishedRequest, party: String, occurrenceId: UUID, key: String) {
        QuarkusTransaction.requiringNew().run {
            occurrenceCommands.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = request.requestId,
                    access = request.access(party),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = key,
                    occurrenceId = occurrenceId,
                ),
            )
        }
    }

    private fun activeEntries(request: PublishedRequest) = QuarkusTransaction.requiringNew().call {
        occurrences.findForRequest(request.requestId)
            .filter { it.parentOccurrenceId == null }
            .sortedBy { it.occurrenceIndex }
    }

    private fun issue(): PublishedRequest =
        published.issue(
            "nested-roles-${UUID.randomUUID().toString().take(8)}",
            emptyList(),
            listOf(CONTRIBUTOR, PREPARER, "DECISION_MAKER")
        ) { _, _ ->
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(
                    InformationRequestTemplateGroupRequest("entries", minOccurrences = 2, maxOccurrences = 4),
                    InformationRequestTemplateGroupRequest(
                        "notes",
                        parentGroupKey = "entries",
                        minOccurrences = 0,
                        maxOccurrences = 3
                    ),
                ),
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "records",
                        title = "Records",
                        requirements = listOf(
                            document("entry-record", "entries", InformationRequestContributorRole.CONTRIBUTOR),
                            document("note-record", "notes", InformationRequestContributorRole.PREPARER),
                        ),
                    ),
                ),
            )
        }

    private fun document(key: String, anchor: String, role: InformationRequestContributorRole) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.DOCUMENT,
            prompt = "Provide this record or explain its absence",
            requiredness = InformationRequestRequiredness.OPTIONAL,
            contributorRole = role,
            occurrenceAnchorKey = anchor,
            permittedDispositions = listOf(InformationRequestResponseDisposition.UNAVAILABLE),
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(minimumFileCount = 1),
        )

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
    }
}
