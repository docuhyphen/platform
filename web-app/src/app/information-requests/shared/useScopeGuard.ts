import {useCallback, useLayoutEffect, useMemo, useRef} from "react";

export interface Scoped<T> {
    scope: string;
    value: T;
}

export const valueInScope = <T>(scoped: Scoped<T> | null, scope: string): T | null =>
    scoped?.scope === scope ? scoped.value : null;

export interface ScopeGuard {
    isCurrent: (scope: string) => boolean;
    beginLoad: (scope: string) => (() => boolean) | null;
}

export const useScopeGuard = (scope: string): ScopeGuard => {
    const current = useRef(scope);
    const latestLoad = useRef(0);

    useLayoutEffect(() => {
        current.current = scope;
    }, [scope]);

    const isCurrent = useCallback((candidate: string) => current.current === candidate, []);

    const beginLoad = useCallback((candidate: string) => {
        if (current.current !== candidate) return null;
        const load = latestLoad.current + 1;
        latestLoad.current = load;
        return () => latestLoad.current === load && current.current === candidate;
    }, []);

    return useMemo(() => ({isCurrent, beginLoad}), [isCurrent, beginLoad]);
};
