package com.docuhyphen.app.api.repository.informationrequest.disposal

import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalObjectCandidate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.util.UUID

@ApplicationScoped
class InformationRequestDisposalRepository @Inject constructor(
    private val entityManager: EntityManager,
)
{
    fun liveReferences(requestId: UUID): List<String> =
        entityManager.createNativeQuery(
            """
            SELECT 'LINEAGE_SUCCESSOR'
            WHERE EXISTS (SELECT 1 FROM information_request_lineage lineage
                          WHERE lineage.source_request_id = :request AND lineage.successor_request_id <> :request)
            UNION ALL
            SELECT 'RECURRENCE_SERIES'
            WHERE EXISTS (SELECT 1 FROM information_request_lineage lineage
                                   JOIN information_request_recurrence recurrence ON recurrence.id = lineage.recurrence_id
                          WHERE recurrence.origin_request_id = :request AND lineage.successor_request_id <> :request)
            UNION ALL
            SELECT 'SUPERSEDED_REQUEST'
            WHERE EXISTS (SELECT 1 FROM information_request superseded
                          WHERE superseded.superseded_by_request_id = :request AND superseded.id <> :request)
            UNION ALL
            SELECT 'CARRIED_FORWARD'
            WHERE EXISTS (SELECT 1 FROM information_request_carry_forward carried
                                   JOIN information_request_submission_item item ON item.id = carried.source_item_id
                          WHERE item.information_request_id = :request AND carried.information_request_id <> :request)
            UNION ALL
            SELECT 'ACCEPTED_FACT_SUCCESSOR'
            WHERE EXISTS (SELECT 1 FROM information_request_accepted_fact later
                                   JOIN information_request_accepted_fact own
                                        ON own.id = later.supersedes_fact_id OR own.id = later.conflicting_fact_id
                          WHERE own.source_information_request_id = :request
                            AND later.source_information_request_id <> :request)
            UNION ALL
            SELECT 'ASSESSMENT_REUSE'
            WHERE EXISTS (SELECT 1 FROM information_request_evidence_assessment reused
                                   JOIN information_request_evidence_assessment own ON own.id = reused.reused_assessment_id
                          WHERE own.information_request_id = :request AND reused.information_request_id <> :request)
            UNION ALL
            SELECT 'ACCOUNT_LINK'
            WHERE EXISTS (SELECT 1 FROM participant_account_link link WHERE link.linked_via_information_request_id = :request)
            UNION ALL
            SELECT 'TEMPLATE_IN_USE'
            WHERE EXISTS (SELECT 1
                          FROM information_request_template_definition definition
                                   JOIN information_request_template_version template_version
                                        ON template_version.template_definition_id = definition.id
                          WHERE definition.origin_request_id = :request
                            AND (EXISTS (SELECT 1 FROM information_request other
                                         WHERE other.template_version_id = template_version.id AND other.id <> :request)
                              OR EXISTS (SELECT 1 FROM blueprint_definition blueprint
                                         WHERE blueprint.information_request_template_version_id = template_version.id)
                              OR EXISTS (SELECT 1 FROM information_request_submission_package submitted_package
                                         WHERE submitted_package.template_version_id = template_version.id
                                           AND submitted_package.information_request_id <> :request)
                              OR EXISTS (SELECT 1 FROM information_request_review review
                                         WHERE review.template_version_id = template_version.id
                                           AND review.information_request_id <> :request)
                              OR EXISTS (SELECT 1 FROM information_request_amendment amendment
                                         WHERE (amendment.from_template_version_id = template_version.id
                                             OR amendment.to_template_version_id = template_version.id)
                                           AND amendment.information_request_id <> :request)))
            """.trimIndent(),
        )
            .setParameter("request", requestId)
            .resultList
            .map { it.toString() }

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
