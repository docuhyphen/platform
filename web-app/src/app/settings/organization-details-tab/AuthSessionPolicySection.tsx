import {useEffect, useState} from "react";
import {
    Badge,
    Button,
    Divider,
    Input,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {useAuthSessionPolicySectionStyles} from "./AuthSessionPolicySectionStyles.tsx";
import {
    getOrgAuthSessionPolicy,
    updateOrgIdpAuthSessionPolicy,
} from "../../../services/authApi.ts";
import type {
    OrgAuthSessionPolicyGuardrails,
    OrgAuthSessionPolicyIdp,
    OrgAuthSessionPolicySettings,
} from "../../../services/authApi.ts";

interface AuthSessionPolicySectionProps
{
    organizationId: string;
}

interface DraftRow
{
    accessTokenExpiryMinutes: string;
    refreshTokenExpiryMinutes: string;
    maxSessionDurationHours: string;
    idleTimeoutMinutes: string;
    saving: boolean;
    error: string | null;
}

const toStr = (v?: number | null): string => (v === null || v === undefined ? "" : String(v));

const parseOptionalLong = (raw: string): number | null | undefined =>
{
    const trimmed = raw.trim();
    if (trimmed === "") return null;
    const n = Number(trimmed);
    if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0) return undefined; // invalid
    return n;
};

const extractErrorMessage = (e: unknown, fallback: string): string =>
{
    if (e && typeof e === "object")
    {
        const obj = e as { errorMessage?: unknown; message?: unknown };
        if (typeof obj.errorMessage === "string" && obj.errorMessage) return obj.errorMessage;
        if (typeof obj.message === "string" && obj.message) return obj.message;
    }
    return fallback;
};

const buildDraft = (row: OrgAuthSessionPolicyIdp): DraftRow => ({
    accessTokenExpiryMinutes: toStr(row.accessTokenExpiryMinutes),
    refreshTokenExpiryMinutes: toStr(row.refreshTokenExpiryMinutes),
    maxSessionDurationHours: toStr(row.maxSessionDurationHours),
    idleTimeoutMinutes: toStr(row.idleTimeoutMinutes),
    saving: false,
    error: null,
});

