/** @vitest-environment jsdom */
import {act, renderHook} from "@testing-library/react";
import {createMocks} from "react-idle-timer";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {useSessionInactivity} from "./useSessionInactivity.ts";

describe("useSessionInactivity", () =>
{
    beforeEach(() =>
    {
        vi.useFakeTimers();
        createMocks();
        vi.setSystemTime(new Date("2026-07-13T12:00:00Z"));
        localStorage.clear();
    });

    afterEach(() =>
    {
        vi.useRealTimers();
    });

    it("opens the warning one minute before expiry and expires at zero", async () =>
    {
        const onContinue = vi.fn().mockResolvedValue(undefined);
        const onExpire = vi.fn().mockResolvedValue(undefined);
        const {result} = renderHook(() => useSessionInactivity({
            userId: "user-1",
            idleTimeoutMinutes: 2,
            onContinue,
            onExpire,
        }));

        expect(result.current.isWarningOpen).toBe(false);

        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        await act(async () => vi.advanceTimersByTime(1_000));
        expect(result.current.isWarningOpen).toBe(true);
        expect(result.current.secondsRemaining).toBe(60);

        await act(async () => vi.advanceTimersByTime(60_000));
        expect(onExpire).toHaveBeenCalledTimes(1);
    });

    it("supports a one-minute inactivity policy", () =>
    {
        const {result} = renderHook(() => useSessionInactivity({
            userId: "user-minimum-timeout",
            idleTimeoutMinutes: 1,
            onContinue: vi.fn().mockResolvedValue(undefined),
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(1));

        expect(result.current.isWarningOpen).toBe(true);
        expect(result.current.secondsRemaining).toBe(60);
    });

    it("resets the warning schedule when the user is active", () =>
    {
        const {result} = renderHook(() => useSessionInactivity({
            userId: "user-2",
            idleTimeoutMinutes: 2,
            onContinue: vi.fn().mockResolvedValue(undefined),
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(30_000));
        act(() => document.dispatchEvent(new KeyboardEvent("keydown")));
        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        act(() => vi.advanceTimersByTime(1_000));
        expect(result.current.isWarningOpen).toBe(true);
    });

    it("resets the warning schedule for wheel activity", () =>
    {
        const {result} = renderHook(() => useSessionInactivity({
            userId: "user-3",
            idleTimeoutMinutes: 2,
            onContinue: vi.fn().mockResolvedValue(undefined),
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(30_000));
        act(() => document.dispatchEvent(new WheelEvent("wheel")));
        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        act(() => vi.advanceTimersByTime(1_000));
        expect(result.current.isWarningOpen).toBe(true);
    });

    it("resets the warning schedule when a nested panel scrolls", () =>
    {
        const {result} = renderHook(() => useSessionInactivity({
            userId: "user-4",
            idleTimeoutMinutes: 2,
            onContinue: vi.fn().mockResolvedValue(undefined),
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        const scrollPanel = document.createElement("div");
        document.body.appendChild(scrollPanel);

        act(() => vi.advanceTimersByTime(30_000));
        act(() => scrollPanel.dispatchEvent(new Event("scroll", {bubbles: false})));
        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        act(() => vi.advanceTimersByTime(1_000));
        expect(result.current.isWarningOpen).toBe(true);

        scrollPanel.remove();
    });
});
