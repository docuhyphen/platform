import {useEffect, useMemo, useState} from "react";
import {
    buildResponsePatches,
    conditionScopeMap,
    hiddenClearConfirmations,
    ResponseEdits,
    workspaceOccurrences,
} from "./structuredResponseWorkspaceState.ts";
import {
    OccurrenceCommandResult,
    SaveResponsesResult,
    StructuredResponseWorkspaceProps,
} from "./StructuredResponseWorkspaceTypes.ts";

const STALE_MESSAGE = "These responses changed after this workspace loaded, so your save was not applied.";
const GENERIC_COMMAND_FAILURE_MESSAGE = "The command could not be completed.";
const HIDDEN_CLEAR_CONFIRMATION_MESSAGE = "Confirm clearing hidden response data before saving.";

const commandErrorMessage = (error: unknown): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    if (typeof error !== "object" || error === null) return GENERIC_COMMAND_FAILURE_MESSAGE;
    const candidate = error as {errorMessage?: unknown; message?: unknown; reasonCode?: unknown};
    if (typeof candidate.errorMessage === "string" && candidate.errorMessage.trim()) return candidate.errorMessage;
    if (typeof candidate.message === "string" && candidate.message.trim()) return candidate.message;
    if (typeof candidate.reasonCode === "string" && candidate.reasonCode.trim()) return candidate.reasonCode;
    return GENERIC_COMMAND_FAILURE_MESSAGE;
};

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
    const [edits, setEdits] = useState<ResponseEdits>({});
    const [confirmedHiddenClearIds, setConfirmedHiddenClearIds] = useState<Set<string>>(new Set());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => setCurrentETag(responseETag), [responseETag]);

    const conditionByScope = useMemo(
        () => conditionScopeMap(request.conditionEvaluations),
        [request.conditionEvaluations],
    );
    const shownOccurrences = useMemo(
        () => workspaceOccurrences(request, occurrences, requirements),
        [occurrences, request, requirements],
    );
    const rootGroups = useMemo(
        () => groups.filter(group => !group.parentGroupKey),
        [groups],
    );
    const clearConfirmations = useMemo(
        () => hiddenClearConfirmations(conditionRules, request.conditionEvaluations, requirements, responses),
        [conditionRules, request.conditionEvaluations, requirements, responses],
    );

    const applyResult = (result: SaveResponsesResult | OccurrenceCommandResult) =>
    {
        if (result.outcome === "STALE") setError(STALE_MESSAGE);
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
    };

    const toggleHiddenClearConfirmation = (requirementId: string, confirmed: boolean) =>
        setConfirmedHiddenClearIds(previous =>
        {
            const next = new Set(previous);
            if (confirmed) next.add(requirementId);
            else next.delete(requirementId);
            return next;
        });

    const save = async () =>
    {
        const patches = buildResponsePatches(
            shownOccurrences,
            groups,
            requirements,
            bindings,
            responses,
            conditionByScope,
            edits,
        );
        if (patches.length === 0) return;
        const requiredClearIds = clearConfirmations.map(confirmation => confirmation.requirementId);
        if (requiredClearIds.some(requirementId => !confirmedHiddenClearIds.has(requirementId)))
        {
            setError(HIDDEN_CLEAR_CONFIRMATION_MESSAGE);
            return;
        }
        applyCommandStart();
        try
        {
            applyResult(await onSaveResponses(
                request.id,
                {patches, confirmedHiddenResponseClearRequirementIds: requiredClearIds},
                currentETag,
            ));
        }
        catch (commandError: unknown)
        {
            applyCommandFailure(commandError);
        }
    };

    return {
        currentETag,
        edits,
        setEdits,
        busy,
        error,
        conditionByScope,
        shownOccurrences,
        rootGroups,
        clearConfirmations,
        confirmedHiddenClearIds,
        applyResult,
        applyCommandStart,
        applyCommandFailure,
        toggleHiddenClearConfirmation,
        save,
    };
};
