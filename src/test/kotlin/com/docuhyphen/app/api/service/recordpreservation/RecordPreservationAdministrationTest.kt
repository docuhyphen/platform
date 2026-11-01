package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.entity.RecordRetentionSchedule
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class RecordPreservationAdministrationTest {
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = mock<RecordOwnerScopeAccess>()
    private val holds = mock<RecordPreservationHoldService>()
    private val schedules = mock<RecordRetentionScheduleService>()
    private val administration = RecordPreservationAdministration(
        access,
        holds,
        schedules,
        mock(),
        mock(),
        CommandReceiptService(InMemoryPreservationReceiptStore(), CommandReceiptScope()),
        mock(),
    )

    @Test
    fun `placing a hold twice under one idempotency key places it once and replays the placed hold`() {
        val owner = ownedBy(RecordOwnerRef.organization(UUID.randomUUID()))
        val hold = hold(owner)
        whenever(holds.place(any())).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        whenever(holds.hold(hold.id, owner)).thenReturn(RecordPreservationHoldView(hold, emptyList()))

        val placed = administration.placeHold(
            "EXCHANGE",
            "record-1",
            RecordPreservationScope.RESOURCE,
            "records review",
            null,
            null,
            "place-1"
        )
        val replayed = administration.placeHold(
            "EXCHANGE",
            "record-1",
            RecordPreservationScope.RESOURCE,
            "records review",
            null,
            null,
            "place-1"
        )

        assertEquals(placed.hold.id, replayed.hold.id)
        verify(holds, times(1)).place(any())
        assertThrows(CommandReceiptConflictException::class.java) {
            administration.placeHold(
                "EXCHANGE",
                "record-1",
                RecordPreservationScope.RESOURCE,
                "another reason",
                null,
                null,
                "place-1"
            )
        }
        verify(holds, times(1)).place(any())
    }

    @Test
    fun `a platform owned hold has a stable receipt scope although the platform owner has no identifier`() {
        val owner = ownedBy(RecordOwnerRef.PLATFORM)
        val hold = hold(owner)
        whenever(holds.place(any())).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        whenever(holds.hold(hold.id, owner)).thenReturn(RecordPreservationHoldView(hold, emptyList()))

        administration.placeHold(
            "EXCHANGE",
            "record-2",
            RecordPreservationScope.RESOURCE,
            "records review",
            null,
            null,
            "platform-1"
        )
        val replayed = administration.placeHold(
            "EXCHANGE",
            "record-2",
            RecordPreservationScope.RESOURCE,
            "records review",
            null,
            null,
            "platform-1"
        )

        assertEquals(hold.id, replayed.hold.id)
        verify(holds, times(1)).place(any())
    }

    @Test
    fun `a scope change and a release replay by key instead of changing the hold again`() {
        val owner = ownedBy(RecordOwnerRef.user(principal.id))
        val hold = hold(owner)
        whenever(holds.lockOwned(hold.id, owner)).thenReturn(hold)
        whenever(holds.changeScope(any())).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        whenever(holds.release(any())).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        whenever(holds.hold(hold.id, owner)).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        val current = CommandPrecondition.ExpectedRevision(RecordPreservationETag.holdOf(hold))

        repeat(2) {
            administration.changeHoldScope(
                hold.id,
                RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                "widened",
                current,
                "scope-1"
            )
        }
        repeat(2) { administration.releaseHold(hold.id, "review closed", current, "release-1") }

        verify(holds, times(1)).changeScope(any())
        verify(holds, times(1)).release(any())
        assertThrows(CommandReceiptConflictException::class.java) {
            administration.releaseHold(hold.id, "another reason", current, "release-1")
        }
    }

    @Test
    fun `a scope change and a release are refused unless they name the current version of the hold`() {
        val owner = ownedBy(RecordOwnerRef.user(principal.id))
        val hold = hold(owner).apply { holdRevision = 3 }
        whenever(holds.lockOwned(hold.id, owner)).thenReturn(hold)
        whenever(holds.changeScope(any())).thenReturn(RecordPreservationHoldView(hold, emptyList()))
        val stale = CommandPrecondition.ExpectedRevision(RevisionETag.of(hold.id, 2))

        val refusals = listOf(
            assertThrows(CommandPreconditionException::class.java) {
                administration.changeHoldScope(
                    hold.id,
                    RecordPreservationScope.RESOURCE,
                    "narrowed",
                    stale,
                    "scope-stale"
                )
            },
            assertThrows(CommandPreconditionException::class.java) {
                administration.releaseHold(hold.id, "review closed", CommandPrecondition.Absent, "release-unstated")
            },
        )

        assertEquals(
            listOf(CommandPreconditionException.Kind.STALE, CommandPreconditionException.Kind.REQUIRED),
            refusals.map { it.kind },
        )
        assertEquals(RecordPreservationETag.holdOf(hold), refusals.first().currentETag)
        verify(holds, never()).changeScope(any())
        verify(holds, never()).release(any())

        administration.changeHoldScope(
            hold.id,
            RecordPreservationScope.RESOURCE,
            "narrowed",
            CommandPrecondition.ExpectedRevision(RecordPreservationETag.holdOf(hold)),
            "scope-current",
        )
        verify(holds).changeScope(any())
    }

    @Test
    fun `a retention schedule is published only over the version its caller read`() {
        val owner = ownedBy(RecordOwnerRef.organization(UUID.randomUUID()))
        val schedule = schedule(owner, version = 1)
        val read = RecordPreservationETag.scheduleOf(owner, "INFORMATION_REQUEST", 1)
        whenever(schedules.schedule(eq(owner), any())).thenReturn(
            RecordRetentionScheduleView(
                schedule,
                listOf(schedule),
                read
            )
        )
        whenever(schedules.publish(any())).thenReturn(RecordRetentionScheduleView(schedule, listOf(schedule), read))

        val stale = assertThrows(CommandPreconditionException::class.java) {
            administration.publishSchedule(
                "INFORMATION_REQUEST",
                30,
                90,
                CommandPrecondition.ExpectedRevision(
                    RecordPreservationETag.scheduleOf(
                        owner,
                        "INFORMATION_REQUEST",
                        0
                    )
                ),
                "publish-stale",
            )
        }

        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        assertEquals(read, stale.currentETag)
        verify(schedules, never()).publish(any())
        administration.publishSchedule(
            "INFORMATION_REQUEST",
            30,
            90,
            CommandPrecondition.ExpectedRevision(read),
            "publish-current"
        )
        verify(schedules).publish(any())
        verify(schedules, atLeastOnce()).lockSeries(owner, "INFORMATION_REQUEST")
    }

    @Test
    fun `publishing a retention schedule twice under one key publishes one version`() {
        val owner = ownedBy(RecordOwnerRef.organization(UUID.randomUUID()))
        val schedule = schedule(owner, version = 1)
        val unpublished = RecordPreservationETag.scheduleOf(owner, "INFORMATION_REQUEST", 0)
        val published = RecordPreservationETag.scheduleOf(owner, "INFORMATION_REQUEST", 1)
        whenever(schedules.publish(any())).thenReturn(
            RecordRetentionScheduleView(
                schedule,
                listOf(schedule),
                published
            )
        )
        whenever(schedules.schedule(eq(owner), any()))
            .thenReturn(RecordRetentionScheduleView(null, emptyList(), unpublished))
        val read = CommandPrecondition.ExpectedRevision(unpublished)

        val first = administration.publishSchedule("INFORMATION_REQUEST", 30, 90, read, "publish-1")
        whenever(schedules.schedule(eq(owner), any()))
            .thenReturn(RecordRetentionScheduleView(schedule, listOf(schedule), published))
        val replayed = administration.publishSchedule("INFORMATION_REQUEST", 30, 90, read, "publish-1")

        assertEquals(first.current?.id, replayed.current?.id)
        verify(schedules, times(1)).publish(any())
        assertThrows(CommandReceiptConflictException::class.java) {
            administration.publishSchedule("INFORMATION_REQUEST", 60, 90, read, "publish-1")
        }
    }

    private fun ownedBy(owner: RecordOwnerRef): RecordOwnerRef {
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(eq(owner), any())).thenReturn(principal)
        return owner
    }

    private fun schedule(owner: RecordOwnerRef, version: Int) = RecordRetentionSchedule().apply {
        ownerKind = owner.kind
        ownerId = requireNotNull(owner.id)
        resourceType = "INFORMATION_REQUEST"
        versionNumber = version
        minimumRetentionDays = 30
        disposalAfterDays = 90
        recordedByPrincipalId = principal.id
    }

    private fun hold(owner: RecordOwnerRef) = RecordPreservationHold().apply {
        ownerKind = owner.kind
        ownerId = owner.id
        resourceType = "EXCHANGE"
        resourceId = "record-1"
        reason = "records review"
        placedByPrincipalId = principal.id
    }
}

private class InMemoryPreservationReceiptStore : CommandReceiptStore {
    private val receipts = mutableListOf<CommandReceipt>()

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

    override fun insert(receipt: CommandReceipt): CommandReceipt {
        receipts += receipt
        return receipt
    }
}
