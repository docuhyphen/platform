package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponsePatch
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.ResponseFieldValuesPatch
import com.docuhyphen.app.api.service.informationrequest.ResponseNarrativePatch
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import io.quarkus.narayana.jta.QuarkusTransaction
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

internal data class ConformanceRequest(
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
)
{
    val requestId: UUID get() = runtime.requestId
}

internal class ConformanceRequestSupport(
    private val dataSource: DataSource,
    private val runtime: InformationRequestRuntimeTestServices,
    private val requestRepository: InformationRequestRepository,
)
{
    fun fieldRequest(
        staged: Boolean = false,
        reviewed: Boolean = false,
        beforePublish: (Connection, SubmissionRuntimeSqlFixture, FieldAnswerSqlFixture) -> Unit = { _, _, _ -> },
        prepare: (Connection, ConformanceRequest) -> Unit = { _, _ -> },
    ): ConformanceRequest =
        dataSource.connection.use { connection ->
            lateinit var answers: FieldAnswerSqlFixture
            val request = SubmissionRuntimeSqlFixture(connection, staged = staged, reviewed = reviewed, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template)
                beforePublish(connection, configured, answers)
            })
            answers.materializeUnanswered(request)
            execute(connection, "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE id = ?", answers.schemaFieldBindingId)
            grantShare(connection, request, request.contributorPartyId, request.contributorUserId, "CONTRIBUTOR")
            grantShare(connection, request, request.attestorPartyId, request.attestorUserId, "ATTESTOR")
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 5, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                request.requestId,
                request.template.organizationId,
            )
            ConformanceRequest(request, answers).also { prepare(connection, it) }
        }

    fun responseETag(request: ConformanceRequest): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(request.requestId)))
        }

    fun submissionETag(request: ConformanceRequest, stageKey: String? = null): String =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(request.requestId))
            InformationRequestETag.submissionOf(stageKey, runtime.contentCollector.collect(stored, stageKey).contentHash)
        }

    fun answerField(
        services: InformationRequestRuntimeServices,
        request: ConformanceRequest,
        value: String,
        key: String,
        access: RequestAccessContext = contributor(request),
    ) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(request)),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = request.answers.requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(FieldValueEntry(request.answers.fieldContractId, JsonPrimitive(value))),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                ),
            )
        }

    fun saveNarrative(services: InformationRequestRuntimeServices, request: ConformanceRequest, requirementId: UUID, narrative: String, key: String) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = contributor(request),
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(request)),
                    idempotencyKey = key,
                    patches = listOf(InformationRequestResponsePatch(requirementId = requirementId, narrative = ResponseNarrativePatch.Set(narrative))),
                ),
            )
        }

    fun assent(services: InformationRequestRuntimeServices, request: ConformanceRequest, key: String, stageKey: String? = null) =
        QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = request.requestId,
                    requirementId = request.runtime.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = attestor(request),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(request, stageKey)),
                    idempotencyKey = key,
                ),
            )
        }

    fun submit(services: InformationRequestRuntimeServices, request: ConformanceRequest, key: String, stageKey: String? = null) =
        QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    stageKey = stageKey,
                    access = contributor(request),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(request, stageKey)),
                    idempotencyKey = key,
                ),
            )
        }

    fun contributor(request: ConformanceRequest) =
        RequestAccessContext(PrincipalRef.user(request.runtime.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    fun attestor(request: ConformanceRequest) =
        RequestAccessContext(PrincipalRef.user(request.runtime.attestorUserId), AuthorizationContext(sessionRef = "attestor-session"))

    fun owner(request: ConformanceRequest) =
        RequestAccessContext(PrincipalRef.user(request.runtime.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun grantShare(connection: Connection, request: SubmissionRuntimeSqlFixture, partyId: UUID, userId: UUID, role: String)
    {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            request.requestId,
            userId,
            role,
        )
        execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
    }
}
