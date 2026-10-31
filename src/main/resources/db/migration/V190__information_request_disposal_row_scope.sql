CREATE
OR REPLACE FUNCTION record_disposal_row_in_progress(table_name text, row_data jsonb)
    RETURNS boolean AS
$$
DECLARE
claim_id uuid := NULLIF(current_setting('docuhyphen.record_disposal_claim', true), '')::uuid;
    claimed_request
uuid;
    bound_request
uuid;
    version_id
uuid;
    definition_id
uuid;
BEGIN
SELECT claim.resource_id::uuid
INTO claimed_request
FROM record_disposal_claim claim
WHERE claim.id = claim_id
  AND claim.state = 'OBJECTS_DELETED'
  AND claim.resource_type = 'INFORMATION_REQUEST';
IF
claimed_request IS NULL THEN RETURN FALSE;
END IF;
    bound_request
:= COALESCE(row_data->>'information_request_id', row_data->>'request_id',
        row_data->>'source_information_request_id', row_data->>'origin_request_id')::uuid;
    IF
bound_request IS NOT NULL THEN RETURN bound_request = claimed_request;
END IF;
    IF
table_name = 'information_request' THEN RETURN (row_data->>'id')::uuid = claimed_request;
END IF;
    IF
table_name IN ('information_request_lineage', 'information_request_lineage_source_package') THEN
        RETURN COALESCE(
            claimed_request IN ((row_data->>'source_request_id')::uuid, (row_data->>'successor_request_id')::uuid),
            FALSE);
END IF;
    IF
row_data->>'fact_id' IS NOT NULL THEN
SELECT fact.source_information_request_id
INTO bound_request
FROM information_request_accepted_fact fact
WHERE fact.id = (row_data ->>'fact_id')::uuid;
ELSIF
row_data->>'recertification_id' IS NOT NULL THEN
SELECT recertification.information_request_id
INTO bound_request
FROM information_request_fact_recertification recertification
WHERE recertification.id = (row_data ->>'recertification_id')::uuid;
ELSIF
row_data->>'outbound_notice_id' IS NOT NULL THEN
SELECT notice.information_request_id
INTO bound_request
FROM information_request_outbound_notice notice
WHERE notice.id = (row_data ->>'outbound_notice_id')::uuid;
ELSIF
row_data->>'review_id' IS NOT NULL THEN
SELECT review.information_request_id
INTO bound_request
FROM information_request_review review
WHERE review.id = (row_data ->>'review_id')::uuid;
END IF;
    IF
bound_request IS NOT NULL THEN RETURN bound_request = claimed_request;
END IF;
    version_id
:= (row_data->>'template_version_id')::uuid;
    definition_id
:= (row_data->>'template_definition_id')::uuid;
    IF
table_name = 'information_request_template_version' THEN version_id := (row_data->>'id')::uuid;
END IF;
    IF
table_name = 'information_request_template_definition' THEN definition_id := (row_data->>'id')::uuid;
END IF;
    IF
version_id IS NOT NULL THEN
SELECT version.template_definition_id
INTO definition_id
FROM information_request_template_version version
WHERE version.id = version_id;
END IF;
    IF
definition_id IS NOT NULL THEN
SELECT definition.origin_request_id
INTO bound_request
FROM information_request_template_definition definition
WHERE definition.id = definition_id;
END IF;
RETURN COALESCE(bound_request = claimed_request, FALSE);
END;
$$
LANGUAGE plpgsql;
