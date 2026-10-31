DROP TABLE schema_field_binding_conflict;

DELETE
FROM field_value fv
WHERE NOT EXISTS (SELECT 1
                  FROM schema_assignment sa
                           JOIN schema_field_binding sfb ON sfb.schema_version_id = sa.schema_version_id
                  WHERE sa.id = fv.schema_assignment_id
                    AND sfb.field_contract_id = fv.field_contract_id);

UPDATE field_value fv
SET schema_field_binding_id = sfb.id FROM schema_assignment sa
JOIN schema_field_binding sfb
ON sfb.schema_version_id = sa.schema_version_id
WHERE sa.id = fv.schema_assignment_id
  AND sfb.field_contract_id = fv.field_contract_id
  AND fv.schema_field_binding_id IS DISTINCT
FROM sfb.id;

ALTER TABLE field_value
    ALTER COLUMN schema_field_binding_id SET NOT NULL;
