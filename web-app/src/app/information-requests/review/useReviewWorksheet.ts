import {useCallback, useState} from "react";
import {
    InformationRequestReviewDto,
    InformationRequestReviewOutcome,
    InformationRequestReviewWorksheetDto,
    InformationRequestReviewWorksheetEntryRequest,
} from "../../models/models.tsx";
import {Scoped, valueInScope} from "../shared/useScopeGuard.ts";

export interface ReviewWorksheetDraft {
    outcome?: InformationRequestReviewOutcome;
    narrative: string;
}

interface ReviewWorksheet {
    drafts: Record<string, ReviewWorksheetDraft>;
    changed: string[];
}

type ReviewWorksheets = Record<string, ReviewWorksheet>;

const EMPTY_WORKSHEET: ReviewWorksheet = {drafts: {}, changed: []};

const savedDrafts = (worksheet: InformationRequestReviewWorksheetDto): Record<string, ReviewWorksheetDraft> =>
    Object.fromEntries((worksheet.entries ?? [])
        .map(entry => [entry.submissionItemId, {outcome: entry.outcome, narrative: entry.narrative ?? ""}]));

const withUnsavedEdits = (worksheet: InformationRequestReviewWorksheetDto, kept: ReviewWorksheet | undefined): ReviewWorksheet =>
    kept
        ? {
            drafts: {...savedDrafts(worksheet), ...Object.fromEntries(kept.changed.map(itemId => [itemId, kept.drafts[itemId]]))},
            changed: kept.changed,
        }
        : {drafts: savedDrafts(worksheet), changed: []};

const reconciled = (review: InformationRequestReviewDto, kept: ReviewWorksheets): ReviewWorksheets =>
    Object.fromEntries(review.worksheets.map(worksheet =>
        [worksheet.assignmentId, withUnsavedEdits(worksheet, kept[worksheet.assignmentId])]));

const entryOf = (sheet: ReviewWorksheet, itemId: string): InformationRequestReviewWorksheetEntryRequest => {
    const draft = sheet.drafts[itemId];
    return draft?.outcome
        ? {submissionItemId: itemId, outcome: draft.outcome, narrative: draft.narrative.trim() || undefined}
        : {submissionItemId: itemId, clear: true};
};

const sameEntry = (left: InformationRequestReviewWorksheetEntryRequest, right: InformationRequestReviewWorksheetEntryRequest) =>
    left.submissionItemId === right.submissionItemId
    && left.outcome === right.outcome
    && left.narrative === right.narrative
    && left.clear === right.clear;

export const useReviewWorksheet = (scope: string) => {
    const [worksheets, setWorksheets] = useState<Scoped<ReviewWorksheets> | null>(null);
    const sheets = valueInScope(worksheets, scope) ?? {};

    const sheetOf = (assignmentId: string | undefined): ReviewWorksheet =>
        (assignmentId ? sheets[assignmentId] : undefined) ?? EMPTY_WORKSHEET;

    const update = useCallback((assignmentId: string, itemId: string, change: Partial<ReviewWorksheetDraft>) => setWorksheets(current => {
        const kept = valueInScope(current, scope) ?? {};
        const sheet = kept[assignmentId] ?? EMPTY_WORKSHEET;
        return {
            scope,
            value: {
                ...kept,
                [assignmentId]: {
                    drafts: {...sheet.drafts, [itemId]: {...(sheet.drafts[itemId] ?? {narrative: ""}), ...change}},
                    changed: sheet.changed.includes(itemId) ? sheet.changed : [...sheet.changed, itemId],
                },
            },
        };
    }), [scope]);

    const reconcile = useCallback((owner: string, review: InformationRequestReviewDto) =>
        setWorksheets(current => ({scope: owner, value: reconciled(review, valueInScope(current, owner) ?? {})})), []);

    const settle = useCallback((
        owner: string,
        assignmentId: string,
        sent: InformationRequestReviewWorksheetEntryRequest[],
        review: InformationRequestReviewDto,
    ) => setWorksheets(current => {
        const kept = valueInScope(current, owner) ?? {};
        const sheet = kept[assignmentId];
        const unsent = sheet && {
            ...sheet,
            changed: sheet.changed.filter(itemId => !sent.some(entry => sameEntry(entry, entryOf(sheet, itemId))))
        };
        return {scope: owner, value: reconciled(review, unsent ? {...kept, [assignmentId]: unsent} : kept)};
    }), []);

    const pendingEntries = (assignmentId: string): InformationRequestReviewWorksheetEntryRequest[] => {
        const sheet = sheetOf(assignmentId);
        return sheet.changed.map(itemId => entryOf(sheet, itemId));
    };

    const reasonsWithoutOutcome = (assignmentId: string): string[] => {
        const sheet = sheetOf(assignmentId);
        return sheet.changed.filter(itemId => !sheet.drafts[itemId]?.outcome && (sheet.drafts[itemId]?.narrative ?? "").trim() !== "");
    };

    return {sheetOf, pendingEntries, reasonsWithoutOutcome, update, reconcile, settle};
};
