import {ReactNode, useCallback, useEffect, useMemo, useState} from "react";
import {darkTheme, lightTheme, ThemeMode} from "./theme";
import {ThemeContext, ThemeContextValue} from "./themeContextBase";

const getSystemPrefersDark = (): boolean =>
{
    if (typeof window === "undefined" || !window.matchMedia)
    {
        return false;
    }
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
};

const isValidMode = (value: unknown): value is ThemeMode =>
    value === "light" || value === "dark" || value === "system";

interface ThemeProviderProps
{
    children: ReactNode;
}

export const ThemeProvider = ({children}: ThemeProviderProps) =>
{
    // Default to light for everyone until the user's stored preference
    // is loaded from the server (see ThemeSync / AppSettingsTab).
    const [mode, setModeState] = useState<ThemeMode>("light");
    const [systemPrefersDark, setSystemPrefersDark] = useState<boolean>(() => getSystemPrefersDark());

    useEffect(() =>
    {
        if (typeof window === "undefined" || !window.matchMedia)
        {
            return;
        }
        const mql = window.matchMedia("(prefers-color-scheme: dark)");
        const listener = (e: MediaQueryListEvent) => setSystemPrefersDark(e.matches);
        if (mql.addEventListener)
        {
            mql.addEventListener("change", listener);
            return () => mql.removeEventListener("change", listener);
        }
        // Safari fallback
        mql.addListener(listener);
        return () => mql.removeListener(listener);
    }, []);

    const setMode = useCallback((next: ThemeMode) =>
    {
        if (!isValidMode(next))
        {
            return;
        }
        setModeState(next);
    }, []);

    const resolvedMode: "light" | "dark" = mode === "system"
        ? (systemPrefersDark ? "dark" : "light")
        : mode;

    const theme = resolvedMode === "dark" ? darkTheme : lightTheme;

    // Keep <html data-theme="..."> in sync for any plain CSS that wants to react.
    useEffect(() =>
    {
        if (typeof document !== "undefined")
        {
            document.documentElement.dataset.theme = resolvedMode;
            document.documentElement.style.colorScheme = resolvedMode;
        }
    }, [resolvedMode]);

    const value = useMemo<ThemeContextValue>(() => ({
        mode,
        resolvedMode,
        theme,
        setMode,
    }), [mode, resolvedMode, theme, setMode]);

    return (
        <ThemeContext.Provider value={value}>
            {children}
        </ThemeContext.Provider>
    );
};
