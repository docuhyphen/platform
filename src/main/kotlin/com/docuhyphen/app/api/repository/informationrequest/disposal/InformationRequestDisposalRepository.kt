package com.docuhyphen.app.api.repository.informationrequest.disposal

import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalReference
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalObjectCandidate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.util.*

@ApplicationScoped
class InformationRequestDisposalRepository @Inject constructor(
    private val entityManager: EntityManager,
)
{
    fun references(requestId: UUID): List<InformationRequestDisposalReference> =
        entityManager.createNativeQuery(
            """
            SELECT 'LINEAGE_SUCCESSOR', lineage.successor_request_id
            FROM information_request_lineage lineage
            WHERE lineage.source_request_id = :request AND lineage.successor_request_id <> :request
            UNION
            SELECT 'RECURRENCE_SERIES', lineage.successor_request_id
            FROM information_request_lineage lineage
                     JOIN information_request_recurrence recurrence ON recurrence.id = lineage.recurrence_id
            WHERE recurrence.origin_request_id = :request AND lineage.successor_request_id <> :request
            UNION
            SELECT 'CARRIED_FORWARD', carried.information_request_id
            FROM information_request_carry_forward carried
                     JOIN information_request_submission_item item ON item.id = carried.source_item_id
            WHERE item.information_request_id = :request AND carried.information_request_id <> :request
            UNION
            SELECT 'ACCEPTED_FACT_SUCCESSOR', later.source_information_request_id
            FROM information_request_accepted_fact later
                     JOIN information_request_accepted_fact own
                          ON own.id = later.supersedes_fact_id OR own.id = later.conflicting_fact_id
            WHERE own.source_information_request_id = :request AND later.source_information_request_id <> :request
            UNION
            SELECT 'FACT_RECERTIFICATION', recertification.information_request_id
            FROM information_request_fact_recertification recertification
            WHERE recertification.source_information_request_id = :request
              AND recertification.information_request_id <> :request
            UNION
            SELECT 'ASSESSMENT_REUSE', reused.information_request_id
            FROM information_request_evidence_assessment reused
                     JOIN information_request_evidence_assessment own ON own.id = reused.reused_assessment_id
            WHERE own.information_request_id = :request AND reused.information_request_id <> :request
            UNION
            SELECT 'TEMPLATE_IN_USE', template_user.request_id
            FROM information_request_template_definition definition
                     JOIN information_request_template_version template_version
                          ON template_version.template_definition_id = definition.id
                     JOIN (SELECT other.template_version_id, other.id AS request_id
                           FROM information_request other
                           UNION ALL
                           SELECT blueprint.information_request_template_version_id, CAST(NULL AS uuid)
                           FROM blueprint_definition blueprint
                           UNION ALL
                           SELECT submitted_package.template_version_id, submitted_package.information_request_id
                           FROM information_request_submission_package submitted_package
                           UNION ALL
                           SELECT review.template_version_id, review.information_request_id
                           FROM information_request_review review
                           UNION ALL
                           SELECT amendment.from_template_version_id, amendment.information_request_id
                           FROM information_request_amendment amendment
                           UNION ALL
                           SELECT amendment.to_template_version_id, amendment.information_request_id
                           FROM information_request_amendment amendment) template_user
                          ON template_user.template_version_id = template_version.id
            WHERE definition.origin_request_id = :request
              AND template_user.request_id IS DISTINCT FROM :request
            """.trimIndent(),
        )
            .setParameter("request", requestId)
            .resultList
            .map { row ->
                val columns = row as Array<*>
                InformationRequestDisposalReference(columns[0].toString(), columns[1] as UUID?)
            }

    fun storedObjects(requestId: UUID): List<RecordDisposalObjectCandidate> =
        entityManager.createNativeQuery(
            """
            SELECT stored_version.document_id,
                   stored_version.id,
                   stored_version.storage_provider,
                   stored_version.storage_locator_kind,
                   stored_version.storage_locator,
                   CASE
                       WHEN EXISTS (SELECT 1 FROM information_request_evidence_version other
                                    WHERE other.document_version_id = stored_version.id AND other.information_request_id <> :request)
                           OR EXISTS (SELECT 1 FROM information_request_submission_evidence other
                                      WHERE other.document_version_id = stored_version.id AND other.information_request_id <> :request)
                           OR EXISTS (SELECT 1 FROM information_request_fact_recertification_evidence reused
                                               JOIN information_request_fact_recertification recertification
                                                    ON recertification.id = reused.recertification_id
                                               JOIN information_request_evidence_version reused_version
                                                    ON reused_version.id = reused.evidence_version_id
                                      WHERE reused_version.document_version_id = stored_version.id
                                        AND recertification.information_request_id <> :request)
                           OR EXISTS (SELECT 1 FROM document_comment document_note
                                      WHERE document_note.document_version_id = stored_version.id OR document_note.document_id = stored_version.document_id)
                           OR EXISTS (SELECT 1 FROM exchange_document linked WHERE linked.documents_id = stored_version.document_id)
                           THEN 'SHARED_REFERENCE'
                       END
            FROM document_version stored_version
            WHERE stored_version.id IN (SELECT evidence.document_version_id
                                 FROM information_request_evidence_version evidence
                                 WHERE evidence.information_request_id = :request
                                   AND evidence.document_version_id IS NOT NULL
                                 UNION
                                 SELECT submitted.document_version_id
                                 FROM information_request_submission_evidence submitted
                                 WHERE submitted.information_request_id = :request
                                   AND submitted.document_version_id IS NOT NULL)
            ORDER BY stored_version.id
            """.trimIndent(),
        )
            .setParameter("request", requestId)
            .resultList
            .map { row ->
                val columns = row as Array<*>
                RecordDisposalObjectCandidate(
                    documentId = columns[0] as UUID,
                    documentVersionId = columns[1] as UUID,
                    storageProvider = columns[2] as String?,
                    storageLocatorKind = columns[3] as String?,
                    storageLocator = columns[4] as String?,
                    retainedReason = columns[5] as String?,
                )
            }

    fun ownedRecords(requestId: UUID): List<Pair<String, String>> =
        entityManager.createNativeQuery(
            "SELECT resource_type, resource_id FROM record_preservation_information_request_records(:request)",
        )
            .setParameter("request", requestId)
            .resultList
            .map { row ->
                val columns = row as Array<*>
                columns[0].toString() to columns[1].toString()
            }

    fun exportCoSources(requestId: UUID): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT DISTINCT other.information_request_id
            FROM information_request_record_export_source own
                     JOIN information_request_record_export_source other ON other.export_id = own.export_id
            WHERE own.information_request_id = :request
              AND other.information_request_id <> :request
            ORDER BY other.information_request_id
            """.trimIndent(),
        )
            .setParameter("request", requestId)
            .resultList
            .map { it as UUID }

    fun lockClaimBoundary(requestId: UUID) {
        entityManager.createNativeQuery("SELECT record_disposal_lock_information_request_objects(:request)")
            .setParameter("request", requestId)
            .singleResult
    }

    fun subjectsOf(requestId: UUID): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT DISTINCT party.subject_identity_ref_id
            FROM information_request_party party
            WHERE party.information_request_id = :request
              AND party.subject_identity_ref_id IS NOT NULL
            """.trimIndent(),
        )
            .setParameter("request", requestId)
            .resultList
            .map { it as UUID }
}
