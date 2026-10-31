import {useCallback, useEffect, useState} from "react";
import {normalizeApiError} from "../utils/apiErrorUtils.ts";

export interface LoadedValue<T>
{
    value: T | null;
    error: string | null;
    reload: () => void;
}

interface LoadResult<T>
{
    owner: string | (() => Promise<T>);
    value: T | null;
    error: string | null;
}

export const useLoadedValue = <T>(load: () => Promise<T>, failure: string, scope?: string): LoadedValue<T> => {
    const owner = scope ?? load;
    const [result, setResult] = useState<LoadResult<T> | null>(null);
    const [generation, setGeneration] = useState(0);

    useEffect(() =>
    {
        let current = true;
        setResult(previous => previous?.owner === owner && previous.error !== null ? {
            ...previous,
            error: null
        } : previous);
        load()
            .then(loaded =>
            {
                if (current) setResult({owner, value: loaded, error: null});
            })
            .catch((caught: unknown) =>
            {
                if (current) setResult({owner, value: null, error: normalizeApiError(caught, failure).message});
            });
        return () =>
        {
            current = false;
        };
    }, [load, owner, failure, generation]);

    const reload = useCallback(() => setGeneration(next => next + 1), []);
    const shown = result?.owner === owner ? result : null;

    return {value: shown?.value ?? null, error: shown?.error ?? null, reload};
};
