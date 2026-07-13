/**
 * Friendly display labels for every `AuditEventType.key` in the backend catalog
 * (com.docuhyphen.app.api.service.audit.catalog.AuditEventType), so the Audit workspace never
 * shows a raw dotted key like "audit.search.performed" to an end user.
 *
 * Keep this in sync with the backend catalog when a key is added, renamed, or removed - there is
 * no runtime coupling (the backend never reads this file), only a documentation-level obligation.
 * [getAuditEventTypeLabel] falls back to a humanized version of the raw key for anything not
 * listed here, so an unmapped or newly-added key never regresses to something worse than the
 * previous "just show the raw key" behavior.
 */
const auditEventTypeLabels: Record<string, string> = {
    // Authentication / session lifecycle.
    "auth.session.request_auth": "Authentication check",
    "auth.sign_in.initiate": "Sign-in started",
    "auth.sign_in.lookup": "Sign-in account lookup",
    "auth.sign_in.completion": "Signed in",
    "auth.sign_out": "Signed out",
    "auth.token.refresh": "Session token refreshed",
    "auth.step_up.initiate": "Step-up verification started",
    "auth.step_up.complete": "Step-up verification completed",
    "auth.step_up.oauth_callback": "Step-up verification via external sign-in",
    "auth.step_up.otp_regenerate": "Step-up verification code resent",
    "auth.oauth.authorize": "External sign-in started",
    "auth.oauth.callback": "External sign-in completed",
    "auth.oauth.link_confirm": "External sign-in linked to account",
    "auth.session.delete_record": "Session record removed",
    "auth.directory.lookup": "Directory search",
    "auth.recipient.resolve": "Recipient identified",

    // Administration.
    "admin.approval.initiate": "Admin approval requested",
    "admin.approval.approve": "Admin approval granted",
    "application.webhook.delivery_failed": "Webhook delivery failed",

    // Security.
    "security.incident.raised": "Security incident raised",

    // Organization.
    "organization.update": "Organization settings updated",
    "organization.app_user.add": "Person added to organization",
    "organization.app_user.update": "Organization member updated",
    "organization.app_user.delete": "Person removed from organization",
    "organization.group.add": "Group created",
    "organization.group.update": "Group updated",
    "organization.group.delete": "Group deleted",
    "organization.link.create": "Organization link requested",
    "organization.link.decide": "Organization link decision made",
    "organization.link.delete": "Organization link removed",
    "organization.share.external_customer": "Shared with an external customer",
    "organization.auth_exchange_policy.update": "Sign-in session policy updated",
    "organization.auth_exchange_policy.view": "Sign-in session policy viewed",
    "organization.membership.role_assign": "Member role assigned",
    "organization.membership.role_remove": "Member role removed",
    "organization.membership.remove": "Member removed from organization",

    // Identity provider.
    "organization.idp_config.create": "Identity provider added",
    "organization.idp_config.update": "Identity provider updated",
    "organization.idp_config.delete": "Identity provider removed",
    "organization.idp_config.list": "Identity providers viewed",
    "organization.idp_secret.activate": "Identity provider secret activated",
    "organization.idp_secret.disable": "Identity provider secret disabled",
    "organization.idp_secret.enable": "Identity provider secret enabled",
    "organization.idp_secret.retire": "Identity provider secret retired",
    "organization.idp_secret.rollback": "Identity provider secret rolled back",
    "organization.idp_secret.rotate": "Identity provider secret rotated",
    "organization.idp_secret.rotation_job": "Identity provider secret rotation ran",
    "organization.idp_secret.rotation_runbook": "Identity provider emergency secret rotation ran",

    // Platform.
    "platform.org_subscription_policy.delete": "Organization subscription policy removed",
    "platform.org_subscription_policy.list": "Organization subscription policies viewed",
    "platform.org_subscription_policy.upsert": "Organization subscription policy updated",
    "platform.org_subscription_policy.view": "Organization subscription policy viewed",

    // SCIM provisioning.
    "scim.user.create": "User provisioned via SCIM",
    "scim.user.deprovision": "User deprovisioned via SCIM",
    "scim.user.patch": "User updated via SCIM",
    "scim.user.replace": "User replaced via SCIM",

    // Document.
    "document.upload": "Document uploaded",
    "document.download": "Document downloaded",
    "document.view": "Document viewed",
    "document.created": "Document created",
    "document.delete": "Document deleted",
    "document.update": "Document updated",
    "document.comment": "Comment added to document",
    "document.version_created": "New document version created",
    "document.preview": "Document previewed",
    "document.version_download": "Document version downloaded",
    "document.zip_export": "Documents exported as ZIP",
    "document.library_download": "Document library file downloaded",
    "document.no_auth_download": "Document downloaded without sign-in",

    // Exchange lifecycle.
    "exchange.lifecycle.rescinded": "Exchange rescinded",
    "exchange.lifecycle.revoke": "Exchange access revoked",
    "exchange.lifecycle.accepted": "Exchange accepted",
    "exchange.lifecycle.rejected": "Exchange rejected",
    "exchange.lifecycle.ended": "Exchange completed",
    "exchange.lifecycle.deleted": "Exchange deleted",

    // Authorization / sharing.
    "authorization.share.grant": "Access granted",
    "authorization.share.activate": "Access activated",
    "authorization.share.role_change": "Access role changed",
    "authorization.share.revoke": "Access revoked",
    "authorization.decision.denied": "Access denied",

    // Workflow definitions.
    "workflow.definition.create": "Workflow created",
    "workflow.definition.update": "Workflow updated",
    "workflow.definition.delete": "Workflow deleted",
    "workflow.definition.publish": "Workflow published",

    // Field / schema definitions.
    "field_schema.field_definition.create": "Field created",
    "field_schema.field_definition.retire": "Field retired",
    "field_schema.schema_definition.create": "Schema created",
    "field_schema.schema_definition.publish": "Schema published",
    "field_schema.schema_definition.retire": "Schema retired",

    // WORM archive.
    "archive.segment.closed": "Audit archive segment closed",
    "archive.integrity.verified": "Audit archive integrity verified",
    "archive.integrity.failed": "Audit archive integrity check failed",

    // Audit governance: engagements, search, exports, integrity, retention/legal hold.
    "audit.engagement.requested": "Audit engagement requested",
    "audit.engagement.approved": "Audit engagement approved",
    "audit.engagement.revoked": "Audit engagement revoked",
    "audit.engagement.expired": "Audit engagement expired",
    "audit.search.performed": "Audit trail searched",
    "audit.event.viewed": "Audit event viewed",
    "audit.access.denied": "Audit access denied",
    "audit.export.requested": "Audit export requested",
    "audit.export.approved": "Audit export approved",
    "audit.export.building": "Audit export in progress",
    "audit.export.ready": "Audit export ready",
    "audit.export.failed": "Audit export failed",
    "audit.export.downloaded": "Audit export downloaded",
    "audit.export.expired": "Audit export expired",
    "audit.export.revoked": "Audit export revoked",
    "audit.integrity.check_performed": "Audit integrity check performed",
    "audit.retention_policy.updated": "Audit retention policy updated",
    "audit.legal_hold.placed": "Legal hold placed",
    "audit.legal_hold.released": "Legal hold released",
    "audit.identity_key.shredded": "Identity data permanently deleted",
    "audit.analytics.reconciled": "Audit analytics reconciled",
};

/** Turns an unmapped dotted/underscored key into "Title Case Words" as a last-resort fallback. */
const humanizeEventTypeKey = (key: string): string =>
    key
        .split(/[._]/)
        .filter(Boolean)
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");

export const getAuditEventTypeLabel = (eventTypeKey: string): string =>
    auditEventTypeLabels[eventTypeKey] ?? humanizeEventTypeKey(eventTypeKey);
