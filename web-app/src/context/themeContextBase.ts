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
     * Update the active theme mode. ThemeProvider persists this locally,
     * while callers can also persist it to the user's account settings.
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

