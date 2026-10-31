package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.response.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseArchiveRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseConditionEffectRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.fields.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.response.model.HiddenConditionalResponseFixture
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestResponseDraftServiceTest
{
    @Test
    fun `sparse patch creates response envelope records participant provenance and replays safely`()
    {
        val fixture = Fixture()
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "save-response-once",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                    narrative = ResponseNarrativePatch.Set("First complete response."),
                ),
            ),
        )

        val first = fixture.service.patch(command)
        val replay = fixture.service.patch(command)

        assertEquals(first.responseETag, replay.responseETag)
        assertEquals(2, fixture.request.responseRevision)
        assertEquals(1, fixture.responses.size)
        val response = fixture.responses.single()
        assertEquals(fixture.request.id, response.informationRequestId)
        assertEquals(fixture.requirement.id, response.informationRequestRequirementId)
        assertEquals(fixture.currentRevision.id, response.requirementRevisionId)
        assertEquals("root", response.occurrencePath)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, response.disposition)
        assertEquals("First complete response.", response.narrative)
        assertEquals(PrincipalKind.PARTICIPANT, response.recordedByPrincipalKind)
        assertEquals(fixture.participantId, response.recordedByPrincipalId)
        assertEquals("verified-session", response.recordedBySessionRef)
        assertEquals(2, response.responseRevision)
        assertEquals(response, first.responses.single())
        assertEquals(ResourceType.INFORMATION_REQUEST, fixture.receiptStore.receipts.single().resultResourceType)
        assertEquals(fixture.request.id, fixture.receiptStore.receipts.single().resultResourceId)
        assertEquals(2, fixture.receiptStore.receipts.single().resultRevision)
        verify(fixture.requestRepository, times(1)).update(fixture.request)
        verify(fixture.transitionHistory, times(1)).record(any())
        verify(fixture.authorizationService, times(2)).authorize(
            fixture.access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(fixture.requirement.id),
            fixture.access.authorization,
        )
    }

    @Test
    fun `replaying a response patch requires current Requirement response authorization`()
    {
        val fixture = Fixture()
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-requires-current-response-authorization",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                    narrative = ResponseNarrativePatch.Set("Original process answer."),
                ),
            ),
        )
        fixture.service.patch(command)
        reset(fixture.authorizationService)
        whenever(
            fixture.authorizationService.authorize(
                eq(fixture.access.principal),
                eq(Action.INFORMATION_REQUEST_VIEW),
                eq(ResourceRef.informationRequest(fixture.request.id)),
                eq(fixture.access.authorization),
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            fixture.authorizationService.authorize(
                eq(fixture.access.principal),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND),
                eq(ResourceRef.informationRequestRequirement(fixture.requirement.id)),
                eq(fixture.access.authorization),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "No current Requirement access"))

        assertThrows(ForbiddenException::class.java) {
            fixture.service.patch(command)
        }
    }

    @Test
    fun `replaying a response patch answers the response as it stands now with the current response ETag`()
    {
        val fixture = Fixture()
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-returns-the-current-response",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                    narrative = ResponseNarrativePatch.Set("Original process answer."),
                ),
            ),
        )
        val first = fixture.service.patch(command)
        fixture.request.responseRevision = 3
        fixture.responses.single().narrative = "Replacement process answer."
        fixture.responses.single().responseRevision = 3

        val replay = fixture.service.patch(command)

        assertNotEquals(first.responseETag, replay.responseETag)
        assertEquals(InformationRequestETag.responsesOf(fixture.request), replay.responseETag)
        assertEquals(listOf("Replacement process answer."), replay.responses.map { it.narrative })
    }

    @Test
    fun `replaying a Field response patch answers the Field projection as it stands now`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = fixture.requirement.occurrencePath
            revision = 8
        }
        val fieldEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Structured answer"))
        fixture.stubFieldBinding(fixture.requirement, fieldEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any())).thenReturn(fixture.schemaAssignmentProjection())
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(
            fixture.fieldsReader.valueSetOf(
                assignment.id,
                FieldValueSetRef.Occurrence(fixture.requirement.occurrencePath)
            )
        )
            .thenReturn(occurrenceSet)
        val currentProjection = fixture.schemaAssignmentProjection().copy(etag = "\"current-etag\"")
        whenever(fixture.schemaAssignmentService.getAssignment(any())).thenReturn(currentProjection)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-returns-the-current-field-response",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(fieldEntry),
                        precondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet)),
                    ),
                ),
            ),
        )
        fixture.service.patch(command)
        fixture.request.responseRevision = 6
        fixture.responses.single().responseRevision = 6

        val replay = fixture.service.patch(command)

        assertEquals(listOf(6L), replay.responses.map { it.responseRevision })
        assertEquals(InformationRequestETag.responsesOf(fixture.request), replay.responseETag)
        assertEquals(currentProjection, replay.fieldValueProjectionsByRequirementId[fixture.requirement.id])
    }

    @Test
    fun `replaying a response patch fails when the parent Exchange now blocks response mutation`()
    {
        val fixture = Fixture()
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-parent-state-blocked",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                    narrative = ResponseNarrativePatch.Set("Original process answer."),
                ),
            ),
        )
        fixture.service.patch(command)
        fixture.exchange.status = ExchangeStatus.REJECTED

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, failure.reasonCode)
    }

    @Test
    fun `replaying a response patch fails when the owner is operationally suspended`()
    {
        val fixture = Fixture()
        val suspension = RuntimeException("Owner operationally suspended")
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-operational-suspension",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                    narrative = ResponseNarrativePatch.Set("Original process answer."),
                ),
            ),
        )
        fixture.service.patch(command)
        whenever(fixture.entitlementGuard.requireNotOperationallySuspended(fixture.exchange)).thenThrow(suspension)

        val failure = assertThrows(RuntimeException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(suspension, failure)
    }

    @Test
    fun `a response save locks the parent Exchange before the request row`()
    {
        val fixture = Fixture()

        fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "save-response-lock-order",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
            ),
        )

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }

    @Test
    fun `explicit narrative clear leaves an omitted disposition unchanged`()
    {
        val fixture = Fixture(responseRevision = 4)
        val existing = fixture.existingResponse(
            disposition = InformationRequestResponseDisposition.PROVIDED,
            narrative = "Will clear",
            responseRevision = 4,
        )
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "clear-response-narrative",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    narrative = ResponseNarrativePatch.Clear,
                ),
            ),
        )

        val result = fixture.service.patch(command)

        assertEquals(5, fixture.request.responseRevision)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, existing.disposition)
        assertNull(existing.narrative)
        assertEquals(5, existing.responseRevision)
        assertEquals(existing, result.responses.single())
    }

    @Test
    fun `exception dispositions require a narrative`()
    {
        val fixture = Fixture(responseRevision = 4)
        fixture.permitDispositions(
            InformationRequestResponseDisposition.PROVIDED,
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
            InformationRequestResponseDisposition.WAIVED,
        )
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "exception-without-narrative",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
                ),
            ),
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(InformationRequestErrorCatalog.RESPONSE_NARRATIVE_REQUIRED, failure.reasonCode)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `permitted neutral exception dispositions save with independent narratives`()
    {
        val dispositions = listOf(
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
            InformationRequestResponseDisposition.WAIVED,
        )
        dispositions.forEach { disposition ->
            val fixture = Fixture(responseRevision = 4)
            fixture.permitDispositions(InformationRequestResponseDisposition.PROVIDED, disposition)
            val command = PatchInformationRequestResponsesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "save-${disposition.name.lowercase()}",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        disposition = disposition,
                        narrative = ResponseNarrativePatch.Set("Respondent explanation for ${disposition.name}."),
                    ),
                ),
            )

            val result = fixture.service.patch(command)

            assertEquals(disposition, result.responses.single().disposition)
            assertEquals("Respondent explanation for ${disposition.name}.", result.responses.single().narrative)
            assertEquals(5, fixture.request.responseRevision)
        }
    }

    @Test
    fun `an answer that carries no value clears the Field value given earlier through the Fields engine`() {
        val fixture = Fixture(responseRevision = 4)
        val fieldDefinitionId = UUID.randomUUID()
        fixture.stubFieldBinding(fixture.requirement, fieldDefinitionId, fieldDefinitionId)
        val earlier = responseFieldProjection(fixture.request.id, listOf(fieldDefinitionId to "120"))
        whenever(fixture.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>())).thenReturn(earlier)
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenReturn(earlier)

        fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "switch-to-unavailable",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        disposition = InformationRequestResponseDisposition.UNAVAILABLE,
                        narrative = ResponseNarrativePatch.Set("The record is not available."),
                    ),
                ),
            ),
        )

        val cleared = argumentCaptor<FieldValueClearCommand>()
        verify(fixture.schemaAssignmentService).clearValues(cleared.capture())
        assertEquals(setOf(fieldDefinitionId), cleared.firstValue.fieldContractIds)
        assertEquals(FieldsPrecondition.ExpectedRevision(requireNotNull(earlier.etag)), cleared.firstValue.precondition)
    }

    @Test
    fun `field patch writes the occurrence through Fields and links the response to its value set`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = fixture.requirement.occurrencePath
            revision = 8
        }
        val fieldEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Structured answer"))
        val fieldPrecondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet))
        fixture.stubFieldBinding(fixture.requirement, fieldEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any())).thenReturn(fixture.schemaAssignmentProjection())
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(
            fixture.fieldsReader.valueSetOf(
                assignment.id,
                FieldValueSetRef.Occurrence(fixture.requirement.occurrencePath)
            )
        )
            .thenReturn(occurrenceSet)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "save-structured-response",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(fieldEntry),
                        precondition = fieldPrecondition,
                    ),
                ),
            ),
        )

        val result = fixture.service.patch(command)

        val response = result.responses.single()
        assertEquals(occurrenceSet.id, response.fieldValueSetId)
        assertEquals(5, response.responseRevision)
        val captor = argumentCaptor<FieldValueWriteCommand>()
        verify(fixture.schemaAssignmentService).setValues(captor.capture())
        assertEquals(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, fixture.request.id),
            captor.firstValue.resource
        )
        assertEquals(fixture.access.principal, captor.firstValue.access.principal)
        assertEquals(fixture.access.authorization, captor.firstValue.access.authorization)
        assertEquals(FieldValueSetRef.Occurrence("items[0]"), captor.firstValue.valueSet)
        assertEquals(listOf(fieldEntry), captor.firstValue.entries)
        assertEquals(fieldPrecondition, captor.firstValue.precondition)
    }

    @Test
    fun `an answer emptied by deselecting every option or blanking the text is cleared, so the Template decides whether it may be empty`() {
        listOf<JsonElement>(JsonArray(emptyList()), JsonPrimitive("   ")).forEach { emptied ->
            val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
            val assignment = SchemaAssignment().apply {
                id = UUID.randomUUID()
                resourceType = ResourceType.INFORMATION_REQUEST.name
                resourceId = fixture.request.id
                schemaVersionId = UUID.randomUUID()
            }
            val occurrenceSet = FieldValueSet().apply {
                id = UUID.randomUUID()
                schemaAssignmentId = assignment.id
                setKind = FieldValueSetKind.OCCURRENCE
                occurrencePath = fixture.requirement.occurrencePath
                revision = 8
            }
            val fieldEntry = FieldValueEntry(UUID.randomUUID(), emptied)
            val fieldPrecondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet))
            fixture.stubFieldBinding(fixture.requirement, fieldEntry.fieldContractId)
            whenever(fixture.schemaAssignmentService.clearValues(any())).thenReturn(fixture.schemaAssignmentProjection())
            whenever(
                fixture.fieldsReader.assignmentFor(
                    FieldsResourceRef(
                        ResourceType.INFORMATION_REQUEST.name,
                        fixture.request.id
                    )
                )
            )
                .thenReturn(assignment)
            whenever(
                fixture.fieldsReader.valueSetOf(
                    assignment.id,
                    FieldValueSetRef.Occurrence(fixture.requirement.occurrencePath)
                )
            )
                .thenReturn(occurrenceSet)

            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "clear-emptied-answer",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(fieldEntry),
                                precondition = fieldPrecondition
                            ),
                        ),
                    ),
                ),
            )

            val clears = argumentCaptor<FieldValueClearCommand>()
            verify(fixture.schemaAssignmentService).clearValues(clears.capture())
            assertEquals(setOf(fieldEntry.fieldContractId), clears.firstValue.fieldContractIds)
            assertEquals(fieldPrecondition, clears.firstValue.precondition)
            verify(fixture.schemaAssignmentService, never()).setValues(any())
        }
    }

    @Test
    fun `duplicate Field entries in one response patch are refused before writing`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = fixture.requirement.occurrencePath
            revision = 8
        }
        val fieldContractId = UUID.randomUUID()
        whenever(fixture.schemaAssignmentService.setValues(any())).thenReturn(fixture.schemaAssignmentProjection())
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(
            fixture.fieldsReader.valueSetOf(
                assignment.id,
                FieldValueSetRef.Occurrence(fixture.requirement.occurrencePath)
            )
        )
            .thenReturn(occurrenceSet)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "duplicate-field-entry",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(
                            FieldValueEntry(fieldContractId, JsonPrimitive("First answer")),
                            FieldValueEntry(fieldContractId, JsonPrimitive("Second answer")),
                        ),
                        precondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet)),
                    ),
                ),
            ),
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals("INFORMATION_REQUEST_STRUCTURED_RESPONSE_VALIDATION_FAILED", failure.reasonCode)
        val problem = assertInstanceOf(InformationRequestItemRefusalException::class.java, failure).problems.single()
        assertEquals(InformationRequestStructuredResponseValidationKind.DUPLICATE, problem.validationKind)
        assertEquals(fixture.requirement.id, problem.requirementId)
        assertEquals(fieldContractId, problem.fieldContractId)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.schemaAssignmentService, never()).setValues(any())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `a Field entry naming a Field outside the named Requirement's collected Field is refused before writing`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = fixture.requirement.occurrencePath
            revision = 8
        }
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(
            fixture.fieldsReader.valueSetOf(
                assignment.id,
                FieldValueSetRef.Occurrence(fixture.requirement.occurrencePath)
            )
        )
            .thenReturn(occurrenceSet)
        fixture.stubFieldBinding(fixture.requirement, UUID.randomUUID())
        val foreignFieldEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Belongs to a sibling Requirement"))
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "foreign-field-entry",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(foreignFieldEntry),
                        precondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet)),
                    ),
                ),
            ),
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_ENTRY_NOT_BOUND, failure.reasonCode)
        assertFalse(failure.message.contains(foreignFieldEntry.fieldContractId.toString()))
        val problem = assertInstanceOf(InformationRequestItemRefusalException::class.java, failure).problems.single()
        assertEquals(InformationRequestErrorCatalog.FIELD_ENTRY_NOT_BOUND, problem.code)
        assertEquals(fixture.requirement.id, problem.requirementId)
        assertEquals("items[0]", problem.occurrencePath)
        assertEquals(foreignFieldEntry.fieldContractId, problem.fieldContractId)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.schemaAssignmentService, never()).setValues(any())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `patch across two occurrences writes each occurrence's Field values independently`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val secondRequirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = fixture.request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "items[1]"
        }
        val secondRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = secondRequirement.id
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = secondRequirement.sourceTemplateVersionId
            sourceTemplateRequirementId = secondRequirement.sourceTemplateRequirementId
            sourceTemplateBindingId = secondRequirement.sourceTemplateBindingId
            revisionNumber = 1
            occurrencePath = secondRequirement.occurrencePath
            configurationHashSha256 = "b".repeat(64)
        }
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, secondRequirement))
        whenever(fixture.revisionRepository.findCurrentForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.currentRevision, secondRevision))

        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val firstSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = "items[0]"
            revision = 3
        }
        val secondSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = "items[1]"
            revision = 5
        }
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(fixture.fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Occurrence("items[0]"))).thenReturn(
            firstSet
        )
        whenever(fixture.fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Occurrence("items[1]"))).thenReturn(
            secondSet
        )
        val firstEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("First answer"))
        val secondEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Second answer"))
        fixture.stubFieldBinding(fixture.requirement, firstEntry.fieldContractId)
        fixture.stubFieldBinding(secondRequirement, secondEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any())).thenAnswer { invocation ->
            val writeCommand = invocation.getArgument<FieldValueWriteCommand>(0)
            val path = (writeCommand.valueSet as FieldValueSetRef.Occurrence).occurrencePath
            fixture.schemaAssignmentProjection().copy(etag = "\"$path-etag\"")
        }
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "multi-occurrence-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(firstEntry),
                        precondition = FieldsPrecondition.Unconditioned,
                    ),
                ),
                InformationRequestResponsePatch(
                    requirementId = secondRequirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(secondEntry),
                        precondition = FieldsPrecondition.Unconditioned,
                    ),
                ),
            ),
        )

        val result = fixture.service.patch(command)

        assertEquals(2, result.responses.size)
        val firstResponse = result.responses.first { it.informationRequestRequirementId == fixture.requirement.id }
        val secondResponse = result.responses.first { it.informationRequestRequirementId == secondRequirement.id }
        assertEquals(firstSet.id, firstResponse.fieldValueSetId)
        assertEquals(secondSet.id, secondResponse.fieldValueSetId)
        assertEquals(
            "\"items[0]-etag\"",
            result.fieldValueProjectionsByRequirementId.getValue(fixture.requirement.id).etag,
        )
        assertEquals(
            "\"items[1]-etag\"",
            result.fieldValueProjectionsByRequirementId.getValue(secondRequirement.id).etag,
        )
        val captor = argumentCaptor<FieldValueWriteCommand>()
        verify(fixture.schemaAssignmentService, times(2)).setValues(captor.capture())
        assertEquals(
            setOf(FieldValueSetRef.Occurrence("items[0]"), FieldValueSetRef.Occurrence("items[1]")),
            captor.allValues.map { it.valueSet }.toSet(),
        )
    }

    @Test
    fun `two Field Requirement patches in the same occurrence batch into one Fields mutation instead of conflicting with each other's precondition`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val secondRequirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = fixture.request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "items[0]"
        }
        val secondRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = secondRequirement.id
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = secondRequirement.sourceTemplateVersionId
            sourceTemplateRequirementId = secondRequirement.sourceTemplateRequirementId
            sourceTemplateBindingId = secondRequirement.sourceTemplateBindingId
            revisionNumber = 1
            occurrencePath = secondRequirement.occurrencePath
            configurationHashSha256 = "b".repeat(64)
        }
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, secondRequirement))
        whenever(fixture.revisionRepository.findCurrentForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.currentRevision, secondRevision))

        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = "items[0]"
            revision = 8
        }
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(fixture.fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Occurrence("items[0]"))).thenReturn(
            occurrenceSet
        )
        val sharedPrecondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet))
        val firstEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("First answer"))
        val secondEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Second answer"))
        fixture.stubFieldBinding(fixture.requirement, firstEntry.fieldContractId)
        fixture.stubFieldBinding(secondRequirement, secondEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any())).thenReturn(fixture.schemaAssignmentProjection())
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "batch-same-occurrence-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(firstEntry),
                        precondition = sharedPrecondition,
                    ),
                ),
                InformationRequestResponsePatch(
                    requirementId = secondRequirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(secondEntry),
                        precondition = sharedPrecondition,
                    ),
                ),
            ),
        )

        val result = fixture.service.patch(command)

        assertEquals(2, result.responses.size)
        val firstResponse = result.responses.first { it.informationRequestRequirementId == fixture.requirement.id }
        val secondResponse = result.responses.first { it.informationRequestRequirementId == secondRequirement.id }
        assertEquals(occurrenceSet.id, firstResponse.fieldValueSetId)
        assertEquals(occurrenceSet.id, secondResponse.fieldValueSetId)
        val captor = argumentCaptor<FieldValueWriteCommand>()
        verify(fixture.schemaAssignmentService, times(1)).setValues(captor.capture())
        assertEquals(FieldValueSetRef.Occurrence("items[0]"), captor.firstValue.valueSet)
        assertEquals(listOf(firstEntry, secondEntry), captor.firstValue.entries)
        assertEquals(sharedPrecondition, captor.firstValue.precondition)
    }

    @Test
    fun `an explicit clear entry is cleared before the values written beside it in the same occurrence`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val secondRequirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = fixture.request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "items[0]"
        }
        val secondRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = secondRequirement.id
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = secondRequirement.sourceTemplateVersionId
            sourceTemplateRequirementId = secondRequirement.sourceTemplateRequirementId
            sourceTemplateBindingId = secondRequirement.sourceTemplateBindingId
            revisionNumber = 1
            occurrencePath = secondRequirement.occurrencePath
            configurationHashSha256 = "b".repeat(64)
        }
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, secondRequirement))
        whenever(fixture.revisionRepository.findCurrentForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.currentRevision, secondRevision))
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = "items[0]"
            revision = 8
        }
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(fixture.fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Occurrence("items[0]"))).thenReturn(
            occurrenceSet
        )
        val sharedPrecondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet))
        val keptEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Kept answer"))
        val clearedEntry = FieldValueEntry(UUID.randomUUID(), JsonNull)
        fixture.stubFieldBinding(fixture.requirement, keptEntry.fieldContractId)
        fixture.stubFieldBinding(secondRequirement, clearedEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.clearValues(any()))
            .thenReturn(fixture.schemaAssignmentProjection().copy(etag = "\"after-clear\""))
        whenever(fixture.schemaAssignmentService.setValues(any()))
            .thenReturn(fixture.schemaAssignmentProjection().copy(etag = "\"after-set\""))
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "batch-clear-with-value-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(keptEntry),
                        precondition = sharedPrecondition
                    ),
                ),
                InformationRequestResponsePatch(
                    requirementId = secondRequirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(clearedEntry),
                        precondition = sharedPrecondition
                    ),
                ),
            ),
        )

        fixture.service.patch(command)

        val clears = argumentCaptor<FieldValueClearCommand>()
        val writes = argumentCaptor<FieldValueWriteCommand>()
        val order = inOrder(fixture.schemaAssignmentService)
        order.verify(fixture.schemaAssignmentService).clearValues(clears.capture())
        order.verify(fixture.schemaAssignmentService).setValues(writes.capture())
        assertEquals(setOf(clearedEntry.fieldContractId), clears.firstValue.fieldContractIds)
        assertEquals(FieldValueSetRef.Occurrence("items[0]"), clears.firstValue.valueSet)
        assertEquals(sharedPrecondition, clears.firstValue.precondition)
        assertEquals(listOf(keptEntry), writes.firstValue.entries)
        assertEquals(FieldsPrecondition.ExpectedRevision("\"after-clear\""), writes.firstValue.precondition)
    }

    @Test
    fun `a stale precondition on one occurrence's batched Field write rolls back the whole patch including an already written sibling occurrence`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val secondRequirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = fixture.request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "items[1]"
        }
        val secondRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = secondRequirement.id
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = secondRequirement.sourceTemplateVersionId
            sourceTemplateRequirementId = secondRequirement.sourceTemplateRequirementId
            sourceTemplateBindingId = secondRequirement.sourceTemplateBindingId
            revisionNumber = 1
            occurrencePath = secondRequirement.occurrencePath
            configurationHashSha256 = "b".repeat(64)
        }
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, secondRequirement))
        whenever(fixture.revisionRepository.findCurrentForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.currentRevision, secondRevision))
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val firstSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = "items[0]"
            revision = 3
        }
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(fixture.fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Occurrence("items[0]"))).thenReturn(
            firstSet
        )
        val firstEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("First answer"))
        val secondEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Second answer"))
        fixture.stubFieldBinding(fixture.requirement, firstEntry.fieldContractId)
        fixture.stubFieldBinding(secondRequirement, secondEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any())).thenAnswer { invocation ->
            val writeCommand = invocation.getArgument<FieldValueWriteCommand>(0)
            val path = (writeCommand.valueSet as FieldValueSetRef.Occurrence).occurrencePath
            if (path == "items[1]") throw FieldsPreconditionException.stale("\"current-field-etag\"")
            fixture.schemaAssignmentProjection()
        }
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "rollback-across-occurrences",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(firstEntry),
                        precondition = FieldsPrecondition.Unconditioned
                    ),
                ),
                InformationRequestResponsePatch(
                    requirementId = secondRequirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(secondEntry),
                        precondition = FieldsPrecondition.ExpectedRevision("\"stale-field-etag\""),
                    ),
                ),
            ),
        )

        val failure = assertThrows(FieldsPreconditionException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.responseStore, never()).update(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `replaying a field patch returns a fresh projection without writing Field values again`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val occurrenceSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = fixture.requirement.occurrencePath
            revision = 8
        }
        val fieldEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Structured answer"))
        val fieldPrecondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet))
        fixture.stubFieldBinding(fixture.requirement, fieldEntry.fieldContractId)
        val writeProjection = fixture.schemaAssignmentProjection().copy(etag = "\"write-etag\"")
        val replayProjection = fixture.schemaAssignmentProjection().copy(etag = "\"replay-etag\"")
        whenever(fixture.schemaAssignmentService.setValues(any())).thenReturn(writeProjection)
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(
            fixture.fieldsReader.valueSetOf(
                assignment.id,
                FieldValueSetRef.Occurrence(fixture.requirement.occurrencePath)
            )
        )
            .thenReturn(occurrenceSet)
        whenever(fixture.schemaAssignmentService.getAssignment(any())).thenReturn(replayProjection)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-structured-response",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(fieldEntry),
                        precondition = fieldPrecondition,
                    ),
                ),
            ),
        )

        val first = fixture.service.patch(command)
        val replay = fixture.service.patch(command)

        assertEquals(first.responseETag, replay.responseETag)
        assertEquals(
            "\"write-etag\"",
            first.fieldValueProjectionsByRequirementId.getValue(fixture.requirement.id).etag,
        )
        assertEquals(
            "\"replay-etag\"",
            replay.fieldValueProjectionsByRequirementId.getValue(fixture.requirement.id).etag,
        )
        verify(fixture.schemaAssignmentService, times(1)).setValues(any())
        verify(fixture.schemaAssignmentService, times(1)).getAssignment(any())
    }

    @Test
    fun `a stale Field value precondition aborts the whole patch without recording any response or history`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val fieldEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Structured answer"))
        fixture.stubFieldBinding(fixture.requirement, fieldEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any()))
            .thenThrow(FieldsPreconditionException.stale("\"current-field-etag\""))
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "stale-field-write",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(
                        entries = listOf(fieldEntry),
                        precondition = FieldsPrecondition.ExpectedRevision("\"stale-field-etag\""),
                    ),
                ),
            ),
        )

        val failure = assertThrows(FieldsPreconditionException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.responseStore, never()).update(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `archive policy snapshots the hidden answer into the archive and clears the live answer without confirmation`() {
        val fixture = Fixture(responseRevision = 4)
        val fieldId = UUID.randomUUID()
        val hidden = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
            fieldId,
        )
        hidden.response.fieldValueSetId = UUID.randomUUID()
        val projection = responseFieldProjection(fixture.request.id, listOf(fieldId to "retained-secret"))
        whenever(fixture.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>())).thenReturn(projection)
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenReturn(projection.copy(fields = emptyList()))

        fixture.service.patch(fixture.patchOwnRequirement("archive-hidden-response"))

        val archive = fixture.archives.single()
        assertEquals(fixture.request.id, archive.informationRequestId)
        assertEquals(hidden.requirement.id, archive.informationRequestRequirementId)
        assertEquals(hidden.response.id, archive.responseId)
        assertEquals("root", archive.occurrencePath)
        assertEquals("when-source-applies", archive.conditionRuleKey)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, archive.disposition)
        assertEquals("Previously captured response.", archive.narrative)
        assertEquals(fieldId, archive.fieldContractId)
        assertEquals("\"retained-secret\"", archive.fieldValueJson)
        assertEquals(projection.etag, archive.fieldValueSetETag)
        assertEquals(fixture.access.principal.kind, archive.archivedByPrincipalKind)
        assertEquals(fixture.access.principal.id, archive.archivedByPrincipalId)
        verify(fixture.schemaAssignmentService).clearValues(check {
            assertEquals(setOf(fieldId), it.fieldContractIds)
            assertEquals(FieldValueSetRef.Root, it.valueSet)
            assertEquals(FieldsPrecondition.ExpectedRevision(projection.etag!!), it.precondition)
        })
        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, hidden.response.disposition)
        assertNull(hidden.response.narrative)
        assertNull(hidden.response.fieldValueSetId)
        assertFalse(hidden.response.activeInResponse)
        assertEquals("when-source-applies", hidden.response.hiddenByConditionRuleKey)
        assertEquals(
            InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
            hidden.response.hiddenDataPolicy,
        )
    }

    @Test
    fun `an archived answer is never restored and the response returns empty when its condition holds again`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden =
            fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE)
        fixture.service.patch(fixture.patchOwnRequirement("archive-before-reactivation"))
        fixture.conditionHolds(
            InformationRequestConditionEvaluationState.TRUE,
            InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
        )

        fixture.service.patch(fixture.patchOwnRequirement("reactivate-archived-response"))

        assertEquals(1, fixture.archives.size)
        assertEquals("Previously captured response.", fixture.archives.single().narrative)
        assertTrue(hidden.response.activeInResponse)
        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, hidden.response.disposition)
        assertNull(hidden.response.narrative)
        assertNull(hidden.response.fieldValueSetId)
        assertNull(hidden.response.hiddenByConditionRuleKey)
        assertNull(hidden.response.hiddenDataPolicy)
        assertNull(hidden.response.hiddenAt)
    }

    @Test
    fun `retained and cleared hidden answers write no archive record`() {
        val retained = Fixture(responseRevision = 4)
        retained.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY)
        retained.service.patch(retained.patchOwnRequirement("retain-without-archive"))

        val cleared = Fixture(responseRevision = 4)
        val clearedHidden =
            cleared.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        cleared.service.patch(
            cleared.patchOwnRequirement("clear-without-archive").copy(
                confirmedHiddenResponseClears = setOf(clearedHidden.requirement.id),
            ),
        )

        assertTrue(retained.archives.isEmpty())
        assertTrue(cleared.archives.isEmpty())
        verify(retained.archiveRepository, never()).save(any())
        verify(cleared.archiveRepository, never()).save(any())
    }

    @Test
    fun `an archive of an answer the caller may not respond to keeps it hidden and retained instead of refusing the save`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden =
            fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE)
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND),
                eq(ResourceRef.informationRequestRequirement(hidden.requirement.id)),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Assigned to another party"))

        fixture.service.patch(fixture.patchOwnRequirement("retain-foreign-archive"))

        assertTrue(fixture.archives.isEmpty())
        assertEquals(InformationRequestResponseDisposition.PROVIDED, hidden.response.disposition)
        assertEquals("Previously captured response.", hidden.response.narrative)
        assertFalse(hidden.response.activeInResponse)
        assertEquals(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY, hidden.response.hiddenDataPolicy)
    }

    @Test
    fun `clear policy requires explicit confirmation before clearing hidden response data`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(
            policy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
        )
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "refuse-hidden-clear-without-confirmation",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED, failure.reasonCode)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, hidden.response.disposition)
        assertEquals("Previously captured response.", hidden.response.narrative)
        assertEquals(true, hidden.response.activeInResponse)
    }

    @Test
    fun `an unconfirmed clear is refused naming every Requirement it would clear and clears none of them`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponses(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            count = 2
        )
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "refuse-two-hidden-clears",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
            confirmedHiddenResponseClears = setOf(hidden.first().requirement.id),
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.patch(command) }

        assertEquals(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED, failure.reasonCode)
        assertInstanceOf(InformationRequestHiddenResponseClearRequiredException::class.java, failure)
        assertEquals(
            listOf(
                HiddenResponseClearTarget(
                    hidden.last().requirement.id,
                    hidden.last().requirement.sourceTemplateBindingId,
                    "root"
                )
            ),
            (failure as InformationRequestHiddenResponseClearRequiredException).targets,
        )
        assertEquals("Previously captured response.", hidden.last().response.narrative)
        assertEquals(true, hidden.last().response.activeInResponse)
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `a clear of an answer the caller may not respond to keeps it hidden without asking for a confirmation`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden =
            fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND),
                eq(ResourceRef.informationRequestRequirement(hidden.requirement.id)),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Assigned to another party"))

        fixture.service.patch(fixture.patchOwnRequirement("retain-foreign-clear"))

        assertEquals("Previously captured response.", hidden.response.narrative)
        assertFalse(hidden.response.activeInResponse)
        assertEquals(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY, hidden.response.hiddenDataPolicy)
    }

    @Test
    fun `a clear the caller confirms but may not perform is refused without naming the other party's Requirement`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden =
            fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND),
                eq(ResourceRef.informationRequestRequirement(hidden.requirement.id)),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Assigned to another party"))

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "refuse-confirmed-foreign-clear",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                    confirmedHiddenResponseClears = setOf(hidden.requirement.id),
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED, failure.reasonCode)
        assertFalse(failure is InformationRequestHiddenResponseClearRequiredException)
        assertFalse(failure.message.contains(hidden.requirement.id.toString()))
        assertEquals("Previously captured response.", hidden.response.narrative)
        assertEquals(true, hidden.response.activeInResponse)
    }

    @Test
    fun `a patch to a Requirement its condition hides is refused naming that Requirement and saves nothing`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY)
        fixture.permitDispositionsFor(hidden.requirement, InformationRequestResponseDisposition.PROVIDED)

        val failure = assertThrows(InformationRequestItemRefusalException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "refuse-hidden-target",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = hidden.requirement.id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            narrative = ResponseNarrativePatch.Set("Written while hidden"),
                        ),
                    ),
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.REQUIREMENT_HIDDEN_BY_CONDITION, failure.reasonCode)
        assertEquals(hidden.requirement.id, failure.problems.single().requirementId)
        assertEquals("root", failure.problems.single().occurrencePath)
        assertEquals("Previously captured response.", hidden.response.narrative)
        assertEquals(4, fixture.request.responseRevision)
    }

    @Test
    fun `confirmed clear policy clears hidden response data outside the active response`()
    {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(
            policy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
        )
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "confirm-hidden-clear",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
            confirmedHiddenResponseClears = setOf(hidden.requirement.id),
        )

        val result = fixture.service.patch(command)

        assertEquals(listOf(fixture.requirement.id), result.responses.map { it.informationRequestRequirementId })
        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, hidden.response.disposition)
        assertNull(hidden.response.narrative)
        assertEquals(false, hidden.response.activeInResponse)
        assertEquals(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            hidden.response.hiddenDataPolicy
        )
        assertEquals("when-source-applies", hidden.response.hiddenByConditionRuleKey)
        assertEquals(5, hidden.response.responseRevision)
        verify(fixture.responseStore).update(hidden.response)
    }

    @Test
    fun `a retained hidden response reactivates when a false condition returns true with authorship intact`()
    {
        listOf(
            InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
        ).forEach { policy ->
            val fixture = Fixture(responseRevision = 4)
            val hidden = fixture.hiddenConditionalResponse(policy = policy)
            hidden.response.recordedBySessionRef = "original-session"

            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "hide-${policy.name.lowercase()}-response",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
            assertEquals(false, hidden.response.activeInResponse)
            assertEquals(policy, hidden.response.hiddenDataPolicy)

            whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenReturn(
                listOf(
                    InformationRequestConditionEvaluationProjection(
                        ruleKey = "when-source-applies",
                        expressionVersion = 1,
                        state = InformationRequestConditionEvaluationState.TRUE,
                        hiddenDataPolicy = policy,
                        sourceRequirementKeys = emptySet(),
                        fieldDefinitionIds = emptySet(),
                    ),
                ),
            )

            val result = fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "reactivate-${policy.name.lowercase()}-response",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )

            assertFalse(result.responses.any { it.informationRequestRequirementId == hidden.requirement.id })
            assertEquals(InformationRequestResponseDisposition.PROVIDED, hidden.response.disposition)
            assertEquals("Previously captured response.", hidden.response.narrative)
            assertEquals(true, hidden.response.activeInResponse)
            assertNull(hidden.response.hiddenByConditionRuleKey)
            assertNull(hidden.response.hiddenDataPolicy)
            assertNull(hidden.response.hiddenAt)
            assertEquals(PrincipalKind.PARTICIPANT, hidden.response.recordedByPrincipalKind)
            assertEquals(fixture.participantId, hidden.response.recordedByPrincipalId)
            assertEquals("original-session", hidden.response.recordedBySessionRef)
            assertEquals(4, hidden.response.responseRevision)
            verify(fixture.responseStore, times(2)).update(hidden.response)
        }
    }

    @Test
    fun `clear policy response reactivates empty when an unknown condition becomes true`()
    {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(
            policy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
        )
        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenReturn(
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-source-applies",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.UNKNOWN,
                    hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                ),
            ),
        )
        fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "hide-unknown-cleared-response",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
                confirmedHiddenResponseClears = setOf(hidden.requirement.id),
            ),
        )
        assertEquals(false, hidden.response.activeInResponse)
        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, hidden.response.disposition)

        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenReturn(
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-source-applies",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.TRUE,
                    hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                ),
            ),
        )

        fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "reactivate-cleared-response",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
            ),
        )

        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, hidden.response.disposition)
        assertNull(hidden.response.narrative)
        assertNull(hidden.response.fieldValueSetId)
        assertEquals(true, hidden.response.activeInResponse)
        assertNull(hidden.response.hiddenByConditionRuleKey)
        assertNull(hidden.response.hiddenDataPolicy)
        assertNull(hidden.response.hiddenAt)
        assertEquals(6, hidden.response.responseRevision)
        verify(fixture.responseStore, times(2)).update(hidden.response)
    }

    @Test
    fun `confirmed clear without new patches clears current Fields through their revision-preserving command`()
    {
        val fixture = Fixture(responseRevision = 4)
        val fieldId = UUID.randomUUID()
        val hidden = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            fieldId
        )
        hidden.response.fieldValueSetId = UUID.randomUUID()
        val projection = responseFieldProjection(fixture.request.id, listOf(fieldId to "retained-secret"))
        whenever(fixture.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>()))
            .thenReturn(projection)
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenReturn(projection.copy(fields = emptyList()))
        val result = fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                fixture.request.id,
                fixture.access,
                CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                "confirmed-field-clear",
                emptyList(),
                setOf(hidden.requirement.id)
            )
        )
        verify(fixture.schemaAssignmentService).clearValues(check {
            assertEquals(setOf(fieldId), it.fieldContractIds)
            assertEquals(FieldValueSetRef.Root, it.valueSet)
            assertEquals(fixture.access.principal, it.access.principal)
            assertEquals(FieldsPrecondition.ExpectedRevision(projection.etag!!), it.precondition)
        })
        assertNull(hidden.response.fieldValueSetId)
        assertEquals(5L, result.request.responseRevision)
    }

    @Test
    fun `confirmed conditional clear preserves Field history and a fresh read stays empty`()
    {
        val fields =
            SchemaAssignmentFieldsFixture(resourceType = ResourceType.INFORMATION_REQUEST.name)
        fields.save(listOf(fields.entry(fields.noteContractId, "Original process data")))
        val fixture =
            Fixture(responseRevision = 4, requestId = fields.resourceId, schemaAssignmentService = fields.service)
        val hidden = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            fields.noteDefinitionId
        )
        hidden.response.fieldValueSetId = fields.rootValueSetId
        fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                fixture.request.id,
                fixture.access,
                CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                "clear-with-history",
                emptyList(),
                setOf(hidden.requirement.id)
            )
        )
        assertEquals(
            JsonNull,
            fields.read()!!.fields.single { it.fieldContractId == fields.noteContractId }.value
        )
        val history = fields.rootRevisions(fields.noteContractId)
        assertEquals(2, history.size)
        assertEquals("Original process data", history.first().textValue)
        assertTrue(history.last().isCleared)
        assertEquals(fixture.access.principal.id, history.last().recordedByPrincipalId)
        assertEquals(fixture.access.authorization.sessionRef, history.last().recordedBySessionRef)
        assertTrue(fields.deletedValues.isEmpty())
    }

    @Test
    fun `a denied Field clear cannot detach the hidden response envelope`()
    {
        val fixture = Fixture(responseRevision = 4)
        val fieldId = UUID.randomUUID()
        val hidden = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            fieldId
        )
        val valueSetId = UUID.randomUUID()
        hidden.response.fieldValueSetId = valueSetId
        whenever(fixture.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>()))
            .thenReturn(responseFieldProjection(fixture.request.id, listOf(fieldId to "retained-secret")))
        whenever(
            fixture.authorizationService.authorize(
                fixture.access.principal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(hidden.requirement.id), fixture.access.authorization
            )
        )
            .thenReturn(Decision.Deny("NO_GRANT", "Not assigned"))
        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    fixture.request.id,
                    fixture.access,
                    CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    "denied-field-clear",
                    emptyList(),
                    setOf(hidden.requirement.id)
                )
            )
        }
        assertEquals(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED, failure.reasonCode)
        assertEquals(valueSetId, hidden.response.fieldValueSetId)
        assertTrue(hidden.response.activeInResponse)
        verify(fixture.schemaAssignmentService, never()).clearValues(any())
    }

    @Test
    fun `clearing a source Field also applies the clear policy of a newly hidden dependent Requirement`()
    {
        val fixture = Fixture(responseRevision = 4)
        val firstField = UUID.randomUUID()
        val secondField = UUID.randomUUID()
        val first = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            firstField
        )
        val firstBinding = fixture.bindingRepository.findOrdered(fixture.request.templateVersionId).single()
        val second = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            secondField
        )
        val secondBinding = fixture.bindingRepository.findOrdered(fixture.request.templateVersionId).single().apply {
            conditionalRuleKey = "when-dependent-data-applies"
        }
        whenever(fixture.bindingRepository.findOrdered(fixture.request.templateVersionId)).thenReturn(
            listOf(
                firstBinding,
                secondBinding
            )
        )
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, first.requirement, second.requirement))
        val cleared = mutableSetOf<UUID>()
        fun projection() = responseFieldProjection(
            fixture.request.id,
            listOf(firstField to "source", secondField to "dependent")
        ).let {
            it.copy(fields = it.fields.map { field ->
                if (field.fieldContractId in cleared)
                    field.copy(isEmpty = true, value = JsonNull)
                else field
            })
        }
        whenever(fixture.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>()))
            .thenAnswer { projection() }
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenAnswer {
            cleared += it.getArgument<com.docuhyphen.app.api.model.fields.FieldValueClearCommand>(0).fieldContractIds
            projection()
        }
        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenAnswer {
            listOf(
                InformationRequestConditionEvaluationProjection(
                    "when-source-applies",
                    1,
                    InformationRequestConditionEvaluationState.FALSE,
                    InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                    emptySet(),
                    emptySet()
                ),
                InformationRequestConditionEvaluationProjection(
                    "when-dependent-data-applies",
                    1,
                    if (firstField in cleared) InformationRequestConditionEvaluationState.UNKNOWN else InformationRequestConditionEvaluationState.TRUE,
                    InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                    emptySet(),
                    emptySet()
                )
            )
        }
        fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                fixture.request.id,
                fixture.access,
                CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                "clear-dependent-data",
                emptyList(),
                setOf(first.requirement.id, second.requirement.id)
            )
        )
        assertEquals(setOf(firstField, secondField), cleared)
        assertEquals(false, second.response.activeInResponse)
    }

    @Test
    fun `a save answers the Field Value Set tag left after its own conditional clear in that set`() {
        val fixture = Fixture(responseRevision = 4)
        val sourceEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("changed"))
        val dependentField = UUID.randomUUID()
        val dependent = fixture.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            dependentField
        )
        fixture.stubFieldBinding(fixture.requirement, sourceEntry.fieldContractId)
        val assignment = SchemaAssignment().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = fixture.request.id
            schemaVersionId = UUID.randomUUID()
        }
        val rootSet = FieldValueSet().apply {
            id = UUID.randomUUID()
            schemaAssignmentId = assignment.id
            setKind = FieldValueSetKind.ROOT
            revision = 2
        }
        whenever(
            fixture.fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    fixture.request.id
                )
            )
        )
            .thenReturn(assignment)
        whenever(fixture.fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Root)).thenReturn(rootSet)
        var tag = "\"fields-2\""
        var dependentCleared = false
        fun projection() = responseFieldProjection(fixture.request.id, listOf(dependentField to "dependent")).let {
            it.copy(
                etag = tag,
                fields = it.fields.map { field ->
                    if (dependentCleared) field.copy(
                        isEmpty = true,
                        value = JsonNull
                    ) else field
                })
        }
        whenever(fixture.schemaAssignmentService.setValues(any())).thenAnswer { projection() }
        whenever(fixture.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>())).thenAnswer { projection() }
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenAnswer {
            dependentCleared = true
            tag = "\"fields-3\""
            projection()
        }
        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenReturn(
            listOf(
                InformationRequestConditionEvaluationProjection(
                    "when-source-applies", 1, InformationRequestConditionEvaluationState.FALSE,
                    InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, emptySet(), emptySet(),
                ),
            ),
        )

        val result = fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                fixture.request.id,
                fixture.access,
                CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                "save-and-clear-dependent",
                listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        fieldValues = ResponseFieldValuesPatch(
                            entries = listOf(sourceEntry),
                            precondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(rootSet)),
                        ),
                    ),
                ),
                setOf(dependent.requirement.id),
            ),
        )

        assertTrue(dependentCleared)
        assertEquals("\"fields-3\"", result.fieldValueProjectionsByRequirementId.getValue(fixture.requirement.id).etag)
    }

    @Test
    fun `a party list validator is refused as a response precondition even when the counters agree`() {
        val fixture = Fixture(responseRevision = 4)
        fixture.request.partyRevision = 4
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "cross-family-response-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        val failure = assertThrows(CommandPreconditionException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(CommandPreconditionException.Kind.STALE, failure.kind)
        assertEquals(InformationRequestETag.responsesOf(fixture.request), failure.currentETag)
        verify(fixture.responseStore, never()).save(any())
    }

    @Test
    fun `stale response precondition refuses before saving`()
    {
        val fixture = Fixture(responseRevision = 4)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision("\"responses:${fixture.request.id}:3\""),
            idempotencyKey = "stale-response-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        val failure = assertThrows(CommandPreconditionException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(CommandPreconditionException.Kind.STALE, failure.kind)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.responseStore, never()).update(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `a patch targeting a removed group occurrence is refused and saves nothing`()
    {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "removed-occurrence-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(command)
        }

        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, failure.reasonCode)
        assertEquals(4, fixture.request.responseRevision)
        assertTrue(fixture.responses.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.responseStore, never()).save(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `a patch result excludes a prior response recorded against a since-removed occurrence`()
    {
        val fixture = Fixture(responseRevision = 4)
        val removedRequirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = fixture.request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "items[0]"
        }
        fixture.responses += InformationRequestResponse().apply {
            informationRequestId = fixture.request.id
            informationRequestRequirementId = removedRequirement.id
            requirementRevisionId = UUID.randomUUID()
            occurrencePath = removedRequirement.occurrencePath
            disposition = InformationRequestResponseDisposition.PROVIDED
            activeInResponse = true
            responseRevision = 3
        }
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, removedRequirement))
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "save-with-removed-sibling",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        val result = fixture.service.patch(command)

        assertTrue(result.responses.any { it.informationRequestRequirementId == fixture.requirement.id })
        assertTrue(result.responses.none { it.informationRequestRequirementId == removedRequirement.id })
    }

    @Test
    fun `hiding a response records the rule, state, policy and occurrence in the transition payload and an effect row`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY)

        fixture.service.patch(fixture.patchOwnRequirement("record-hidden-effect"))

        val history = argumentCaptor<InformationRequestTransitionHistoryCommand>()
        verify(fixture.transitionHistory).record(history.capture())
        assertEquals("1", history.firstValue.details["conditionEffectCount"])
        val effect =
            Json.parseToJsonElement(history.firstValue.details.getValue("conditionEffects")).jsonArray.single().jsonObject
        assertEquals(hidden.requirement.id.toString(), effect.getValue("requirementId").jsonPrimitive.content)
        assertEquals("root", effect.getValue("occurrencePath").jsonPrimitive.content)
        assertEquals("when-source-applies", effect.getValue("ruleKey").jsonPrimitive.content)
        assertEquals("FALSE", effect.getValue("evaluationState").jsonPrimitive.content)
        assertEquals("RETAIN_SECURELY", effect.getValue("hiddenDataPolicy").jsonPrimitive.content)
        assertEquals("HIDDEN", effect.getValue("effect").jsonPrimitive.content)
        val row = fixture.effects.single()
        assertEquals(fixture.transitions.single().id, row.transitionId)
        assertEquals(fixture.request.id, row.informationRequestId)
        assertEquals(hidden.requirement.id, row.informationRequestRequirementId)
        assertEquals(hidden.response.id, row.responseId)
        assertEquals("root", row.occurrencePath)
        assertEquals("when-source-applies", row.ruleKey)
        assertEquals(1, row.expressionVersion)
        assertEquals(InformationRequestConditionEvaluationState.FALSE, row.evaluationState)
        assertEquals(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY, row.hiddenDataPolicy)
        assertEquals(InformationRequestConditionEffectKind.HIDDEN, row.effect)
        assertEquals(4, row.responseRevision)
        assertEquals(fixture.access.principal.id, row.recordedByPrincipalId)
    }

    @Test
    fun `reactivation clears every hidden marker and records the rule that had hidden the response`() {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY)
        fixture.service.patch(fixture.patchOwnRequirement("hide-before-reactivation"))
        fixture.conditionHolds(
            InformationRequestConditionEvaluationState.TRUE,
            InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
        )

        fixture.service.patch(fixture.patchOwnRequirement("reactivate-with-effect"))

        assertTrue(hidden.response.activeInResponse)
        assertNull(hidden.response.hiddenByConditionRuleKey)
        assertNull(hidden.response.hiddenDataPolicy)
        assertNull(hidden.response.hiddenAt)
        assertEquals(
            listOf(InformationRequestConditionEffectKind.HIDDEN, InformationRequestConditionEffectKind.REACTIVATED),
            fixture.effects.map { it.effect },
        )
        val reactivation = fixture.effects.last()
        assertEquals("when-source-applies", reactivation.ruleKey)
        assertEquals(InformationRequestConditionEvaluationState.TRUE, reactivation.evaluationState)
        assertEquals(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY, reactivation.hiddenDataPolicy)
        assertEquals(fixture.transitions.last().id, reactivation.transitionId)
        assertEquals(hidden.response.responseRevision, reactivation.responseRevision)
    }

    @Test
    fun `clearing and archiving record which effect each policy had`() {
        val clearing = Fixture(responseRevision = 4)
        val clearedField = UUID.randomUUID()
        val cleared = clearing.hiddenConditionalResponse(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            clearedField
        )
        val clearingProjection = responseFieldProjection(clearing.request.id, listOf(clearedField to "cleared-value"))
        whenever(clearing.schemaAssignmentService.getAssignment(any<FieldValueReadCommand>())).thenReturn(
            clearingProjection
        )
        whenever(clearing.schemaAssignmentService.clearValues(any())).thenReturn(clearingProjection.copy(fields = emptyList()))
        clearing.service.patch(
            clearing.patchOwnRequirement("record-cleared-effect")
                .copy(confirmedHiddenResponseClears = setOf(cleared.requirement.id)),
        )

        val archiving = Fixture(responseRevision = 4)
        archiving.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE)
        archiving.service.patch(archiving.patchOwnRequirement("record-archived-effect"))

        val clearedEffect = clearing.effects.single()
        assertEquals(InformationRequestConditionEffectKind.CLEARED, clearedEffect.effect)
        assertEquals(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            clearedEffect.hiddenDataPolicy
        )
        assertNull(clearedEffect.archiveId)
        val archivedEffect = archiving.effects.single()
        assertEquals(InformationRequestConditionEffectKind.ARCHIVED, archivedEffect.effect)
        assertEquals(archiving.archives.single().id, archivedEffect.archiveId)
    }

    @Test
    fun `a draft patch clears a Field its Schema marks required and keeps the earlier revision`() {
        val fields = SchemaAssignmentFieldsFixture(
            resourceType = ResourceType.INFORMATION_REQUEST.name,
            requiredFields = true,
        )
        val saved = fields.save(listOf(fields.entry(fields.noteContractId, "Original process data")))
        val fixture =
            Fixture(responseRevision = 4, requestId = fields.resourceId, schemaAssignmentService = fields.service)
        fixture.useFieldsFixture(fields.assignmentId, fields.rootValueSetId)
        fixture.stubFieldBinding(fixture.requirement, fields.noteContractId, fields.noteDefinitionId)
        val before = InformationRequestETag.responsesOf(fixture.request)

        val result = fixture.service.patch(
            PatchInformationRequestResponsesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(before),
                idempotencyKey = "clear-required-field",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = fixture.requirement.id,
                        fieldValues = ResponseFieldValuesPatch(
                            entries = listOf(
                                FieldValueEntry(
                                    fields.noteContractId,
                                    JsonNull
                                )
                            ),
                            precondition = FieldsPrecondition.ExpectedRevision(saved.etag!!),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            JsonNull,
            fields.read()!!.fields.single { it.fieldContractId == fields.noteContractId }.value,
        )
        val history = fields.rootRevisions(fields.noteContractId)
        assertEquals("Original process data", history.first().textValue)
        assertTrue(history.last().isCleared)
        assertNotEquals(before, result.responseETag)
        assertEquals(fields.rootValueSetId, fixture.responses.single().fieldValueSetId)
    }

    @Test
    fun `a disposition the Requirement does not permit is refused naming the Requirement and occurrence instead of the value`() {
        val fixture = Fixture(responseRevision = 4)

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "refuse-unpermitted-disposition",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            disposition = InformationRequestResponseDisposition.NOT_APPLICABLE,
                            narrative = ResponseNarrativePatch.Set("Not relevant to this process."),
                        ),
                    ),
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED, failure.reasonCode)
        assertFalse(failure.message.contains("NOT_APPLICABLE"))
        val problem = assertInstanceOf(InformationRequestItemRefusalException::class.java, failure).problems.single()
        assertEquals(InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED, problem.code)
        assertEquals(fixture.requirement.id, problem.requirementId)
        assertEquals("root", problem.occurrencePath)
        assertTrue(fixture.responses.isEmpty())
    }

    @Test
    fun `refusals for several Requirements in one patch are reported together with one problem each`() {
        val fixture = Fixture(responseRevision = 4)
        val second = fixture.addRootRequirement()

        val failure = assertThrows(InformationRequestItemRefusalException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "refuse-two-dispositions",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            disposition = InformationRequestResponseDisposition.WAIVED,
                            narrative = ResponseNarrativePatch.Set("Waived by agreement."),
                        ),
                        InformationRequestResponsePatch(
                            requirementId = second.id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED, failure.reasonCode)
        assertEquals(setOf(fixture.requirement.id, second.id), failure.problems.map { it.requirementId }.toSet())
        assertTrue(failure.problems.all { it.code == InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED })
        verify(fixture.responseStore, never()).save(any())
    }

    @Test
    fun `a patch naming a Requirement the caller may not view is refused before its membership, removal or lock is read`() {
        val fixture = Fixture(responseRevision = 4)
        val hiddenRequirementId = UUID.randomUUID()
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_VIEW),
                eq(ResourceRef.informationRequestRequirement(hiddenRequirementId)),
                any(),
            ),
        ).thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))

        val refusal = assertThrows(ForbiddenException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "refuse-hidden-requirement",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = hiddenRequirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
        }

        assertEquals("Access denied to respond to Information Request Requirement", refusal.message)
        verify(fixture.lockService, never()).requireUnlocked(any(), any())
        verify(fixture.responseStore, never()).save(any())
    }

    @Test
    fun `a patch naming a Requirement the caller may view outside this request or in a removed occurrence names the Requirement it addressed`() {
        val unknownFixture = Fixture(responseRevision = 4)
        val unknownRequirementId = UUID.randomUUID()
        val unknown = assertThrows(InformationRequestItemRefusalException::class.java) {
            unknownFixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = unknownFixture.request.id,
                    access = unknownFixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(
                            unknownFixture.request
                        )
                    ),
                    idempotencyKey = "refuse-unknown-requirement",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = unknownRequirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
        }
        val removedFixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        whenever(removedFixture.occurrenceRepository.findForRequest(removedFixture.request.id)).thenReturn(emptyList())
        val removed = assertThrows(InformationRequestItemRefusalException::class.java) {
            removedFixture.service.patch(removedFixture.patchOwnRequirement("refuse-removed-occurrence"))
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, unknown.reasonCode)
        assertEquals(unknownRequirementId, unknown.problems.single().requirementId)
        assertFalse(unknown.message.contains(unknownRequirementId.toString()))
        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, removed.reasonCode)
        assertEquals(removedFixture.requirement.id, removed.problems.single().requirementId)
        assertEquals("items[0]", removed.problems.single().occurrencePath)
    }

    @Test
    fun `Field patches in one occurrence stating different preconditions are refused naming that occurrence`() {
        val fixture = Fixture(responseRevision = 4, occurrencePath = "items[0]")
        val second = fixture.addRootRequirement("items[0]")
        val firstEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("First answer"))
        val secondEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Second answer"))
        fixture.stubFieldBinding(fixture.requirement, firstEntry.fieldContractId)
        fixture.stubFieldBinding(second, secondEntry.fieldContractId)

        val failure = assertThrows(InformationRequestItemRefusalException::class.java) {
            fixture.service.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "refuse-conflicting-field-preconditions",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.requirement.id,
                            fieldValues = ResponseFieldValuesPatch(
                                listOf(firstEntry),
                                FieldsPrecondition.ExpectedRevision("\"first-read\"")
                            ),
                        ),
                        InformationRequestResponsePatch(
                            requirementId = second.id,
                            fieldValues = ResponseFieldValuesPatch(
                                listOf(secondEntry),
                                FieldsPrecondition.ExpectedRevision("\"second-read\"")
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_PRECONDITION_CONFLICT, failure.reasonCode)
        assertEquals(setOf(fixture.requirement.id, second.id), failure.problems.map { it.requirementId }.toSet())
        assertTrue(failure.problems.all { it.occurrencePath == "items[0]" })
        verify(fixture.schemaAssignmentService, never()).setValues(any())
    }

    @Test
    fun `a patch result carries only the responses the caller's own patch names, on save and on replay`() {
        listOf(
            RequestAccessContext(
                PrincipalRef.participant(UUID.randomUUID()),
                AuthorizationContext(sessionRef = "verified-session")
            ),
            RequestAccessContext(
                PrincipalRef.user(UUID.randomUUID()),
                AuthorizationContext(activeOrgId = UUID.randomUUID())
            ),
        ).forEach { access ->
            val fixture = Fixture(responseRevision = 4)
            val other = fixture.addRootRequirement()
            fixture.responses += InformationRequestResponse().apply {
                informationRequestId = fixture.request.id
                informationRequestRequirementId = other.id
                requirementRevisionId = UUID.randomUUID()
                occurrencePath = other.occurrencePath
                disposition = InformationRequestResponseDisposition.PROVIDED
                narrative = "Another party's answer."
                recordedByPrincipalKind = PrincipalKind.PARTICIPANT
                recordedByPrincipalId = UUID.randomUUID()
                responseRevision = 3
            }
            val command = fixture.patchOwnRequirement("own-responses-only-${access.principal.kind.name.lowercase()}")
                .copy(access = access)

            val saved = fixture.service.patch(command)
            val replayed = fixture.service.patch(command)

            listOf(saved, replayed).forEach { result ->
                assertEquals(
                    listOf(fixture.requirement.id),
                    result.responses.map { it.informationRequestRequirementId })
                assertFalse(result.requirementsById.containsKey(other.id))
            }
        }
    }

    @Test
    fun `a rule kept on a Requirement that is not conditional never hides its response`() {
        val fixture = Fixture(responseRevision = 4)
        val kept = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY)
        fixture.bindingRepository.findOrdered(fixture.request.templateVersionId).single().requiredness =
            InformationRequestRequiredness.REQUIRED

        fixture.service.patch(fixture.patchOwnRequirement("keep-unconditional-response"))

        assertTrue(kept.response.activeInResponse)
        assertNull(kept.response.hiddenByConditionRuleKey)
        assertEquals("Previously captured response.", kept.response.narrative)
        assertTrue(fixture.effects.isEmpty())
    }

    @Test
    fun `structured validators run after the write against the saved state and refuse with one problem per disclosed issue`() {
        val hiddenFromCaller = UUID.randomUUID()
        val validator = object : InformationRequestStructuredResponseValidator {
            override val stages = setOf(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE)

            override fun validate(context: InformationRequestStructuredResponseValidationContext) = listOf(
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                    message = "This entry repeats an earlier entry",
                    requirementId = context.requirementsById.keys.single(),
                    occurrencePath = "root",
                ),
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.PERIOD_COVERAGE,
                    message = "The covered periods leave a gap",
                    requirementId = hiddenFromCaller,
                ),
            )
        }
        val fixture = Fixture(responseRevision = 4, validators = listOf(validator))
        whenever(fixture.validationContextLoader.load(any(), any(), any())).thenAnswer {
            InformationRequestStructuredResponseValidationContext(
                stage = InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE,
                request = fixture.request,
                requirementsById = mapOf(fixture.requirement.id to fixture.requirement),
                responses = fixture.responses.toList(),
            )
        }
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_VIEW),
                eq(ResourceRef.informationRequestRequirement(hiddenFromCaller)),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Assigned to another party"))

        val failure = assertThrows(InformationRequestItemRefusalException::class.java) {
            fixture.service.patch(fixture.patchOwnRequirement("validate-after-write"))
        }

        assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, failure.reasonCode)
        assertFalse(failure.message.contains("repeats an earlier entry"))
        val problem = failure.problems.single()
        assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, problem.code)
        assertEquals(InformationRequestStructuredResponseValidationKind.DUPLICATE, problem.validationKind)
        assertEquals(fixture.requirement.id, problem.requirementId)
        assertEquals("root", problem.occurrencePath)
        assertEquals("This entry repeats an earlier entry", problem.message)
        assertEquals(1, failure.undisclosedProblemCount)
        val order = inOrder(fixture.responseStore, fixture.validationContextLoader)
        order.verify(fixture.responseStore).save(any())
        order.verify(fixture.validationContextLoader).load(
            eq(fixture.request),
            eq(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE),
            any(),
        )
    }

    private class Fixture(
        responseRevision: Long = 1, occurrencePath: String = "root",
        requestId: UUID = UUID.randomUUID(),
        val schemaAssignmentService: SchemaAssignmentService = mock(),
        validators: List<InformationRequestStructuredResponseValidator> = emptyList()
    )
    {
        val organizationId: UUID = UUID.randomUUID()
        val participantId: UUID = UUID.randomUUID()
        val access = RequestAccessContext(
            principal = PrincipalRef.participant(participantId),
            authorization = AuthorizationContext(activeOrgId = organizationId, sessionRef = "verified-session"),
        )
        val request = InformationRequest().apply {
            id = requestId
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = organizationId
            state = InformationRequestState.ISSUED
            this.responseRevision = responseRevision
        }
        val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = organizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val requirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            this.occurrencePath = occurrencePath
        }
        val currentRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = requirement.id
            informationRequestId = request.id
            sourceTemplateVersionId = requirement.sourceTemplateVersionId
            sourceTemplateRequirementId = requirement.sourceTemplateRequirementId
            sourceTemplateBindingId = requirement.sourceTemplateBindingId
            revisionNumber = 1
            this.occurrencePath = requirement.occurrencePath
            configurationHashSha256 = "a".repeat(64)
        }
        val responses = mutableListOf<InformationRequestResponse>()
        val requestRepository = mock<InformationRequestRepository>()
        val exchangeLookup = mock<ExchangeLookupService>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        private val dispositionRepository = mock<InformationRequestTemplateBindingDispositionRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val responseStore = mock<InformationRequestResponseStore>()
        val fieldsReader = mock<FieldsReadFacade>()
        val authorizationService = mock<AuthorizationService>()
        val entitlementGuard = mock<InformationRequestEntitlementGuard>()
        private val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        val conditionEvaluationService = mock<InformationRequestConditionEvaluationService>()
        private val structuredValidationService = InformationRequestStructuredResponseValidationService(validators)
        val validationContextLoader = mock<InformationRequestStructuredResponseValidationContextLoader>()
        val lockService =
            mock<com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService>()
        val archiveRepository = mock<InformationRequestResponseArchiveRepository>()
        val effectRepository = mock<InformationRequestResponseConditionEffectRepository>()
        val archives = mutableListOf<InformationRequestResponseArchive>()
        val effects = mutableListOf<InformationRequestResponseConditionEffect>()
        val transitions = mutableListOf<InformationRequestTransition>()
        val receiptStore = InMemoryResponseReceiptStore()
        private val activeResponses = InformationRequestActiveResponseReader(
            requirementRepository, occurrenceRepository, bindingRepository, responseStore, conditionEvaluationService,
        )
        val service = InformationRequestResponseDraftService(
            requestRepository = requestRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            revisionRepository = revisionRepository,
            responseStore = responseStore,
            mutationGuard = InformationRequestResponseMutationGuard(
                requestRepository,
                exchangeLookup,
                InformationRequestContinuationGuard(executionGrantService, entitlementGuard),
            ),
            patchValidator = InformationRequestResponsePatchValidator(dispositionRepository, responseStore),
            fieldWriter = InformationRequestResponseFieldWriter(
                schemaAssignmentService,
                fieldsReader,
                bindingRepository,
                activeResponses
            ),
            activeResponses = activeResponses,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope()),
            transitionHistory = transitionHistory,
            structuredResponseValidationService = structuredValidationService,
            validationContextLoader = validationContextLoader,
            lockService = lockService,
            hiddenResponsePolicies = InformationRequestHiddenResponsePolicyEnforcer(
                conditionEvaluationService = conditionEvaluationService,
                bindingRepository = bindingRepository,
                revisionRepository = revisionRepository,
                responseStore = responseStore,
                schemaAssignmentService = schemaAssignmentService,
                authorizationService = authorizationService,
                archiver = InformationRequestHiddenResponseArchiver(archiveRepository, mock()),
                fieldsReader = fieldsReader,
                hiddenFiles = InformationRequestHiddenResponseFiles(mock(), mock(), mock()),
                evidenceCollection = mock<InformationRequestEvidenceCollectionService>(),
                submissionLocks = lockService,
                conditionVisibility = InformationRequestConditionVisibility(
                    conditionEvaluationService,
                    bindingRepository
                ),
            ),
            conditionEffectRecorder = InformationRequestConditionEffectRecorder(effectRepository),
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )

        init
        {
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.update(request)).thenReturn(request)
            whenever(exchangeLookup.lockExchangeForUpdate(request.exchangeId)).thenReturn(exchange)
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(requirement))
            whenever(occurrenceRepository.findForRequest(request.id)).thenAnswer {
                requirementRepository.findForRequest(request.id).map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            this.occurrencePath = path
                        }
                    }
            }
            whenever(revisionRepository.findCurrentForRequest(request.id)).thenReturn(listOf(currentRevision))
            permitDispositions(
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.UNAVAILABLE,
            )
            whenever(responseStore.findCurrentForRequest(request.id)).thenAnswer {
                responses.filter { response -> response.activeInResponse }
            }
            whenever(responseStore.findAllForRequest(request.id)).thenAnswer { responses }
            whenever(conditionEvaluationService.evaluate(request.id)).thenReturn(emptyList())
            whenever(responseStore.findCurrentForUpdate(eq(request.id), eq(requirement.id))).thenAnswer {
                responses.firstOrNull { it.informationRequestRequirementId == requirement.id }
            }
            whenever(responseStore.save(any())).thenAnswer {
                it.getArgument<InformationRequestResponse>(0).also { response -> responses += response }
            }
            whenever(responseStore.update(any())).thenAnswer { it.getArgument<InformationRequestResponse>(0) }
            whenever(executionGrantService.findForRequest(request.id)).thenReturn(
                issuedExecutionGrant().apply { this.requestId = this@Fixture.request.id },
            )
            whenever(
                authorizationService.authorize(
                    any(),
                    any(),
                    any<ResourceRef>(),
                    any(),
                ),
            ).thenReturn(Decision.Allow())
            whenever(archiveRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestResponseArchive>(0).also { archive -> archives += archive }
            }
            whenever(effectRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestResponseConditionEffect>(0).also { effect -> effects += effect }
            }
            whenever(transitionHistory.record(any())).thenAnswer {
                InformationRequestTransition().apply { informationRequestId = request.id }.also { transition ->
                    transitions += transition
                }
            }
        }

        fun patchOwnRequirement(idempotencyKey: String) = PatchInformationRequestResponsesCommand(
            requestId = request.id,
            access = access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(request)),
            idempotencyKey = idempotencyKey,
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        fun conditionHolds(
            state: InformationRequestConditionEvaluationState,
            policy: InformationRequestConditionHiddenDataPolicy,
        ) {
            whenever(conditionEvaluationService.evaluate(request.id)).thenReturn(
                listOf(
                    InformationRequestConditionEvaluationProjection(
                        ruleKey = "when-source-applies",
                        expressionVersion = 1,
                        state = state,
                        hiddenDataPolicy = policy,
                        sourceRequirementKeys = emptySet(),
                        fieldDefinitionIds = emptySet(),
                    ),
                ),
            )
        }

        fun addRootRequirement(occurrencePath: String = "root"): InformationRequestRequirement {
            val added = InformationRequestRequirement().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = UUID.randomUUID()
                sourceTemplateBindingId = UUID.randomUUID()
                this.occurrencePath = occurrencePath
            }
            val addedRevision = InformationRequestRequirementRevision().apply {
                id = UUID.randomUUID()
                informationRequestRequirementId = added.id
                informationRequestId = request.id
                sourceTemplateVersionId = added.sourceTemplateVersionId
                sourceTemplateRequirementId = added.sourceTemplateRequirementId
                sourceTemplateBindingId = added.sourceTemplateBindingId
                revisionNumber = 1
                this.occurrencePath = occurrencePath
                configurationHashSha256 = "d".repeat(64)
            }
            val requirements = requirementRepository.findForRequest(request.id) + added
            val revisions = revisionRepository.findCurrentForRequest(request.id) + addedRevision
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(requirements)
            whenever(revisionRepository.findCurrentForRequest(request.id)).thenReturn(revisions)
            whenever(responseStore.findCurrentForUpdate(eq(request.id), eq(added.id))).thenAnswer {
                responses.firstOrNull { it.informationRequestRequirementId == added.id }
            }
            return added
        }

        fun useFieldsFixture(assignmentId: UUID, rootValueSetId: UUID)
        {
            whenever(fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id)))
                .thenReturn(SchemaAssignment().apply {
                    id = assignmentId
                    resourceType = ResourceType.INFORMATION_REQUEST.name
                    resourceId = request.id
                    schemaVersionId = UUID.randomUUID()
                })
            whenever(fieldsReader.valueSetOf(assignmentId, FieldValueSetRef.Root)).thenReturn(FieldValueSet().apply {
                id = rootValueSetId
                schemaAssignmentId = assignmentId
                setKind = FieldValueSetKind.ROOT
            })
        }

        fun permitDispositions(vararg dispositions: InformationRequestResponseDisposition) =
            permitDispositionsFor(requirement, *dispositions)

        fun permitDispositionsFor(
            permittedRequirement: InformationRequestRequirement,
            vararg dispositions: InformationRequestResponseDisposition,
        ) {
            whenever(dispositionRepository.findForBinding(permittedRequirement.sourceTemplateBindingId)).thenReturn(
                dispositions.map { permitted ->
                    InformationRequestTemplateBindingDisposition().apply {
                        templateBindingId = permittedRequirement.sourceTemplateBindingId
                        templateVersionId = request.templateVersionId
                        disposition = permitted
                    }
                },
            )
        }

        fun stubFieldBinding(
            requirement: InformationRequestRequirement,
            fieldContractId: UUID,
            fieldDefinitionId: UUID = UUID.randomUUID(),
        )
        {
            whenever(bindingRepository.findById(requirement.sourceTemplateBindingId)).thenReturn(
                InformationRequestTemplateRequirementBinding().apply {
                    id = requirement.sourceTemplateBindingId
                    templateVersionId = request.templateVersionId
                    templateDefinitionId = UUID.randomUUID()
                    templateRequirementId = requirement.sourceTemplateRequirementId
                    templateSectionId = UUID.randomUUID()
                    prompt = "Prompt"
                    collectedFieldDefinitionId = fieldDefinitionId
                },
            )
            whenever(fieldsReader.contract(fieldContractId)).thenReturn(
                FieldContract().apply {
                    id = fieldContractId
                    this.fieldDefinitionId = fieldDefinitionId
                    label = "Field"
                },
            )
        }

        fun existingResponse(
            disposition: InformationRequestResponseDisposition,
            narrative: String?,
            responseRevision: Long,
        ): InformationRequestResponse =
            InformationRequestResponse().apply {
                informationRequestId = request.id
                informationRequestRequirementId = requirement.id
                requirementRevisionId = currentRevision.id
                occurrencePath = requirement.occurrencePath
                this.disposition = disposition
                this.narrative = narrative
                recordedByPrincipalKind = PrincipalKind.USER
                recordedByPrincipalId = UUID.randomUUID()
                this.responseRevision = responseRevision
            }.also { responses += it }

        fun hiddenConditionalResponses(
            policy: InformationRequestConditionHiddenDataPolicy,
            count: Int,
        ): List<HiddenConditionalResponseFixture> {
            val hidden = (1..count).map { hiddenConditionalResponse(policy) }
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(requirement) + hidden.map { it.requirement })
            whenever(revisionRepository.findCurrentForRequest(request.id)).thenReturn(
                listOf(currentRevision) + hidden.map { fixture ->
                    InformationRequestRequirementRevision().apply {
                        id = fixture.response.requirementRevisionId
                        informationRequestRequirementId = fixture.requirement.id
                        informationRequestId = request.id
                        sourceTemplateVersionId = fixture.requirement.sourceTemplateVersionId
                        sourceTemplateRequirementId = fixture.requirement.sourceTemplateRequirementId
                        sourceTemplateBindingId = fixture.requirement.sourceTemplateBindingId
                        revisionNumber = 1
                        occurrencePath = fixture.requirement.occurrencePath
                        configurationHashSha256 = "c".repeat(64)
                    }
                },
            )
            whenever(bindingRepository.findOrdered(request.templateVersionId)).thenReturn(
                hidden.map { fixture ->
                    InformationRequestTemplateRequirementBinding().apply {
                        id = fixture.requirement.sourceTemplateBindingId
                        templateVersionId = request.templateVersionId
                        templateDefinitionId = UUID.randomUUID()
                        templateRequirementId = fixture.requirement.sourceTemplateRequirementId
                        templateSectionId = UUID.randomUUID()
                        prompt = "Provide related process information"
                        requiredness = InformationRequestRequiredness.CONDITIONAL
                        conditionalRuleKey = "when-source-applies"
                    }
                },
            )
            return hidden
        }

        fun hiddenConditionalResponse(
            policy: InformationRequestConditionHiddenDataPolicy,
            fieldDefinitionId: UUID? = null,
        ): HiddenConditionalResponseFixture
        {
            val hiddenRequirement = InformationRequestRequirement().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = UUID.randomUUID()
                sourceTemplateBindingId = UUID.randomUUID()
                occurrencePath = "root"
            }
            val hiddenRevision = InformationRequestRequirementRevision().apply {
                id = UUID.randomUUID()
                informationRequestRequirementId = hiddenRequirement.id
                informationRequestId = request.id
                sourceTemplateVersionId = hiddenRequirement.sourceTemplateVersionId
                sourceTemplateRequirementId = hiddenRequirement.sourceTemplateRequirementId
                sourceTemplateBindingId = hiddenRequirement.sourceTemplateBindingId
                revisionNumber = 1
                occurrencePath = hiddenRequirement.occurrencePath
                configurationHashSha256 = "c".repeat(64)
            }
            val hiddenBinding = InformationRequestTemplateRequirementBinding().apply {
                id = hiddenRequirement.sourceTemplateBindingId
                templateVersionId = request.templateVersionId
                templateDefinitionId = UUID.randomUUID()
                templateRequirementId = hiddenRequirement.sourceTemplateRequirementId
                templateSectionId = UUID.randomUUID()
                prompt = "Provide related process information"
                requiredness = InformationRequestRequiredness.CONDITIONAL
                conditionalRuleKey = "when-source-applies"
                collectedFieldDefinitionId = fieldDefinitionId
            }
            val hiddenResponse = InformationRequestResponse().apply {
                informationRequestId = request.id
                informationRequestRequirementId = hiddenRequirement.id
                requirementRevisionId = hiddenRevision.id
                occurrencePath = hiddenRequirement.occurrencePath
                disposition = InformationRequestResponseDisposition.PROVIDED
                narrative = "Previously captured response."
                recordedByPrincipalKind = PrincipalKind.PARTICIPANT
                recordedByPrincipalId = participantId
                responseRevision = 4
            }
            responses += hiddenResponse
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(
                listOf(
                    requirement,
                    hiddenRequirement
                )
            )
            whenever(revisionRepository.findCurrentForRequest(request.id)).thenReturn(
                listOf(
                    currentRevision,
                    hiddenRevision
                )
            )
            whenever(bindingRepository.findOrdered(request.templateVersionId)).thenReturn(listOf(hiddenBinding))
            whenever(conditionEvaluationService.evaluate(request.id)).thenReturn(
                listOf(
                    InformationRequestConditionEvaluationProjection(
                        ruleKey = "when-source-applies",
                        expressionVersion = 1,
                        state = InformationRequestConditionEvaluationState.FALSE,
                        hiddenDataPolicy = policy,
                        sourceRequirementKeys = emptySet(),
                        fieldDefinitionIds = emptySet(),
                    ),
                ),
            )
            return HiddenConditionalResponseFixture(hiddenRequirement, hiddenResponse)
        }

        fun schemaAssignmentProjection() = SchemaAssignmentDto(
            id = UUID.randomUUID(),
            resourceType = ResourceType.INFORMATION_REQUEST.name,
            resourceId = request.id,
            schemaVersionId = UUID.randomUUID(),
            schemaDefinitionId = UUID.randomUUID(),
            schemaKey = "process-data",
            displayName = "Process data",
            versionNumber = 1,
            assignmentSource = SchemaAssignmentSource.API,
            assignedAt = Timestamp.from(Instant.now()),
            etag = "\"field-values\"",
        )
    }
}

private class InMemoryResponseReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
