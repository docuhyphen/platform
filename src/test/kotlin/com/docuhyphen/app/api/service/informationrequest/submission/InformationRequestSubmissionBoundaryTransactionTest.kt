package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.everyContinuationAction
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubmissionBoundaryTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorization: DefaultAuthorizationService

    @Inject
    lateinit var grants: InformationRequestExecutionGrantService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requests) }

    @Test
    fun `an assertion hidden by its condition is omitted from review before submission`() {
        val prepared = conditionalRequest()
        val services = services(prepared)
        support.answerField(services, prepared, "not-requested", "hide-assertion")

        val preview = QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(prepared.requestId, null, support.attestor(prepared))
        }

        assertTrue(preview.attestations.isEmpty())
    }

    @Test
    fun `a party cannot attest to an assertion hidden by its condition`() {
        val prepared = conditionalRequest()
        val services = services(prepared)
        support.answerField(services, prepared, "not-requested", "hide-before-attestation")

        val refused = assertThrows(InformationRequestItemRefusalException::class.java) {
            support.assent(services, prepared, "attest-hidden-assertion")
        }

        assertEquals(InformationRequestErrorCatalog.REQUIREMENT_HIDDEN_BY_CONDITION, refused.reasonCode)
        assertTrue(
            QuarkusTransaction.requiringNew().call { runtime.attestationRepository.findForRequest(prepared.requestId) }
                .isEmpty()
        )
    }

    @Test
    fun `repeating an earlier role assent preserves the later role assent for unchanged content`() {
        val prepared = support.fieldRequest(beforePublish = { _, configured, _ ->
            val policy = UUID.randomUUID()
            configured.template.insertAttestationPolicy(
                policy, configured.attestationBindingId, configured.template.versionId,
                "ROLE_SEQUENCE", 2, "VERIFIED_CONTACT", null, "NOT_ACCEPTED",
            )
            listOf("CONTRIBUTOR", "ATTESTOR").forEachIndexed { index, role ->
                configured.template.insertAttestationRole(policy, configured.template.versionId, role, index + 1)
            }
        })
        val services = services(prepared)
        support.answerField(services, prepared, "record-value", "answer-before-role-assents")
        val first = attest(prepared, support.contributor(prepared), "first-role")
        val later = support.assent(services, prepared, "later-role")
        assertEquals(InformationRequestAttestationState.SATISFIED, later.evaluation?.evaluation?.state)

        val repeated = attest(prepared, support.contributor(prepared), "first-role-again")

        assertEquals(first.attestation.id, repeated.attestation.id)
        assertEquals(InformationRequestAttestationState.SATISFIED, repeated.evaluation?.evaluation?.state)
        assertEquals(
            2,
            QuarkusTransaction.requiringNew()
                .call { runtime.attestationRepository.findForRequest(prepared.requestId) }.size
        )
    }

    @Test
    fun `a preview no longer offers submission or confirmation after its grant is revoked`() {
        val prepared = support.fieldRequest()
        val services = services(prepared)
        support.answerField(services, prepared, "record-value", "answer-before-grant-revocation")
        support.assent(services, prepared, "assent-before-grant-revocation")
        assertTrue(preview(prepared, support.contributor(prepared)).canSubmit)
        assertTrue(preview(prepared, support.attestor(prepared)).attestations.single().callerCanAttest)
        QuarkusTransaction.requiringNew().run { grants.revoke(prepared.requestId, "OWNER_REVOKED") }

        assertFalse(preview(prepared, support.contributor(prepared)).canSubmit)
        assertFalse(preview(prepared, support.attestor(prepared)).attestations.single().callerCanAttest)
    }

    @Test
    fun `a preview offers no confirmation when its grant does not list attestation`() {
        val prepared = support.fieldRequest(continuationActions = everyContinuationAction.filter { it != "ATTEST" })

        assertFalse(preview(prepared, support.attestor(prepared)).attestations.single().callerCanAttest)
    }

    private fun services(prepared: ConformanceRequest) = runtime.build(
        prepared.requestId, centralAuthorization = authorization, executionGrants = grants,
    )

    private fun preview(prepared: ConformanceRequest, access: RequestAccessContext) =
        QuarkusTransaction.requiringNew().call {
            services(prepared).submissionQueries.preview(prepared.requestId, null, access)
        }

    private fun attest(prepared: ConformanceRequest, access: RequestAccessContext, key: String) =
        QuarkusTransaction.requiringNew().call {
            services(prepared).attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = prepared.requestId,
                    requirementId = prepared.runtime.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(support.submissionETag(prepared)),
                    idempotencyKey = key,
                )
            )
        }

    private fun conditionalRequest() = support.fieldRequest(beforePublish = { connection, configured, answers ->
        val rule = UUID.randomUUID()
        execute(
            connection,
            "INSERT INTO information_request_template_condition_rule (id, template_version_id, rule_key, expression_version, hidden_data_policy) VALUES (?, ?, 'when-confirmation-applies', 1, 'RETAIN_SECURELY')",
            rule, configured.template.versionId,
        )
        execute(
            connection,
            "INSERT INTO information_request_template_condition_predicate (id, condition_rule_id, template_version_id, display_order, field_definition_id, value_type, operator, text_value) VALUES (?, ?, ?, 0, ?, 'SHORT_TEXT', 'EQUALS', 'confirmation-requested')",
            UUID.randomUUID(), rule, configured.template.versionId, answers.fieldDefinitionId,
        )
        execute(
            connection,
            "UPDATE information_request_template_requirement_binding SET requiredness = 'CONDITIONAL', conditional_rule_key = 'when-confirmation-applies' WHERE id = ?",
            configured.attestationBindingId,
        )
    })
}
