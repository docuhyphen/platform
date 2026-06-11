ALTER TABLE public.mfa_record
    ADD COLUMN IF NOT EXISTS action_description VARCHAR(255);

