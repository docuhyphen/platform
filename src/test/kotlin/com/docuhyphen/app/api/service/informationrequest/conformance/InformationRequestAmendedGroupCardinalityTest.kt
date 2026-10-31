package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
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
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionIncompleteException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAmendedGroupCardinalityTest {
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
    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitions, versions, capabilities, writer)
    }

    @Test
    fun `an initial minimum of two provisions two entries`() {
        assertEquals(listOf("entries[0]", "entries[1]"), activePaths(issue(2)))
    }

    @Test
    fun `an unchanged minimum stays submittable after an amendment`() {
        val request = issue(1)
        amendTo(request, 1)
        val services = services(request)
        answer(request, services, "answer-unchanged")

        assertEquals(listOf("entries[0]"), activePaths(request))
        assertTrue(preview(request, services).readiness.ready)
        assertEquals(1, submit(request, services).submission.items.size)
    }

    @Test
    fun `a raised minimum provisions the missing entry while keeping the answered one`() {
        val request = issue(1)
        val services = services(request)
        answer(request, services, "answer-before")
        val original =
            QuarkusTransaction.requiringNew().call { requirements.findForRequest(request.requestId).single() }

        amendTo(request, 2)

        assertEquals(listOf("entries[0]", "entries[1]"), activePaths(request))
        val current = QuarkusTransaction.requiringNew().call { requirements.findForRequest(request.requestId) }
        assertEquals(original.id, current.single { it.occurrencePath == "entries[0]" }.id)
        val readiness = preview(request, services).readiness
        assertFalse(readiness.ready)
        assertEquals(setOf("entries[0]", "entries[1]"), readiness.problems.map { it.occurrencePath }.toSet())
        assertEquals(
            InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE,
            readiness.problems.single { it.occurrencePath == "entries[1]" }.code,
        )
        assertSubmissionBlocked(request, services)

        answer(request, services, "answer-after")
        assertTrue(preview(request, services).readiness.ready)
        assertEquals(2, submit(request, services).submission.items.size)
    }

    @Test
    fun `a group that becomes mandatory is provisioned and cannot close empty`() {
        val request = issue(0)
        assertEquals(emptyList<String>(), activePaths(request))

        amendTo(request, 1)

        assertEquals(listOf("entries[0]"), activePaths(request))
        val services = services(request)
        assertFalse(preview(request, services).readiness.ready)
        assertSubmissionBlocked(request, services)
        answer(request, services, "answer-provisioned")
        assertEquals(1, submit(request, services).submission.items.size)
    }

    @Test
    fun `a raised enclosing minimum provisions the outer entry with its nested minimum`() {
        val request = issue(1, nested = true)
        val services = services(request)
        answer(request, services, "answer-nested-before")

        amendTo(request, 2, nested = true)

        assertEquals(
            listOf("entries[0]", "entries[0]/details[0]", "entries[1]", "entries[1]/details[0]"),
            activePaths(request),
        )
        assertFalse(preview(request, services).readiness.ready)
        assertSubmissionBlocked(request, services)
        answer(request, services, "answer-nested-after")
        assertEquals(2, submit(request, services).submission.items.size)
    }

    @Test
    fun `provisioned entries never reuse the identity of a removed entry`() {
        val request = issue(1)
        val added = published.addOccurrence(
            occurrenceCommands,
            request,
            request.access("CONTRIBUTOR"),
            "entries",
            null,
            "add-second"
        )
        QuarkusTransaction.requiringNew().run {
            occurrenceCommands.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = request.requestId,
                    access = request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = "remove-second",
                    occurrenceId = added.id,
                ),
            )
        }

        amendTo(request, 3)

        assertEquals(listOf("entries[0]", "entries[2]", "entries[3]"), activePaths(request))
    }

    @Test
    fun `an entry added after an amendment never reuses the identity of an entry removed before it`() {
        val request = issue(1)
        published.addOccurrence(
            occurrenceCommands,
            request,
            request.access("CONTRIBUTOR"),
            "entries",
            null,
            "add-second"
        )
        val third = published.addOccurrence(
            occurrenceCommands,
            request,
            request.access("CONTRIBUTOR"),
            "entries",
            null,
            "add-third"
        )
        QuarkusTransaction.requiringNew().run {
            occurrenceCommands.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = request.requestId,
                    access = request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = "remove-third",
                    occurrenceId = third.id,
                ),
            )
        }
        amendTo(request, 1)

        published.addOccurrence(
            occurrenceCommands,
            request,
            request.access("CONTRIBUTOR"),
            "entries",
            null,
            "add-after-amendment"
        )

        assertEquals(listOf("entries[0]", "entries[1]", "entries[3]"), activePaths(request))
        val removedAgain = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().run {
                occurrenceCommands.remove(
                    RemoveInformationRequestGroupOccurrenceCommand(
                        requestId = request.requestId,
                        access = request.access("CONTRIBUTOR"),
                        precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                        idempotencyKey = "remove-third-again",
                        occurrenceId = third.id,
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, removedAgain.reasonCode)
    }

    @Test
    fun `a replayed amendment provisions nothing further`() {
        val request = issue(1)
        amendTo(request, 2)
        val afterFirst = activePaths(request)

        replayAmendment(request)

        assertEquals(afterFirst, activePaths(request))
        assertEquals(2, afterFirst.size)
    }

    @Test
    fun `submission refuses a group below its minimum even when every remaining item is complete`() {
        val request = issue(2)
        val services = services(request)
        answer(request, services, "answer-both")
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE information_request_group_occurrence
                SET removed_at = now(), removed_by_principal_kind = 'USER', removed_by_principal_id = ?
                WHERE information_request_id = ? AND occurrence_path = 'entries[1]'
                """.trimIndent(),
                request.ownerId,
                request.requestId,
            )
        }

        val readiness = preview(request, services).readiness

        assertFalse(readiness.ready)
        assertTrue(readiness.problems.isEmpty())
        val shortfall = readiness.groupProblems.single()
        assertEquals("entries", shortfall.groupKey)
        assertNull(shortfall.parentOccurrencePath)
        assertEquals(2, shortfall.minOccurrences)
        assertEquals(1, shortfall.occurrenceCount)
        val refusal = assertSubmissionBlocked(request, services)
        assertEquals(listOf("entries"), refusal.readiness.groupProblems.map { it.groupKey })
    }

    @Test
    fun `a submitted stage refuses a change to its directly anchored group minimum`() {
        val request = issue(1, layout = Layout.GROUPED_FIRST_STAGE)
        val services = services(request)
        answer(request, services, "answer-direct")
        submit(request, services, "first")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            amendTo(request, 2, layout = Layout.GROUPED_FIRST_STAGE)
        }

        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED, refusal.reasonCode)
    }

    @Test
    fun `a submitted stage refuses a change to an enclosing group minimum`() {
        val request = issue(1, nested = true, layout = Layout.GROUPED_FIRST_STAGE)
        val services = services(request)
        answer(request, services, "answer-enclosed")
        submit(request, services, "first")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            amendTo(request, 2, nested = true, layout = Layout.GROUPED_FIRST_STAGE)
        }

        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED, refusal.reasonCode)
        assertEquals(listOf("entries[0]", "entries[0]/details[0]"), activePaths(request))
    }

    @Test
    fun `an enclosing group change reaching only an open stage is still accepted after an earlier stage`() {
        val request = issue(1, nested = true, layout = Layout.GROUPED_LAST_STAGE)
        val services = services(request)
        answer(request, services, "answer-open-before")
        submit(request, services, "first")

        amendTo(request, 2, nested = true, layout = Layout.GROUPED_LAST_STAGE)

        assertEquals(
            listOf("entries[0]", "entries[0]/details[0]", "entries[1]", "entries[1]/details[0]"),
            activePaths(request),
        )
        assertFalse(preview(request, services, "last").readiness.ready)
        answer(request, services, "answer-open-after") { it.occurrencePath != ROOT }
        assertTrue(preview(request, services, "last").readiness.ready)
        assertEquals(2, submit(request, services, "last").submission.items.size)
    }

    private fun assertSubmissionBlocked(
        request: PublishedRequest,
        services: InformationRequestRuntimeServices,
    ): InformationRequestSubmissionIncompleteException {
        val outcome = runCatching { submit(request, services) }
        val recorded = outcome.getOrNull()
        val refusal = outcome.exceptionOrNull()
        assertTrue(
            refusal is InformationRequestSubmissionIncompleteException,
            "Expected an incomplete submission refusal; got ${recorded?.request?.state} with " +
                    "${recorded?.submission?.items?.size} items and $refusal",
        )
        return refusal as InformationRequestSubmissionIncompleteException
    }

    private fun issue(minimum: Int, nested: Boolean = false, layout: Layout = Layout.WHOLE_PACKAGE): PublishedRequest =
        published.issue(
            "amended-group-${UUID.randomUUID().toString().take(8)}",
            emptyList(),
            listOf("CONTRIBUTOR", "DECISION_MAKER"),
        ) { _, _ -> configuration(minimum, nested, layout) }

    private fun amendTo(
        request: PublishedRequest,
        minimum: Int,
        nested: Boolean = false,
        layout: Layout = Layout.WHOLE_PACKAGE
    ) {
        val draft =
            WalkingSkeletonDraft(request.definitionId, request.templateVersionId, request.ownerId, "amended-group")
        val next = store.insertNextDraft(draft, 2)
        store.publish(next, configuration(minimum, nested, layout))
        QuarkusTransaction.requiringNew().run { services(request).amendments.amend(amendment(request, next.versionId)) }
    }

    private fun replayAmendment(request: PublishedRequest) {
        val target = QuarkusTransaction.requiringNew().call { requests.findById(request.requestId)!!.templateVersionId }
        QuarkusTransaction.requiringNew().run { services(request).amendments.amend(amendment(request, target)) }
    }

    private fun amendment(request: PublishedRequest, targetVersionId: UUID) = AmendInformationRequestCommand(
        requestId = request.requestId,
        targetTemplateVersionId = targetVersionId,
        reasonCode = "collection-revised",
        access = request.access("DECISION_MAKER"),
        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(requests.findById(request.requestId)!!)),
        idempotencyKey = "revise-minimum",
    )

    private fun answer(
        request: PublishedRequest,
        services: InformationRequestRuntimeServices,
        idempotencyKey: String,
        selected: (InformationRequestRequirement) -> Boolean = { true },
    ) {
        QuarkusTransaction.requiringNew().run {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(
                            requests.findById(
                                request.requestId
                            )!!
                        )
                    ),
                    idempotencyKey = idempotencyKey,
                    patches = requirements.findForRequest(request.requestId).filter(selected).map {
                        InformationRequestResponsePatch(
                            requirementId = it.id,
                            disposition = InformationRequestResponseDisposition.UNAVAILABLE,
                            narrative = ResponseNarrativePatch.Set("No recorded material is available for this item"),
                        )
                    },
                ),
            )
        }
    }

    private fun activePaths(request: PublishedRequest): List<String> = QuarkusTransaction.requiringNew().call {
        occurrences.findForRequest(request.requestId).map { it.occurrencePath }.sorted()
    }

    private fun services(request: PublishedRequest) =
        runtime.build(request.requestId, centralAuthorization = authorization)

    private fun preview(
        request: PublishedRequest,
        services: InformationRequestRuntimeServices,
        stageKey: String? = null
    ) =
        QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(request.requestId, stageKey, request.access("CONTRIBUTOR"))
        }

    private fun submit(
        request: PublishedRequest,
        services: InformationRequestRuntimeServices,
        stageKey: String? = null
    ) =
        QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    stageKey = stageKey,
                    access = request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        preview(
                            request,
                            services,
                            stageKey
                        ).submissionETag
                    ),
                    idempotencyKey = "submit-${stageKey ?: "whole"}",
                ),
            )
        }

    private fun configuration(
        minimum: Int,
        nested: Boolean,
        layout: Layout
    ): InformationRequestTemplateConfigurationRequest {
        val grouped = InformationRequestTemplateSectionRequest(
            sectionKey = "records",
            title = "Records",
            submissionStageKey = when (layout) {
                Layout.WHOLE_PACKAGE -> null
                Layout.GROUPED_FIRST_STAGE -> "first"
                Layout.GROUPED_LAST_STAGE -> "last"
            },
            requirements = listOf(
                requirement(
                    "entry-record",
                    InformationRequestRequiredness.REQUIRED,
                    if (nested) "details" else "entries"
                ),
            ),
        )
        val closing = InformationRequestTemplateSectionRequest(
            sectionKey = "closing-records",
            title = "Closing records",
            submissionStageKey = if (layout == Layout.GROUPED_FIRST_STAGE) "last" else "first",
            requirements = listOf(requirement("closing-record", InformationRequestRequiredness.OPTIONAL, null)),
        )
        return InformationRequestTemplateConfigurationRequest(
            submissionMode = if (layout == Layout.WHOLE_PACKAGE) InformationRequestSubmissionMode.WHOLE_PACKAGE
            else InformationRequestSubmissionMode.STAGED,
            groups = listOf(
                InformationRequestTemplateGroupRequest(
                    "entries",
                    minOccurrences = minimum,
                    maxOccurrences = 4
                )
            ) +
                    if (nested) listOf(
                        InformationRequestTemplateGroupRequest(
                            "details",
                            parentGroupKey = "entries",
                            minOccurrences = 1,
                            maxOccurrences = 2
                        ),
                    ) else emptyList(),
            sections = when (layout) {
                Layout.WHOLE_PACKAGE -> listOf(grouped)
                Layout.GROUPED_FIRST_STAGE -> listOf(grouped, closing)
                Layout.GROUPED_LAST_STAGE -> listOf(closing, grouped)
            },
        )
    }

    private fun requirement(key: String, requiredness: InformationRequestRequiredness, anchor: String?) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.DOCUMENT,
            prompt = "Provide this record or explain its absence",
            requiredness = requiredness,
            occurrenceAnchorKey = anchor,
            permittedDispositions = listOf(InformationRequestResponseDisposition.UNAVAILABLE),
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(minimumFileCount = 1),
        )

    private enum class Layout {
        WHOLE_PACKAGE,
        GROUPED_FIRST_STAGE,
        GROUPED_LAST_STAGE,
    }

    private companion object {
        const val ROOT = "root"
    }
}