export const AuthSessionPolicySection = ({organizationId}: AuthSessionPolicySectionProps) =>
{
    const styles = useAuthSessionPolicySectionStyles();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [settings, setSettings] = useState<OrgAuthSessionPolicySettings | null>(null);
    const [drafts, setDrafts] = useState<Record<string, DraftRow>>({});

    const refresh = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const data = await getOrgAuthSessionPolicy(organizationId);
            setSettings(data);
            const next: Record<string, DraftRow> = {};
            data.idpConfigs.forEach(c => (next[c.configId] = buildDraft(c)));
            setDrafts(next);
        }
        catch (e: unknown)
        {
            setError(extractErrorMessage(e, "Failed to load auth session policy"));
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        if (organizationId) refresh();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [organizationId]);

    const updateDraft = (configId: string, patch: Partial<DraftRow>) =>
    {
        setDrafts(prev => ({...prev, [configId]: {...prev[configId], ...patch}}));
    };

    const validateAgainstGuardrails = (
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

    const onSave = async (configId: string) =>
    {
        if (!settings) return;
        const draft = drafts[configId];
        if (!draft) return;

        const validationError = validateAgainstGuardrails(draft, settings.guardrails);
        if (validationError)
        {
            updateDraft(configId, {error: validationError});
            return;
        }

        updateDraft(configId, {saving: true, error: null});
        try
        {
            await updateOrgIdpAuthSessionPolicy(organizationId, configId, {
                accessTokenExpiryMinutes: parseOptionalLong(draft.accessTokenExpiryMinutes) as number | null,
                refreshTokenExpiryMinutes: parseOptionalLong(draft.refreshTokenExpiryMinutes) as number | null,
                maxSessionDurationHours: parseOptionalLong(draft.maxSessionDurationHours) as number | null,
                idleTimeoutMinutes: parseOptionalLong(draft.idleTimeoutMinutes) as number | null,
            });
            await refresh();
        }
        catch (e: unknown)
        {
            updateDraft(configId, {
                saving: false,
                error: extractErrorMessage(e, "Failed to update policy"),
            });
        }
    };

    const onReset = (configId: string) =>
    {
        if (!settings) return;
        const row = settings.idpConfigs.find(c => c.configId === configId);
        if (row) setDrafts(prev => ({...prev, [configId]: buildDraft(row)}));
    };

    return (
        <>
            <Divider
                alignContent="start"
                appearance="brand"
                className={styles.divider}
            >
                Auth Session Policy
            </Divider>

            {loading && (
                <div className={styles.loadingWrapper}>
                    <Spinner
                        size="tiny"
                        label="Loading policy"
                    />
                </div>
            )}

            {error && (
                <div className={styles.error}>{error}</div>
            )}

            {settings && !loading && (
                <>
                    <Text size={200}>
                        Effective policy is the minimum across active Identity Provider configurations
                        (including the built-in INTERNAL provider) and is clamped to platform guardrails.
                        Short refresh and idle windows are intentional for sensitive-document workloads.
                    </Text>

                    <div className={styles.effectiveGrid}>
                        <Text weight="semibold">Effective access token</Text>
                        <Text>{settings.effective.accessTokenExpiryMinutes} min</Text>
                        <Text size={200}>
                            (range {settings.guardrails.minAccessTokenExpiryMinutes}–
                            {settings.guardrails.maxAccessTokenExpiryMinutes} min)
                        </Text>

                        <Text weight="semibold">Effective refresh token</Text>
                        <Text>{settings.effective.refreshTokenExpiryMinutes} min</Text>
                        <Text size={200}>
                            (range {settings.guardrails.minRefreshTokenExpiryMinutes}–
                            {settings.guardrails.maxRefreshTokenExpiryMinutes} min)
                        </Text>

                        <Text weight="semibold">Effective max session</Text>
                        <Text>{settings.effective.maxSessionDurationHours} hours</Text>
                        <Text size={200}>
                            (range {settings.guardrails.minSessionMaxDurationHours}–
                            {settings.guardrails.maxSessionMaxDurationHours} hrs)
                        </Text>

                        <Text weight="semibold">Effective idle timeout</Text>
                        <Text>{settings.effective.idleTimeoutMinutes} min</Text>
                        <Text size={200}>
                            (range {settings.guardrails.minIdleTimeoutMinutes}–
                            {settings.guardrails.maxIdleTimeoutMinutes} min)
                        </Text>
                    </div>

                    {settings.idpConfigs.length === 0 && (
                        <Text
                            size={300}
                            className={styles.noIdpText}
                        >
                            No Identity Provider configurations exist yet.
                        </Text>
                    )}

                    {settings.idpConfigs.map(row =>
                    {
                        const draft = drafts[row.configId];
                        if (!draft) return null;
                        const disabled = !row.isActive || draft.saving;

                        return (
                            <div
                                key={row.configId}
                                className={styles.idpCard}
                            >
                                <div className={styles.idpCardHeader}>
                                    <Text weight="semibold">{row.provider}</Text>
                                    <Badge
                                        appearance="outline"
                                        color={row.isActive ? "success" : "subtle"}
                                    >
                                        {row.isActive ? "Active" : "Inactive"}
                                    </Badge>
                                </div>

                                <div className={styles.inputsGrid}>
                                    <label className={styles.inputLabel}>
                                        <Text size={200}>Access token expiry (minutes)</Text>
                                        <Input
                                            id={`input-access-token-expiry-${row.configId}`}
                                            value={draft.accessTokenExpiryMinutes}
                                            disabled={disabled}
                                            placeholder="(inherit default)"
                                            onChange={(_, d) =>
                                                updateDraft(row.configId, {
                                                    accessTokenExpiryMinutes: d.value,
                                                    error: null,
                                                })
                                            }
                                        />
                                    </label>
                                    <label className={styles.inputLabel}>
                                        <Text size={200}>Refresh token expiry (minutes)</Text>
                                        <Input
                                            id={`input-refresh-token-expiry-${row.configId}`}
                                            value={draft.refreshTokenExpiryMinutes}
                                            disabled={disabled}
                                            placeholder="(inherit default)"
                                            onChange={(_, d) =>
                                                updateDraft(row.configId, {
                                                    refreshTokenExpiryMinutes: d.value,
                                                    error: null,
                                                })
                                            }
                                        />
                                    </label>
                                    <label className={styles.inputLabel}>
                                        <Text size={200}>Max session duration (hours)</Text>
                                        <Input
                                            id={`input-max-session-duration-${row.configId}`}
                                            value={draft.maxSessionDurationHours}
                                            disabled={disabled}
                                            placeholder="(inherit default)"
                                            onChange={(_, d) =>
                                                updateDraft(row.configId, {
                                                    maxSessionDurationHours: d.value,
                                                    error: null,
                                                })
                                            }
                                        />
                                    </label>
                                    <label className={styles.inputLabel}>
                                        <Text size={200}>Idle timeout (minutes)</Text>
                                        <Input
                                            id={`input-idle-timeout-${row.configId}`}
                                            value={draft.idleTimeoutMinutes}
                                            disabled={disabled}
                                            placeholder="(inherit default)"
                                            onChange={(_, d) =>
                                                updateDraft(row.configId, {
                                                    idleTimeoutMinutes: d.value,
                                                    error: null,
                                                })
                                            }
                                        />
                                    </label>
                                </div>

                                {draft.error && (
                                    <div className={styles.draftError}>
                                        {draft.error}
                                    </div>
                                )}

                                <div className={styles.actionRow}>
                                    <Button
                                        id={`button-auth-policy-save-${row.configId}`}
                                        appearance="primary"
                                        shape={"circular"}
                                        size="small"
                                        disabled={disabled}
                                        onClick={() => onSave(row.configId)}
                                    >
                                        {draft.saving ? "Saving…" : "Save"}
                                    </Button>
                                    <Button
                                        id={`button-auth-policy-reset-${row.configId}`}
                                        appearance="secondary"
                                        shape={"circular"}
                                        size="small"
                                        disabled={disabled}
                                        onClick={() => onReset(row.configId)}
                                    >
                                        Reset
                                    </Button>
                                </div>
                            </div>
                        );
                    })}
                </>
            )}
        </>
    );
};

export default AuthSessionPolicySection;









