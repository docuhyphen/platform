package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.conformance.model.SiblingFieldRequest
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

internal class SharedStableFieldRequestSupport(
    private val dataSource: DataSource,
    private val runtime: InformationRequestRuntimeTestServices,
    private val requestRepository: InformationRequestRepository,
) {
    fun siblingOf(first: ConformanceRequest): SiblingFieldRequest {
        val source = first.runtime
        val template = source.template
        val requestId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                requestId,
                source.exchangeId,
                template.versionId,
                template.organizationId,
            )
        }
        QuarkusTransaction.requiringNew().run {
            runtime.materializer.materialize(
                requireNotNull(requestRepository.findById(requestId)),
                FieldsAccessContext(
                    PrincipalRef.user(template.userId),
                    AuthorizationContext(sessionRef = "owner-session")
                ),
            )
        }
        return dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?",
                requestId
            )
            val sibling = SiblingFieldRequest(
                requestId = requestId,
                fieldRequirementId = requirementFor(connection, requestId, first.answers.bindingId),
                documentRequirementId = requirementFor(connection, requestId, source.documentBindingId),
                attestationRequirementId = requirementFor(connection, requestId, source.attestationBindingId),
            )
            insertParty(connection, requestId, "CONTRIBUTOR", source.contributorUserId)
            insertParty(connection, requestId, "ATTESTOR", source.attestorUserId)
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, granted_features, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 5, ARRAY ['BUSINESS_FIELDS_AND_SCHEMAS', 'INFORMATION_REQUESTS'], now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                requestId,
                template.organizationId,
            )
            insertProvidedDocument(connection, sibling, source.contributorUserId)
            sibling
        }
    }

    fun answer(
        services: InformationRequestRuntimeServices,
        requestId: UUID,
        requirementId: UUID,
        fieldContractId: UUID,
        value: String,
        key: String,
        access: RequestAccessContext,
    ): InformationRequestResponseDraftResult =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = requestId,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(requestId)),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(FieldValueEntry(fieldContractId, JsonPrimitive(value))),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                ),
            )
        }

    fun assent(
        services: InformationRequestRuntimeServices,
        request: SiblingFieldRequest,
        access: RequestAccessContext,
        key: String,
    ) =
        QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = request.requestId,
                    requirementId = request.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(request.requestId)),
                    idempotencyKey = key,
                ),
            )
        }

    fun submit(
        services: InformationRequestRuntimeServices,
        request: SiblingFieldRequest,
        access: RequestAccessContext,
        key: String,
    ) =
        QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    stageKey = null,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(request.requestId)),
                    idempotencyKey = key,
                ),
            )
        }

    fun assignExchangeField(connection: Connection, prepared: ConformanceRequest): UUID {
        val now = prepared.runtime.template.now
        val schemaId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val bindingId = UUID.randomUUID()
        val assignmentId = UUID.randomUUID()
        val setId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key, display_name,
                                           target_resource_type, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Exchange data', 'EXCHANGE', 'PUBLISHED', ?, ?)
            """.trimIndent(),
            schemaId,
            prepared.runtime.template.organizationId,
            "exchange-data-${schemaId.toString().take(8)}",
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            versionId,
            schemaId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id, field_definition_id, display_order,
                                              is_required, is_read_only, visibility)
            VALUES (?, ?, ?, ?, 0, FALSE, FALSE, 'INTERNAL')
            """.trimIndent(),
            bindingId,
            versionId,
            prepared.answers.fieldContractId,
            prepared.answers.fieldDefinitionId,
        )
        execute(
            connection,
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind, scope_org_id,
                                           assignment_source, assigned_by_principal_kind, assigned_by_principal_id, assigned_at)
            VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', 'USER', ?, ?)
            """.trimIndent(),
            assignmentId,
            prepared.runtime.exchangeId,
            versionId,
            prepared.runtime.template.organizationId,
            prepared.runtime.template.userId,
            now,
        )
        execute(
            connection,
            "INSERT INTO field_value_set (id, schema_assignment_id, set_kind, created_at, updated_at) VALUES (?, ?, 'ROOT', ?, ?)",
            setId,
            assignmentId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO field_value (id, field_value_set_id, schema_assignment_id, schema_field_binding_id, field_contract_id,
                                     resource_type, resource_id, value_type, text_value, provenance, created_at, updated_at,
                                     updated_by_principal_kind, updated_by_principal_id)
            VALUES (?, ?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', 'Exchange answer', 'USER', ?, ?, 'USER', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            setId,
            assignmentId,
            bindingId,
            prepared.answers.fieldContractId,
            prepared.runtime.exchangeId,
            now,
            now,
            prepared.runtime.template.userId,
        )
        return assignmentId
    }

    fun answerOf(requestId: UUID): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                """
                SELECT value.text_value FROM field_value value
                JOIN schema_assignment assignment ON assignment.id = value.schema_assignment_id
                WHERE assignment.resource_type = 'INFORMATION_REQUEST' AND assignment.resource_id = ?
                """.trimIndent(),
                requestId,
            )
        }

    fun revisionTextsOf(requestId: UUID): Set<String> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                """
                SELECT revision.text_value FROM field_value_revision revision
                JOIN schema_assignment assignment ON assignment.id = revision.schema_assignment_id
                WHERE assignment.resource_type = 'INFORMATION_REQUEST' AND assignment.resource_id = ?
                """.trimIndent(),
                requestId,
            )
        }

    fun fieldState(assignmentId: UUID): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                """
                SELECT concat_ws('|', value_set.id, value_set.revision, value_set.updated_at, value.id, value.text_value,
                                 value.updated_at,
                                 (SELECT COUNT(*) FROM field_value_revision WHERE schema_assignment_id = value_set.schema_assignment_id))
                FROM field_value_set value_set
                LEFT JOIN field_value value ON value.field_value_set_id = value_set.id
                WHERE value_set.schema_assignment_id = ? AND value_set.set_kind = 'ROOT'
                """.trimIndent(),
                assignmentId,
            )
        }

    fun assignmentOf(requestId: UUID): UUID =
        dataSource.connection.use { connection ->
            UUID.fromString(
                queryString(
                    connection,
                    "SELECT id::text FROM schema_assignment WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
                    requestId,
                ),
            )
        }

    fun projectedAnswers(result: InformationRequestResponseDraftResult, requirementId: UUID): List<String> =
        result.fieldValueProjectionsByRequirementId.getValue(requirementId).fields
            .filterNot { it.isEmpty }
            .map { it.value.jsonPrimitive.content }

    private fun responseETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(requestId)))
        }

    private fun submissionETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(stored, null).contentHash)
        }

    private fun requirementFor(connection: Connection, requestId: UUID, templateBindingId: UUID): UUID =
        UUID.fromString(
            queryString(
                connection,
                """
                SELECT id::text FROM information_request_requirement
                WHERE information_request_id = ? AND source_template_binding_id = ?
                """.trimIndent(),
                requestId,
                templateBindingId,
            ),
        )

    private fun insertParty(connection: Connection, requestId: UUID, role: String, userId: UUID) {
        val partyId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, ?, 'USER', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            partyId,
            requestId,
            role,
            userId,
        )
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            userId,
            role,
        )
        execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
    }

    private fun insertProvidedDocument(connection: Connection, request: SiblingFieldRequest, contributorUserId: UUID) {
        val revisionId = UUID.fromString(
            queryString(
                connection,
                "SELECT current_revision_id::text FROM information_request_requirement_current WHERE information_request_requirement_id = ?",
                request.documentRequirementId,
            ),
        )
        val documentId = UUID.randomUUID()
        val documentVersionId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val evidenceVersionId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_response
                (id, information_request_id, information_request_requirement_id, requirement_revision_id,
                 occurrence_path, disposition, response_revision, recorded_by_principal_kind,
                 recorded_by_principal_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'root', 'PROVIDED', 2, 'USER', ?, now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            request.requestId,
            request.documentRequirementId,
            revisionId,
            contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO document (id, encryption_mode, is_deleted, created_date, update_date, title, type)
            VALUES (?, 0, FALSE, now(), now(), 'Process record', 'PDF')
            """.trimIndent(),
            documentId,
        )
        execute(
            connection,
            """
            INSERT INTO document_version
                (id, document_id, created_by_principal_kind, created_by_principal_id, created_date, file_name,
                 version, storage_provider, storage_locator_kind, storage_locator,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, 'USER', ?, now(), 'process-record.pdf', '1', 'OBJECT_STORE', 'OBJECT_KEY', ?,
                    3, 'SHA_256', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 'VERIFIED')
            """.trimIndent(),
            documentVersionId,
            documentId,
            contributorUserId,
            "document-versions/$documentVersionId/process-record.pdf",
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_artifact
                (id, information_request_id, information_request_requirement_id, artifact_key, artifact_revision,
                 created_at, updated_at, created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, 'evidence-1', 1, now(), now(), 'USER', ?)
            """.trimIndent(),
            artifactId,
            request.requestId,
            request.documentRequirementId,
            contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_version
                (id, evidence_artifact_id, information_request_id, version_number, source_kind, document_version_id,
                 created_at, created_by_principal_kind, created_by_principal_id, declared_file_name, declared_media_type)
            VALUES (?, ?, ?, 1, 'DOCUMENT_VERSION', ?, now(), 'USER', ?, 'process-record.pdf', 'application/pdf')
            """.trimIndent(),
            evidenceVersionId,
            artifactId,
            request.requestId,
            documentVersionId,
            contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_assessment
                (id, information_request_id, evidence_version_id, assessment_kind, outcome, content_hash_algorithm,
                 content_hash, content_length, assessed_at, detected_media_type, page_count)
            VALUES (?, ?, ?, 'CONTENT_INSPECTION', 'INSPECTED', 'SHA_256',
                    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 3, now(), 'application/pdf', 1)
            """.trimIndent(),
            UUID.randomUUID(),
            request.requestId,
            evidenceVersionId,
        )
    }
}
