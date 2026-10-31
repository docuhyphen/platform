import {useCallback, useEffect, useRef, useState} from "react";
import {InformationRequestResponseDto} from "../../models/models.tsx";
import {pruneSavedEdits, ResponseAnswerEdits} from "./responseAnswerState.ts";
import {ResponseDrafts, ResponseRevisions, SavedDrafts} from "./responseDraftReview.ts";
import {ResponseEdits} from "./structuredResponseWorkspaceState.ts";

const EMPTY: ResponseDrafts = {edits: {}, answers: {}, bases: {}};

const storageKey = (requestId: string, respondentKey: string): string =>
    `information-request-unsaved:${requestId}:${respondentKey}`;

const isEmpty = (unsaved: ResponseDrafts): boolean =>
    Object.keys(unsaved.edits).length === 0 && Object.keys(unsaved.answers).length === 0;

const withoutKeys = <T, >(record: Record<string, T>, keys: ReadonlySet<string>): Record<string, T> =>
    Object.fromEntries(Object.entries(record).filter(([key]) => !keys.has(key)));

const newest = (known: ResponseRevisions, revisions: ResponseRevisions): ResponseRevisions =>
    Object.fromEntries([...new Set([...Object.keys(known), ...Object.keys(revisions)])]
        .map(key => [key, Math.max(known[key] ?? 0, revisions[key] ?? 0)]));

const withBases = (unsaved: ResponseDrafts, known: ResponseRevisions): ResponseDrafts =>
{
    const keys = new Set([...Object.keys(unsaved.edits), ...Object.keys(unsaved.answers)]);
    const bases = Object.fromEntries([...keys]
        .map(key => [key, unsaved.bases[key] ?? known[key]] as const)
        .filter((entry): entry is readonly [string, number] => entry[1] !== undefined));
    return {...unsaved, bases};
};

const withoutSent = (unsaved: ResponseDrafts, sent: SavedDrafts): ResponseDrafts =>
    withBases({
        edits: pruneSavedEdits(unsaved.edits, sent.edits),
        answers: pruneSavedEdits(unsaved.answers, sent.answers),
        bases: unsaved.bases,
    }, {});

const rebased = (unsaved: ResponseDrafts, sent: SavedDrafts, revisions: ResponseRevisions): ResponseDrafts => {
    const sentKeys = [...Object.keys(sent.edits), ...Object.keys(sent.answers)].filter(key => revisions[key] !== undefined);
    return withBases({
        ...unsaved,
        bases: {...unsaved.bases, ...Object.fromEntries(sentKeys.map(key => [key, revisions[key]]))}
    }, {});
};

const restored = (requestId: string, respondentKey: string): ResponseDrafts =>
{
    try
    {
        const raw = window.sessionStorage.getItem(storageKey(requestId, respondentKey));
        if (!raw) return EMPTY;
        const parsed = JSON.parse(raw) as Partial<ResponseDrafts>;
        return {edits: parsed.edits ?? {}, answers: parsed.answers ?? {}, bases: parsed.bases ?? {}};
    }
    catch
    {
        return EMPTY;
    }
};

const persist = (requestId: string, respondentKey: string, unsaved: ResponseDrafts) =>
{
    try
    {
        if (isEmpty(unsaved))
            window.sessionStorage.removeItem(storageKey(requestId, respondentKey));
        else
            window.sessionStorage.setItem(storageKey(requestId, respondentKey), JSON.stringify(unsaved));
    }
    catch
    {
        return;
    }
};

export const moveUnsavedResponses = (requestId: string, fromRespondentKey: string, toRespondentKey: string): void =>
{
    if (!fromRespondentKey || !toRespondentKey || fromRespondentKey === toRespondentKey) return;
    const moving = restored(requestId, fromRespondentKey);
    if (isEmpty(moving)) return;
    const kept = restored(requestId, toRespondentKey);
    persist(requestId, toRespondentKey, {
        edits: {...kept.edits, ...moving.edits},
        answers: {...kept.answers, ...moving.answers},
        bases: {...kept.bases, ...moving.bases},
    });
    persist(requestId, fromRespondentKey, EMPTY);
};

export const useUnsavedResponses = (
    requestId: string,
    respondentKey: string,
    responses: InformationRequestResponseDto[],
    revisions: ResponseRevisions,
) => {
    const [unsaved, setUnsaved] = useState<ResponseDrafts>(() => restored(requestId, respondentKey));
    const [saved, setSaved] = useState<SavedDrafts | null>(null);
    const known = useRef(revisions);
    const seenResponses = useRef(responses);

    useEffect(() => {
        known.current = newest(known.current, revisions);
    }, [revisions]);

    useEffect(
        () => persist(requestId, respondentKey, saved ? withoutSent(unsaved, saved) : unsaved),
        [requestId, respondentKey, saved, unsaved],
    );

    useEffect(() =>
    {
        if (seenResponses.current === responses) return;
        seenResponses.current = responses;
        if (!saved) return;
        setSaved(null);
        setUnsaved(current => withoutSent(current, saved));
    }, [responses, saved]);

    const setEdits = useCallback((change: (previous: ResponseEdits) => ResponseEdits) =>
        setUnsaved(current => withBases({...current, edits: change(current.edits)}, known.current)), []);

    const setAnswers = useCallback((change: (previous: ResponseAnswerEdits) => ResponseAnswerEdits) =>
        setUnsaved(current => withBases({...current, answers: change(current.answers)}, known.current)), []);

    const discard = useCallback((keys: ReadonlySet<string>) =>
        setUnsaved(current => {
            const held = [...keys].some(key => key in current.edits || key in current.answers);
            return held
                ? {
                    edits: withoutKeys(current.edits, keys),
                    answers: withoutKeys(current.answers, keys),
                    bases: withoutKeys(current.bases, keys),
                }
                : current;
        }), []);

    const markSaved = useCallback((sent: SavedDrafts, savedRevisions: ResponseRevisions) => {
        known.current = newest(known.current, savedRevisions);
        setSaved(sent);
        setUnsaved(current => rebased(current, sent, savedRevisions));
    }, []);

    return {
        edits: unsaved.edits,
        answers: unsaved.answers,
        bases: unsaved.bases,
        setEdits,
        setAnswers,
        discard,
        markSaved,
    };
};
