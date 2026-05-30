import {createContext, useContext} from "react";
import type {Theme} from "@fluentui/react-components";
import type {ThemeMode} from "./theme";

type ResolvedTheme = "light" | "dark";

export interface ThemeContextValue
{
    mode: ThemeMode;
    resolvedMode: ResolvedTheme;
    theme: Theme;
    /**
     * Update the active theme in memory only. Persistence to the user's
     * account is the caller's responsibility (e.g. via updateAppUserSettings).
     */
    setMode: (mode: ThemeMode) => void;
}

export const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export const useTheme = (): ThemeContextValue =>
{
    const ctx = useContext(ThemeContext);
    if (!ctx)
    {
        throw new Error("useTheme must be used within a ThemeProvider");
    }
    return ctx;
};

