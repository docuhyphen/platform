package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition.EXCEPTION_REQUESTED
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition.PARTIALLY_PROVIDED
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition.PROVIDED
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition.UNAVAILABLE
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition.WAIVED
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ItemizedStagedSubmissionRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var materializer: InformationRequestTemplateMaterializer
    @Inject lateinit var contentCollector: InformationRequestSubmissionContentCollector
    @Inject lateinit var authorizationService: AuthorizationService

    private val published by lazy {
        PublishedRequestSupport(
            dataSource, requestRepository, requirementRepository, definitionRepository, versionRepository, capabilityRepository,
            configurationWriter, materializer,
        )
    }

    @Test
    fun `each item accepts only its configured dispositions, a submitted stage locks while the later stage stays editable, and each package manifest is exact`() =
        itemizedSubmission(unregistered = false)

    @Test
    fun `an unregistered participant contributor meets the same dispositions, stage locks, and exact manifests`() =
        itemizedSubmission(unregistered = true)

    private fun itemizedSubmission(unregistered: Boolean)
    {
        val participants = if (unregistered) setOf(CONTRIBUTOR) else emptySet()
        val request = published.issue("itemized-staged-submission-request", FIELD_ITEMS + LATER_ITEM, listOf(CONTRIBUTOR), participants) { schemaVersionId, fields ->
            configuration(schemaVersionId, fields.mapValues { it.value.fieldDefinitionId })
        }
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val id = { key: String -> published.requirement(request, key).id }

        val notPermitted = assertThrows(InformationRequestLifecycleException::class.java) {
            patch(services, request, "unconfigured", listOf(InformationRequestResponsePatch(id(PROVIDED_ITEM), UNAVAILABLE, ResponseNarrativePatch.Set("Not held"))))
        }
        assertEquals(InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED, notPermitted.reasonCode)
        val unexplained = assertThrows(InformationRequestLifecycleException::class.java) {
            patch(services, request, "unexplained", listOf(InformationRequestResponsePatch(id(PARTIAL_ITEM), PARTIALLY_PROVIDED)))
        }
        assertEquals(InformationRequestErrorCatalog.RESPONSE_NARRATIVE_REQUIRED, unexplained.reasonCode)

        val answers = mapOf(
            PROVIDED_ITEM to (PROVIDED to null),
            PARTIAL_ITEM to (PARTIALLY_PROVIDED to "Two of three periods are held"),
            UNAVAILABLE_ITEM to (UNAVAILABLE to "The record was never issued"),
            EXCEPTION_ITEM to (EXCEPTION_REQUESTED to "An exception applies to this period"),
            REFERENCED_ITEM to (SATISFIED_BY_REFERENCE to "Given in the earlier package"),
            WAIVED_ITEM to (WAIVED to "The record does not exist for this subject"),
        )
        patch(
            services,
            request,
            "itemized-answers",
            answers.map { (key, answer) ->
                val (disposition, narrative) = answer
                InformationRequestResponsePatch(
                    requirementId = id(key),
                    disposition = disposition,
                    narrative = narrative?.let(ResponseNarrativePatch::Set) ?: ResponseNarrativePatch.Unchanged,
                    fieldValues = if (key in FIELD_ITEMS && disposition in setOf(PROVIDED, PARTIALLY_PROVIDED))
                        ResponseFieldValuesPatch(listOf(FieldValueEntry(request.fields.getValue(key).fieldContractId, JsonPrimitive("value of $key"))), FieldsPrecondition.Unconditioned)
                    else null,
                )
            },
        )

        val firstETag = submissionETag(request, FIRST_STAGE)
        val first = submit(services, request, FIRST_STAGE, firstETag, "submit-first-stage")
        val firstPackage = first.submission.submissionPackage
        assertEquals(FIRST_STAGE, firstPackage.stageKey)
        assertEquals(firstETag.substringAfterLast(':').trimEnd('"'), firstPackage.contentHashSha256)
        assertEquals(answers.keys.map(id).toSet(), first.submission.items.map { it.informationRequestRequirementId }.toSet())
        answers.forEach { (key, answer) ->
            val item = first.submission.items.single { it.informationRequestRequirementId == id(key) }
            assertEquals(answer.first, item.disposition, key)
            assertEquals(answer.second, item.narrative, key)
            assertEquals(request.principal(CONTRIBUTOR).kind, item.respondedByPrincipalKind, key)
            assertEquals(request.users.getValue(CONTRIBUTOR), item.respondedByPrincipalId, key)
        }
        assertNotEquals(InformationRequestState.CLOSED, first.request.state)

        val locked = assertThrows(InformationRequestLifecycleException::class.java) {
            patch(services, request, "edit-locked", listOf(InformationRequestResponsePatch(id(PARTIAL_ITEM), PARTIALLY_PROVIDED, ResponseNarrativePatch.Set("Changed"))))
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, locked.reasonCode)
        patch(
            services,
            request,
            "later-stage-edit",
            listOf(
                InformationRequestResponsePatch(
                    requirementId = id(LATER_ITEM),
                    disposition = PROVIDED,
                    fieldValues = ResponseFieldValuesPatch(listOf(FieldValueEntry(request.fields.getValue(LATER_ITEM).fieldContractId, JsonPrimitive("later value"))), FieldsPrecondition.Unconditioned),
                ),
            ),
        )

        val secondETag = submissionETag(request, SECOND_STAGE)
        val second = submit(services, request, SECOND_STAGE, secondETag, "submit-second-stage")
        assertEquals(listOf(id(LATER_ITEM)), second.submission.items.map { it.informationRequestRequirementId })
        assertEquals(secondETag.substringAfterLast(':').trimEnd('"'), second.submission.submissionPackage.contentHashSha256)
        assertNotEquals(firstPackage.manifestHashSha256, second.submission.submissionPackage.manifestHashSha256)
        val reread = QuarkusTransaction.requiringNew().call { runtime.packageReader.view(request.requestId, firstPackage.id) }
        assertEquals(firstPackage.manifestHashSha256, reread.submissionPackage.manifestHashSha256)
        assertEquals(first.submission.items.map { it.itemHashSha256 }.toSet(), reread.items.map { it.itemHashSha256 }.toSet())
    }

    private fun patch(services: InformationRequestRuntimeServices, request: PublishedRequest, key: String, patches: List<InformationRequestResponsePatch>) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = key,
                    patches = patches,
                ),
            )
        }

    private fun submissionETag(request: PublishedRequest, stageKey: String): String =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(request.requestId))
            InformationRequestETag.submissionOf(stageKey, contentCollector.collect(stored, stageKey).contentHash)
        }

    private fun submit(services: InformationRequestRuntimeServices, request: PublishedRequest, stageKey: String, etag: String, key: String) =
        QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    stageKey = stageKey,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = key,
                ),
            )
        }

    private fun configuration(schemaVersionId: UUID, fieldIds: Map<String, UUID>) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        submissionMode = InformationRequestSubmissionMode.STAGED,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "itemized-answers",
                title = "Itemized answers",
                submissionStageKey = FIRST_STAGE,
                requirements = listOf(
                    field(PROVIDED_ITEM, fieldIds, listOf(PROVIDED)),
                    field(PARTIAL_ITEM, fieldIds, listOf(PROVIDED, PARTIALLY_PROVIDED)),
                    field(UNAVAILABLE_ITEM, fieldIds, listOf(PROVIDED, UNAVAILABLE)),
                    field(EXCEPTION_ITEM, fieldIds, listOf(PROVIDED, EXCEPTION_REQUESTED)),
                    field(REFERENCED_ITEM, fieldIds, listOf(PROVIDED, SATISFIED_BY_REFERENCE)),
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = WAIVED_ITEM,
                        requirementType = InformationRequestRequirementType.DOCUMENT,
                        prompt = "Provide the supporting record",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        permittedDispositions = listOf(PROVIDED, WAIVED),
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            minimumFileCount = 1,
                            maximumFileCount = 1,
                            maximumFileSizeBytes = 1_000_000,
                            maximumTotalSizeBytes = 1_000_000,
                            waiverPolicy = InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED,
                            acceptedValues = listOf(
                                InformationRequestTemplateAcceptedValueRequest(InformationRequestEvidenceAttribute.CONTENT_TYPE, "application/pdf"),
                            ),
                        ),
                    ),
                ),
            ),
            InformationRequestTemplateSectionRequest(
                sectionKey = "later-answers",
                title = "Later answers",
                submissionStageKey = SECOND_STAGE,
                requirements = listOf(field(LATER_ITEM, fieldIds, listOf(PROVIDED))),
            ),
        ),
    )

    private fun field(key: String, fieldIds: Map<String, UUID>, dispositions: List<InformationRequestResponseDisposition>) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.FIELD,
            prompt = "Answer $key",
            responseMode = InformationRequestResponseMode.PROVIDE,
            requiredness = InformationRequestRequiredness.REQUIRED,
            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
            collectedFieldDefinitionId = fieldIds.getValue(key),
            permittedDispositions = dispositions,
        )

    private companion object
    {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val FIRST_STAGE = "first-stage"
        const val SECOND_STAGE = "second-stage"
        const val PROVIDED_ITEM = "provided-item"
        const val PARTIAL_ITEM = "partial-item"
        const val UNAVAILABLE_ITEM = "unavailable-item"
        const val EXCEPTION_ITEM = "exception-item"
        const val REFERENCED_ITEM = "referenced-item"
        const val WAIVED_ITEM = "waived-item"
        const val LATER_ITEM = "later-item"
        val FIELD_ITEMS = listOf(PROVIDED_ITEM, PARTIAL_ITEM, UNAVAILABLE_ITEM, EXCEPTION_ITEM, REFERENCED_ITEM)
    }
}
