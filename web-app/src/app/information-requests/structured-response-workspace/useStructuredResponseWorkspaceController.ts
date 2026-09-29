import {useEffect, useMemo, useState} from "react";
import {
    buildResponsePatches,
    conditionScopeMap,
    hiddenClearConfirmations,
    workspaceOccurrences,
} from "./structuredResponseWorkspaceState.ts";
import {ResponseETagResult, StructuredResponseWorkspaceProps} from "./StructuredResponseWorkspaceTypes.ts";
import {commandErrorMessage, isAccessSessionEnded, saveStatusText, STALE_MESSAGE} from "./responseSaveStatus.ts";
import {useResponseAutosave} from "./useResponseAutosave.ts";
import {useUnsavedResponses} from "./useUnsavedResponses.ts";

const HIDDEN_CLEAR_CONFIRMATION_MESSAGE = "Confirm clearing hidden response data before saving.";

export const useStructuredResponseWorkspaceController = ({
    request,
    responseETag,
    groups,
    conditionRules = [],
    occurrences,
    requirements,
    bindings,
    responses,
    onSaveResponses,
    onRefresh,
}: StructuredResponseWorkspaceProps) =>
{
    const [currentETag, setCurrentETag] = useState(responseETag);
    const unsaved = useUnsavedResponses(request.id, responses);
    const [confirmedHiddenClearIds, setConfirmedHiddenClearIds] = useState<Set<string>>(new Set());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [stale, setStale] = useState(false);
    const [savedSignature, setSavedSignature] = useState("");
    const [refusedSignature, setRefusedSignature] = useState("");

    useEffect(() => setCurrentETag(responseETag), [responseETag]);

    const conditionByScope = useMemo(() => conditionScopeMap(request.conditionEvaluations), [request.conditionEvaluations]);
    const shownOccurrences = useMemo(() => workspaceOccurrences(request, occurrences, requirements), [occurrences, request, requirements]);
    const rootGroups = useMemo(() => groups.filter(group => !group.parentGroupKey), [groups]);
    const clearConfirmations = useMemo(
        () => hiddenClearConfirmations(conditionRules, request.conditionEvaluations, requirements, responses),
        [conditionRules, request.conditionEvaluations, requirements, responses],
    );
    const patches = useMemo(
        () => buildResponsePatches(shownOccurrences, groups, requirements, bindings, responses, conditionByScope, unsaved.edits, unsaved.answers),
        [bindings, conditionByScope, groups, requirements, responses, shownOccurrences, unsaved.answers, unsaved.edits],
    );
    const signature = patches.length === 0 ? "" : JSON.stringify(patches);
    const requiredClearIds = clearConfirmations.map(confirmation => confirmation.requirementId);
    const clearsConfirmed = requiredClearIds.every(requirementId => confirmedHiddenClearIds.has(requirementId));

    const applyResult = (result: ResponseETagResult) =>
    {
        if (result.outcome === "STALE") setStale(true);
        else setCurrentETag(result.responseETag);
        setBusy(false);
        onRefresh();
    };

    const applyCommandStart = () =>
    {
        setBusy(true);
        setError(null);
    };

    const applyCommandFailure = (commandError: unknown) =>
    {
        setError(commandErrorMessage(commandError));
        setBusy(false);
        if (isAccessSessionEnded(commandError)) onRefresh();
    };

    const toggleHiddenClearConfirmation = (requirementId: string, confirmed: boolean) =>
        setConfirmedHiddenClearIds(previous =>
        {
            const next = new Set(previous);
            if (confirmed) next.add(requirementId);
            else next.delete(requirementId);
            return next;
        });

    const save = async (explicit = true) =>
    {
        if (!signature || busy || (!explicit && signature === savedSignature)) return;
        if (!clearsConfirmed)
        {
            if (explicit) setError(HIDDEN_CLEAR_CONFIRMATION_MESSAGE);
            return;
        }
        const sent = {edits: unsaved.edits, answers: unsaved.answers};
        applyCommandStart();
        if (explicit) setStale(false);
        try
        {
            const result = await onSaveResponses(request.id, {patches, confirmedHiddenResponseClearRequirementIds: requiredClearIds}, currentETag);
            if (result.outcome === "SAVED")
            {
                unsaved.markSaved(sent);
                setSavedSignature(signature);
            }
            applyResult(result);
        }
        catch (commandError: unknown)
        {
            setRefusedSignature(signature);
            applyCommandFailure(commandError);
        }
    };

    useResponseAutosave({
        signature: signature !== savedSignature && signature !== refusedSignature ? signature : "",
        enabled: !busy && !stale && clearsConfirmed,
        save: () => void save(false),
    });

    return {
        currentETag,
        edits: unsaved.edits,
        setEdits: unsaved.setEdits,
        answers: unsaved.answers,
        setAnswers: unsaved.setAnswers,
        busy,
        error,
        status: saveStatusText({busy, stale, pending: Boolean(signature) && signature !== savedSignature, saved: Boolean(savedSignature)}),
        staleMessage: stale ? STALE_MESSAGE : null,
        conditionByScope,
        shownOccurrences,
        rootGroups,
        clearConfirmations,
        confirmedHiddenClearIds,
        applyResult,
        applyCommandStart,
        applyCommandFailure,
        toggleHiddenClearConfirmation,
        save: () => save(true),
    };
};
