import {createContext, useContext} from "react";
import type {Theme} from "@fluentui/react-components";
import type {ThemeMode} from "./theme";

type ResolvedTheme = "light" | "dark";

export interface ThemeContextValue
{
    mode: ThemeMode;
    resolvedMode: ResolvedTheme;
    theme: Theme;
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

