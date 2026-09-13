package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldValueSetETag
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldValueWriteCommand
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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
        assertEquals("$", response.occurrencePath)
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
    fun `replaying a response patch does not disclose responses newer than the receipt revision`()
    {
        val fixture = Fixture()
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-does-not-return-replacement-response",
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

        assertEquals(first.responseETag, replay.responseETag)
        assertTrue(replay.responses.isEmpty())
    }

    @Test
    fun `replaying a Field response patch does not disclose projections newer than the receipt revision`()
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, fixture.requirement.occurrencePath))
            .thenReturn(occurrenceSet)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "replay-does-not-return-replacement-field-response",
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

        assertTrue(replay.responses.isEmpty())
        assertTrue(replay.fieldValueProjectionsByRequirementId.isEmpty())
        verify(fixture.schemaAssignmentService, never()).getAssignment(any())
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

        val locks = inOrder(fixture.exchangeRepository, fixture.requestRepository)
        locks.verify(fixture.exchangeRepository).findByIdForUpdate(fixture.request.exchangeId)
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, fixture.requirement.occurrencePath))
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
        assertEquals(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, fixture.request.id), captor.firstValue.resource)
        assertEquals(fixture.access.principal, captor.firstValue.access.principal)
        assertEquals(fixture.access.authorization, captor.firstValue.access.authorization)
        assertEquals(FieldValueSetRef.Occurrence("items[0]"), captor.firstValue.valueSet)
        assertEquals(listOf(fieldEntry), captor.firstValue.entries)
        assertEquals(fieldPrecondition, captor.firstValue.precondition)
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, fixture.requirement.occurrencePath))
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, fixture.requirement.occurrencePath))
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, "items[0]")).thenReturn(firstSet)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, "items[1]")).thenReturn(secondSet)
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, "items[0]")).thenReturn(occurrenceSet)
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
    fun `an explicit clear entry batches with a normal Field entry addressing the same occurrence`()
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, "items[0]")).thenReturn(occurrenceSet)
        val sharedPrecondition = FieldsPrecondition.ExpectedRevision(FieldValueSetETag.of(occurrenceSet))
        val keptEntry = FieldValueEntry(UUID.randomUUID(), JsonPrimitive("Kept answer"))
        val clearedEntry = FieldValueEntry(UUID.randomUUID(), kotlinx.serialization.json.JsonNull)
        fixture.stubFieldBinding(fixture.requirement, keptEntry.fieldContractId)
        fixture.stubFieldBinding(secondRequirement, clearedEntry.fieldContractId)
        whenever(fixture.schemaAssignmentService.setValues(any())).thenReturn(fixture.schemaAssignmentProjection())
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "batch-clear-with-value-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    fieldValues = ResponseFieldValuesPatch(entries = listOf(keptEntry), precondition = sharedPrecondition),
                ),
                InformationRequestResponsePatch(
                    requirementId = secondRequirement.id,
                    fieldValues = ResponseFieldValuesPatch(entries = listOf(clearedEntry), precondition = sharedPrecondition),
                ),
            ),
        )

        fixture.service.patch(command)

        val captor = argumentCaptor<FieldValueWriteCommand>()
        verify(fixture.schemaAssignmentService, times(1)).setValues(captor.capture())
        assertEquals(listOf(keptEntry, clearedEntry), captor.firstValue.entries)
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, "items[0]")).thenReturn(firstSet)
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
                    fieldValues = ResponseFieldValuesPatch(entries = listOf(firstEntry), precondition = FieldsPrecondition.Unconditioned),
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
        whenever(fixture.schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, fixture.request.id))
            .thenReturn(assignment)
        whenever(fixture.fieldValueSetRepository.findOccurrence(assignment.id, fixture.requirement.occurrencePath))
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
    fun `archive policy removes a hidden conditional response from the active response without clearing it`()
    {
        val fixture = Fixture(responseRevision = 4)
        val hidden = fixture.hiddenConditionalResponse(
            policy = InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
        )
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "archive-hidden-response",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.requirement.id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        val result = fixture.service.patch(command)

        assertEquals(listOf(fixture.requirement.id), result.responses.map { it.informationRequestRequirementId })
        assertEquals(InformationRequestResponseDisposition.PROVIDED, hidden.response.disposition)
        assertEquals("Previously captured response.", hidden.response.narrative)
        assertEquals(false, hidden.response.activeInResponse)
        assertEquals(
            InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
            hidden.response.hiddenDataPolicy,
        )
        assertEquals("when-source-applies", hidden.response.hiddenByConditionRuleKey)
        assertEquals(5, hidden.response.responseRevision)
        verify(fixture.responseStore).update(hidden.response)
    }

    @Test
    fun `clear policy requires explicit confirmation before clearing hidden response data`()
    {
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
        assertEquals(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, hidden.response.hiddenDataPolicy)
        assertEquals("when-source-applies", hidden.response.hiddenByConditionRuleKey)
        assertEquals(5, hidden.response.responseRevision)
        verify(fixture.responseStore).update(hidden.response)
    }

    @Test
    fun `retained and archived hidden responses reactivate when a false condition returns true with authorship intact`()
    {
        listOf(
            InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
            InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
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

            assertTrue(result.responses.any { it.informationRequestRequirementId == hidden.requirement.id })
            assertEquals(InformationRequestResponseDisposition.PROVIDED, hidden.response.disposition)
            assertEquals("Previously captured response.", hidden.response.narrative)
            assertEquals(true, hidden.response.activeInResponse)
            assertNull(hidden.response.hiddenByConditionRuleKey)
            assertNull(hidden.response.hiddenDataPolicy)
            assertNull(hidden.response.hiddenAt)
            assertEquals(PrincipalKind.PARTICIPANT, hidden.response.recordedByPrincipalKind)
            assertEquals(fixture.participantId, hidden.response.recordedByPrincipalId)
            assertEquals("original-session", hidden.response.recordedBySessionRef)
            assertEquals(6, hidden.response.responseRevision)
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
        val hidden = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, fieldId)
        hidden.response.fieldValueSetId = UUID.randomUUID()
        val projection = responseFieldProjection(fixture.request.id, listOf(fieldId to "retained-secret"))
        whenever(fixture.schemaAssignmentService.getAssignment(any<com.docuhyphen.app.api.service.fields.FieldValueReadCommand>()))
            .thenReturn(projection)
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenReturn(projection.copy(fields = emptyList()))
        val result = fixture.service.patch(PatchInformationRequestResponsesCommand(fixture.request.id, fixture.access,
            CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)), "confirmed-field-clear",
            emptyList(), setOf(hidden.requirement.id)))
        verify(fixture.schemaAssignmentService).clearValues(org.mockito.kotlin.check {
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
        val fields = com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture(resourceType = ResourceType.INFORMATION_REQUEST.name)
        fields.save(listOf(fields.entry(fields.noteContractId, "Original process data")))
        val fixture = Fixture(responseRevision = 4, requestId = fields.resourceId, schemaAssignmentService = fields.service)
        val hidden = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, fields.noteDefinitionId)
        hidden.response.fieldValueSetId = fields.rootValueSetId
        fixture.service.patch(PatchInformationRequestResponsesCommand(fixture.request.id, fixture.access,
            CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)), "clear-with-history",
            emptyList(), setOf(hidden.requirement.id)))
        assertEquals(kotlinx.serialization.json.JsonNull, fields.read()!!.fields.single { it.fieldContractId == fields.noteContractId }.value)
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
        val hidden = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, fieldId)
        val valueSetId = UUID.randomUUID()
        hidden.response.fieldValueSetId = valueSetId
        whenever(fixture.schemaAssignmentService.getAssignment(any<com.docuhyphen.app.api.service.fields.FieldValueReadCommand>()))
            .thenReturn(responseFieldProjection(fixture.request.id, listOf(fieldId to "retained-secret")))
        whenever(fixture.authorizationService.authorize(fixture.access.principal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(hidden.requirement.id), fixture.access.authorization))
            .thenReturn(Decision.Deny("NO_GRANT", "Not assigned"))
        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            fixture.service.patch(PatchInformationRequestResponsesCommand(fixture.request.id, fixture.access,
                CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)), "denied-field-clear",
                emptyList(), setOf(hidden.requirement.id)))
        }
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
        val first = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, firstField)
        val firstBinding = fixture.bindingRepository.findOrdered(fixture.request.templateVersionId).single()
        val second = fixture.hiddenConditionalResponse(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, secondField)
        val secondBinding = fixture.bindingRepository.findOrdered(fixture.request.templateVersionId).single().apply {
            conditionalRuleKey = "when-dependent-data-applies"
        }
        whenever(fixture.bindingRepository.findOrdered(fixture.request.templateVersionId)).thenReturn(listOf(firstBinding, secondBinding))
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.requirement, first.requirement, second.requirement))
        val cleared = mutableSetOf<UUID>()
        fun projection() = responseFieldProjection(fixture.request.id, listOf(firstField to "source", secondField to "dependent")).let {
            it.copy(fields = it.fields.map { field -> if (field.fieldContractId in cleared)
                field.copy(isEmpty = true, value = kotlinx.serialization.json.JsonNull) else field })
        }
        whenever(fixture.schemaAssignmentService.getAssignment(any<com.docuhyphen.app.api.service.fields.FieldValueReadCommand>()))
            .thenAnswer { projection() }
        whenever(fixture.schemaAssignmentService.clearValues(any())).thenAnswer {
            cleared += it.getArgument<com.docuhyphen.app.api.model.fields.FieldValueClearCommand>(0).fieldContractIds
            projection()
        }
        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenAnswer {
            listOf(InformationRequestConditionEvaluationProjection("when-source-applies", 1,
                InformationRequestConditionEvaluationState.FALSE, InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                emptySet(), emptySet()),
                InformationRequestConditionEvaluationProjection("when-dependent-data-applies", 1,
                    if (firstField in cleared) InformationRequestConditionEvaluationState.UNKNOWN else InformationRequestConditionEvaluationState.TRUE,
                    InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION, emptySet(), emptySet()))
        }
        fixture.service.patch(PatchInformationRequestResponsesCommand(fixture.request.id, fixture.access,
            CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)), "clear-dependent-data",
            emptyList(), setOf(first.requirement.id, second.requirement.id)))
        assertEquals(setOf(firstField, secondField), cleared)
        assertEquals(false, second.response.activeInResponse)
    }

    @Test
    fun `stale response precondition refuses before saving`()
    {
        val fixture = Fixture(responseRevision = 4)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision("\"${fixture.request.id}:3\""),
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

    private class Fixture(responseRevision: Long = 1, occurrencePath: String = "$",
                          requestId: UUID = UUID.randomUUID(),
                          val schemaAssignmentService: SchemaAssignmentService = mock())
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
        val exchangeRepository = mock<ExchangeRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        private val dispositionRepository = mock<InformationRequestTemplateBindingDispositionRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val responseStore = mock<InformationRequestResponseStore>()
        val schemaAssignmentRepository = mock<SchemaAssignmentRepository>()
        val fieldValueSetRepository = mock<FieldValueSetRepository>()
        val fieldContractRepository = mock<FieldContractRepository>()
        val authorizationService = mock<AuthorizationService>()
        val entitlementGuard = mock<InformationRequestEntitlementGuard>()
        private val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        val conditionEvaluationService = mock<InformationRequestConditionEvaluationService>()
        private val structuredValidationService = InformationRequestStructuredResponseValidationService(emptyList())
        val receiptStore = InMemoryResponseReceiptStore()
        val service = InformationRequestResponseDraftService(
            requestRepository = requestRepository,
            exchangeRepository = exchangeRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            revisionRepository = revisionRepository,
            dispositionRepository = dispositionRepository,
            bindingRepository = bindingRepository,
            responseStore = responseStore,
            schemaAssignmentService = schemaAssignmentService,
            schemaAssignmentRepository = schemaAssignmentRepository,
            fieldValueSetRepository = fieldValueSetRepository,
            fieldContractRepository = fieldContractRepository,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(receiptStore),
            entitlementGuard = entitlementGuard,
            executionGrantService = executionGrantService,
            transitionHistory = transitionHistory,
            conditionEvaluationService = conditionEvaluationService,
            structuredResponseValidationService = structuredValidationService,
        )

        init
        {
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.update(request)).thenReturn(request)
            whenever(exchangeRepository.findByIdForUpdate(request.exchangeId)).thenReturn(exchange)
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
                RequestExecutionGrant().apply { this.requestId = this@Fixture.request.id },
            )
            whenever(
                authorizationService.authorize(
                    any(),
                    any(),
                    any<ResourceRef>(),
                    any(),
                ),
            ).thenReturn(Decision.Allow())
        }

        fun permitDispositions(vararg dispositions: InformationRequestResponseDisposition)
        {
            whenever(dispositionRepository.findForBinding(requirement.sourceTemplateBindingId)).thenReturn(
                dispositions.map { permitted ->
                    InformationRequestTemplateBindingDisposition().apply {
                        templateBindingId = requirement.sourceTemplateBindingId
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
            whenever(fieldContractRepository.findById(fieldContractId)).thenReturn(
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
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(requirement, hiddenRequirement))
            whenever(revisionRepository.findCurrentForRequest(request.id)).thenReturn(listOf(currentRevision, hiddenRevision))
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

    private data class HiddenConditionalResponseFixture(
        val requirement: InformationRequestRequirement,
        val response: InformationRequestResponse,
    )
}

private class InMemoryResponseReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()

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
