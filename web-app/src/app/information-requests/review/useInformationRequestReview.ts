import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    getInformationRequestReview,
    recordInformationRequestReviewDecisions,
    recordInformationRequestReviewFinding,
    saveInformationRequestReviewWorksheet,
} from "../../../services/informationRequestReviewService.ts";
import {InformationRequestReviewDto, RecordInformationRequestReviewFindingRequest} from "../../models/models.tsx";
import {Scoped, useScopeGuard, valueInScope} from "../shared/useScopeGuard.ts";
import {useStableIdempotencyKey} from "../shared/useStableIdempotencyKey.ts";
import {submissionErrorMessage} from "../submission/submissionLabels.ts";
import {withNewestWorksheets, withSavedWorksheet} from "./newestWorksheets.ts";
import {reviewStatePresentation} from "./reviewLabels.ts";
import {ReviewMessage, useReviewFeedback} from "./useReviewFeedback.ts";
import {ReviewWorksheetDraft, useReviewWorksheet} from "./useReviewWorksheet.ts";
import {workableAssignments, workScopeOf} from "./workableAssignments.ts";

export type {ReviewMessage} from "./useReviewFeedback.ts";

export interface ReviewAssignmentChoice
{
    assignmentId: string;
    stageTitle: string;
    position: number;
    unsavedCount: number;
}

const STALE: ReviewMessage = {
    intent: "warning",
    text: "This review changed while you were working. The latest review is shown with your unsaved choices kept; save again.",
};
const CHANGED_ELSEWHERE: ReviewMessage = {
    intent: "warning",
    text: "This worksheet changed elsewhere while it was being saved. The latest saved choices are shown.",
};

export const reviewScopeOf = (requestId: string | undefined, reviewId: string | undefined): string =>
    requestId && reviewId ? `${requestId}|${reviewId}` : "";

