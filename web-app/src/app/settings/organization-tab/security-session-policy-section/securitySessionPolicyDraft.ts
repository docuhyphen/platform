import type {OrgAuthSessionPolicyGuardrails, OrgAuthSessionPolicyIdp} from "../../../../services/authApi.ts";

export interface DraftRow
{
    accessTokenExpiryMinutes: string;
    refreshTokenExpiryMinutes: string;
    maxSessionDurationHours: string;
    idleTimeoutMinutes: string;
    saving: boolean;
    error: string | null;
}

export type DraftValueKey =
    | "accessTokenExpiryMinutes"
    | "refreshTokenExpiryMinutes"
    | "maxSessionDurationHours"
    | "idleTimeoutMinutes";

const toStr = (v?: number | null): string => (v === null || v === undefined ? "" : String(v));

export const parseOptionalLong = (raw: string): number | null | undefined =>
{
    const trimmed = raw.trim();
    if (trimmed === "") return null;
    const n = Number(trimmed);
    if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0) return undefined;
    return n;
};

export const extractPolicyErrorMessage = (e: unknown, fallback: string): string =>
{
    if (e && typeof e === "object")
    {
        const obj = e as { errorMessage?: unknown; message?: unknown };
        if (typeof obj.errorMessage === "string" && obj.errorMessage) return obj.errorMessage;
        if (typeof obj.message === "string" && obj.message) return obj.message;
    }
    return fallback;
};

export const buildDraft = (row: OrgAuthSessionPolicyIdp): DraftRow => ({
    accessTokenExpiryMinutes: toStr(row.accessTokenExpiryMinutes),
    refreshTokenExpiryMinutes: toStr(row.refreshTokenExpiryMinutes),
    maxSessionDurationHours: toStr(row.maxSessionDurationHours),
    idleTimeoutMinutes: toStr(row.idleTimeoutMinutes),
    saving: false,
    error: null,
});

export const validateAgainstGuardrails = (
    draft: DraftRow,
    guardrails: OrgAuthSessionPolicyGuardrails,
): string | null =>
{
    const access = parseOptionalLong(draft.accessTokenExpiryMinutes);
    const refresh = parseOptionalLong(draft.refreshTokenExpiryMinutes);
    const session = parseOptionalLong(draft.maxSessionDurationHours);
    const idle = parseOptionalLong(draft.idleTimeoutMinutes);

    if (access === undefined) return "Access token expiry must be a positive integer";
    if (refresh === undefined) return "Refresh token expiry must be a positive integer";
    if (session === undefined) return "Max session duration must be a positive integer";
    if (idle === undefined) return "Idle timeout must be a positive integer";

    if (
        access !== null &&
        (access < guardrails.minAccessTokenExpiryMinutes || access > guardrails.maxAccessTokenExpiryMinutes)
    )
    {
        return `Access token expiry must be between ${guardrails.minAccessTokenExpiryMinutes} and ${guardrails.maxAccessTokenExpiryMinutes} minutes`;
    }
    if (
        refresh !== null &&
        (refresh < guardrails.minRefreshTokenExpiryMinutes || refresh > guardrails.maxRefreshTokenExpiryMinutes)
    )
    {
        return `Refresh token expiry must be between ${guardrails.minRefreshTokenExpiryMinutes} and ${guardrails.maxRefreshTokenExpiryMinutes} minutes`;
    }
    if (
        session !== null &&
        (session < guardrails.minSessionMaxDurationHours || session > guardrails.maxSessionMaxDurationHours)
    )
    {
        return `Max session duration must be between ${guardrails.minSessionMaxDurationHours} and ${guardrails.maxSessionMaxDurationHours} hours`;
    }
    if (
        idle !== null &&
        (idle < guardrails.minIdleTimeoutMinutes || idle > guardrails.maxIdleTimeoutMinutes)
    )
    {
        return `Idle timeout must be between ${guardrails.minIdleTimeoutMinutes} and ${guardrails.maxIdleTimeoutMinutes} minutes`;
    }
    return null;
};
