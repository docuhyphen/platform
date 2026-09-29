import {useCallback, useEffect, useRef, useState} from "react";
import {InformationRequestResponseDto} from "../../models/models.tsx";
import {pruneSavedEdits, ResponseAnswerEdits} from "./responseAnswerState.ts";
import {ResponseEdits} from "./structuredResponseWorkspaceState.ts";

interface UnsavedResponses
{
    edits: ResponseEdits;
    answers: ResponseAnswerEdits;
}

const EMPTY: UnsavedResponses = {edits: {}, answers: {}};

const storageKey = (requestId: string): string => `information-request-unsaved:${requestId}`;

const restored = (requestId: string): UnsavedResponses =>
{
    try
    {
        const raw = window.sessionStorage.getItem(storageKey(requestId));
        if (!raw) return EMPTY;
        const parsed = JSON.parse(raw) as Partial<UnsavedResponses>;
        return {edits: parsed.edits ?? {}, answers: parsed.answers ?? {}};
    }
    catch
    {
        return EMPTY;
    }
};

const persist = (requestId: string, unsaved: UnsavedResponses) =>
{
    try
    {
        if (Object.keys(unsaved.edits).length === 0 && Object.keys(unsaved.answers).length === 0)
            window.sessionStorage.removeItem(storageKey(requestId));
        else
            window.sessionStorage.setItem(storageKey(requestId), JSON.stringify(unsaved));
    }
    catch
    {
        return;
    }
};

export const useUnsavedResponses = (requestId: string, responses: InformationRequestResponseDto[]) =>
{
    const [unsaved, setUnsaved] = useState<UnsavedResponses>(() => restored(requestId));
    const saved = useRef<UnsavedResponses | null>(null);
    const seenResponses = useRef(responses);

    useEffect(() => persist(requestId, unsaved), [requestId, unsaved]);

    useEffect(() =>
    {
        if (seenResponses.current === responses) return;
        seenResponses.current = responses;
        const sent = saved.current;
        if (!sent) return;
        saved.current = null;
        setUnsaved(current => ({
            edits: pruneSavedEdits(current.edits, sent.edits),
            answers: pruneSavedEdits(current.answers, sent.answers),
        }));
    }, [responses]);

    const setEdits = useCallback((change: (previous: ResponseEdits) => ResponseEdits) =>
        setUnsaved(current => ({...current, edits: change(current.edits)})), []);

    const setAnswers = useCallback((change: (previous: ResponseAnswerEdits) => ResponseAnswerEdits) =>
        setUnsaved(current => ({...current, answers: change(current.answers)})), []);

    return {
        edits: unsaved.edits,
        answers: unsaved.answers,
        setEdits,
        setAnswers,
        markSaved: (sent: UnsavedResponses) =>
        {
            saved.current = sent;
        },
    };
};
