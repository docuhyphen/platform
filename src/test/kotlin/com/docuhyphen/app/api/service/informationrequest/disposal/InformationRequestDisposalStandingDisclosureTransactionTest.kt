package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestDisposalStandingDto
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.disposal.model.HeldDisposalFixture
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestDisposalStandingDisclosureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var schedules: RecordRetentionScheduleService

    @Inject
    lateinit var holds: RecordPreservationHoldService

    @Inject
    lateinit var standings: InformationRequestDisposalQueryService

    private val json = Json { encodeDefaults = true }

    @Test
    fun `an operations viewer who cannot read holds sees each hold's coverage without its reason, case, or attribution`() {
        val fixture = heldFixture()

        val standing = standingOf(fixture)

        assertEquals(0, standing.getValue("holds").jsonArray.size)
        val withheld = standing.getValue("withheldHolds").jsonArray.single().jsonObject
        assertEquals("EXCHANGE", withheld.getValue("resourceType").jsonPrimitive.content)
        assertEquals("DESCENDANTS_AND_REFERENCES", withheld.getValue("scope").jsonPrimitive.content)
        assertEquals("ACTIVE", withheld.getValue("status").jsonPrimitive.content)
        assertEquals(setOf("resourceType", "scope", "status", "effectiveFrom"), withheld.keys)
        assertFalse(standing.toString().contains(CASE_REFERENCE))
        assertFalse(standing.toString().contains(REASON))
    }

    @Test
    fun `a caller who may read the owner's holds sees each hold in full`() {
        val fixture = heldFixture()
        grantAuditRead(fixture)

        val standing = standingOf(fixture)

        val hold = standing.getValue("holds").jsonArray.single().jsonObject
        assertEquals(CASE_REFERENCE, hold.getValue("caseReference").jsonPrimitive.content)
        assertEquals(REASON, hold.getValue("reason").jsonPrimitive.content)
        assertEquals(0, standing["withheldHolds"]?.jsonArray?.size ?: 0)
    }

    @Test
    fun `a hold shows on the Records tab of a finished request whose owner has no retention schedule`() {
        val fixture = heldFixture(retention = null)
        grantAuditRead(fixture)

        val standing = standingOf(fixture)

        assertEquals(CASE_REFERENCE, heldCase(standing))
    }

    @Test
    fun `a hold shows on the Records tab of a request that is still open`() {
        val fixture = heldFixture(retention = null, finished = false)
        grantAuditRead(fixture)

        val standing = standingOf(fixture)

        assertEquals(CASE_REFERENCE, heldCase(standing))
    }

    @Test
    fun `a hold shows on the Records tab while the request is still inside its minimum retention or disposal age`() {
        val minimum = heldFixture(retention = 365 to 400)
        grantAuditRead(minimum)
        val disposalAge = heldFixture(retention = 0 to 400)
        grantAuditRead(disposalAge)

        assertEquals(CASE_REFERENCE, heldCase(standingOf(minimum)))
        assertEquals(CASE_REFERENCE, heldCase(standingOf(disposalAge)))
    }

    @Test
    fun `an operations viewer who cannot read holds still sees that a hold covers a request without a retention schedule`() {
        val fixture = heldFixture(retention = null)

        val standing = standingOf(fixture)

        assertEquals(0, standing.getValue("holds").jsonArray.size)
        assertEquals(
            "ACTIVE",
            standing.getValue("withheldHolds").jsonArray.single().jsonObject.getValue("status").jsonPrimitive.content
        )
    }

    private fun heldCase(standing: JsonObject): String =
        standing.getValue("holds").jsonArray.single().jsonObject.getValue("caseReference").jsonPrimitive.content

    private fun heldFixture(retention: Pair<Int, Int>? = 0 to 0, finished: Boolean = true): HeldDisposalFixture {
        val runtime = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { runtime ->
                if (finished) execute(
                    connection,
                    "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() - make_interval(days => 10) WHERE id = ?",
                    runtime.requestId,
                )
                execute(
                    connection,
                    """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    runtime.exchangeId,
                    runtime.template.userId,
                )
            }
        }
        val owner = RecordOwnerRef.organization(runtime.template.organizationId)
        val principal = PrincipalRef.user(runtime.template.userId)
        retention?.let { (minimumDays, disposalDays) ->
            QuarkusTransaction.requiringNew().run {
                schedules.publish(
                    PublishRecordRetentionScheduleCommand(
                        owner,
                        "INFORMATION_REQUEST",
                        minimumDays,
                        disposalDays,
                        principal
                    )
                )
            }
        }
        QuarkusTransaction.requiringNew().run {
            holds.place(
                PlaceRecordPreservationHoldCommand(
                    owner = owner,
                    resourceType = "EXCHANGE",
                    resourceId = runtime.exchangeId.toString(),
                    scope = RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    reason = REASON,
                    caseReference = CASE_REFERENCE,
                    effectiveFrom = null,
                    principal = principal,
                ),
            )
        }
        return HeldDisposalFixture(runtime)
    }

    private fun grantAuditRead(fixture: HeldDisposalFixture) {
        dataSource.connection.use { connection ->
            val membershipId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO organization_membership (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
                VALUES (?, ?, ?, 'ACTIVE', TRUE, now(), now())
                """.trimIndent(),
                membershipId,
                fixture.runtime.template.userId,
                fixture.runtime.template.organizationId,
            )
            execute(
                connection,
                "INSERT INTO organization_membership_role (organization_membership_id, role_name) VALUES (?, 'ORG_AUDITOR')",
                membershipId,
            )
        }
    }

    private fun standingOf(fixture: HeldDisposalFixture): JsonObject =
        QuarkusTransaction.requiringNew().call {
            val standing = standings.standing(
                fixture.runtime.requestId,
                RequestAccessContext(
                    PrincipalRef.user(fixture.runtime.template.userId),
                    AuthorizationContext(
                        sessionRef = "records-session",
                        mfaSatisfied = true,
                        activeOrgId = fixture.runtime.template.organizationId,
                    ),
                ),
            )
            json.encodeToJsonElement(
                InformationRequestDisposalStandingDto.serializer(),
                RecordPreservationDtoMapper.toDto(standing)
            ).jsonObject
        }

    private companion object {
        const val REASON = "Preserve the collection for a pending matter"
        const val CASE_REFERENCE = "matter-reference-1"
    }
}
