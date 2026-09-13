package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

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

        val locks = inOrder(fixture.exchangeRepository, fixture.requestRepository, fixture.groupOccurrenceRepository)
        locks.verify(fixture.exchangeRepository).findByIdForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
        locks.verify(fixture.groupOccurrenceRepository).findForGroupAndParentForUpdate(
            fixture.request.id,
            fixture.group.id,
            null,
        )
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
        verify(fixture.transitionHistory).record(any())
    }

    @Test
    fun `adding refuses to exceed the authored maximum without changing the response revision`()
    {
        val fixture = Fixture()
        val first = fixture.occurrence(fixture.group, 0, "items[0]")
        val second = fixture.occurrence(fixture.group, 1, "items[1]")
        whenever(fixture.groupOccurrenceRepository.findForGroupAndParentForUpdate(fixture.request.id, fixture.group.id, null))
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
        verify(fixture.transitionHistory).record(any())
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

        val failure = assertThrows<com.docuhyphen.app.api.service.command.CommandPreconditionException> {
            fixture.service.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = fixture.request.id,
                    access = fixture.access,
                    precondition = CommandPrecondition.ExpectedRevision("\"${fixture.request.id}:0\""),
                    idempotencyKey = "stale-add",
                    groupKey = "items",
                ),
            )
        }

        assertEquals(
            com.docuhyphen.app.api.service.command.CommandPreconditionException.Kind.STALE,
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
        order.verify(fixture.groupAuthorizationService).authorizeMaterializedBindings(
            eq(fixture.access),
            eq(fixture.request),
            eq(listOf(fixture.binding)),
        )
        order.verify(fixture.groupOccurrenceRepository).save(any())
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

        verify(fixture.groupAuthorizationService).authorizeMaterializedBindings(
            eq(fixture.access),
            eq(fixture.request),
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
            .authorizeMaterializedBindings(any(), any(), any())

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
        order.verify(fixture.groupAuthorizationService).authorizeOccurrenceScope(
            eq(fixture.access),
            eq(fixture.request.id),
            eq(setOf("items[1]", "items[1]/entries[0]")),
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
            .authorizeOccurrenceScope(any(), any(), any())

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
        order.verify(fixture.groupAuthorizationService).authorizeOccurrenceScope(
            eq(fixture.access),
            eq(fixture.request.id),
            eq(setOf("items[0]", "items[1]")),
        )
        order.verify(fixture.groupOccurrenceRepository).update(second)
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
            .authorizeOccurrenceScope(any(), any(), any())

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

    private class Fixture
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
        val savedCurrents = mutableListOf<com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent>()

        val requestRepository = mock<InformationRequestRepository>()
        val exchangeRepository = mock<ExchangeRepository>()
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
        val schemaAssignmentRepository = mock<SchemaAssignmentRepository>()
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val authorizationService = mock<AuthorizationService>()
        val entitlementGuard = mock<InformationRequestEntitlementGuard>()
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        val groupAuthorizationService = mock<InformationRequestGroupAuthorizationService>()
        val receiptStore = InMemoryGroupOccurrenceReceiptStore()
        val service = InformationRequestGroupOccurrenceService(
            requestRepository = requestRepository,
            exchangeRepository = exchangeRepository,
            groupRepository = groupRepository,
            groupOccurrenceRepository = groupOccurrenceRepository,
            templateVersionRepository = mock {
                on { findById(request.templateVersionId) }.thenReturn(
                    com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion().apply {
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
            schemaAssignmentRepository = schemaAssignmentRepository,
            schemaAssignmentService = schemaAssignmentService,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(receiptStore),
            entitlementGuard = entitlementGuard,
            executionGrantService = executionGrantService,
            transitionHistory = transitionHistory,
            groupAuthorizationService = groupAuthorizationService,
        )

        init
        {
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.update(request)).thenReturn(request)
            whenever(exchangeRepository.findByIdForUpdate(request.exchangeId)).thenReturn(exchange)
            whenever(groupRepository.findForVersion(request.templateVersionId)).thenReturn(listOf(group))
            whenever(templateBindingRepository.findOrdered(request.templateVersionId)).thenReturn(listOf(binding))
            whenever(templateRequirementRepository.findAllByDefinition(templateDefinitionId)).thenReturn(listOf(requirement))
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
                it.getArgument<com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent>(0)
                    .also { current -> savedCurrents += current }
            }
            whenever(schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, request.id))
                .thenReturn(SchemaAssignment().apply {
                    id = UUID.randomUUID()
                    resourceType = ResourceType.INFORMATION_REQUEST.name
                    resourceId = request.id
                    schemaVersionId = UUID.randomUUID()
                })
            whenever(executionGrantService.findForRequest(request.id)).thenReturn(
                RequestExecutionGrant().apply { requestId = this@Fixture.request.id },
            )
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
        }

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
    val receipts = mutableListOf<com.docuhyphen.app.api.model.entity.CommandReceipt>()

    override fun findForCommand(request: CommandReceiptRequest): com.docuhyphen.app.api.model.entity.CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                it.resourceId == request.resource.id &&
                it.operationName == request.operation &&
                it.actorKind == request.actor.kind &&
                it.actorId == request.actor.id &&
                it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(
        receipt: com.docuhyphen.app.api.model.entity.CommandReceipt,
    ): com.docuhyphen.app.api.model.entity.CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
