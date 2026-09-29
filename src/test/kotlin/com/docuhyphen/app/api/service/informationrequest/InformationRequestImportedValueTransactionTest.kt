package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.informationrequest.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReconciliationOutcome
import com.docuhyphen.app.api.model.informationrequest.ProposeInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestGeneratedOutputCommand
import com.docuhyphen.app.api.model.informationrequest.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.model.informationrequest.ResolveInformationRequestDiscrepancyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestImportedValueTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var authorizationService: AuthorizationService
    @Inject lateinit var assembler: InformationRequestRecordAssembler

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a manual value keeps its source, confidence, verification time, expiry, and provenance, once per key, and never touches the answer`()
    {
        val setup = setup()
        support.answerField(setup.services, setup.request, "Recorded answer", "answer")
        val before = answerState(setup.request)

        val command = proposal(setup, "manual-once", JsonPrimitive("Recorded answer"), verified = true)
        val view = QuarkusTransaction.requiringNew().call { setup.services.importedValues.propose(command) }

        val value = view.value
        assertEquals(InformationRequestImportedValueSource.MANUAL, value.sourceKind)
        assertNull(value.connectorExchangeId)
        assertEquals("registry-extract", value.sourceReference)
        assertEquals("recorded-value", value.resultKey)
        assertEquals(FieldValueType.SHORT_TEXT, value.valueType)
        assertEquals("\"Recorded answer\"", value.canonicalValue)
        assertEquals(InformationRequestSourceConfidence.VERIFIED, value.confidence)
        assertEquals(setup.clock.instant().minus(Duration.ofHours(1)), value.verifiedAt?.toInstant())
        assertEquals(setup.clock.instant().plus(Duration.ofDays(30)), value.expiresAt?.toInstant())
        assertEquals("extract-2026-09", value.provenanceReference)
        assertEquals(setup.request.runtime.template.userId, value.recordedByPrincipalId)
        assertNull(view.decision)
        QuarkusTransaction.requiringNew().run {
            val mutations = transitionRepository.findForRequest(setup.request.requestId).map { it.mutation }
            assertTrue(InformationRequestMutation.RECORD_EXTERNAL_VALUE in mutations, mutations.toString())
        }

        val replayed = QuarkusTransaction.requiringNew().call { setup.services.importedValues.propose(command) }
        assertEquals(value.id, replayed.value.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.propose(proposal(setup, "manual-once", JsonPrimitive("Another answer")))
            }
        }
        assertEquals(1, importedValues(setup.request))
        assertEquals(before, answerState(setup.request))
    }

    @Test
    fun `proposals that break the value contract or the provenance rules are refused and record nothing`()
    {
        val setup = setup()
        val now = setup.clock.instant()
        listOf(
            proposal(setup, "blank-source", JsonPrimitive("value"), source = " "),
            proposal(setup, "blank-provenance", JsonPrimitive("value"), provenance = " "),
            proposal(setup, "bad-key", JsonPrimitive("value"), resultKey = "Recorded Value"),
            proposal(setup, "unverified", JsonPrimitive("value"), confidence = InformationRequestSourceConfidence.MATCHED),
            proposal(setup, "future", JsonPrimitive("value"), verified = true, verifiedAt = now.plus(Duration.ofDays(1))),
            proposal(setup, "expiry-first", JsonPrimitive("value"), verified = true, expiresAt = now.minus(Duration.ofHours(2))),
            proposal(setup, "already-expired", JsonPrimitive("value"), expiresAt = now.minus(Duration.ofMinutes(1))),
            proposal(setup, "wrong-type", JsonPrimitive("12.5"), valueType = FieldValueType.DECIMAL),
            proposal(setup, "empty", JsonPrimitive(" ")),
        ).forEach { command ->
            val refused = runCatching { QuarkusTransaction.requiringNew().call { setup.services.importedValues.propose(command) } }.exceptionOrNull()
            assertTrue(refused is InformationRequestCommandRequestException, "${command.idempotencyKey}: $refused")
        }
        val foreign = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.propose(proposal(setup, "foreign", JsonPrimitive("value"), requirementId = UUID.randomUUID()))
            }
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, foreign.reasonCode)
        assertEquals(0, importedValues(setup.request))
    }

    @Test
    fun `only the requesting side reads external sources, managers record values, and reviewers decide them`()
    {
        val setup = setup()
        val contributor = support.contributor(setup.request)
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.propose(proposal(setup, "by-contributor", JsonPrimitive("value"), access = contributor))
            }
        }
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.propose(proposal(setup, "by-reviewer", JsonPrimitive("value"), access = setup.reviewer))
            }
        }
        val value = QuarkusTransaction.requiringNew().call {
            setup.services.importedValues.propose(proposal(setup, "by-owner", JsonPrimitive("value")))
        }.value
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.importedValues.values(setup.request.requestId, contributor) }
        }
        assertEquals(
            listOf(value.id),
            QuarkusTransaction.requiringNew().call { setup.services.importedValues.values(setup.request.requestId, setup.reviewer) }.map { it.value.id },
        )
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.decide(decision(setup, value.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "owner-decides", setup.owner))
            }
        }
    }

    @Test
    fun `a reviewer decides a value once, never one they proposed, and cannot accept one that has expired`()
    {
        val setup = setup(ownerAlsoReviews = true)
        support.answerField(setup.services, setup.request, "Recorded answer", "answer")
        val before = answerState(setup.request)
        val proposed = QuarkusTransaction.requiringNew().call {
            setup.services.importedValues.propose(proposal(setup, "to-decide", JsonPrimitive("Recorded answer")))
        }.value

        val own = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.decide(decision(setup, proposed.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "self", setup.owner))
            }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, own.reasonCode)
        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.decide(
                    decision(setup, proposed.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "long-reason", setup.reviewer, reason = "r".repeat(129)),
                )
            }
        }
        val accepted = QuarkusTransaction.requiringNew().call {
            setup.services.importedValues.decide(decision(setup, proposed.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "accept", setup.reviewer))
        }
        assertEquals(InformationRequestImportedValueDecisionKind.ACCEPTED, accepted.decision?.decision)
        assertEquals("matches the record", accepted.decision?.reasonCode)
        val twice = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.decide(decision(setup, proposed.id, InformationRequestImportedValueDecisionKind.REJECTED, "again", setup.reviewer))
            }
        }
        assertEquals(InformationRequestErrorCatalog.IMPORTED_VALUE_ALREADY_DECIDED, twice.reasonCode)
        assertEquals(before, answerState(setup.request))
        assertEquals(0, acceptedFacts(setup.request))

        val lapsing = QuarkusTransaction.requiringNew().call {
            setup.services.importedValues.propose(
                proposal(setup, "lapsing", JsonPrimitive("Recorded answer"), expiresAt = setup.clock.instant().plus(Duration.ofHours(1))),
            )
        }.value
        setup.clock.instant = setup.clock.instant().plus(Duration.ofHours(2))
        val expired = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.importedValues.decide(decision(setup, lapsing.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "accept-expired", setup.reviewer))
            }
        }
        assertEquals(InformationRequestErrorCatalog.IMPORTED_VALUE_EXPIRED, expired.reasonCode)
        QuarkusTransaction.requiringNew().run {
            setup.services.importedValues.decide(decision(setup, lapsing.id, InformationRequestImportedValueDecisionKind.REJECTED, "reject-expired", setup.reviewer))
        }
    }

    @Test
    fun `reconciliation compares each value with the current answer and records a discrepancy once per answer revision`()
    {
        val setup = setup()
        val matching = propose(setup, "matching", JsonPrimitive("Recorded answer"))
        val first = reconcile(setup, "before-answer").associateBy { it.importedValueId }
        assertEquals(InformationRequestReconciliationOutcome.NO_ANSWER, first.getValue(matching).outcome)

        support.answerField(setup.services, setup.request, "Recorded answer", "answer")
        val differing = propose(setup, "differing", JsonPrimitive("Another answer"))
        val record = propose(
            setup, "record", JsonPrimitive("2026-09-01"),
            requirementId = setup.request.runtime.documentRequirementId, valueType = FieldValueType.DATE,
        )
        val lapsing = propose(setup, "lapsing", JsonPrimitive("Recorded answer"), expiresAt = setup.clock.instant().plus(Duration.ofHours(1)))
        val rejected = propose(setup, "rejected", JsonPrimitive("Rejected answer"))
        QuarkusTransaction.requiringNew().run {
            setup.services.importedValues.decide(decision(setup, rejected, InformationRequestImportedValueDecisionKind.REJECTED, "reject", setup.reviewer))
        }
        setup.clock.instant = setup.clock.instant().plus(Duration.ofHours(2))

        val second = reconcile(setup, "after-answer").associateBy { it.importedValueId }
        assertEquals(InformationRequestReconciliationOutcome.MATCHES, second.getValue(matching).outcome)
        assertEquals(InformationRequestReconciliationOutcome.DIFFERS, second.getValue(differing).outcome)
        assertEquals("\"Recorded answer\"", second.getValue(differing).responseCanonicalValue)
        assertEquals(InformationRequestReconciliationOutcome.NOT_COMPARABLE, second.getValue(record).outcome)
        assertEquals(InformationRequestReconciliationOutcome.EXPIRED, second.getValue(lapsing).outcome)
        assertTrue(rejected !in second.keys)
        val discrepancyId = requireNotNull(second.getValue(differing).discrepancyId)

        val repeated = reconcile(setup, "repeated").associateBy { it.importedValueId }
        assertEquals(discrepancyId, repeated.getValue(differing).discrepancyId)
        assertEquals(1, discrepancies(setup.request))

        support.answerField(setup.services, setup.request, "Another answer", "changed-answer")
        val third = reconcile(setup, "changed").associateBy { it.importedValueId }
        assertEquals(InformationRequestReconciliationOutcome.DIFFERS, third.getValue(matching).outcome)
        assertEquals(InformationRequestReconciliationOutcome.MATCHES, third.getValue(differing).outcome)
        assertEquals(2, discrepancies(setup.request))
        val revision = dataSource.connection.use { connection ->
            queryString(
                connection,
                """
                SELECT d.response_revision::text FROM information_request_imported_value_discrepancy d
                WHERE d.imported_value_id = ?
                """.trimIndent(),
                matching,
            )
        }
        val current = dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT response_revision::text FROM information_request_response WHERE information_request_requirement_id = ?",
                setup.request.answers.requirementId,
            )
        }
        assertEquals(current, revision)
        assertThrows(ForbiddenException::class.java) { reconcile(setup, "by-owner", setup.owner) }
    }

    @Test
    fun `a discrepancy is resolved once with its reason and the answer stands`()
    {
        val setup = setup()
        support.answerField(setup.services, setup.request, "Recorded answer", "answer")
        val differing = propose(setup, "differing", JsonPrimitive("Another answer"))
        val discrepancyId = requireNotNull(reconcile(setup, "reconcile").single { it.importedValueId == differing }.discrepancyId)
        val before = answerState(setup.request)

        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.importedValues.resolve(resolution(setup, discrepancyId, "blank", reason = " ")) }
        }
        val resolved = QuarkusTransaction.requiringNew().call { setup.services.importedValues.resolve(resolution(setup, discrepancyId, "resolve")) }
        assertEquals(InformationRequestDiscrepancyResolution.RESPONSE_STANDS, resolved.resolution?.resolution)
        assertEquals("answer confirmed", resolved.resolution?.reasonCode)
        val replayed = QuarkusTransaction.requiringNew().call { setup.services.importedValues.resolve(resolution(setup, discrepancyId, "resolve")) }
        assertEquals(resolved.resolution?.id, replayed.resolution?.id)
        val twice = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.importedValues.resolve(resolution(setup, discrepancyId, "again")) }
        }
        assertEquals(InformationRequestErrorCatalog.DISCREPANCY_ALREADY_RESOLVED, twice.reasonCode)
        assertEquals(before, answerState(setup.request))
        val view = QuarkusTransaction.requiringNew().call { setup.services.importedValues.values(setup.request.requestId, setup.reviewer) }
            .single { it.value.id == differing }
        assertEquals(listOf(discrepancyId), view.discrepancies.map { it.discrepancy.id })
    }

    @Test
    fun `the request record lists its external sources with provenance, decisions, discrepancies, and resolutions`()
    {
        val setup = setup()
        support.answerField(setup.services, setup.request, "Recorded answer", "answer")
        val differing = propose(setup, "differing", JsonPrimitive("Another answer"))
        QuarkusTransaction.requiringNew().run {
            setup.services.importedValues.decide(decision(setup, differing, InformationRequestImportedValueDecisionKind.REJECTED, "reject", setup.reviewer))
        }
        val other = propose(setup, "other", JsonPrimitive("Other answer"))
        val discrepancyId = requireNotNull(reconcile(setup, "reconcile").single { it.importedValueId == other }.discrepancyId)
        QuarkusTransaction.requiringNew().run { setup.services.importedValues.resolve(resolution(setup, discrepancyId, "resolve")) }
        val output = QuarkusTransaction.requiringNew().call {
            setup.services.generatedOutputs.record(
                RecordInformationRequestGeneratedOutputCommand(
                    setup.request.requestId, null, "summary-output", "external://outputs/summary-1", null, "application/pdf",
                    "summary-service", setup.clock.instant().minus(Duration.ofMinutes(5)), setup.owner, "output",
                ),
            )
        }

        val record = QuarkusTransaction.requiringNew().call { assembler.assemble(requireNotNull(requestRepository.findById(setup.request.requestId))) }
        assertEquals(2, record.getValue("schemaVersion").jsonPrimitive.int)
        val external = record.getValue("externalSources").jsonObject
        val values = external.getValue("importedValues").jsonArray.map { it.jsonObject }.associateBy { it.getValue("importedValueId").jsonPrimitive.content }
        val rejected = values.getValue(differing.toString())
        assertEquals("MANUAL", rejected.getValue("sourceKind").jsonPrimitive.content)
        assertEquals("registry-extract", rejected.getValue("sourceReference").jsonPrimitive.content)
        assertEquals("extract-2026-09", rejected.getValue("provenanceReference").jsonPrimitive.content)
        assertEquals(JsonPrimitive("Another answer"), rejected.getValue("value"))
        assertEquals("REJECTED", rejected.getValue("decision").jsonObject.getValue("decision").jsonPrimitive.content)
        val discrepancy = values.getValue(other.toString()).getValue("discrepancies").jsonArray.single().jsonObject
        assertEquals(discrepancyId.toString(), discrepancy.getValue("discrepancyId").jsonPrimitive.content)
        assertEquals(JsonPrimitive("Recorded answer"), discrepancy.getValue("responseValue"))
        assertEquals("RESPONSE_STANDS", discrepancy.getValue("resolution").jsonObject.getValue("resolution").jsonPrimitive.content)
        assertEquals(
            listOf(output.id.toString()),
            external.getValue("generatedOutputs").jsonArray.map { it.jsonObject.getValue("generatedOutputId").jsonPrimitive.content },
        )
        assertEquals(emptyList<Any>(), external.getValue("connectorExchanges").jsonArray.toList())
    }

    @Test
    fun `a request that no longer takes external records refuses them`()
    {
        val setup = setup()
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", setup.request.requestId)
        }
        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.importedValues.propose(proposal(setup, "cancelled", JsonPrimitive("value"))) }
        }
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, refused.reasonCode)
    }

    private fun setup(ownerAlsoReviews: Boolean = false): ExternalSourceSetup
    {
        lateinit var reviewerId: UUID
        val request = support.fieldRequest(prepare = { connection, prepared -> reviewerId = requestingSide(connection, prepared, ownerAlsoReviews) })
        val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService, externalClock = clock)
        return ExternalSourceSetup(
            request,
            services,
            clock,
            support.owner(request),
            RequestAccessContext(PrincipalRef.user(reviewerId), AuthorizationContext(sessionRef = "reviewer-session")),
        )
    }

    @Suppress("LongParameterList")
    private fun proposal(
        setup: ExternalSourceSetup,
        key: String,
        value: JsonElement,
        requirementId: UUID = setup.request.answers.requirementId,
        valueType: FieldValueType = FieldValueType.SHORT_TEXT,
        resultKey: String = "recorded-value",
        source: String = "registry-extract",
        provenance: String = "extract-2026-09",
        confidence: InformationRequestSourceConfidence = InformationRequestSourceConfidence.ASSERTED,
        verified: Boolean = false,
        verifiedAt: Instant? = null,
        expiresAt: Instant? = null,
        access: RequestAccessContext = setup.owner,
    ): ProposeInformationRequestImportedValueCommand
    {
        val now = setup.clock.instant()
        return ProposeInformationRequestImportedValueCommand(
            requestId = setup.request.requestId,
            requirementId = requirementId,
            resultKey = resultKey,
            valueType = valueType,
            value = value,
            sourceReference = source,
            confidence = if (verified) InformationRequestSourceConfidence.VERIFIED else confidence,
            verifiedAt = verifiedAt ?: if (verified) now.minus(Duration.ofHours(1)) else null,
            expiresAt = expiresAt ?: if (verified) now.plus(Duration.ofDays(30)) else null,
            provenanceReference = provenance,
            access = access,
            idempotencyKey = key,
        )
    }

    @Suppress("LongParameterList")
    private fun propose(
        setup: ExternalSourceSetup,
        key: String,
        value: JsonElement,
        requirementId: UUID = setup.request.answers.requirementId,
        valueType: FieldValueType = FieldValueType.SHORT_TEXT,
        expiresAt: Instant? = null,
    ): UUID =
        QuarkusTransaction.requiringNew().call {
            setup.services.importedValues.propose(proposal(setup, key, value, requirementId, valueType, expiresAt = expiresAt))
        }.value.id

    @Suppress("LongParameterList")
    private fun decision(
        setup: ExternalSourceSetup,
        valueId: UUID,
        kind: InformationRequestImportedValueDecisionKind,
        key: String,
        access: RequestAccessContext,
        reason: String = "matches the record",
    ) = DecideInformationRequestImportedValueCommand(setup.request.requestId, valueId, kind, reason, access, key)

    private fun resolution(setup: ExternalSourceSetup, discrepancyId: UUID, key: String, reason: String = "answer confirmed") =
        ResolveInformationRequestDiscrepancyCommand(
            setup.request.requestId, discrepancyId, InformationRequestDiscrepancyResolution.RESPONSE_STANDS, reason, setup.reviewer, key,
        )

    private fun reconcile(setup: ExternalSourceSetup, key: String, access: RequestAccessContext = setup.reviewer) =
        QuarkusTransaction.requiringNew().call {
            setup.services.importedValues.reconcile(ReconcileInformationRequestImportedValuesCommand(setup.request.requestId, access, key))
        }

    private fun answerState(request: ConformanceRequest): String =
        dataSource.connection.use { connection ->
            val text = queryString(
                connection,
                "SELECT COALESCE(string_agg(text_value, ','), '') FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
                request.requestId,
            )
            val revisions = queryString(
                connection,
                "SELECT COALESCE(string_agg(response_revision::text, ','), '') FROM information_request_response WHERE information_request_id = ?",
                request.requestId,
            )
            "$text|$revisions"
        }

    private fun importedValues(request: ConformanceRequest): Int = count("information_request_imported_value", request)

    private fun discrepancies(request: ConformanceRequest): Int = count("information_request_imported_value_discrepancy", request)

    private fun acceptedFacts(request: ConformanceRequest): Int =
        dataSource.connection.use { connection ->
            queryInt(connection, "SELECT COUNT(*) FROM information_request_accepted_fact WHERE source_information_request_id = ?", request.requestId)
        }

    private fun count(table: String, request: ConformanceRequest): Int =
        dataSource.connection.use { connection ->
            queryInt(connection, "SELECT COUNT(*) FROM $table WHERE information_request_id = ?", request.requestId)
        }

    private fun requestingSide(connection: Connection, request: ConformanceRequest, ownerAlsoReviews: Boolean): UUID
    {
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
        reviewerParty(connection, request, reviewerId, newUser = true)
        if (ownerAlsoReviews) reviewerParty(connection, request, request.runtime.template.userId, newUser = false)
        return reviewerId
    }

    private fun reviewerParty(connection: Connection, request: ConformanceRequest, userId: UUID, newUser: Boolean)
    {
        val partyId = UUID.randomUUID()
        if (newUser) request.runtime.insertActingParty(partyId, "REVIEWER", userId)
        else request.runtime.insertPartyForUser(partyId, "REVIEWER", userId)
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'REVIEWER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            request.requestId,
            userId,
        )
        execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
    }

    private data class ExternalSourceSetup(
        val request: ConformanceRequest,
        val services: InformationRequestRuntimeServices,
        val clock: MutableClock,
        val owner: RequestAccessContext,
        val reviewer: RequestAccessContext,
    )
}

internal class MutableClock(var instant: Instant) : Clock()
{
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant
}