export const useInformationRequestReview = (requestId: string | undefined, reviewId: string | undefined) =>
{
    const scopeKey = reviewScopeOf(requestId, reviewId);
    const guard = useScopeGuard(scopeKey);
    const worksheet = useReviewWorksheet(scopeKey);
    const {reconcile} = worksheet;
    const [loaded, setLoaded] = useState<Scoped<InformationRequestReviewDto> | null>(null);
    const latest = useRef<Scoped<InformationRequestReviewDto> | null>(null);
    const [failure, setFailure] = useState<Scoped<string> | null>(null);
    const feedback = useReviewFeedback(scopeKey);
    const [busyScope, setBusyScope] = useState("");
    const [chosen, setChosen] = useState<Scoped<string> | null>(null);
    const keyed = useStableIdempotencyKey();
    const say = feedback.say;

    const accept = useCallback((next: Scoped<InformationRequestReviewDto> | null) => {
        latest.current = next;
        setLoaded(next);
    }, []);

    const load = useCallback(async () =>
    {
        if (!requestId || !reviewId) return;
        const owner = reviewScopeOf(requestId, reviewId);
        const current = guard.beginLoad(owner);
        if (!current) return;
        try
        {
            const next = await getInformationRequestReview(requestId, reviewId);
            if (!current()) return;
            const shown = withNewestWorksheets(next, valueInScope(latest.current, owner));
            accept({scope: owner, value: shown});
            setFailure(null);
            reconcile(owner, shown);
        }
        catch (caught: unknown)
        {
            if (!current()) return;
            accept(null);
            setFailure({scope: owner, value: submissionErrorMessage(caught, "This review could not be loaded.")});
        }
    }, [accept, guard, reconcile, requestId, reviewId]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const scoped = valueInScope(loaded, scopeKey);
    const review = scoped?.review.id === reviewId && scoped?.review.informationRequestId === requestId ? scoped : null;
    const loadError = valueInScope(failure, scopeKey);

    const workable = useMemo(() => review ? workableAssignments(review) : [], [review]);
    const chosenId = valueInScope(chosen, scopeKey);
    const working = workable.find(candidate => candidate.assignment.id === chosenId) ?? workable[0];
    const scope = useMemo(() => review && working ? workScopeOf(review, working) : null, [review, working]);
    const sheet = worksheet.sheetOf(scope?.worksheet.assignmentId);
    const choices: ReviewAssignmentChoice[] = workable.map(({assignment, stage}) => ({
        assignmentId: assignment.id,
        stageTitle: stage.title,
        position: stage.position,
        unsavedCount: worksheet.sheetOf(assignment.id).changed.length,
    }));

    const updateDraft = (itemId: string, change: Partial<ReviewWorksheetDraft>) =>
    {
        if (review && scope) worksheet.update(scope.worksheet.assignmentId, itemId, change);
    };

    const run = async <T>(owner: string, action: () => Promise<T>, fallback: string): Promise<T | null> =>
    {
        setBusyScope(owner);
        feedback.clear(owner);
        try
        {
            return await action();
        }
        catch (caught: unknown)
        {
            say(owner, {intent: "error", text: submissionErrorMessage(caught, fallback)});
            return null;
        }
        finally
        {
            setBusyScope(current => current === owner ? "" : current);
        }
    };

    const saveWorksheet = async (): Promise<string | null> =>
    {
        if (!review || !scope) return null;
        const owner = scopeKey;
        const {id, informationRequestId} = review.review;
        const {assignmentId, draftETag} = scope.worksheet;
        if (worksheet.reasonsWithoutOutcome(assignmentId).length > 0) {
            say(owner, {
                intent: "error",
                text: "Choose an outcome for each item you gave a reason for, then save again."
            });
            return null;
        }
        const entries = worksheet.pendingEntries(assignmentId);
        if (entries.length === 0) return draftETag;
        const outcome = await run(owner, () => saveInformationRequestReviewWorksheet(
            informationRequestId, id, assignmentId, {entries}, draftETag,
        ), "The worksheet could not be saved.");
        if (!outcome || !guard.isCurrent(owner)) return null;
        if (outcome.outcome === "STALE")
        {
            say(owner, STALE);
            await load();
            return null;
        }
        const shown = valueInScope(latest.current, owner);
        if (!shown) return null;
        const accepted = withSavedWorksheet(shown, outcome.data);
        accept({scope: owner, value: accepted});
        worksheet.settle(owner, assignmentId, entries, accepted);
        if (!accepted.worksheets.includes(outcome.data)) {
            say(owner, CHANGED_ELSEWHERE);
            return null;
        }
        say(owner, {intent: "success", text: "Worksheet saved."});
        return outcome.data.draftETag;
    };

    const recordDecisions = async () =>
    {
        if (!review || !scope) return;
        const owner = scopeKey;
        const {id, informationRequestId} = review.review;
        const assignmentId = scope.worksheet.assignmentId;
        const draftETag = await saveWorksheet();
        if (!draftETag || !guard.isCurrent(owner)) return;
        const outcome = await run(owner, () => keyed(
            `decisions|${owner}|${assignmentId}`,
            {expectedETag: draftETag},
            idempotencyKey => recordInformationRequestReviewDecisions(informationRequestId, id, assignmentId, {
                expectedETag: draftETag,
                idempotencyKey
            }),
        ), "The decisions could not be recorded.");
        if (!outcome || !guard.isCurrent(owner)) return;
        say(owner, outcome.outcome === "STALE"
            ? STALE
            : {intent: "success", text: `Decisions recorded. This review is ${reviewStatePresentation[outcome.data.review.state].label.toLowerCase()}.`});
        await load();
    };

    const recordFinding = async (request: RecordInformationRequestReviewFindingRequest, asManager = false): Promise<boolean> =>
    {
        if (!review) return false;
        const owner = scopeKey;
        const {id, informationRequestId} = review.review;
        const named: RecordInformationRequestReviewFindingRequest = {
            ...request,
            assignmentId: asManager ? undefined : scope?.worksheet.assignmentId,
        };
        const outcome = await run(
            owner,
            () => keyed(`findings|${owner}`, named, key => recordInformationRequestReviewFinding(informationRequestId, id, named, key)),
            "The finding could not be recorded.",
        );
        if (!outcome || !guard.isCurrent(owner)) return false;
        say(owner, {intent: "success", text: "Finding recorded."});
        await load();
        return true;
    };

    return {
        review,
        scope,
        choices,
        chooseAssignment: (assignmentId: string) => setChosen({scope: scopeKey, value: assignmentId}),
        drafts: sheet.drafts,
        changed: sheet.changed,
        busy: busyScope !== "" && busyScope === scopeKey,
        message: loadError ? {intent: "error" as const, text: loadError} : feedback.message,
        feedback,
        load,
        updateDraft,
        saveWorksheet,
        recordDecisions,
        recordFinding,
    };
};
