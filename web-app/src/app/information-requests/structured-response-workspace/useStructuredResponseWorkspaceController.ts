import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {
    acceptsAnswerChanges,
    buildResponsePatches,
    conditionScopeMap,
    explanationsAwaited,
    HiddenClearConfirmation,
    lockedResponseKeys,
    workspaceOccurrences,
} from "./structuredResponseWorkspaceState.ts";
import {ResponseETagResult, StructuredResponseWorkspaceProps} from "./StructuredResponseWorkspaceTypes.ts";
import {
    commandErrorMessage,
    RESPONDENT_CHANGED_MESSAGE,
    saveStatusText,
    STALE_COMMAND_MESSAGE,
    VERIFICATION_LAPSED_MESSAGE,
} from "./responseSaveStatus.ts";
import {
    isInformationRequestAccessSessionEnded,
    isInformationRequestEditLocked,
    isInformationRequestRespondentChanged,
    isInformationRequestVerificationLapsed,
} from "../shared/informationRequestRefusal.ts";
import {
    confirmationsForTargets,
    HIDDEN_CLEAR_REFUSAL_MESSAGE,
    hiddenClearTargetsOf,
    mergeConfirmations,
} from "./hiddenResponseClearRefusal.ts";
import {IDLE_RESPONSE_WORK, usePendingWorkReporter} from "../shared/respondentPendingWork.ts";
import {focusRequirement} from "../submission/submissionReview.ts";
import {RefusedResponseItem, refusedResponseItems, withoutNamedProblems} from "./responseRefusalItems.ts";
import {occurrenceLabels, occurrenceTree} from "./occurrenceTree.ts";
import {occurrenceLabel} from "./responseAnswerState.ts";
import {supportingEvidenceNotes} from "./supportingEvidenceNotes.ts";
import {InformationRequestSupportingEvidenceLinkDto} from "../../models/models.tsx";
import {useResponseAutosave} from "./useResponseAutosave.ts";
import {useUnsavedResponses} from "./useUnsavedResponses.ts";
import {responseRevisions, reviewResponseDrafts, shownDrafts, withSavedResponses} from "./responseDraftReview.ts";

const HIDDEN_CLEAR_CONFIRMATION_MESSAGE = "Confirm clearing hidden response data before saving.";

interface RefusedClears {
    signature: string;
    confirmations: HiddenClearConfirmation[];
}

interface SavedPatches {
    signature: string;
    responseETag: string;
}

const NO_REFUSED_CLEARS: RefusedClears = {signature: "", confirmations: []};

const NOTHING_SAVED: SavedPatches = {signature: "", responseETag: ""};

const NOTHING_TO_FLUSH = () => Promise.resolve(true);

const NO_LINKS: InformationRequestSupportingEvidenceLinkDto[] = [];

const failureMessage = (commandError: unknown): string => {
    if (isInformationRequestRespondentChanged(commandError)) return RESPONDENT_CHANGED_MESSAGE;
    if (isInformationRequestVerificationLapsed(commandError)) return VERIFICATION_LAPSED_MESSAGE;
    return commandErrorMessage(commandError);
};

const reloadsAfter = (commandError: unknown): boolean =>
    isInformationRequestRespondentChanged(commandError) ||
    isInformationRequestVerificationLapsed(commandError) ||
    isInformationRequestAccessSessionEnded(commandError) ||
    isInformationRequestEditLocked(commandError);

