import {useEffect, useState} from "react";

/**
 * Shared responsive breakpoints used across the app. Keep them aligned with
 * the @media rules defined in the various *Styles.tsx files.
 */
export const BREAKPOINTS = {
    /** Phone-sized viewports (single-pane master/detail). */
    mobile: 768,
    /** Tablet viewports - we may relax some side-by-side layouts here. */
    tablet: 1024,
} as const;

/**
 * Subscribe to a CSS media query and re-render when its match state changes.
 * Returns false during SSR / before mount.
 */
export const useMediaQuery = (query: string): boolean =>
{
    const getMatches = (): boolean =>
    {
        if (typeof window === "undefined" || typeof window.matchMedia !== "function")
        {
            return false;
        }
        return window.matchMedia(query).matches;
    };

    const [matches, setMatches] = useState<boolean>(getMatches);

    useEffect(() =>
    {
        if (typeof window === "undefined" || typeof window.matchMedia !== "function")
        {
            return;
        }

        const mql = window.matchMedia(query);
        const onChange = (event: MediaQueryListEvent) => setMatches(event.matches);

        // Sync once on mount in case the query state changed between initial render and effect.
        setMatches(mql.matches);

        if (typeof mql.addEventListener === "function")
        {
            mql.addEventListener("change", onChange);
            return () => mql.removeEventListener("change", onChange);
        }

        // Safari < 14 fallback
        mql.addListener(onChange);
        return () => mql.removeListener(onChange);
    }, [query]);

    return matches;
};

export const useIsMobile = (): boolean =>
    useMediaQuery(`(max-width: ${BREAKPOINTS.mobile}px)`);

export const useIsTablet = (): boolean =>
    useMediaQuery(`(max-width: ${BREAKPOINTS.tablet}px)`);

