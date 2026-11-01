import {useEffect, useState} from "react";
import {getOrgAuthSessionPolicy, updateOrgIdpAuthSessionPolicy} from "../../../../services/authApi.ts";
import type {OrgAuthSessionPolicySettings} from "../../../../services/authApi.ts";
import {
    buildDraft,
    DraftRow,
    extractPolicyErrorMessage,
    parseOptionalLong,
    validateAgainstGuardrails,
} from "./securitySessionPolicyDraft.ts";

export const useSecuritySessionPolicy = (organizationId: string) =>
{
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
            setError(extractPolicyErrorMessage(e, "Failed to load auth session policy"));
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        if (organizationId) refresh();
    }, [organizationId]);

    const updateDraft = (configId: string, patch: Partial<DraftRow>) =>
    {
        setDrafts(prev => ({...prev, [configId]: {...prev[configId], ...patch}}));
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
                error: extractPolicyErrorMessage(e, "Failed to update policy"),
            });
        }
    };

    const onReset = (configId: string) =>
    {
        if (!settings) return;
        const row = settings.idpConfigs.find(c => c.configId === configId);
        if (row) setDrafts(prev => ({...prev, [configId]: buildDraft(row)}));
    };

    return {loading, error, settings, drafts, updateDraft, onSave, onReset};
};
