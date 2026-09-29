package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import java.sql.Connection
import java.util.UUID

internal object ConformanceEvidenceSql
{
    fun insertConformingFile(connection: Connection, requestId: UUID, requirementId: UUID, creatorId: UUID, artifactKey: String = "evidence-1"): UUID
    {
        val documentId = UUID.randomUUID()
        val documentVersionId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
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
            creatorId,
            "document-versions/$documentVersionId/process-record.pdf",
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_artifact
                (id, information_request_id, information_request_requirement_id, artifact_key, artifact_revision,
                 created_at, updated_at, created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, ?, 1, now(), now(), 'USER', ?)
            """.trimIndent(),
            artifactId,
            requestId,
            requirementId,
            artifactKey,
            creatorId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_version
                (id, evidence_artifact_id, information_request_id, version_number, source_kind, document_version_id,
                 created_at, created_by_principal_kind, created_by_principal_id, declared_file_name, declared_media_type)
            VALUES (?, ?, ?, 1, 'DOCUMENT_VERSION', ?, now(), 'USER', ?, 'process-record.pdf', 'application/pdf')
            """.trimIndent(),
            versionId,
            artifactId,
            requestId,
            documentVersionId,
            creatorId,
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
            requestId,
            versionId,
        )
        return versionId
    }
}
