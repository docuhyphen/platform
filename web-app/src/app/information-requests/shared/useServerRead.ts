import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {Scoped, useScopeGuard, valueInScope} from "./useScopeGuard.ts";
import {useRequestReadRefresh} from "./request-read-refresh/RequestReadRefreshContext.ts";

export interface ServerRead<T> {
    value: T | null;
    error: string | null;
    loading: boolean;
    reload: () => Promise<void>;
}

interface ReadState<T> {
    value: T | null;
    error: string | null;
    loading: boolean;
}

export const useServerRead = <T, >(
    scope: string,
    read: () => Promise<T>,
    refusalMessage: (refusal: unknown) => string,
): ServerRead<T> => {
    const guard = useScopeGuard(scope);
    const refresh = useRequestReadRefresh();
    const [state, setState] = useState<Scoped<ReadState<T>> | null>(null);
    const latestRead = useRef(read);
    const latestMessage = useRef(refusalMessage);

    useLayoutEffect(() => {
        latestRead.current = read;
        latestMessage.current = refusalMessage;
    });

    const load = useCallback(async () => {
        const current = guard.beginLoad(scope);
        if (!current) return;
        setState(previous => ({
            scope,
            value: {value: valueInScope(previous, scope)?.value ?? null, error: null, loading: true}
        }));
        try {
            const loaded = await latestRead.current();
            if (current()) setState({scope, value: {value: loaded, error: null, loading: false}});
        } catch (refusal: unknown) {
            if (current()) setState({
                scope,
                value: {value: null, error: latestMessage.current(refusal), loading: false}
            });
        }
    }, [guard, scope]);

    useEffect(() => {
        void load();
    }, [load, refresh]);

    const shown = valueInScope(state, scope);

    return {
        value: shown?.value ?? null,
        error: shown?.error ?? null,
        loading: shown === null || shown.loading,
        reload: load,
    };
};
