-- Adds a durable build-claim lease and an optimistic-lock version to audit_export.
--
-- The lease (build_worker_id, build_lease_expires_at) lets more than one application node run the
-- export-build scheduler concurrently: a node may only build an export while it holds an unexpired
-- lease on it, claimed through a locked conditional update, so two nodes never perform the archive
-- I/O for the same export at once, and a node that dies mid-build does not block the export
-- forever - its lease simply expires and another node reclaims it. The lease columns are only
-- meaningful while status = 'BUILDING'.
--
-- version is a standard JPA optimistic-lock column: every update to the row also bumps it, so a
-- transaction that read a stale row - for example one racing a revoke against a completed build -
-- fails to commit instead of silently overwriting a newer READY/REVOKED/EXPIRED/FAILED state.
ALTER TABLE audit_export
    ADD COLUMN build_worker_id VARCHAR(128),
    ADD COLUMN build_lease_expires_at TIMESTAMP,
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_audit_export_build_lease ON audit_export (status, build_lease_expires_at);