export const useStructuredResponseWorkspaceController = ({
                                                             request,
                                                             readOnly: answersLocked,
                                                             respondentKey,
                                                             responseETag,
                                                             groups,
                                                             creatableGroupKeys,
                                                             occurrences,
                                                             requirements,
                                                             bindings,
                                                             responses,
                                                             supportingEvidenceLinks = NO_LINKS,
                                                             onSaveResponses,
                                                             onRefresh,
                                                         }: StructuredResponseWorkspaceProps) =>
{
    const [currentETag, setCurrentETag] = useState(responseETag);
    const conditionByScope = useMemo(() => conditionScopeMap(request.conditionEvaluations), [request.conditionEvaluations]);
    const shownOccurrences = useMemo(() => workspaceOccurrences(request, occurrences, requirements), [occurrences, request, requirements]);
    const scope = useMemo(
        () => ({occurrences: shownOccurrences, groups, requirements, conditionByScope}),
        [conditionByScope, groups, requirements, shownOccurrences],
    );
    const revisions = useMemo(() => responseRevisions(scope, responses), [responses, scope]);
    const entryLabels = useMemo(() => occurrenceLabels(occurrenceTree(shownOccurrences, groups)), [groups, shownOccurrences]);
    const labelOf = useCallback((occurrencePath: string) => entryLabels.get(occurrencePath) ?? occurrenceLabel(occurrencePath), [entryLabels]);
    const supportingNotes = useMemo(
        () => supportingEvidenceNotes(supportingEvidenceLinks, requirements, responses, labelOf),
        [labelOf, requirements, responses, supportingEvidenceLinks],
    );
    const unsaved = useUnsavedResponses(request.id, respondentKey, responses, revisions);
    const [confirmedHiddenClearIds, setConfirmedHiddenClearIds] = useState<Set<string>>(new Set());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [refusedItems, setRefusedItems] = useState<RefusedResponseItem[]>([]);
    const [clearsFocusRequest, setClearsFocusRequest] = useState(0);
    const [stale, setStale] = useState(false);
    const [saved, setSaved] = useState<SavedPatches>(NOTHING_SAVED);
    const [refusedSignature, setRefusedSignature] = useState("");
    const [refusedClears, setRefusedClears] = useState<RefusedClears>(NO_REFUSED_CLEARS);
    const [reloads, setReloads] = useState(0);
    const reporter = usePendingWorkReporter();

    useEffect(() => setCurrentETag(responseETag), [responseETag]);

    const creatable = useCallback(
        (groupKey: string) => creatableGroupKeys === undefined || creatableGroupKeys.includes(groupKey),
        [creatableGroupKeys],
    );
    const rootGroups = useMemo(
        () => groups.filter(group => !group.parentGroupKey && creatable(group.groupKey)),
        [creatable, groups],
    );
    const patches = useMemo(
        () => buildResponsePatches(shownOccurrences, groups, requirements, bindings, responses, conditionByScope, unsaved.edits, unsaved.answers),
        [bindings, conditionByScope, groups, requirements, responses, shownOccurrences, unsaved.answers, unsaved.edits],
    );
    const awaitedKeys = useMemo(
        () => explanationsAwaited(shownOccurrences, groups, requirements, bindings, responses, conditionByScope, unsaved.edits, unsaved.answers),
        [bindings, conditionByScope, groups, requirements, responses, shownOccurrences, unsaved.answers, unsaved.edits],
    );
    const lockedKeys = useMemo(
        () => lockedResponseKeys(shownOccurrences, groups, requirements, responses, conditionByScope),
        [conditionByScope, groups, requirements, responses, shownOccurrences],
    );
    const {discard, edits, answers, bases} = unsaved;
    useEffect(() => {
        if (lockedKeys.length > 0) discard(new Set(lockedKeys));
    }, [discard, lockedKeys]);
    const review = useMemo(
        () => reviewResponseDrafts(scope, bindings, responses, {edits, answers, bases}, labelOf),
        [answers, bases, bindings, edits, labelOf, responses, scope],
    );
    useEffect(() => {
        if (review.settledKeys.length > 0) discard(new Set(review.settledKeys));
    }, [discard, review.settledKeys]);
    const conflicted = review.conflicts.length > 0;
    const readOnly = answersLocked === true || !acceptsAnswerChanges(request);
    const signature = readOnly || patches.length === 0 ? "" : JSON.stringify(patches);
    const alreadySaved = Boolean(signature) && signature === saved.signature && responseETag === saved.responseETag;
    const pending = Boolean(signature) && !alreadySaved;
    useEffect(() => {
        if (!signature) setStale(false);
    }, [signature]);
    const clearConfirmations = signature && refusedClears.signature === signature ? refusedClears.confirmations : [];
    const requiredClearIds = clearConfirmations.map(confirmation => confirmation.requirementId);
    const clearsConfirmed = requiredClearIds.every(requirementId => confirmedHiddenClearIds.has(requirementId));

    const refreshAfterCommand = () => {
        const settle = () => setReloads(count => count - 1);
        setReloads(count => count + 1);
        void Promise.resolve(onRefresh()).then(settle, settle);
    };

    const settleCommand = (result: ResponseETagResult) => {
        if (result.outcome === "SAVED") {
            setCurrentETag(result.responseETag);
            setStale(false);
        }
        setBusy(false);
        refreshAfterCommand();
    };

    const applySaveResult = (result: ResponseETagResult) => {
        if (result.outcome === "STALE") setStale(true);
        settleCommand(result);
    };

    const applyResult = (result: ResponseETagResult) => {
        if (result.outcome === "STALE") setError(STALE_COMMAND_MESSAGE);
        settleCommand(result);
    };

    const applyCommandStart = () =>
    {
        setBusy(true);
        setError(null);
        setRefusedItems([]);
    };

    const applyCommandFailure = (commandError: unknown, explicit = false) =>
    {
        const items = refusedResponseItems(commandError, requirements, responses, labelOf);
        setError(failureMessage(withoutNamedProblems(commandError, items)));
        setRefusedItems(items);
        setBusy(false);
        if (explicit && items.length > 0) focusRequirement(items[0].occurrencePath, items[0].requirementKey);
        if (reloadsAfter(commandError)) void onRefresh();
    };

    const toggleHiddenClearConfirmation = (requirementId: string, confirmed: boolean) =>
        setConfirmedHiddenClearIds(previous =>
        {
            const next = new Set(previous);
            if (confirmed) next.add(requirementId);
            else next.delete(requirementId);
            return next;
        });

    const save = async (explicit = true): Promise<boolean> =>
    {
        if (!signature) return true;
        if (busy || (!explicit && alreadySaved)) return false;
        if (!clearsConfirmed)
        {
            if (explicit) {
                setError(HIDDEN_CLEAR_CONFIRMATION_MESSAGE);
                setClearsFocusRequest(count => count + 1);
            }
            return false;
        }
        const sent = shownDrafts(scope, {edits: unsaved.edits, answers: unsaved.answers}, new Set(awaitedKeys));
        applyCommandStart();
        if (explicit) setStale(false);
        try
        {
            const result = await onSaveResponses(request.id, {
                patches,
                confirmedHiddenResponseClearRequirementIds: requiredClearIds,
                respondentKey,
            }, currentETag);
            if (result.outcome === "SAVED")
            {
                unsaved.markSaved(sent, responseRevisions(scope, withSavedResponses(responses, result.responses)));
                setSaved({signature, responseETag});
                setRefusedClears(NO_REFUSED_CLEARS);
                setConfirmedHiddenClearIds(new Set());
            }
            applySaveResult(result);
            return result.outcome === "SAVED";
        }
        catch (commandError: unknown)
        {
            setRefusedSignature(signature);
            const targets = hiddenClearTargetsOf(commandError);
            if (targets.length === 0) {
                applyCommandFailure(commandError, explicit);
                return false;
            }
            if (refusedClears.signature !== signature) setConfirmedHiddenClearIds(new Set());
            setRefusedClears(previous => ({
                signature,
                confirmations: mergeConfirmations(
                    previous.signature === signature ? previous.confirmations : [],
                    confirmationsForTargets(targets, requirements),
                ),
            }));
            setError(HIDDEN_CLEAR_REFUSAL_MESSAGE);
            if (explicit) setClearsFocusRequest(count => count + 1);
            setBusy(false);
            return false;
        }
    };

    const latestSave = useRef(save);

    useLayoutEffect(() => {
        latestSave.current = save;
    });

    const flush = useCallback(() => latestSave.current(true), []);

    useEffect(() => {
        reporter?.reportResponses({
            unsaved: pending || (!readOnly && awaitedKeys.length > 0),
            saving: busy || reloads > 0,
            failed: error !== null,
            stale: stale || conflicted,
        }, flush);
    }, [awaitedKeys.length, busy, conflicted, error, flush, pending, readOnly, reloads, reporter, stale]);

    useEffect(() => () => reporter?.reportResponses(IDLE_RESPONSE_WORK, NOTHING_TO_FLUSH), [reporter]);

    useResponseAutosave({
        signature: !alreadySaved && signature !== refusedSignature ? signature : "",
        enabled: !busy && reloads === 0 && !stale && !conflicted && clearsConfirmed,
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
        refusedItems,
        clearsFocusRequest,
        labelOf,
        supportingNotes,
        readOnly,
        status: saveStatusText({
            readOnly,
            busy,
            stale: stale || conflicted,
            pending,
            saved: Boolean(saved.signature),
            refused: pending && signature === refusedSignature,
            awaitingExplanation: readOnly ? 0 : awaitedKeys.length,
        }),
        conflicts: review.conflicts,
        keepSavedAnswer: (key: string) => discard(new Set([key])),
        conditionByScope,
        shownOccurrences,
        rootGroups,
        creatable,
        clearConfirmations,
        confirmedHiddenClearIds,
        applyResult,
        applyCommandStart,
        applyCommandFailure,
        toggleHiddenClearConfirmation,
        save: () => save(true),
    };
};
