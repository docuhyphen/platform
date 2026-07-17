CREATE FUNCTION validate_exchange_recipient_share_binding()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    share_resource_type VARCHAR(32);
    share_resource_id UUID;
    share_source VARCHAR(32);
    share_source_share_id UUID;
    share_role_name VARCHAR(64);
BEGIN
    SELECT resource_type, resource_id, source, source_share_id, role_name
      INTO share_resource_type, share_resource_id, share_source, share_source_share_id, share_role_name
      FROM share
     WHERE id = NEW.direct_share_id;

    IF share_resource_type IS DISTINCT FROM 'EXCHANGE'
        OR share_resource_id IS DISTINCT FROM NEW.exchange_id THEN
        RAISE EXCEPTION 'Exchange recipient Share must belong to the same Exchange'
            USING ERRCODE = '23514';
    END IF;

    IF share_source IS DISTINCT FROM 'DIRECT'
        OR share_source_share_id IS NOT NULL THEN
        RAISE EXCEPTION 'Exchange recipient Share must be a direct Share'
            USING ERRCODE = '23514';
    END IF;

    IF share_role_name = 'OWNER' THEN
        RAISE EXCEPTION 'Exchange recipient Share cannot be an owner Share'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_exchange_recipient_share_binding
BEFORE INSERT OR UPDATE OF exchange_id, direct_share_id
ON exchange_recipient
FOR EACH ROW
EXECUTE FUNCTION validate_exchange_recipient_share_binding();
