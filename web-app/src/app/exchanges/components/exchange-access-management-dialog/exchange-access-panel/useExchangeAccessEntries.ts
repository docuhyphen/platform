import {useCallback, useEffect, useState} from 'react';
import {
    changeExchangeAccessRole,
    grantExchangeAccess,
    listExchangeAccess,
    revokeExchangeAccess,
} from '../../../../../services/exchangeApi';
import {ExchangeAccessEntryDto} from '../../../../../services/types/dtos';
import {ExchangeShareRoleName} from '../../../../../services/types/roles';
import {
    AccessEntryDraft,
    buildDraft,
    buildDrafts,
    ConstraintTag,
    mergeTagsInTheDocument,
    serializeConstraintsForRole,
} from './accessEntryConstraints.ts';

const describeFailure = (err: unknown, fallback: string): string =>
{
    const apiError = err as Record<string, unknown> | undefined;
    const message = apiError?.message || apiError?.error || (err instanceof Error ? err.message : fallback);
    return String(message);
};

export const useExchangeAccessEntries = (exchangeId: string) =>
{
    const [entries, setEntries] = useState<ExchangeAccessEntryDto[]>([]);
    const [drafts, setDrafts] = useState<Record<string, AccessEntryDraft>>({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [expandedEntryId, setExpandedEntryId] = useState<string | null>(null);

    const loadAccess = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const data = await listExchangeAccess(exchangeId);
            setEntries(data);
            setDrafts(buildDrafts(data));
        }
        catch (err: unknown)
        {
            const message = err instanceof Error ? err.message : 'Failed to load access';
            setError(message);
        }
        finally
        {
            setLoading(false);
        }
    }, [exchangeId]);

    useEffect(() =>
    {
        loadAccess();
    }, [loadAccess]);

    const updateDraft = (shareId: string, updater: (prev: AccessEntryDraft) => AccessEntryDraft) =>
    {
        setDrafts((prev) =>
        {
            const current = prev[shareId];
            if (!current) return prev;
            return {
                ...prev,
                [shareId]: {...updater(current), dirty: true},
            };
        });
    };

    const changeRole = (shareId: string, roleName: ExchangeShareRoleName) =>
        updateDraft(shareId, (prev) => ({...prev, roleName}));

    const changeConstraintTags = (shareId: string, tags: ConstraintTag[]) =>
        updateDraft(shareId, (prev) => ({
            ...prev,
            constraints: mergeTagsInTheDocument(prev.constraints, tags),
        }));

    const runMutation = async (mutation: () => Promise<ExchangeAccessEntryDto[]>, fallback: string) =>
    {
        setBusy(true);
        setError(null);
        try
        {
            const result = await mutation();
            setEntries(result);
            setDrafts(buildDrafts(result));
        }
        catch (err: unknown)
        {
            setError(describeFailure(err, fallback));
        }
        finally
        {
            setBusy(false);
        }
    };

    const draftFor = (entry: ExchangeAccessEntryDto): AccessEntryDraft => drafts[entry.shareId] ?? buildDraft(entry);

    const saveEntry = (entry: ExchangeAccessEntryDto) =>
    {
        const draft = draftFor(entry);
        return runMutation(() => changeExchangeAccessRole(exchangeId, entry.shareId, {
            roleName: draft.roleName,
            constraintsJson: serializeConstraintsForRole(draft.roleName, draft.constraints),
        }), 'Failed to save access entry');
    };

    const reinstateEntry = (entry: ExchangeAccessEntryDto) =>
    {
        const draft = draftFor(entry);
        return runMutation(() => grantExchangeAccess(exchangeId, {
            principalKind: entry.principalKind,
            principalId: entry.principalId,
            roleName: draft.roleName,
            constraintsJson: serializeConstraintsForRole(draft.roleName, draft.constraints),
        }), 'Failed to reinstate access');
    };

    const revokeEntry = (shareId: string) =>
        runMutation(() => revokeExchangeAccess(exchangeId, shareId), 'Failed to revoke access');

    const toggleExpanded = (shareId: string) =>
        setExpandedEntryId((current) => (current === shareId ? null : shareId));

    return {
        entries,
        loading,
        error,
        busy,
        expandedEntryId,
        draftFor,
        changeRole,
        changeConstraintTags,
        saveEntry,
        reinstateEntry,
        revokeEntry,
        toggleExpanded,
    };
};
