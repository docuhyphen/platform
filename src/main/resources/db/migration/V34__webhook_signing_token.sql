-- Rename signing_secret_hash to signing_secret_token.
--
-- The original column stored a BCrypt hash, which is irreversible and cannot be used
-- for HMAC-SHA256 outbound webhook signing. The new column stores the raw Base64 token
-- returned once at registration time so the delivery service can compute signatures.
-- Existing rows become invalid (BCrypt strings are not valid HMAC keys); they must be
-- rotated via the rotateSigningSecret endpoint before webhook delivery will succeed.

ALTER TABLE workflow_webhook_endpoint RENAME COLUMN signing_secret_hash TO signing_secret_token;
