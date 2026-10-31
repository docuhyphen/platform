UPDATE information_request_requirement_revision revision
SET effective_to = GREATEST(revision.effective_from, removal.removed_at) FROM (SELECT removed.requirement_id, MIN(removed.removed_at) AS removed_at
      FROM (SELECT requirement.id AS requirement_id, MAX(amendment.amended_at) AS removed_at
            FROM information_request_requirement requirement
                     JOIN information_request request ON request.id = requirement.information_request_id
                     JOIN information_request_amendment_change change
                          ON change.information_request_id = requirement.information_request_id
                              AND change.template_requirement_id = requirement.source_template_requirement_id
                              AND change.change_kind = 'REMOVED'
                     JOIN information_request_amendment amendment ON amendment.id = change.amendment_id
            WHERE NOT EXISTS (SELECT 1
                              FROM information_request_template_requirement_binding binding
                              WHERE binding.template_version_id = request.template_version_id
                                AND binding.template_requirement_id = requirement.source_template_requirement_id)
            GROUP BY requirement.id
            UNION ALL
            SELECT requirement.id, occurrence.removed_at
            FROM information_request_requirement requirement
                     JOIN information_request_group_occurrence occurrence
                          ON occurrence.information_request_id = requirement.information_request_id
                              AND occurrence.occurrence_path = requirement.occurrence_path
            WHERE occurrence.removed_at IS NOT NULL) removed
      GROUP BY removed.requirement_id) removal
WHERE revision.information_request_requirement_id = removal.requirement_id
  AND revision.effective_to IS NULL;
