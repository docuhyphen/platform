import {getAuditEventTypeLabel} from "./auditEventTypeLabels.ts";
import {AuditEventDto} from "../models/models.tsx";

const actorRoleLabels: Record<string, string> = {
    APP_ADMIN: "Application admin",
    APP_USER: "Application user",
    MEMBER: "Member",
    ORG_ADMIN: "Organization admin",
    OWNER: "Owner",
    PARTICIPANT: "Participant",
    REVIEWER: "Reviewer",
    SYSTEM: "System",
    USER: "User",
};

const outcomeLabels: Record<string, string> = {
    DENIED: "Denied",
    FAILURE: "Failed",
    PENDING: "Pending",
    SUCCESS: "Successful",
};

const payloadKeyLabels: Record<string, string> = {
    document_id: "Document",
    document_ids: "Documents",
    document_title: "Document title",
    exchange_id: "Exchange",
    exchange_name: "Exchange name",
    organization_id: "Organization",
    user_id: "User",
};

const entityPayloadValueLabels: Record<string, string> = {
    document_id: "Document record",
    document_ids: "Document records",
    exchange_id: "Exchange record",
    organization_id: "Organization record",
    user_id: "User record",
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const humanizeToken = (value: string): string =>
    value
        .split(/[._\s-]+/)
        .filter(Boolean)
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
        .join(" ");

export const formatAuditActorRole = (role: string): string =>
    actorRoleLabels[role] ?? humanizeToken(role);

export const formatAuditOutcome = (outcome: string): string =>
    outcomeLabels[outcome] ?? humanizeToken(outcome);

export const formatAuditPayloadKey = (key: string): string =>
    payloadKeyLabels[key] ?? humanizeToken(key);

export const getAuditPayloadEntries = (event: AuditEventDto): [string, string][] =>
    Object.entries(event.payload ?? {}).filter(([key]) => key !== "exchange_name");

const payloadKeyMatchesTarget = (key: string, targetType?: string | null): boolean =>
    Boolean(targetType) && key === `${targetType?.toLowerCase()}_id`;

export const formatAuditPayloadValue = (key: string, value: string, event: AuditEventDto): string =>
{
    if (payloadKeyMatchesTarget(key, event.targetType) && event.targetLabel)
    {
        return event.targetLabel;
    }

    if (key === "organization_id" && event.organizationLabel)
    {
        return event.organizationLabel;
    }

    if (key === "exchange_id" && event.payload?.exchange_name)
    {
        return event.payload.exchange_name;
    }

    if (uuidPattern.test(value) && (key.endsWith("_id") || key.endsWith("_ids")))
    {
        return entityPayloadValueLabels[key] ?? `${formatAuditPayloadKey(key)} record`;
    }

    if (key.endsWith("_ids") && value.split(",").every((item) => uuidPattern.test(item.trim())))
    {
        return entityPayloadValueLabels[key] ?? `${formatAuditPayloadKey(key)} records`;
    }

    if (/^[A-Z][A-Z0-9_]+$/.test(value))
    {
        return humanizeToken(value);
    }

    return value;
};

export const formatAuditActor = (actorKind: string, actorLabel?: string, actorRole?: string): string =>
{
    const actor = actorLabel ?? formatAuditActorRole(actorKind);
    return actorRole ? `${actor} - ${formatAuditActorRole(actorRole)}` : actor;
};

export const formatAuditEntityLabel = (label?: string, type?: string | null): string =>
    label ?? (type ? humanizeToken(type) : "-");

export const formatAuditEventType = (eventTypeKey: string): string =>
    getAuditEventTypeLabel(eventTypeKey);
