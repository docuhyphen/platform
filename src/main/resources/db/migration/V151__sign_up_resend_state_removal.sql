UPDATE sign_up
SET status = 'PENDING'
WHERE status IN ('OTP_LOCKED', 'EXPIRED');

ALTER TABLE sign_up
    DROP CONSTRAINT sign_up_status_check;

ALTER TABLE sign_up
    ADD CONSTRAINT sign_up_status_check CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED_MAX_RETRIES'));

ALTER TABLE sign_up
    DROP COLUMN otp_regeneration_attempts,
    DROP COLUMN last_regeneration_attempt_time;
