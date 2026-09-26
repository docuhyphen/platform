import {useCallback, useEffect, useMemo, useState} from "react";
import {
    getInformationRequestReview,
    recordInformationRequestReviewDecisions,
    recordInformationRequestReviewFinding,
    saveInformationRequestReviewWorksheet,
} from "../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewOutcome,
    InformationRequestReviewWorksheetEntryRequest,
    RecordInformationRequestReviewFindingRequest,
} from "../../models/models.tsx";
import {submissionErrorMessage} from "../submission/submissionLabels.ts";
import {reviewStatePresentation} from "./reviewLabels.ts";

export interface ReviewWorksheetDraft
{
    outcome?: InformationRequestReviewOutcome;
    narrative: string;
}

export interface ReviewMessage
{
    intent: "success" | "warning" | "error";
    text: string;
}

const STALE: ReviewMessage = {intent: "warning", text: "This review changed while you were working. Reload it to see the latest decisions."};

export const useInformationRequestReview = (requestId: string | undefined, reviewId: string | undefined) =>
{
    const [review, setReview] = useState<InformationRequestReviewDto | null>(null);
    const [drafts, setDrafts] = useState<Record<string, ReviewWorksheetDraft>>({});
    const [changed, setChanged] = useState<string[]>([]);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<ReviewMessage | null>(null);

    const load = useCallback(async () =>
    {
        if (!requestId || !reviewId) return;
        try
        {
            const loaded = await getInformationRequestReview(requestId, reviewId);
            setReview(loaded);
            setDrafts(Object.fromEntries((loaded.worksheets[0]?.entries ?? [])
                .map(entry => [entry.submissionItemId, {outcome: entry.outcome, narrative: entry.narrative ?? ""}])));
            setChanged([]);
        }
        catch (caught: unknown)
        {
            setMessage({intent: "error", text: submissionErrorMessage(caught, "This review could not be loaded.")});
        }
    }, [requestId, reviewId]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const scope = useMemo(() =>
    {
        const worksheet = review?.worksheets[0];
        const assignment = review?.assignments.find(candidate => candidate.id === worksheet?.assignmentId);
        const stage = review?.stages.find(candidate => candidate.stageKey === assignment?.stageKey);
        if (!review || !worksheet || !assignment || !stage || assignment.decidedAt) return null;
        const settledOtherwise = new Set(review.decisions
            .filter(decision => decision.stageKey === stage.stageKey && decision.kind !== InformationRequestReviewDecisionKind.REVIEWER)
            .map(decision => decision.submissionItemId));
        return {
            worksheet,
            stageKey: stage.stageKey,
            itemIds: stage.items.map(item => item.submissionItemId).filter(itemId => !settledOtherwise.has(itemId)),
        };
    }, [review]);

    const updateDraft = (itemId: string, change: Partial<ReviewWorksheetDraft>) =>
    {
        setDrafts(current => ({...current, [itemId]: {narrative: "", ...current[itemId], ...change}}));
        setChanged(current => current.includes(itemId) ? current : [...current, itemId]);
    };

    const run = async <T>(action: () => Promise<T>, fallback: string): Promise<T | null> =>
    {
        setBusy(true);
        try
        {
            return await action();
        }
        catch (caught: unknown)
        {
            setMessage({intent: "error", text: submissionErrorMessage(caught, fallback)});
            return null;
        }
        finally
        {
            setBusy(false);
        }
    };

    const saveWorksheet = async (): Promise<string | null> =>
    {
        if (!requestId || !reviewId || !scope) return null;
        if (changed.length === 0) return scope.worksheet.draftETag;
        const entries: InformationRequestReviewWorksheetEntryRequest[] = changed.map(itemId => drafts[itemId]?.outcome
            ? {submissionItemId: itemId, outcome: drafts[itemId].outcome, narrative: drafts[itemId].narrative.trim() || undefined}
            : {submissionItemId: itemId, clear: true});
        const outcome = await run(() => saveInformationRequestReviewWorksheet(requestId, reviewId, scope.worksheet.assignmentId, {entries}, scope.worksheet.draftETag),
            "The worksheet could not be saved.");
        if (!outcome) return null;
        if (outcome.outcome === "STALE")
        {
            setMessage(STALE);
            return null;
        }
        setReview(current => current && {...current, worksheets: current.worksheets.map(worksheet =>
            worksheet.assignmentId === outcome.data.assignmentId ? outcome.data : worksheet)});
        setChanged([]);
        setMessage({intent: "success", text: "Worksheet saved."});
        return outcome.data.draftETag;
    };

    const recordDecisions = async () =>
    {
        if (!requestId || !reviewId || !scope) return;
        const draftETag = await saveWorksheet();
        if (!draftETag) return;
        const outcome = await run(() => recordInformationRequestReviewDecisions(requestId, reviewId, scope.worksheet.assignmentId, {
            expectedETag: draftETag,
            idempotencyKey: crypto.randomUUID(),
        }), "The decisions could not be recorded.");
        if (!outcome) return;
        setMessage(outcome.outcome === "STALE"
            ? STALE
            : {intent: "success", text: `Decisions recorded. This review is ${reviewStatePresentation[outcome.data.review.state].label.toLowerCase()}.`});
        await load();
    };

    const recordFinding = async (request: RecordInformationRequestReviewFindingRequest): Promise<boolean> =>
    {
        if (!requestId || !reviewId) return false;
        const outcome = await run(() => recordInformationRequestReviewFinding(requestId, reviewId, request, crypto.randomUUID()),
            "The finding could not be recorded.");
        if (!outcome) return false;
        setMessage({intent: "success", text: "Finding recorded."});
        await load();
        return true;
    };

    return {review, scope, drafts, changed, busy, message, load, updateDraft, saveWorksheet, recordDecisions, recordFinding};
};
