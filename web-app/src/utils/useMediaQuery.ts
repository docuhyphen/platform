import {useEffect, useState} from "react";

export const BREAKPOINTS = {
    mobile: 768,
    tablet: 1024,
} as const;

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

        setMatches(mql.matches);

        if (typeof mql.addEventListener === "function")
        {
            mql.addEventListener("change", onChange);
            return () => mql.removeEventListener("change", onChange);
        }

        mql.addListener(onChange);
        return () => mql.removeListener(onChange);
    }, [query]);

    return matches;
};

export const useIsMobile = (): boolean =>
    useMediaQuery(`(max-width: ${BREAKPOINTS.mobile}px)`);

export const useIsTablet = (): boolean =>
    useMediaQuery(`(max-width: ${BREAKPOINTS.tablet}px)`);

