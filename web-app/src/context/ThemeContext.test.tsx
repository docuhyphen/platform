/** @vitest-environment jsdom */
import React from "react";
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {ThemeProvider} from "./ThemeContext.tsx";
import {useTheme} from "./themeContextBase.ts";

const ThemeProbe = () =>
{
    const {mode, resolvedMode} = useTheme();
    return <div data-testid="theme-value">{`${mode}:${resolvedMode}`}</div>;
};

describe("ThemeProvider defaults", () =>
{
    beforeEach(() =>
    {
        window.localStorage.clear();
        window.matchMedia = vi.fn().mockReturnValue({
            matches: true,
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
        });
    });

    afterEach(() =>
    {
        cleanup();
        vi.restoreAllMocks();
    });

    it("uses light mode when no preference has been selected", () =>
    {
        render(
            <ThemeProvider>
                <ThemeProbe/>
            </ThemeProvider>
        );

        expect(screen.getByTestId("theme-value").textContent).toBe("light:light");
    });

    it("follows the machine theme only after system mode is explicitly stored", () =>
    {
        window.localStorage.setItem("docuhyphen:theme:mode", "system");

        render(
            <ThemeProvider>
                <ThemeProbe/>
            </ThemeProvider>
        );

        expect(screen.getByTestId("theme-value").textContent).toBe("system:dark");
    });
});
