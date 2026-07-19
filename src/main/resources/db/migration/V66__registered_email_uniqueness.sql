DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM app_user
        WHERE is_temporary = FALSE
        GROUP BY LOWER(email)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Registered email uniqueness cannot be enforced while duplicate registered accounts exist';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_app_user_registered_email_ci
    ON app_user (LOWER(email))
    WHERE is_temporary = FALSE;
