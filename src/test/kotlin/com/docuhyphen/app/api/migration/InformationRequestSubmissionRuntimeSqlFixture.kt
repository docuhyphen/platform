package com.docuhyphen.app.api.migration

import java.sql.Connection
import java.util.UUID

internal class SubmissionRuntimeSqlFixture(
    private val connection: Connection,
    staged: Boolean = false,
    sequential: Boolean = false,
)
{
    val template = SubmissionTemplateSqlFixture(connection)
    val exchangeId: UUID = UUID.randomUUID()
    val requestId: UUID = UUID.randomUUID()
    val documentTemplateRequirementId: UUID = UUID.randomUUID()
    val documentBindingId: UUID = UUID.randomUUID()
    val attestationTemplateRequirementId: UUID = UUID.randomUUID()
    val attestationBindingId: UUID = UUID.randomUUID()
    val documentRequirementId: UUID = UUID.randomUUID()
    val documentRevisionId: UUID = UUID.randomUUID()
    val attestationRequirementId: UUID = UUID.randomUUID()
    val attestationRevisionId: UUID = UUID.randomUUID()
    val contributorPartyId: UUID = UUID.randomUUID()
    val attestorPartyId: UUID = UUID.randomUUID()
    val contributorUserId: UUID = UUID.randomUUID()
    val attestorUserId: UUID = UUID.randomUUID()
    val documentResponseId: UUID = UUID.randomUUID()
    val documentId: UUID = UUID.randomUUID()
    val documentVersionId: UUID = UUID.randomUUID()
    val artifactId: UUID = UUID.randomUUID()
    val evidenceVersionId: UUID = UUID.randomUUID()
    val inspectionId: UUID = UUID.randomUUID()
    val supportingLinkTemplateId: UUID = UUID.randomUUID()
    val confirmationSectionId: UUID = UUID.randomUUID()

    init
    {
        val now = template.now
        val attestationSectionId = if (staged)
        {
            execute(
                connection,
                "UPDATE information_request_template_version SET submission_mode = 'STAGED', submission_stage_ordering = ? WHERE id = ?",
                if (sequential) "SEQUENTIAL" else "ANY_ORDER",
                template.versionId,
            )
            execute(
                connection,
                "UPDATE information_request_template_section SET submission_stage_key = 'record-stage' WHERE id = ?",
                template.sectionId,
            )
            template.insertSection(confirmationSectionId, template.versionId, "confirmations", 2, "confirmation-stage")
            confirmationSectionId
        }
        else template.sectionId
        template.insertRequirement(documentTemplateRequirementId, "supporting-record", "DOCUMENT")
        template.insertBinding(documentBindingId, template.versionId, documentTemplateRequirementId, template.sectionId, 1)
        template.insertEvidencePolicy(UUID.randomUUID(), documentBindingId, template.versionId)
        template.insertDisposition(documentBindingId, template.versionId, "PROVIDED")
        template.insertRequirement(attestationTemplateRequirementId, "recorded-assertion", "RESPONSE_ATTESTATION")
        template.insertBinding(
            attestationBindingId,
            template.versionId,
            attestationTemplateRequirementId,
            attestationSectionId,
            2,
            "ATTESTOR",
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_binding_evidence_link
                (id, template_binding_id, supporting_template_binding_id, template_version_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            supportingLinkTemplateId,
            attestationBindingId,
            documentBindingId,
            template.versionId,
        )
        template.recordDerivedCapabilities(template.versionId)
        template.publish(template.versionId)

        listOf(contributorUserId, attestorUserId).forEach(::insertUser)
        execute(
            connection,
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                    'Process collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
            exchangeId,
            template.organizationId,
            template.userId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, ?, ?, ?)
            """.trimIndent(),
            requestId,
            exchangeId,
            template.versionId,
            template.organizationId,
            now,
            now,
            now,
        )
        insertParty(contributorPartyId, "CONTRIBUTOR", contributorUserId)
        insertParty(attestorPartyId, "ATTESTOR", attestorUserId)
        insertRuntimeRequirement(documentRequirementId, documentRevisionId, documentTemplateRequirementId, documentBindingId)
        insertRuntimeRequirement(
            attestationRequirementId,
            attestationRevisionId,
            attestationTemplateRequirementId,
            attestationBindingId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_response
                (id, information_request_id, information_request_requirement_id, requirement_revision_id,
                 occurrence_path, disposition, response_revision, recorded_by_principal_kind,
                 recorded_by_principal_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'root', 'PROVIDED', 2, 'USER', ?, ?, ?)
            """.trimIndent(),
            documentResponseId,
            requestId,
            documentRequirementId,
            documentRevisionId,
            contributorUserId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO document (id, encryption_mode, is_deleted, created_date, update_date, title, type)
            VALUES (?, 0, FALSE, ?, ?, 'Process record', 'PDF')
            """.trimIndent(),
            documentId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO document_version
                (id, document_id, created_by_principal_kind, created_by_principal_id, created_date, file_name,
                 version, storage_provider, storage_locator_kind, storage_locator,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, 'USER', ?, ?, 'process-record.pdf', '1', 'OBJECT_STORE', 'OBJECT_KEY', ?,
                    3, 'SHA_256', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 'VERIFIED')
            """.trimIndent(),
            documentVersionId,
            documentId,
            contributorUserId,
            now,
            "document-versions/$documentVersionId/process-record.pdf",
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_artifact
                (id, information_request_id, information_request_requirement_id, artifact_key, artifact_revision,
                 created_at, updated_at, created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, 'evidence-1', 1, ?, ?, 'USER', ?)
            """.trimIndent(),
            artifactId,
            requestId,
            documentRequirementId,
            now,
            now,
            contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_version
                (id, evidence_artifact_id, information_request_id, version_number, source_kind, document_version_id,
                 created_at, created_by_principal_kind, created_by_principal_id, declared_file_name, declared_media_type)
            VALUES (?, ?, ?, 1, 'DOCUMENT_VERSION', ?, ?, 'USER', ?, 'process-record.pdf', 'application/pdf')
            """.trimIndent(),
            evidenceVersionId,
            artifactId,
            requestId,
            documentVersionId,
            now,
            contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_assessment
                (id, information_request_id, evidence_version_id, assessment_kind, outcome, content_hash_algorithm,
                 content_hash, content_length, assessed_at, detected_media_type, page_count)
            VALUES (?, ?, ?, 'CONTENT_INSPECTION', 'INSPECTED', 'SHA_256',
                    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 3, ?, 'application/pdf', 1)
            """.trimIndent(),
            inspectionId,
            requestId,
            evidenceVersionId,
            now,
        )
    }

    fun publishNextVersion(
        number: Int = 2,
        keepAttestation: Boolean = true,
        adjust: (NextSubmissionVersion) -> Unit = {},
    ): NextSubmissionVersion
    {
        val next = NextSubmissionVersion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        template.insertVersion(next.versionId, number)
        template.insertSectionBeforeStages(next.sectionId, next.versionId, "records", 1)
        template.insertBinding(next.documentBindingId, next.versionId, documentTemplateRequirementId, next.sectionId, 1)
        template.insertEvidencePolicy(UUID.randomUUID(), next.documentBindingId, next.versionId)
        template.insertDisposition(next.documentBindingId, next.versionId, "PROVIDED")
        if (keepAttestation)
        {
            template.insertBinding(
                next.attestationBindingId,
                next.versionId,
                attestationTemplateRequirementId,
                next.sectionId,
                2,
                "ATTESTOR",
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_binding_evidence_link
                    (id, template_binding_id, supporting_template_binding_id, template_version_id)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                next.attestationBindingId,
                next.documentBindingId,
                next.versionId,
            )
        }
        adjust(next)
        template.recordDerivedCapabilities(next.versionId)
        template.publish(next.versionId)
        return next
    }

    fun insertUser(id: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
            id,
            template.now,
            "submission-party-${id.toString().take(8)}@process.test",
        )
    }

    private fun insertParty(id: UUID, role: String, userId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, ?, 'USER', ?, TRUE, 1, ?, ?, ?)
            """.trimIndent(),
            id,
            requestId,
            role,
            userId,
            template.now,
            template.now,
            template.now,
        )
    }

    private fun insertRuntimeRequirement(id: UUID, revisionId: UUID, templateRequirementId: UUID, bindingId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path, created_at)
            VALUES (?, ?, ?, ?, ?, 'root', ?)
            """.trimIndent(),
            id,
            requestId,
            template.versionId,
            templateRequirementId,
            bindingId,
            template.now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_revision
                (id, information_request_requirement_id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                 effective_from, configuration_hash_sha256, optimistic_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, 'root', ?, ?, 1, ?)
            """.trimIndent(),
            revisionId,
            id,
            requestId,
            template.versionId,
            templateRequirementId,
            bindingId,
            template.now,
            "0".repeat(64),
            template.now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_current
                (information_request_requirement_id, current_revision_id, current_revision_number, updated_at)
            VALUES (?, ?, 1, ?)
            """.trimIndent(),
            id,
            revisionId,
            template.now,
        )
    }

    @Suppress("LongParameterList")
    fun insertPackage(
        id: UUID,
        number: Int,
        stageKey: String? = null,
        templateVersionId: UUID = template.versionId,
        previousPackageId: UUID? = null,
        reviewRequired: Boolean = false,
        completesRequest: Boolean = true,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_submission_package
                (id, information_request_id, package_number, stage_key, template_version_id, content_hash_sha256,
                 manifest_hash_sha256, review_required, completes_request, previous_package_id,
                 submitted_by_principal_kind, submitted_by_principal_id, submitted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USER', ?, ?)
            """.trimIndent(),
            id,
            requestId,
            number,
            stageKey,
            templateVersionId,
            "a".repeat(64),
            "b".repeat(64),
            reviewRequired,
            completesRequest,
            previousPackageId,
            contributorUserId,
            template.now,
        )
    }

    fun insertItem(
        id: UUID,
        packageId: UUID,
        requirementId: UUID,
        revisionId: UUID,
        bindingId: UUID,
        requirementType: String,
        responseId: UUID? = null,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_submission_item
                (id, package_id, information_request_id, information_request_requirement_id, requirement_revision_id,
                 template_binding_id, requirement_key, requirement_type, occurrence_path, completeness_state,
                 disposition, response_id, response_revision, responded_by_principal_kind, responded_by_principal_id,
                 item_hash_sha256)
            VALUES (?, ?, ?, ?, ?, ?, 'recorded-item', ?, 'root', 'COMPLETE', 'PROVIDED', ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            packageId,
            requestId,
            requirementId,
            revisionId,
            bindingId,
            requirementType,
            responseId,
            responseId?.let { 2L },
            responseId?.let { "USER" },
            responseId?.let { contributorUserId },
            "c".repeat(64),
        )
    }

    fun insertEvidenceMember(id: UUID, packageId: UUID, itemId: UUID, artifact: UUID = artifactId, version: UUID = evidenceVersionId)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_submission_evidence
                (id, package_id, item_id, information_request_id, evidence_artifact_id, evidence_version_id,
                 evidence_version_number, document_version_id, content_hash_algorithm, content_hash, content_length,
                 content_verification, conformance, inspection_assessment_id)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?, 'SHA_256',
                    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 3, 'VERIFIED', 'CONFORMING', ?)
            """.trimIndent(),
            id,
            packageId,
            itemId,
            requestId,
            artifact,
            version,
            documentVersionId,
            inspectionId,
        )
    }

    @Suppress("LongParameterList")
    fun insertAttestation(
        id: UUID,
        sequence: Int,
        decision: String = "ASSENTED",
        refusalReason: String? = null,
        partyId: UUID = attestorPartyId,
        requirementId: UUID = attestationRequirementId,
        revisionId: UUID = attestationRevisionId,
        expiresAfterAttestation: Boolean = true,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_submission_attestation
                (id, information_request_id, attestation_requirement_id, requirement_revision_id, party_id,
                 party_role, principal_kind, principal_id, decision, refusal_reason, authentication_strength,
                 attested_content_hash_sha256, statement_hash_sha256, policy_hash_sha256, attested_at, expires_at,
                 sequence_number)
            VALUES (?, ?, ?, ?, ?, 'ATTESTOR', 'USER', ?, ?, ?, 'ACCOUNT_SIGN_IN', ?, ?, ?, ?,
                    ?::timestamptz + (CASE WHEN ? THEN INTERVAL '1 hour' ELSE INTERVAL '-1 hour' END), ?)
            """.trimIndent(),
            id,
            requestId,
            requirementId,
            revisionId,
            partyId,
            attestorUserId,
            decision,
            refusalReason,
            "a".repeat(64),
            "d".repeat(64),
            "e".repeat(64),
            template.now,
            template.now,
            expiresAfterAttestation,
            sequence,
        )
    }
}

internal data class NextSubmissionVersion(
    val versionId: UUID,
    val sectionId: UUID,
    val documentBindingId: UUID,
    val attestationBindingId: UUID,
)
