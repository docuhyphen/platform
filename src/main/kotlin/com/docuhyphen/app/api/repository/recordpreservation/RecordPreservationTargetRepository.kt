package com.docuhyphen.app.api.repository.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.util.*

@ApplicationScoped
class RecordPreservationTargetRepository @Inject constructor(
    private val entityManager: EntityManager,
) {
    fun belongsTo(owner: RecordOwnerRef, resourceType: String, resourceId: UUID): Boolean {
        val ownerId = owner.id ?: return false
        val statement = statementFor(owner.kind, resourceType) ?: return false
        return entityManager.createNativeQuery(statement)
            .setParameter("id", resourceId)
            .setParameter("owner", ownerId)
            .singleResult as Boolean
    }

    private fun statementFor(kind: RecordOwnerKind, resourceType: String): String? {
        val request = requestOwned(kind) ?: return null
        val exchange = exchangeOwned(kind) ?: return null
        return when (resourceType) {
            RecordPreservationResourceTypes.INFORMATION_REQUEST ->
                "SELECT EXISTS (SELECT 1 FROM information_request request WHERE request.id = :id AND $request)"

            RecordPreservationResourceTypes.EXCHANGE ->
                "SELECT EXISTS (SELECT 1 FROM exchange exchange WHERE exchange.id = :id AND $exchange)"

            RecordPreservationResourceTypes.SUBJECT_IDENTITY ->
                """
                SELECT EXISTS (SELECT 1 FROM subject_identity_ref subject
                               WHERE subject.id = :id AND subject.owner_type = '${kind.name}' AND subject.owner_id = :owner)
                """.trimIndent()

            RecordPreservationResourceTypes.ORGANIZATION ->
                if (kind == RecordOwnerKind.ORGANIZATION) "SELECT CAST(:id AS uuid) = CAST(:owner AS uuid)" else null

            RecordPreservationResourceTypes.APP_USER -> when (kind) {
                RecordOwnerKind.USER -> "SELECT CAST(:id AS uuid) = CAST(:owner AS uuid)"
                RecordOwnerKind.ORGANIZATION ->
                    """
                    SELECT EXISTS (SELECT 1 FROM organization_membership membership
                                   WHERE membership.app_user_id = :id AND membership.organization_id = :owner)
                    """.trimIndent()

                RecordOwnerKind.PLATFORM -> null
            }

            RecordPreservationResourceTypes.INFORMATION_REQUEST_SUBMISSION_PACKAGE ->
                requestRecord("information_request_submission_package", request)

            RecordPreservationResourceTypes.INFORMATION_REQUEST_EVIDENCE_ARTIFACT ->
                requestRecord("information_request_evidence_artifact", request)

            RecordPreservationResourceTypes.INFORMATION_REQUEST_EVIDENCE_VERSION ->
                requestRecord("information_request_evidence_version", request)

            RecordPreservationResourceTypes.INFORMATION_REQUEST_OUTBOUND_NOTICE ->
                requestRecord("information_request_outbound_notice", request)

            RecordPreservationResourceTypes.INFORMATION_REQUEST_RECORD_EXPORT ->
                """
                SELECT EXISTS (SELECT 1 FROM information_request_record_export export
                               WHERE export.id = :id AND export.owner_kind = '${kind.name}' AND export.owner_id = :owner)
                """.trimIndent()

            RecordPreservationResourceTypes.DOCUMENT ->
                """
                SELECT EXISTS (SELECT 1 FROM document document
                               WHERE document.id = :id
                                 AND (EXISTS (SELECT 1 FROM exchange_document link JOIN exchange exchange ON exchange.id = link.exchange_id
                                              WHERE link.documents_id = document.id AND $exchange)
                                   OR EXISTS (SELECT 1 FROM document_version version
                                                       JOIN information_request_evidence_version evidence ON evidence.document_version_id = version.id
                                                       JOIN information_request request ON request.id = evidence.information_request_id
                                              WHERE version.document_id = document.id AND $request)))
                """.trimIndent()

            RecordPreservationResourceTypes.DOCUMENT_VERSION ->
                """
                SELECT EXISTS (SELECT 1 FROM document_version version
                               WHERE version.id = :id
                                 AND (EXISTS (SELECT 1 FROM exchange_document link JOIN exchange exchange ON exchange.id = link.exchange_id
                                              WHERE link.documents_id = version.document_id AND $exchange)
                                   OR EXISTS (SELECT 1 FROM information_request_evidence_version evidence
                                                       JOIN information_request request ON request.id = evidence.information_request_id
                                              WHERE evidence.document_version_id = version.id AND $request)))
                """.trimIndent()

            else -> null
        }
    }

    private fun requestRecord(table: String, request: String): String =
        """
        SELECT EXISTS (SELECT 1 FROM $table record JOIN information_request request ON request.id = record.information_request_id
                       WHERE record.id = :id AND $request)
        """.trimIndent()

    private fun requestOwned(kind: RecordOwnerKind): String? = when (kind) {
        RecordOwnerKind.ORGANIZATION -> "request.owner_type = 'ORGANIZATION' AND request.owner_organization_id = :owner"
        RecordOwnerKind.USER -> "request.owner_type = 'USER' AND request.owner_user_id = :owner"
        RecordOwnerKind.PLATFORM -> null
    }

    private fun exchangeOwned(kind: RecordOwnerKind): String? = when (kind) {
        RecordOwnerKind.ORGANIZATION -> "exchange.owner_organization_id = :owner"
        RecordOwnerKind.USER -> "exchange.owner_organization_id IS NULL AND exchange.owner_user_id = :owner"
        RecordOwnerKind.PLATFORM -> null
    }
}
