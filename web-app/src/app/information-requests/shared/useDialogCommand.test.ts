/** @vitest-environment jsdom */
import {act, renderHook} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import {useDialogCommand} from "./useDialogCommand.ts";

const STALE = "This request changed while you were working. The latest details are shown; try again.";

describe("useDialogCommand", () => {
    it("closes the dialog only for a command that answered", async () => {
        const close = vi.fn();
        const {result} = renderHook(() => useDialogCommand(null, null));

        await act(() => result.current.run(async () => ({}), close));

        expect(close).toHaveBeenCalledTimes(1);
        expect(result.current.error).toBeNull();
    });

    it("keeps the dialog open with the page's notice when the command ended stale rather than refused", async () => {
        const close = vi.fn();
        const {result} = renderHook(() => useDialogCommand(null, STALE));

        await act(() => result.current.run(async () => null, close));

        expect(close).not.toHaveBeenCalled();
        expect(result.current.error).toBe(STALE);
    });

    it("prefers the command's refusal over a notice", async () => {
        const {result} = renderHook(() => useDialogCommand("The current policy refuses this change", STALE));

        await act(() => result.current.run(async () => null, vi.fn()));

        expect(result.current.error).toBe("The current policy refuses this change");
    });
});
