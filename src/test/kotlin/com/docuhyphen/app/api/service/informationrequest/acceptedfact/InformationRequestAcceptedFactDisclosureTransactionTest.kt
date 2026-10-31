package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RevokeInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.model.DisclosureFactFixture
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAcceptedFactDisclosureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var standing: InformationRequestAcceptedFactStanding

    @Inject
    lateinit var recordExports: com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService

    @Test
    fun `the real authorizer withholds protected fact metadata from an unverified record export`() {
        val fixture = fixture()
        val fact =
            promote(central(fixture.requestId), fixture, owner(fixture, verified = true), "fact-before-export").fact

        fun export(verified: Boolean, key: String) = recordExports.create(
            com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand(
                fixture.requestId, owner(fixture, verified), null, key,
            ),
        ).export.contentJson

        val withheld = kotlinx.serialization.json.Json.parseToJsonElement(export(false, "unverified-fact-export"))
        val shown = kotlinx.serialization.json.Json.parseToJsonElement(export(true, "verified-fact-export"))
        val hiddenFact = withheld.jsonObject.getValue("acceptedFacts").jsonArray.single().jsonObject
        assertEquals(setOf("acceptedFactId", "requirementId", "redacted"), hiddenFact.keys)
        assertEquals(fact.id.toString(), hiddenFact.getValue("acceptedFactId").jsonPrimitive.content)
        assertEquals(
            "DECLARED",
            shown.jsonObject.getValue("acceptedFacts").jsonArray.single().jsonObject.getValue("confidence").jsonPrimitive.content
        )
    }

    @Test
    fun `a waived Field item persists a declared fact without review provenance`() {
        val fixture = fixture(reviewed = true)
        val promoted =
            promote(central(fixture.requestId), fixture, owner(fixture, verified = true), "waived-item-promotion").fact
        assertEquals(
            com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConfidence.DECLARED,
            promoted.confidence
        )
        assertNull(promoted.sourceReviewId)
        assertEquals(1, facts(fixture))
    }

    @Test
    fun `an unverified decision maker cannot promote a protected answer and the attempt records nothing`() {
        val fixture = fixture()
        val services = central(fixture.requestId)

        assertThrows(StepUpRequiredException::class.java) {
            promote(
                services,
                fixture,
                owner(fixture, verified = false),
                "unverified-promotion"
            )
        }
        assertEquals(0, facts(fixture))
        val promoted = promote(services, fixture, owner(fixture, verified = true), "verified-promotion")

        assertEquals(RECORDED_VALUE, promoted.fact.canonicalValue)
    }

    @Test
    fun `a retried promotion returns a protected value only to a caller who is still recently verified`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        promote(services, fixture, owner(fixture, verified = true), "promotion-once")

        assertThrows(StepUpRequiredException::class.java) {
            promote(
                services,
                fixture,
                owner(fixture, verified = false),
                "promotion-once"
            )
        }
        assertEquals(1, facts(fixture))
    }

    @Test
    fun `the promoted-from list leaves out a protected fact until the caller verifies again`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        promote(services, fixture, owner(fixture, verified = true), "verified-promotion")

        val unverified = QuarkusTransaction.requiringNew()
            .call { services.acceptedFactQueries.promotedFrom(fixture.requestId, owner(fixture, verified = false)) }
        val verified = QuarkusTransaction.requiringNew()
            .call { services.acceptedFactQueries.promotedFrom(fixture.requestId, owner(fixture, verified = true)) }

        assertTrue(unverified.isEmpty(), "$unverified")
        assertEquals(RECORDED_VALUE, verified.single().fact.canonicalValue)
    }

    @Test
    fun `a decision maker's offers leave out a protected Requirement's fact until the caller verifies again`() {
        val fixture = fixture()
        promote(central(fixture.requestId), fixture, owner(fixture, verified = true), "verified-promotion")
        val later = central(fixture.laterRequestId)

        val unverified = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, owner(fixture, verified = false)) }
        val verified = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, owner(fixture, verified = true)) }

        assertTrue(unverified.isEmpty(), "$unverified")
        assertEquals(RECORDED_VALUE, verified.single().fact.fact.canonicalValue)
    }

    @Test
    fun `a fact promoted from a protected Requirement is offered to a respondent only after recent verification`() {
        val fixture = fixture()
        insertFact(fixture)
        val later = runtime.build(fixture.laterRequestId, denies = { principal, action ->
            principal.id == fixture.runtime.contributorUserId && action == Action.INFORMATION_REQUEST_PROMOTE_FACT
        })

        val unverified = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, contributor(fixture, verified = false)) }
        val verified = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, contributor(fixture, verified = true)) }

        assertTrue(unverified.isEmpty(), "$unverified")
        assertEquals(listOf(fixture.laterRequirementId), verified.map { it.requirementId })
    }

    @Test
    fun `revoking a protected fact needs recent verification because the answer carries its value`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val factId = promote(services, fixture, owner(fixture, verified = true), "verified-promotion").fact.id

        assertThrows(StepUpRequiredException::class.java) {
            revoke(
                services,
                fixture,
                factId,
                owner(fixture, verified = false),
                "unverified-revocation"
            )
        }
        assertEquals(0, revocations(factId))
        revoke(services, fixture, factId, owner(fixture, verified = true), "verified-revocation")

        assertEquals(1, revocations(factId))
    }

    @Test
    fun `a requesting-side fact is offered on a later request neither to its owner nor to a decision maker who cannot read its source`() {
        val fixture = fixture()
        promote(
            central(fixture.requestId),
            fixture,
            owner(fixture, verified = true),
            "requesting-side-promotion",
            InformationRequestAcceptedFactVisibility.REQUESTING_SIDE,
        )
        val outsider = laterDecisionMaker(fixture)
        val later = central(fixture.laterRequestId)

        val outsiderOffers = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, access(outsider, verified = true)) }
        val ownerOffers = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, owner(fixture, verified = true)) }

        assertTrue(outsiderOffers.isEmpty(), "$outsiderOffers")
        assertTrue(ownerOffers.isEmpty(), "$ownerOffers")
    }

    @Test
    fun `a fact published for respondents is still offered to a later decision maker who cannot read its source`() {
        val fixture = fixture()
        promote(central(fixture.requestId), fixture, owner(fixture, verified = true), "respondent-promotion")
        val outsider = laterDecisionMaker(fixture)
        val later = central(fixture.laterRequestId)

        val offers = QuarkusTransaction.requiringNew()
            .call { later.acceptedFactQueries.offers(fixture.laterRequestId, access(outsider, verified = true)) }

        assertEquals(RECORDED_VALUE, offers.single().fact.fact.canonicalValue)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ENDED", "REJECTED", "RESCINDED"])
    fun `the owner still lists and revokes a request's facts after its Exchange finishes`(status: String) {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val factId = promote(services, fixture, owner(fixture, verified = true), "promotion-before-ending").fact.id
        finishExchange(fixture, status)

        val listed = QuarkusTransaction.requiringNew()
            .call { services.acceptedFactQueries.promotedFrom(fixture.requestId, owner(fixture, verified = true)) }
        revoke(services, fixture, factId, owner(fixture, verified = true), "revocation-after-ending")

        assertEquals(listOf(factId), listed.map { it.fact.id })
        assertEquals(1, revocations(factId))
    }

    @Test
    fun `a fact stays revocable after the Exchange ends and the request's execution grant is revoked`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val factId = promote(services, fixture, owner(fixture, verified = true), "promotion-before-revocation").fact.id
        revokedGrant(fixture)
        finishExchange(fixture, "ENDED")

        revoke(services, fixture, factId, owner(fixture, verified = true), "revocation-without-grant")

        assertEquals(1, revocations(factId))
    }

    @Test
    fun `a new fact cannot be promoted once the Exchange has ended`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        finishExchange(fixture, "ENDED")

        assertThrows(ForbiddenException::class.java) {
            promote(services, fixture, owner(fixture, verified = true), "promotion-after-ending")
        }
        assertEquals(0, facts(fixture))
    }

    @Test
    fun `a fact cannot be revoked by a party who does not own the request after its Exchange ends`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val factId = promote(services, fixture, owner(fixture, verified = true), "promotion-before-outsider").fact.id
        finishExchange(fixture, "ENDED")

        assertThrows(ForbiddenException::class.java) {
            revoke(services, fixture, factId, contributor(fixture, verified = true), "outsider-revocation")
        }
        assertEquals(0, revocations(factId))
    }

    @Test
    fun `a deleted Exchange refuses fact revocation`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val factId = promote(services, fixture, owner(fixture, verified = true), "promotion-before-deletion").fact.id
        dataSource.connection.use {
            execute(
                it,
                "UPDATE exchange SET is_deleted = TRUE WHERE id = ?",
                fixture.runtime.exchangeId
            )
        }

        assertThrows(ForbiddenException::class.java) {
            revoke(services, fixture, factId, owner(fixture, verified = true), "revocation-after-deletion")
        }
        assertEquals(0, revocations(factId))
    }

    @Test
    fun `facts from a deleted source Exchange are excluded from reuse`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val fact = promote(services, fixture, owner(fixture, verified = true), "promotion-before-source-deletion").fact
        fun offered() = QuarkusTransaction.requiringNew().call {
            standing.eligibleForReuse(
                fact.ownerType,
                requireNotNull(fact.ownerOrganizationId ?: fact.ownerUserId),
                fact.subjectIdentityRefId,
                listOf(fact.fieldDefinitionId),
                fact.purposeKey
            )
        }
        assertEquals(listOf(fact.id), offered().map { it.id })
        dataSource.connection.use {
            execute(
                it,
                "UPDATE exchange SET is_deleted = TRUE WHERE id = ?",
                fixture.runtime.exchangeId
            )
        }
        assertEquals(emptyList<UUID>(), offered().map { it.id })
    }

    @Test
    fun `a corrected source item blocks both reuse and another promotion of its frozen value`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val fact = promote(services, fixture, owner(fixture, verified = true), "promotion-before-correction").fact
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request_item_correction
                    (id, information_request_id, package_id, submission_item_id, corrected_value_json,
                     reason_code, recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
                VALUES (?, ?, ?, ?, '"Corrected answer"', 'subject.correction', 'USER', ?, now())
            """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                fixture.facts.packageId,
                fixture.facts.itemId,
                fixture.runtime.template.userId
            )
        }
        assertAll(
            {
                val offered = QuarkusTransaction.requiringNew().call {
                    standing.eligibleForReuse(
                        fact.ownerType,
                        requireNotNull(fact.ownerOrganizationId ?: fact.ownerUserId),
                        fact.subjectIdentityRefId,
                        listOf(fact.fieldDefinitionId),
                        fact.purposeKey
                    )
                }
                assertTrue(offered.isEmpty())
            },
            {
                val refusal =
                    assertThrows(com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException::class.java) {
                        promote(services, fixture, owner(fixture, verified = true), "promotion-after-correction")
                    }
                assertTrue(refusal.message.orEmpty().contains("corrected"), refusal.message)
            },
        )
    }

    @Test
    fun `the owner still revokes a fact after the request is cancelled while its Exchange continues`() {
        val fixture = fixture()
        val services = central(fixture.requestId)
        val factId = promote(services, fixture, owner(fixture, verified = true), "promotion-before-cancel").fact.id
        dataSource.connection.use {
            execute(
                it,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                fixture.requestId
            )
        }

        revoke(services, fixture, factId, owner(fixture, verified = true), "revocation-after-cancel")

        assertEquals(1, revocations(factId))
    }

    private fun finishExchange(fixture: DisclosureFactFixture, status: String) {
        dataSource.connection.use {
            execute(
                it,
                "UPDATE exchange SET status = ? WHERE id = ?",
                status,
                fixture.runtime.exchangeId
            )
        }
    }

    private fun revokedGrant(fixture: DisclosureFactFixture) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode,
                     revoked_at, revoked_reason, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', now(), 'subscription-ended', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                fixture.runtime.template.organizationId,
            )
        }
    }

    private fun laterDecisionMaker(fixture: DisclosureFactFixture): UUID {
        val userId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                userId,
                "later-decision-${userId.toString().take(8)}@process.test",
            )
            decisionMaker(connection, fixture.laterRequestId, userId)
        }
        return userId
    }

    private fun fixture(reviewed: Boolean = false): DisclosureFactFixture =
        dataSource.connection.use { connection ->
            lateinit var answers: FieldAnswerSqlFixture
            val runtimeFixture =
                SubmissionRuntimeSqlFixture(connection, reviewed = reviewed, beforePublish = { configured ->
                    answers = FieldAnswerSqlFixture(
                        connection,
                        configured.template,
                        reviewed = reviewed,
                        reusePurpose = PURPOSE
                    )
                    if (reviewed) configured.template.insertDisposition(
                        answers.bindingId,
                        configured.template.versionId,
                        "WAIVED"
                    )
                    execute(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = 'restricted-record' WHERE id = ?",
                        answers.bindingId,
                    )
                })
            answers.materialize(runtimeFixture)
            val facts = FactSqlFixture(connection, runtimeFixture, answers, reviewRequired = reviewed)
            if (reviewed) {
                val reviews = ReviewSqlFixture(connection, runtimeFixture, seedPackage = false)
                val reviewId = UUID.randomUUID()
                val assignmentId = UUID.randomUUID()
                reviews.insertReview(reviewId, 1, state = "IN_REVIEW", forPackage = facts.packageId)
                reviews.insertAssignment(assignmentId, reviewId)
                reviews.insertDecision(
                    UUID.randomUUID(),
                    reviewId,
                    1,
                    assignmentId = assignmentId,
                    outcome = "WAIVED",
                    narrative = "The configured review permits this waiver",
                    itemId = facts.itemId,
                    requirementId = answers.requirementId,
                    forPackage = facts.packageId
                )
                execute(
                    connection, """
                    UPDATE information_request_review SET state = 'SATISFIED', settled_at = now(),
                        settled_by_principal_kind = 'USER', settled_by_principal_id = ?, review_revision = review_revision + 1
                    WHERE id = ?
                """.trimIndent(), reviews.reviewerUserId, reviewId
                )
            }
            val laterRequestId = UUID.randomUUID()
            val laterRequirementId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state, gates_exchange_closure,
                     aggregate_revision, party_revision, created_at, updated_at, issued_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, now(), now(), now())
                """.trimIndent(),
                laterRequestId,
                runtimeFixture.exchangeId,
                runtimeFixture.template.versionId,
                runtimeFixture.template.organizationId,
            )
            answers.assignSchema(laterRequestId)
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                laterRequestId,
                facts.subjectId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_requirement
                    (id, information_request_id, source_template_version_id, source_template_requirement_id,
                     source_template_binding_id, occurrence_path, created_at)
                VALUES (?, ?, ?, ?, ?, 'root', now())
                """.trimIndent(),
                laterRequirementId,
                laterRequestId,
                runtimeFixture.template.versionId,
                answers.templateRequirementId,
                answers.bindingId,
            )
            val laterRevisionId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request_requirement_revision
                    (id, information_request_requirement_id, information_request_id, source_template_version_id,
                     source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                     effective_from, configuration_hash_sha256, optimistic_version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, 'root', now(), ?, 1, now())
                """.trimIndent(),
                laterRevisionId,
                laterRequirementId,
                laterRequestId,
                runtimeFixture.template.versionId,
                answers.templateRequirementId,
                answers.bindingId,
                "0".repeat(64),
            )
            execute(
                connection,
                """
                INSERT INTO information_request_requirement_current
                    (information_request_requirement_id, current_revision_id, current_revision_number, updated_at)
                VALUES (?, ?, 1, now())
                """.trimIndent(),
                laterRequirementId,
                laterRevisionId,
            )
            decisionMaker(connection, runtimeFixture.requestId, runtimeFixture.template.userId)
            decisionMaker(connection, laterRequestId, runtimeFixture.template.userId)
            DisclosureFactFixture(runtimeFixture, facts, answers, laterRequestId, laterRequirementId)
        }

    private fun decisionMaker(connection: Connection, requestId: UUID, userId: UUID) {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            userId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'DECISION_MAKER', 'USER', ?, ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            userId,
            shareId,
        )
    }

    private fun insertFact(fixture: DisclosureFactFixture) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request_accepted_fact
                    (id, owner_type, owner_organization_id, subject_identity_ref_id, purpose_key, policy_basis_key, field_definition_id, value_type,
                     canonical_value, source_information_request_id, source_package_id, source_submission_item_id,
                     source_requirement_id, source_response_id, source_response_revision, source_field_value_revision_id,
                     visibility, confidence, valid_from, conflict_state, promoted_by_principal_kind, promoted_by_principal_id,
                     promoted_at)
                VALUES (?, 'ORGANIZATION', ?, ?, ?, 'policy.reuse', ?, 'SHORT_TEXT', ?, ?, ?, ?, ?, ?, 2, ?,
                        'RESPONDING_PARTIES', 'DECLARED', now() - INTERVAL '1 day', 'NONE', 'USER', ?, now() - INTERVAL '1 day')
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.runtime.template.organizationId,
                fixture.facts.subjectId,
                PURPOSE,
                fixture.answers.fieldDefinitionId,
                RECORDED_VALUE,
                fixture.requestId,
                fixture.facts.packageId,
                fixture.facts.itemId,
                fixture.answers.requirementId,
                fixture.answers.responseId,
                fixture.answers.fieldValueRevisionId,
                fixture.runtime.template.userId,
            )
        }
    }

    private fun central(requestId: UUID): InformationRequestRuntimeServices =
        runtime.build(requestId, centralAuthorization = authorizationService)

    private fun promote(
        services: InformationRequestRuntimeServices,
        fixture: DisclosureFactFixture,
        access: RequestAccessContext,
        key: String,
        visibility: InformationRequestAcceptedFactVisibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
    ) =
        QuarkusTransaction.requiringNew().call {
            services.acceptedFacts.promote(
                PromoteInformationRequestAcceptedFactCommand(
                    requestId = fixture.requestId,
                    packageId = fixture.facts.packageId,
                    submissionItemId = fixture.facts.itemId,
                    purposeKey = PURPOSE,
                    policyBasisKey = "policy.reuse",
                    visibility = visibility,
                    validTo = fixture.validTo,
                    access = access,
                    idempotencyKey = key,
                ),
            )
        }

    private fun revoke(
        services: InformationRequestRuntimeServices,
        fixture: DisclosureFactFixture,
        factId: UUID,
        access: RequestAccessContext,
        key: String
    ) =
        QuarkusTransaction.requiringNew().call {
            services.acceptedFacts.revoke(
                RevokeInformationRequestAcceptedFactCommand(
                    requestId = fixture.requestId,
                    factId = factId,
                    reasonCode = "no-longer-accurate",
                    access = access,
                    idempotencyKey = key,
                ),
            )
        }

    private fun facts(fixture: DisclosureFactFixture): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM information_request_accepted_fact WHERE source_information_request_id = ?",
                fixture.requestId
            )
        }

    private fun revocations(factId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM information_request_accepted_fact_revocation WHERE fact_id = ?",
                factId
            )
        }

    private fun owner(fixture: DisclosureFactFixture, verified: Boolean) =
        access(fixture.runtime.template.userId, verified)

    private fun contributor(fixture: DisclosureFactFixture, verified: Boolean) =
        access(fixture.runtime.contributorUserId, verified)

    private fun access(userId: UUID, verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "fact-session", mfaSatisfied = verified)
        )

    private companion object {
        const val PURPOSE = "profile.reuse"
        const val RECORDED_VALUE = "\"Recorded answer\""
    }
}
