-- Drop the unused channel_overrides_json column from communication.
--
-- This column was added as a speculative "future use" placeholder for per-channel
-- subject/body overrides but was never read, written, or parsed by any code. When
-- per-channel overrides are actually implemented they will use a normalized
-- communication_channel_override (communication_id, channel, subject, body) table
-- keyed by the NotificationChannelType enum, not a JSON blob.
ALTER TABLE communication
    DROP COLUMN channel_overrides_json;
