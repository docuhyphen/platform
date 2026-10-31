package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationIssue
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationKind
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationStage
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.*
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseMutationGuard
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestGroupOccurrenceServiceTest
{
    @Test
    fun `a group occurrence command locks the parent Exchange before the request row`()
    {
        val fixture = Fixture()
        val existing = fixture.occurrence(
            group = fixture.group,
            index = 0,
            path = "items[0]",
        )
        whenever(
            fixture.groupOccurrenceRepository.findForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(existing))
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(existing))

        fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-item-lock-order",
                groupKey = "items",
            ),
        )

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository, fixture.groupOccurrenceRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
        locks.verify(fixture.groupOccurrenceRepository).findForGroupAndParentForUpdate(
            fixture.request.id,
            fixture.group.id,
            null,
        )
    }

    @Test
    fun `a replayed group command is refused once the parent Exchange no longer accepts responses`() {
        val fixture = Fixture()
        val command = fixture.reorderCommand("reorder-before-termination")
        fixture.service.reorder(command)
        fixture.exchange.status = ExchangeStatus.REJECTED

        assertThrows<InformationRequestLifecycleException> { fixture.service.reorder(command) }
    }

    @Test
    fun `a replayed group command is refused after the request's execution grant is revoked`() {
        val fixture = Fixture()
        val command = fixture.reorderCommand("reorder-before-grant-revocation")
        fixture.service.reorder(command)
        fixture.grant.revokedAt = java.sql.Timestamp.from(java.time.Instant.now())

        val refusal = assertThrows<InformationRequestLifecycleException> { fixture.service.reorder(command) }

        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
    }

    @Test
    fun `a replayed group command is refused when the caller may still read the request but no longer respond`() {
        val fixture = Fixture()
        val command = fixture.reorderCommand("reorder-before-respond-loss")
        fixture.service.reorder(command)
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND),
                eq(ResourceRef.informationRequest(fixture.request.id)),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "No longer a responding party"))

        assertThrows<ForbiddenException> { fixture.service.reorder(command) }
    }

    @Test
    fun `a replayed reorder answers with its own group's visible siblings under the current response ETag`() {
        val fixture = Fixture()
        val command = fixture.reorderCommand("reorder-then-replay")
        val (first, second) = fixture.reorderedSiblings
        fixture.service.reorder(command)
        val elsewhere = fixture.occurrence(fixture.nestedGroup, 0, "items[0]/entries[0]", first.id)
        whenever(fixture.groupOccurrenceRepository.findForRequest(fixture.request.id)).thenReturn(
            listOf(
                first,
                second,
                elsewhere
            )
        )
        whenever(fixture.groupAuthorizationService.visibleOccurrences(any(), any(), any())).thenAnswer {
            it.getArgument<List<InformationRequestGroupOccurrence>>(2)
                .filterNot { occurrence -> occurrence.id == first.id }
        }
        fixture.request.responseRevision = 5

        val replayed = fixture.service.reorder(command)

        assertEquals(listOf(second), replayed.occurrences)
        assertEquals(InformationRequestETag.responsesOf(fixture.request), replayed.responseETag)
    }

    @Test
    fun `adding and removing answer only with siblings the caller may see`() {
        val fixture = Fixture()
        fixture.group.maxOccurrences = 3
        val first = fixture.activeOccurrence(fixture.group, 0, "items[0]")
        val second = fixture.activeOccurrence(fixture.group, 1, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null
            )
        )
            .thenReturn(listOf(first, second))
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null
            )
        )
            .thenReturn(listOf(first, second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id)).thenReturn(
            listOf(
                first,
                second
            )
        )
        whenever(fixture.groupAuthorizationService.visibleOccurrences(any(), any(), any())).thenAnswer {
            it.getArgument<List<InformationRequestGroupOccurrence>>(2)
                .filterNot { occurrence -> occurrence.id == second.id }
        }

        val added = fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-with-hidden-sibling",
                groupKey = "items",
            ),
        )
        val removed = fixture.service.remove(
            RemoveInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "remove-with-hidden-sibling",
                occurrenceId = first.id,
            ),
        )

        assertFalse(added.occurrences.contains(second))
        assertEquals(2, added.occurrences.size)
        assertEquals(emptyList<InformationRequestGroupOccurrence>(), removed.occurrences)
    }

    @Test
    fun `a replayed remove answers with the removed occurrence's remaining siblings`() {
        val fixture = Fixture()
        val first = fixture.activeOccurrence(fixture.group, 0, "items[0]")
        val second = fixture.activeOccurrence(fixture.group, 1, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null
            )
        )
            .thenReturn(listOf(first, second))
            .thenReturn(listOf(second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id)).thenReturn(
            listOf(
                first,
                second
            )
        )
        whenever(fixture.groupOccurrenceRepository.findById(first.id)).thenReturn(first)
        whenever(fixture.groupOccurrenceRepository.findForRequest(fixture.request.id)).thenReturn(listOf(first, second))
        val command = RemoveInformationRequestGroupOccurrenceCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
            idempotencyKey = "remove-then-replay",
            occurrenceId = first.id,
        )
        fixture.service.remove(command)

        val replayed = fixture.service.remove(command)

        assertEquals(listOf(second), replayed.occurrences)
        assertEquals(InformationRequestETag.responsesOf(fixture.request), replayed.responseETag)
    }

    @Test
    fun `a group whose stage was submitted refuses a new occurrence before creating one`()
    {
        val fixture = Fixture()
        whenever(fixture.lockService.requireBindingsOpen(any(), any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.SUBMISSION_LOCKED, "submitted"),
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "add-to-submitted-group",
                    groupKey = "items",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        verify(fixture.groupOccurrenceRepository, never()).save(any())
    }

    @Test
    fun `adding an occurrence creates a stable path provisions Fields and advances the response ETag`()
    {
        val fixture = Fixture()
        val existing = fixture.occurrence(
            group = fixture.group,
            index = 0,
            path = "items[0]",
        )
        whenever(
            fixture.groupOccurrenceRepository.findForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(existing))
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(existing))

        whenever(fixture.groupOccurrenceRepository.findAllForRequest(fixture.request.id)).thenReturn(listOf(existing))

        val result = fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-item",
                groupKey = "items",
            ),
        )

        assertEquals(2, fixture.request.responseRevision)
        assertEquals(InformationRequestETag.responsesOf(fixture.request), result.responseETag)
        val occurrence = fixture.savedGroupOccurrences.single()
        assertEquals("items[1]", occurrence.occurrencePath)
        assertEquals(1, occurrence.occurrenceIndex)
        assertEquals(fixture.request.id, occurrence.informationRequestId)
        assertEquals(fixture.group.id, occurrence.sourceTemplateGroupId)
        assertEquals(null, occurrence.parentOccurrenceId)
        assertEquals(listOf(existing, occurrence), result.occurrences)

        val requirement = fixture.savedRequirements.single()
        assertEquals(fixture.binding.id, requirement.sourceTemplateBindingId)
        assertEquals("items[1]", requirement.occurrencePath)
        assertEquals(requirement.id, fixture.savedRevisions.single().informationRequestRequirementId)
        assertEquals("items[1]", fixture.savedRevisions.single().occurrencePath)
        assertTrue(fixture.savedRevisions.single().configurationHashSha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals(requirement.id, fixture.savedCurrents.single().informationRequestRequirementId)

        verify(fixture.schemaAssignmentService).createOccurrenceValueSet(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, fixture.request.id),
            "items[1]",
        )
        verify(fixture.authorizationService).authorize(
            fixture.access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequest(fixture.request.id),
            fixture.access.authorization,
        )
        verify(fixture.transitionHistory).record(check {
            assertEquals(
                mapOf(
                    "occurrenceChange" to "ADD",
                    "groupKey" to "items",
                    "occurrenceId" to occurrence.id.toString(),
                    "occurrencePath" to "items[1]",
                ),
                it.details,
            )
        })
        verify(fixture.supportingEvidenceLinkService).materialize(fixture.request)
    }

    @Test
    fun `adding refuses to exceed the authored maximum without changing the response revision`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null
            )
        )
            .thenReturn(listOf(first, second))
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))

        val failure = assertThrows<InformationRequestLifecycleException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "add-past-max",
                    groupKey = "items",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_CARDINALITY_INVALID, failure.reasonCode)
        assertEquals(1, fixture.request.responseRevision)
        verify(fixture.groupOccurrenceRepository, never()).save(any())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `removing an occurrence marks it removed and keeps prior requirement history addressable`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(fixture.groupOccurrenceRepository.findActiveByIdForUpdate(fixture.request.id, second.id))
            .thenReturn(second)
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id))
            .thenReturn(listOf(first, second))

        val result = fixture.service.remove(
            RemoveInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "remove-item",
                occurrenceId = second.id,
            ),
        )

        assertEquals(2, fixture.request.responseRevision)
        assertEquals(listOf(first), result.occurrences)
        assertEquals(PrincipalKind.PARTICIPANT, second.removedByPrincipalKind)
        assertEquals(fixture.participantId, second.removedByPrincipalId)
        assertTrue(second.removedAt != null)
        assertTrue(fixture.savedRequirements.isEmpty())
        verify(fixture.groupOccurrenceRepository).update(second)
        verify(fixture.transitionHistory).record(check {
            assertEquals(
                mapOf(
                    "occurrenceChange" to "REMOVE",
                    "groupKey" to "items",
                    "occurrenceId" to second.id.toString(),
                    "occurrencePath" to "items[1]",
                ),
                it.details,
            )
        })
    }

    @Test
    fun `removing an occurrence closes the open revision of every Requirement asked beneath it`() {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        val nested = fixture.occurrence(fixture.nestedGroup, 0, "items[1]/entries[0]", second.id)
        fun runtimeRequirement(path: String) = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = fixture.request.id
            sourceTemplateVersionId = fixture.request.templateVersionId
            sourceTemplateRequirementId = fixture.requirement.id
            sourceTemplateBindingId = fixture.binding.id
            occurrencePath = path
        }

        val kept = runtimeRequirement("items[0]")
        val removed = runtimeRequirement("items[1]")
        val removedNested = runtimeRequirement("items[1]/entries[0]")
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(kept, removed, removedNested))
        whenever(fixture.groupOccurrenceRepository.findActiveByIdForUpdate(fixture.request.id, second.id))
            .thenReturn(second)
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id))
            .thenReturn(listOf(first, second, nested))

        fixture.service.remove(
            RemoveInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "remove-item-closes-revisions",
                occurrenceId = second.id,
            ),
        )

        verify(fixture.revisionRepository).closeOpen(eq(listOf(removed.id, removedNested.id)), any())
    }

    @Test
    fun `reordering active siblings updates only their display order and advances the response ETag`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))

        val result = fixture.service.reorder(
            ReorderInformationRequestGroupOccurrencesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "reorder-items",
                groupKey = "items",
                orderedOccurrenceIds = listOf(second.id, first.id),
            ),
        )

        assertEquals(2, fixture.request.responseRevision)
        assertEquals(listOf(second, first), result.occurrences)
        assertEquals(1, first.occurrenceIndex)
        assertEquals(0, second.occurrenceIndex)
        assertEquals("items[0]", first.occurrencePath)
        assertEquals("items[1]", second.occurrencePath)
        verify(fixture.groupOccurrenceRepository).update(first)
        verify(fixture.groupOccurrenceRepository).update(second)
    }

    @Test
    fun `adding after removal and reorder keeps a new stable occurrence path`()
    {
        val fixture = Fixture()
        val removed = fixture.occurrence(fixture.group, 0, "items[0]").apply {
            removedAt = java.sql.Timestamp.from(java.time.Instant.now())
            removedByPrincipalKind = PrincipalKind.PARTICIPANT
            removedByPrincipalId = fixture.participantId
        }
        val remaining = fixture.occurrence(fixture.group, 0, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(removed, remaining))
        whenever(fixture.groupOccurrenceRepository.findAllForRequest(fixture.request.id)).thenReturn(
            listOf(
                removed,
                remaining
            )
        )
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(remaining))

        val result = fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-after-remove-reorder",
                groupKey = "items",
            ),
        )

        val occurrence = fixture.savedGroupOccurrences.single()
        assertEquals("items[2]", occurrence.occurrencePath)
        assertEquals(1, occurrence.occurrenceIndex)
        assertEquals(listOf(remaining, occurrence), result.occurrences)
        assertEquals("items[2]", fixture.savedRequirements.single().occurrencePath)
        verify(fixture.schemaAssignmentService).createOccurrenceValueSet(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, fixture.request.id),
            "items[2]",
        )
    }

    @Test
    fun `a stale response-shape precondition refuses before touching group occurrences`()
    {
        val fixture = Fixture()

        val failure = assertThrows<CommandPreconditionException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision("\"responses:${fixture.request.id}:0\""),
                    idempotencyKey = "stale-add",
                    groupKey = "items",
                ),
            )
        }

        assertEquals(
            CommandPreconditionException.Kind.STALE,
            failure.kind,
        )
        assertEquals(1, fixture.request.responseRevision)
        verify(fixture.groupOccurrenceRepository, never()).save(any())
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `a nested occurrence roots its path at its parent and provisions its own Field Value Set`()
    {
        val fixture = Fixture()
        fixture.withNestedGroup()
        val parent = fixture.activeOccurrence(fixture.group, 0, "items[0]")
        fixture.expectNoSiblings(fixture.nestedGroup, parent.id)

        val result = fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-nested",
                groupKey = "entries",
                parentOccurrenceId = parent.id,
            ),
        )

        val occurrence = fixture.savedGroupOccurrences.single()
        assertEquals("items[0]/entries[0]", occurrence.occurrencePath)
        assertEquals(parent.id, occurrence.parentOccurrenceId)
        assertEquals(0, occurrence.occurrenceIndex)
        assertEquals(fixture.nestedGroup.id, occurrence.sourceTemplateGroupId)
        assertEquals(listOf(occurrence), result.occurrences)
        assertEquals("items[0]/entries[0]", fixture.savedRequirements.single().occurrencePath)
        verify(fixture.schemaAssignmentService).createOccurrenceValueSet(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, fixture.request.id),
            "items[0]/entries[0]",
        )
    }

    @Test
    fun `adding a parent occurrence materializes the nested group's authored minimum beneath it`()
    {
        val fixture = Fixture()
        fixture.withNestedGroup(minOccurrences = 2)
        fixture.expectNoSiblings(fixture.group, null)

        fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-parent",
                groupKey = "items",
            ),
        )

        val parent = fixture.savedGroupOccurrences.first()
        assertEquals("items[0]", parent.occurrencePath)
        val children = fixture.savedGroupOccurrences.drop(1)
        assertEquals(
            listOf("items[0]/entries[0]", "items[0]/entries[1]"),
            children.map { it.occurrencePath },
        )
        assertTrue(children.all { it.parentOccurrenceId == parent.id })
    }

    @Test
    fun `two nested occurrences under different parents keep independent paths and Field Value Sets`()
    {
        val fixture = Fixture()
        fixture.withNestedGroup()
        val firstParent = fixture.activeOccurrence(fixture.group, 0, "items[0]")
        val secondParent = fixture.activeOccurrence(fixture.group, 1, "items[1]")
        fixture.expectNoSiblings(fixture.nestedGroup, firstParent.id)
        fixture.expectNoSiblings(fixture.nestedGroup, secondParent.id)

        listOf(firstParent, secondParent).forEachIndexed { index, parent ->
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "add-nested-$index",
                    groupKey = "entries",
                    parentOccurrenceId = parent.id,
                ),
            )
        }

        assertEquals(
            listOf("items[0]/entries[0]", "items[1]/entries[0]"),
            fixture.savedGroupOccurrences.map { it.occurrencePath },
        )
        assertEquals(
            listOf(firstParent.id, secondParent.id),
            fixture.savedGroupOccurrences.map { it.parentOccurrenceId },
        )
        assertEquals(
            listOf("items[0]/entries[0]", "items[1]/entries[0]"),
            fixture.savedRequirements.map { it.occurrencePath },
        )
        listOf("items[0]/entries[0]", "items[1]/entries[0]").forEach { path ->
            verify(fixture.schemaAssignmentService).createOccurrenceValueSet(
                FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, fixture.request.id),
                path,
            )
        }
    }

    @Test
    fun `a nested occurrence parent is validated in both directions`()
    {
        val fixture = Fixture()
        fixture.withNestedGroup()
        val parent = fixture.activeOccurrence(fixture.group, 0, "items[0]")

        val rootNamingParent = assertThrows<InformationRequestLifecycleException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "root-with-parent",
                    groupKey = "items",
                    parentOccurrenceId = parent.id,
                ),
            )
        }

        val nestedWithoutParent = assertThrows<InformationRequestLifecycleException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "nested-without-parent",
                    groupKey = "entries",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID, rootNamingParent.reasonCode)
        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID, nestedWithoutParent.reasonCode)
        assertEquals(1, fixture.request.responseRevision)
        verify(fixture.groupOccurrenceRepository, never()).save(any())
    }

    @Test
    fun `adding an occurrence authorizes the group's materialized bindings before creating it`()
    {
        val fixture = Fixture()
        fixture.expectNoSiblings(fixture.group, null)

        fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-authorized-scope",
                groupKey = "items",
            ),
        )

        val order = inOrder(fixture.groupAuthorizationService, fixture.groupOccurrenceRepository)
        order.verify(fixture.groupAuthorizationService).authorizeGroupCreation(
            eq(fixture.access),
            eq(fixture.request),
            eq(listOf(fixture.binding)),
            eq(listOf(fixture.binding)),
        )
        order.verify(fixture.groupOccurrenceRepository).save(any())
    }

    @Test
    fun `adding a container group's occurrence authorizes every binding authored beneath it`() {
        val fixture = Fixture()
        fixture.withNestedGroup(minOccurrences = 0)
        fixture.binding.occurrenceAnchorKey = fixture.nestedGroup.groupKey
        fixture.expectNoSiblings(fixture.group, null)

        fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-container-scope",
                groupKey = "items",
            ),
        )

        val order = inOrder(fixture.groupAuthorizationService, fixture.groupOccurrenceRepository)
        order.verify(fixture.groupAuthorizationService).authorizeGroupCreation(
            eq(fixture.access),
            eq(fixture.request),
            eq(emptyList()),
            eq(listOf(fixture.binding, fixture.nestedBinding)),
        )
        order.verify(fixture.groupOccurrenceRepository).save(any())
    }

    @Test
    fun `an occurrence change is validated against the changed shape before it commits`() {
        val validator = object :
            com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidator {
            override val stages = setOf(InformationRequestStructuredResponseValidationStage.OCCURRENCE_CHANGE)

            override fun validate(context: InformationRequestStructuredResponseValidationContext) = listOf(
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.CROSS_ROW,
                    message = "The entries no longer cover every period",
                    occurrencePath = "items[0]",
                ),
            )
        }
        val fixture = Fixture(listOf(validator))
        fixture.expectNoSiblings(fixture.group, null)
        whenever(fixture.validationContextLoader.load(any(), any(), any())).thenAnswer {
            InformationRequestStructuredResponseValidationContext(
                stage = InformationRequestStructuredResponseValidationStage.OCCURRENCE_CHANGE,
                request = fixture.request,
                requirementsById = emptyMap(),
            )
        }

        val failure = assertThrows<InformationRequestItemRefusalException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                    idempotencyKey = "add-validated-occurrence",
                    groupKey = "items",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, failure.reasonCode)
        assertEquals("items[0]", failure.problems.single().occurrencePath)
        assertEquals(
            InformationRequestStructuredResponseValidationKind.CROSS_ROW,
            failure.problems.single().validationKind
        )
        val order = inOrder(fixture.groupOccurrenceRepository, fixture.validationContextLoader)
        order.verify(fixture.groupOccurrenceRepository).save(any())
        order.verify(fixture.validationContextLoader).load(
            eq(fixture.request),
            eq(InformationRequestStructuredResponseValidationStage.OCCURRENCE_CHANGE),
            any(),
        )
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `an occurrence change loads no validation state when no validator checks occurrence changes`() {
        val fixture = Fixture()
        fixture.expectNoSiblings(fixture.group, null)

        fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-without-validators",
                groupKey = "items",
            ),
        )

        verify(fixture.validationContextLoader, never()).load(any(), any(), any())
    }

    @Test
    fun `adding a parent occurrence authorizes the nested group's materialized minimum bindings too`()
    {
        val fixture = Fixture()
        fixture.withNestedGroup(minOccurrences = 2)
        fixture.expectNoSiblings(fixture.group, null)

        fixture.service.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "add-authorized-nested-scope",
                groupKey = "items",
            ),
        )

        verify(fixture.groupAuthorizationService).authorizeGroupCreation(
            eq(fixture.access),
            eq(fixture.request),
            eq(listOf(fixture.binding, fixture.nestedBinding)),
            eq(listOf(fixture.binding, fixture.nestedBinding)),
        )
    }

    @Test
    fun `add refuses when the group scope authorization denies the actor and creates nothing`()
    {
        val fixture = Fixture()
        fixture.expectNoSiblings(fixture.group, null)
        doThrow(ForbiddenException("Access denied to change Information Request group occurrences"))
            .whenever(fixture.groupAuthorizationService)
            .authorizeGroupCreation(any(), any(), any(), any())

        assertThrows<ForbiddenException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "add-denied-scope",
                    groupKey = "items",
                ),
            )
        }

        assertEquals(1, fixture.request.responseRevision)
        verify(fixture.groupOccurrenceRepository, never()).save(any())
        verify(fixture.requestRepository, never()).update(any())
        verify(fixture.transitionHistory, never()).record(any())
    }

    @Test
    fun `removing an occurrence authorizes its own and its descendants' occurrence paths before mutating`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        fixture.withNestedGroup()
        val nested = fixture.occurrence(fixture.nestedGroup, 0, "items[1]/entries[0]", parentOccurrenceId = second.id)
        whenever(fixture.groupOccurrenceRepository.findActiveByIdForUpdate(fixture.request.id, second.id))
            .thenReturn(second)
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id))
            .thenReturn(listOf(first, second, nested))

        fixture.service.remove(
            RemoveInformationRequestGroupOccurrenceCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "remove-authorized-scope",
                occurrenceId = second.id,
            ),
        )

        val order = inOrder(fixture.groupAuthorizationService, fixture.groupOccurrenceRepository)
        order.verify(fixture.groupAuthorizationService).authorizeOccurrenceChange(
            eq(fixture.access),
            eq(fixture.request),
            eq(setOf("items[1]", "items[1]/entries[0]")),
            eq(listOf(fixture.binding)),
        )
        order.verify(fixture.groupOccurrenceRepository).update(second)
    }

    @Test
    fun `remove refuses when the occurrence scope authorization denies the actor and marks nothing removed`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(fixture.groupOccurrenceRepository.findActiveByIdForUpdate(fixture.request.id, second.id))
            .thenReturn(second)
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id))
            .thenReturn(listOf(first, second))
        doThrow(ForbiddenException("Access denied to change this Information Request group occurrence"))
            .whenever(fixture.groupAuthorizationService)
            .authorizeOccurrenceChange(any(), any(), any(), any())

        assertThrows<ForbiddenException> {
            fixture.service.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "remove-denied-scope",
                    occurrenceId = second.id,
                ),
            )
        }

        assertNull(second.removedAt)
        assertEquals(1, fixture.request.responseRevision)
        verify(fixture.groupOccurrenceRepository, never()).update(any())
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `reordering authorizes every active sibling occurrence path before applying the new order`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))

        fixture.service.reorder(
            ReorderInformationRequestGroupOccurrencesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "reorder-authorized-scope",
                groupKey = "items",
                orderedOccurrenceIds = listOf(second.id, first.id),
            ),
        )

        val order = inOrder(fixture.groupAuthorizationService, fixture.groupOccurrenceRepository)
        order.verify(fixture.groupAuthorizationService).authorizeOccurrenceChange(
            eq(fixture.access),
            eq(fixture.request),
            eq(setOf("items[0]", "items[1]")),
            eq(listOf(fixture.binding)),
        )
        order.verify(fixture.groupOccurrenceRepository).update(second)
    }

    @Test
    fun `reordering also authorizes the active descendants of every sibling`() {
        val fixture = Fixture()
        fixture.withNestedGroup()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        val nested = fixture.occurrence(fixture.nestedGroup, 0, "items[1]/entries[0]", parentOccurrenceId = second.id)
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))
        whenever(fixture.groupOccurrenceRepository.findActiveForRequestForUpdate(fixture.request.id))
            .thenReturn(listOf(first, second, nested))

        fixture.service.reorder(
            ReorderInformationRequestGroupOccurrencesCommand(
                requestId = fixture.request.id,
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(fixture.request)),
                idempotencyKey = "reorder-with-descendants",
                groupKey = "items",
                orderedOccurrenceIds = listOf(second.id, first.id),
            ),
        )

        verify(fixture.groupAuthorizationService).authorizeOccurrenceChange(
            eq(fixture.access),
            eq(fixture.request),
            eq(setOf("items[0]", "items[1]", "items[1]/entries[0]")),
            eq(listOf(fixture.binding)),
        )
    }

    @Test
    fun `reorder refuses when the group scope authorization denies the actor and changes no order`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(
            fixture.groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                fixture.request.id,
                fixture.group.id,
                null,
            ),
        ).thenReturn(listOf(first, second))
        doThrow(ForbiddenException("Access denied to change this Information Request group occurrence"))
            .whenever(fixture.groupAuthorizationService)
            .authorizeOccurrenceChange(any(), any(), any(), any())

        assertThrows<ForbiddenException> {
            fixture.service.reorder(
                ReorderInformationRequestGroupOccurrencesCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(fixture.request),
                    ),
                    idempotencyKey = "reorder-denied-scope",
                    groupKey = "items",
                    orderedOccurrenceIds = listOf(second.id, first.id),
                ),
            )
        }

        assertEquals(0, first.occurrenceIndex)
        assertEquals(1, second.occurrenceIndex)
        verify(fixture.groupOccurrenceRepository, never()).update(any())
    }

    private class Fixture(
        validators: List<com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidator> = emptyList(),
    )
    {
        val organizationId: UUID = UUID.randomUUID()
        val participantId: UUID = UUID.randomUUID()
        val templateDefinitionId: UUID = UUID.randomUUID()
        val access = RequestAccessContext(
            principal = PrincipalRef.participant(participantId),
            authorization = AuthorizationContext(activeOrgId = organizationId, sessionRef = "verified-session"),
        )
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = organizationId
            state = InformationRequestState.ISSUED
            responseRevision = 1
        }
        val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = organizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val group = InformationRequestTemplateRequirementGroup().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            groupKey = "items"
            minOccurrences = 1
            maxOccurrences = 2
        }
        val requirement = InformationRequestTemplateRequirement().apply {
            id = UUID.randomUUID()
            templateDefinitionId = this@Fixture.templateDefinitionId
            requirementKey = "recorded-item"
            requirementType = InformationRequestRequirementType.FIELD
        }
        val binding = InformationRequestTemplateRequirementBinding().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            this.templateDefinitionId = this@Fixture.templateDefinitionId
            templateRequirementId = requirement.id
            templateSectionId = UUID.randomUUID()
            displayOrder = 1
            prompt = "Provide item data"
            occurrenceAnchorKey = group.groupKey
            collectedFieldDefinitionId = UUID.randomUUID()
        }
        val savedGroupOccurrences = mutableListOf<InformationRequestGroupOccurrence>()
        val savedRequirements = mutableListOf<InformationRequestRequirement>()
        val savedRevisions = mutableListOf<InformationRequestRequirementRevision>()
        val savedCurrents = mutableListOf<InformationRequestRequirementCurrent>()

        val requestRepository = mock<InformationRequestRepository>()
        val exchangeLookup = mock<ExchangeLookupService>()
        val groupRepository = mock<InformationRequestTemplateRequirementGroupRepository>()
        val groupOccurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val templateBindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val templateRequirementRepository = mock<InformationRequestTemplateRequirementRepository>()
        val dispositionRepository = mock<InformationRequestTemplateBindingDispositionRepository>()
        val evidencePolicyRepository = mock<InformationRequestTemplateEvidencePolicyRepository>()
        val acceptedValueRepository = mock<InformationRequestTemplateEvidenceAcceptedValueRepository>()
        val substituteRepository = mock<InformationRequestTemplateBindingSubstituteRepository>()
        val evidenceLinkRepository = mock<InformationRequestTemplateBindingEvidenceLinkRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        val currentRepository = mock<InformationRequestRequirementCurrentRepository>()
        val fieldsReader = mock<FieldsReadFacade>()
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val authorizationService = mock<AuthorizationService>()
        val entitlementGuard = mock<InformationRequestEntitlementGuard>()
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        val groupAuthorizationService = mock<InformationRequestGroupAuthorizationService>()
        val supportingEvidenceLinkService = mock<InformationRequestSupportingEvidenceLinkService>()
        val lockService = mock<InformationRequestSubmissionLockService>()
        val validationContextLoader =
            mock<com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationContextLoader>()
        val receiptStore = InMemoryGroupOccurrenceReceiptStore()
        val grant = issuedExecutionGrant().apply { requestId = this@Fixture.request.id }
        val service = InformationRequestGroupOccurrenceService(
            requestRepository = requestRepository,
            groupOccurrenceRepository = groupOccurrenceRepository,
            requirementRepository = requirementRepository,
            revisionRepository = revisionRepository,
            materializer = InformationRequestGroupOccurrenceMaterializer(
                groupRepository = groupRepository,
                groupOccurrenceRepository = groupOccurrenceRepository,
                templateVersionRepository = mock {
                    on { findById(request.templateVersionId) }.thenReturn(
                        InformationRequestTemplateVersion().apply {
                            id = request.templateVersionId
                            this.templateDefinitionId = this@Fixture.templateDefinitionId
                        },
                    )
                },
                templateBindingRepository = templateBindingRepository,
                templateRequirementRepository = templateRequirementRepository,
                dispositionRepository = dispositionRepository,
                evidencePolicyRepository = evidencePolicyRepository,
                acceptedValueRepository = acceptedValueRepository,
                substituteRepository = substituteRepository,
                evidenceLinkRepository = evidenceLinkRepository,
                requirementRepository = requirementRepository,
                revisionRepository = revisionRepository,
                currentRepository = currentRepository,
                fieldsReader = fieldsReader,
                schemaAssignmentService = schemaAssignmentService,
                supportingEvidenceLinkService = supportingEvidenceLinkService,
            ),
            structure = InformationRequestGroupOccurrenceStructure(groupRepository, groupOccurrenceRepository),
            mutationGuard = InformationRequestResponseMutationGuard(
                requestRepository,
                exchangeLookup,
                InformationRequestContinuationGuard(executionGrantService, entitlementGuard),
            ),
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope()),
            transitionHistory = transitionHistory,
            groupAuthorizationService = groupAuthorizationService,
            lockService = lockService,
            structuredResponseValidationService =
                com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationService(
                    validators
                ),
            validationContextLoader = validationContextLoader,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )

        init
        {
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.update(request)).thenReturn(request)
            whenever(exchangeLookup.lockExchangeForUpdate(request.exchangeId)).thenReturn(exchange)
            whenever(groupRepository.findForVersion(request.templateVersionId)).thenReturn(listOf(group))
            whenever(templateBindingRepository.findOrdered(request.templateVersionId)).thenReturn(listOf(binding))
            whenever(templateRequirementRepository.findAllByDefinition(templateDefinitionId)).thenReturn(
                listOf(
                    requirement
                )
            )
            whenever(dispositionRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
            whenever(evidencePolicyRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
            whenever(acceptedValueRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
            whenever(substituteRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
            whenever(evidenceLinkRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
            whenever(groupOccurrenceRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestGroupOccurrence>(0).also { occurrence ->
                    savedGroupOccurrences += occurrence
                }
            }
            whenever(requirementRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestRequirement>(0).also { runtimeRequirement ->
                    savedRequirements += runtimeRequirement
                }
            }
            whenever(revisionRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestRequirementRevision>(0).also { revision ->
                    savedRevisions += revision
                }
            }
            whenever(currentRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestRequirementCurrent>(0)
                    .also { current -> savedCurrents += current }
            }
            whenever(fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id)))
                .thenReturn(SchemaAssignment().apply {
                    id = UUID.randomUUID()
                    resourceType = ResourceType.INFORMATION_REQUEST.name
                    resourceId = request.id
                    schemaVersionId = UUID.randomUUID()
                })
            whenever(executionGrantService.findForRequest(request.id)).thenReturn(grant)
            whenever(groupAuthorizationService.visibleOccurrences(any(), any(), any())).thenAnswer {
                it.getArgument<List<InformationRequestGroupOccurrence>>(2)
            }
            whenever(authorizationService.authorize(any(), any(), any<ResourceRef>(), any()))
                .thenReturn(Decision.Allow())
        }

        fun occurrence(
            group: InformationRequestTemplateRequirementGroup,
            index: Int,
            path: String,
            parentOccurrenceId: UUID? = null,
        ) = InformationRequestGroupOccurrence().apply {
            informationRequestId = request.id
            sourceTemplateGroupId = group.id
            this.parentOccurrenceId = parentOccurrenceId
            occurrenceIndex = index
            occurrencePath = path
        }.also { whenever(groupOccurrenceRepository.findById(it.id)).thenReturn(it) }

        val nestedGroup = InformationRequestTemplateRequirementGroup().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            groupKey = "entries"
            parentGroupId = group.id
            minOccurrences = 0
        }
        private val nestedRequirement = InformationRequestTemplateRequirement().apply {
            id = UUID.randomUUID()
            templateDefinitionId = this@Fixture.templateDefinitionId
            requirementKey = "recorded-entry"
            requirementType = InformationRequestRequirementType.FIELD
        }
        val nestedBinding = InformationRequestTemplateRequirementBinding().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            this.templateDefinitionId = this@Fixture.templateDefinitionId
            templateRequirementId = nestedRequirement.id
            templateSectionId = UUID.randomUUID()
            displayOrder = 2
            prompt = "Provide entry data"
            occurrenceAnchorKey = nestedGroup.groupKey
            collectedFieldDefinitionId = UUID.randomUUID()
        }

        fun withNestedGroup(minOccurrences: Int = 0)
        {
            nestedGroup.minOccurrences = minOccurrences
            whenever(groupRepository.findForVersion(request.templateVersionId))
                .thenReturn(listOf(group, nestedGroup))
            whenever(templateBindingRepository.findOrdered(request.templateVersionId))
                .thenReturn(listOf(binding, nestedBinding))
            whenever(templateRequirementRepository.findAllByDefinition(templateDefinitionId))
                .thenReturn(listOf(requirement, nestedRequirement))
        }

        var reorderedSiblings: Pair<InformationRequestGroupOccurrence, InformationRequestGroupOccurrence> =
            occurrence(group, 0, "items[0]") to occurrence(group, 1, "items[1]")

        fun reorderCommand(key: String): ReorderInformationRequestGroupOccurrencesCommand {
            val (first, second) = reorderedSiblings
            whenever(groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(request.id, group.id, null))
                .thenReturn(listOf(first, second))
            return ReorderInformationRequestGroupOccurrencesCommand(
                requestId = request.id,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.responsesOf(request)),
                idempotencyKey = key,
                groupKey = "items",
                orderedOccurrenceIds = listOf(second.id, first.id),
            )
        }

        fun activeOccurrence(
            group: InformationRequestTemplateRequirementGroup,
            index: Int,
            path: String,
            parentOccurrenceId: UUID? = null,
        ): InformationRequestGroupOccurrence
        {
            val occurrence = occurrence(group, index, path, parentOccurrenceId)
            whenever(groupOccurrenceRepository.findActiveByIdForUpdate(request.id, occurrence.id))
                .thenReturn(occurrence)
            return occurrence
        }

        fun expectNoSiblings(group: InformationRequestTemplateRequirementGroup, parentOccurrenceId: UUID?)
        {
            whenever(groupOccurrenceRepository.findForGroupAndParentForUpdate(request.id, group.id, parentOccurrenceId))
                .thenReturn(emptyList())
            whenever(
                groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                    request.id,
                    group.id,
                    parentOccurrenceId,
                ),
            ).thenReturn(emptyList())
        }
    }
}

private class InMemoryGroupOccurrenceReceiptStore : CommandReceiptStore
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

    override fun insert(
        receipt: CommandReceipt,
    ): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
