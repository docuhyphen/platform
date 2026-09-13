import type {BadgeProps} from "@fluentui/react-components";
import {makeStyles} from "@fluentui/react-components";

export const useAuditCategoryBadgeStyles = makeStyles({
    badge: {
        textTransform: "capitalize",
    },
});

export interface AuditCategoryMeta
{
    label: string;
    color: BadgeProps["color"];
}

/**
 * category -> friendly label + Fluent Badge color, one entry per
 * com.docuhyphen.app.api.service.audit.catalog.AuditCategory. Unknown/future categories fall
 * back to the raw string with a neutral color at render time.
 */
export const auditCategoryMetaMap: Record<string, AuditCategoryMeta> = {
    AUTHENTICATION: {label: "Authentication", color: "informative"},
    ADMINISTRATION: {label: "Administration", color: "important"},
    SECURITY: {label: "Security", color: "danger"},
    ORGANIZATION: {label: "Organization", color: "brand"},
    IDENTITY_PROVIDER: {label: "Identity Provider", color: "informative"},
    PLATFORM: {label: "Platform", color: "subtle"},
    SCIM: {label: "SCIM", color: "subtle"},
    DOCUMENT: {label: "Document", color: "success"},
    EXCHANGE: {label: "Exchange", color: "brand"},
    AUTHORIZATION: {label: "Authorization", color: "danger"},
    WORKFLOW: {label: "Workflow", color: "important"},
    FIELD_SCHEMA: {label: "Field / Schema", color: "informative"},
    INFORMATION_REQUEST: {label: "Information Request", color: "brand"},
    ARCHIVE: {label: "Archive", color: "subtle"},
    AUDIT_GOVERNANCE: {label: "Audit Governance", color: "warning"},
};
