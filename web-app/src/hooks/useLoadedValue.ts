import {useCallback, useEffect, useState} from "react";
import {normalizeApiError} from "../utils/apiErrorUtils.ts";

export interface LoadedValue<T>
{
    value: T | null;
    error: string | null;
    reload: () => void;
}

export const useLoadedValue = <T>(load: () => Promise<T>, failure: string): LoadedValue<T> =>
{
    const [value, setValue] = useState<T | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [generation, setGeneration] = useState(0);

    useEffect(() =>
    {
        let current = true;
        setError(null);
        load()
            .then(loaded =>
            {
                if (current) setValue(loaded);
            })
            .catch((caught: unknown) =>
            {
                if (current) setError(normalizeApiError(caught, failure).message);
            });
        return () =>
        {
            current = false;
        };
    }, [load, failure, generation]);

    const reload = useCallback(() => setGeneration(next => next + 1), []);

    return {value, error, reload};
};
