-- Tracks whether a user has completed (or dismissed) the new-user feature tour.
-- Defaults to FALSE so existing users without a settings row are treated as new.
-- The application will flip this to TRUE once the user finishes or skips the tour.

ALTER TABLE public.app_user_settings
    ADD COLUMN tour_completed boolean NOT NULL DEFAULT false;

