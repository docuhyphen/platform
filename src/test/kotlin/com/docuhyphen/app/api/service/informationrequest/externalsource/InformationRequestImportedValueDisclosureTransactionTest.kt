package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestImportedValueDisclosureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `external values refuse a Requirement whose current occurrence revision has closed`() {
        val scenario = scenario(protectedAnswer = false)
        dataSource.connection.use { connection ->
            execute(
                connection, """
                UPDATE information_request_requirement_revision SET effective_to = effective_from + interval '1 second'
                WHERE id = (SELECT current_revision_id FROM information_request_requirement_current WHERE information_request_requirement_id = ?)
            """.trimIndent(), scenario.requirementId
            )
        }
        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            propose(scenario, scenario.owner(true), "closed-occurrence-value")
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["ENDED", "REJECTED", "RESCINDED", "DELETED"])
    fun `external source records retain authorized owner reads after the parent stops`(state: String) {
        val scenario = scenario(protectedAnswer = false)
        propose(scenario, scenario.owner(true), "before-parent-stops")
        assertEquals(
            1,
            QuarkusTransaction.requiringNew()
                .call { scenario.services.importedValueRecords.values(scenario.requestId, scenario.owner(true)).size })
        dataSource.connection.use { connection ->
            if (state == "DELETED") execute(
                connection,
                "UPDATE exchange SET is_deleted = TRUE WHERE id = ?",
                scenario.request.runtime.exchangeId
            )
            else execute(
                connection,
                "UPDATE exchange SET status = ? WHERE id = ?",
                state,
                scenario.request.runtime.exchangeId
            )
        }
        assertAll(
            {
                assertEquals(
                    1,
                    QuarkusTransaction.requiringNew().call {
                        scenario.services.importedValueRecords.values(
                            scenario.requestId,
                            scenario.owner(true)
                        ).size
                    })
            },
            {
                assertTrue(
                    QuarkusTransaction.requiringNew().call {
                        scenario.services.connectorExchanges.exchanges(scenario.requestId, scenario.owner(true))
                            .isEmpty()
                    })
            },
            {
                assertTrue(
                    QuarkusTransaction.requiringNew().call {
                        scenario.services.generatedOutputs.outputs(scenario.requestId, scenario.owner(true)).isEmpty()
                    })
            },
        )
    }

    @Test
    fun `every reconciliation run is audited even when its discrepancy already exists`() {
        val scenario = scenario(protectedAnswer = false)
        propose(scenario, scenario.owner(true), "value-before-reconciliation")
        reconcile(scenario, scenario.reviewer(true), "first-comparison")
        reconcile(scenario, scenario.reviewer(true), "repeated-comparison")
        dataSource.connection.use { connection ->
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_imported_value_reconciliation WHERE information_request_id = ?",
                    scenario.requestId
                )
            )
            assertEquals(
                2,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key = 'information_request.external_value.reconciled'",
                    scenario.requestId.toString()
                )
            )
        }
    }

    @Test
    fun `an unverified reviewer's reconciliation leaves a protected answer out and records nothing about it`() {
        val scenario = scenario(protectedAnswer = true)
        propose(scenario, scenario.owner(verified = true), "protected-value")

        val unverified = reconcile(scenario, scenario.reviewer(verified = false), "unverified-reconciliation")

        assertTrue(unverified.none { it.requirementId == scenario.requirementId }, "$unverified")
        assertEquals(0, discrepancies(scenario))
        val verified = reconcile(scenario, scenario.reviewer(verified = true), "verified-reconciliation")
        assertEquals(InformationRequestReconciliationOutcome.DIFFERS, verified.single().outcome)
        assertTrue(verified.single().responseCanonicalValue.orEmpty().contains(PROTECTED_ANSWER))
        assertEquals(1, discrepancies(scenario))
    }

    @Test
    fun `a retried reconciliation leaves out a protected answer once the caller is no longer recently verified`() {
        val scenario = scenario(protectedAnswer = true)
        propose(scenario, scenario.owner(verified = true), "protected-value")
        reconcile(scenario, scenario.reviewer(verified = true), "reconciliation-once")

        val retried = reconcile(scenario, scenario.reviewer(verified = false), "reconciliation-once")

        assertTrue(retried.none { it.requirementId == scenario.requirementId }, "$retried")
    }

    @Test
    fun `an unverified manager's listing leaves out a protected Requirement's values and their discrepancies`() {
        val scenario = scenario(protectedAnswer = true)
        propose(scenario, scenario.owner(verified = true), "protected-value")
        reconcile(scenario, scenario.reviewer(verified = true), "verified-reconciliation")

        val unverified = QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueRecords.values(
                scenario.requestId,
                scenario.owner(verified = false)
            )
        }
        val verified = QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueRecords.values(
                scenario.requestId,
                scenario.reviewer(verified = true)
            )
        }

        assertTrue(unverified.isEmpty(), "$unverified")
        assertTrue(verified.single().discrepancies.single().discrepancy.responseCanonicalValue.contains(PROTECTED_ANSWER))
    }

    @Test
    fun `an unprotected answer stays comparable and listed without recent verification, but only for callers who may see it`() {
        val scenario = scenario(protectedAnswer = false)
        propose(scenario, scenario.owner(verified = false), "open-value")

        val reconciled = reconcile(scenario, scenario.reviewer(verified = false), "open-reconciliation")
        val reviewerListing = QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueRecords.values(
                scenario.requestId,
                scenario.reviewer(verified = false)
            )
        }
        val ownerListing = QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueRecords.values(
                scenario.requestId,
                scenario.owner(verified = false)
            )
        }

        assertTrue(reconciled.single().responseCanonicalValue.orEmpty().contains(PROTECTED_ANSWER))
        assertTrue(
            reviewerListing.single().discrepancies.single().discrepancy.responseCanonicalValue.contains(
                PROTECTED_ANSWER
            )
        )
        assertEquals(1, ownerListing.size)
        assertTrue(ownerListing.single().discrepancies.isEmpty(), "${ownerListing.single().discrepancies}")
    }

    @Test
    fun `proposing, deciding, and resolving a protected value need recent verification`() {
        val scenario = scenario(protectedAnswer = true)

        assertThrows(StepUpRequiredException::class.java) {
            propose(
                scenario,
                scenario.owner(verified = false),
                "unverified-value"
            )
        }
        assertEquals(0, importedValues(scenario))
        val valueId = propose(scenario, scenario.owner(verified = true), "verified-value")
        assertThrows(StepUpRequiredException::class.java) {
            decide(
                scenario,
                valueId,
                scenario.reviewer(verified = false),
                "unverified-decision"
            )
        }
        reconcile(scenario, scenario.reviewer(verified = true), "verified-reconciliation")
        val discrepancyId = discrepancyId(scenario)
        assertThrows(StepUpRequiredException::class.java) {
            resolve(
                scenario,
                discrepancyId,
                scenario.reviewer(verified = false),
                "unverified-resolution"
            )
        }

        decide(scenario, valueId, scenario.reviewer(verified = true), "verified-decision")
        val resolved = resolve(scenario, discrepancyId, scenario.reviewer(verified = true), "verified-resolution")
        assertEquals(InformationRequestDiscrepancyResolution.RESPONSE_STANDS, resolved.resolution?.resolution)
    }

    private fun scenario(protectedAnswer: Boolean): DisclosureScenario {
        lateinit var reviewerId: UUID
        val request = support.fieldRequest(
            beforePublish = { connection, _, answers ->
                if (protectedAnswer) {
                    execute(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = 'restricted-record' WHERE id = ?",
                        answers.bindingId,
                    )
                }
            },
            prepare = { connection, prepared -> reviewerId = requestingSide(connection, prepared) },
        )
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        support.answerField(
            services,
            request,
            PROTECTED_ANSWER,
            "answer",
            access = access(request.runtime.contributorUserId, verified = true)
        )
        return DisclosureScenario(request, services, reviewerId)
    }

    private fun propose(scenario: DisclosureScenario, access: RequestAccessContext, key: String): UUID =
        QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueIntake.propose(
                ProposeInformationRequestImportedValueCommand(
                    requestId = scenario.requestId,
                    requirementId = scenario.requirementId,
                    resultKey = "registry-value",
                    valueType = FieldValueType.SHORT_TEXT,
                    value = JsonPrimitive("Registry value"),
                    sourceReference = "registry-extract",
                    confidence = InformationRequestSourceConfidence.ASSERTED,
                    verifiedAt = null,
                    expiresAt = null,
                    provenanceReference = "extract-1",
                    access = access,
                    idempotencyKey = key,
                ),
            )
        }.value.id

    private fun decide(scenario: DisclosureScenario, valueId: UUID, access: RequestAccessContext, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueDecisions.decide(
                DecideInformationRequestImportedValueCommand(
                    scenario.requestId,
                    valueId,
                    InformationRequestImportedValueDecisionKind.REJECTED,
                    "does not match",
                    access,
                    key,
                ),
            )
        }

    private fun resolve(scenario: DisclosureScenario, discrepancyId: UUID, access: RequestAccessContext, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueReconciliation.resolve(
                ResolveInformationRequestDiscrepancyCommand(
                    scenario.requestId,
                    discrepancyId,
                    InformationRequestDiscrepancyResolution.RESPONSE_STANDS,
                    "answer confirmed",
                    access,
                    key,
                ),
            )
        }

    private fun reconcile(scenario: DisclosureScenario, access: RequestAccessContext, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.importedValueReconciliation.reconcile(
                ReconcileInformationRequestImportedValuesCommand(
                    scenario.requestId,
                    access,
                    key
                )
            )
        }

    private fun discrepancies(scenario: DisclosureScenario): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM information_request_imported_value_discrepancy WHERE information_request_id = ?",
                scenario.requestId
            )
        }

    private fun importedValues(scenario: DisclosureScenario): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM information_request_imported_value WHERE information_request_id = ?",
                scenario.requestId
            )
        }

    private fun discrepancyId(scenario: DisclosureScenario): UUID =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id FROM information_request_imported_value_discrepancy WHERE information_request_id = ?")
                .use { statement ->
                    statement.setObject(1, scenario.requestId)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getObject(1, UUID::class.java)
                    }
                }
        }

    private fun requestingSide(connection: Connection, request: ConformanceRequest): UUID {
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            request.runtime.exchangeId,
            request.runtime.template.userId,
        )
        val reviewerId = UUID.randomUUID()
        val reviewerPartyId = UUID.randomUUID()
        request.runtime.insertActingParty(reviewerPartyId, "REVIEWER", reviewerId)
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'REVIEWER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            request.requestId,
            reviewerId,
        )
        execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, reviewerPartyId)
        return reviewerId
    }

    private inner class DisclosureScenario(
        val request: ConformanceRequest,
        val services: InformationRequestRuntimeServices,
        val reviewerId: UUID,
    ) {
        val requestId: UUID get() = request.requestId
        val requirementId: UUID get() = request.answers.requirementId

        fun owner(verified: Boolean) = access(request.runtime.template.userId, verified)

        fun reviewer(verified: Boolean) = access(reviewerId, verified)
    }

    private fun access(userId: UUID, verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "imported-value-session", mfaSatisfied = verified)
        )

    private companion object {
        const val PROTECTED_ANSWER = "Recorded answer for the item"
    }
}
