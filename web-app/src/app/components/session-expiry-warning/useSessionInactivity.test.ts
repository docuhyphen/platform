/** @vitest-environment jsdom */
import {act, cleanup, renderHook} from "@testing-library/react";
import {createMocks} from "react-idle-timer";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {CurrentSessionDto} from "../../models/models.tsx";
import {useSessionInactivity} from "./useSessionInactivity.ts";

class BroadcastChannelMock
{
    readonly name: string;
    onmessage: ((event: MessageEvent) => void) | null = null;
    onmessageerror: ((event: MessageEvent) => void) | null = null;

    constructor(name: string)
    {
        this.name = name;
    }

    postMessage(message: unknown): void { void message; }
    close(): void {}
    addEventListener(): void {}
    removeEventListener(): void {}
    dispatchEvent(): boolean { return true; }
}

const createSession = (
    idleTimeoutMinutes: number,
    idleExpiresInMs: number,
    sessionExpiresInMs: number | null = null,
): CurrentSessionDto =>
{
    const now = Date.now();
    return {
        userId: "user-1",
        email: "user@example.test",
        appRoles: [],
        activeOrganizationId: null,
        organizationRoles: [],
        capabilities: [],
        availableOrganizations: [],
        idleTimeoutMinutes,
        serverTimeEpochMs: now,
        idleExpiresAtEpochMs: now + idleExpiresInMs,
        sessionExpiresAtEpochMs: sessionExpiresInMs === null ? null : now + sessionExpiresInMs,
        subscription: null,
    };
};

describe("useSessionInactivity", () =>
{
    beforeEach(() =>
    {
        vi.useFakeTimers();
        createMocks();
        Object.defineProperty(window, "BroadcastChannel", {
            configurable: true,
            writable: true,
            value: BroadcastChannelMock,
        });
        vi.setSystemTime(new Date("2026-07-13T12:00:00Z"));
        localStorage.clear();
    });

    afterEach(() =>
    {
        cleanup();
        vi.useRealTimers();
    });

    it("opens the warning one minute before authoritative idle expiry and expires at zero", async () =>
    {
        const currentSession = createSession(2, 120_000);
        const onExpire = vi.fn().mockResolvedValue(undefined);
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue: vi.fn().mockResolvedValue(currentSession),
            onExpire,
        }));

        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        act(() => vi.advanceTimersByTime(1_000));
        expect(result.current.isWarningOpen).toBe(true);
        expect(result.current.secondsRemaining).toBe(60);

        await act(async () => vi.advanceTimersByTime(60_000));
        expect(onExpire).toHaveBeenCalledWith("INACTIVITY_TIMEOUT");
    });

    it("supports a one-minute inactivity policy", () =>
    {
        const currentSession = createSession(1, 60_000);
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue: vi.fn().mockResolvedValue(currentSession),
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(0));

        expect(result.current.isWarningOpen).toBe(true);
        expect(result.current.secondsRemaining).toBe(60);
    });

    it("uses the server acknowledgement to extend the idle deadline", async () =>
    {
        const currentSession = createSession(2, 120_000);
        const onContinue = vi.fn().mockImplementation(() => Promise.resolve(createSession(2, 120_000)));
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue,
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(30_000));
        await act(async () =>
        {
            document.dispatchEvent(new KeyboardEvent("keydown"));
            await Promise.resolve();
        });
        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        act(() => vi.advanceTimersByTime(1_000));
        expect(result.current.isWarningOpen).toBe(true);
        expect(onContinue).toHaveBeenCalledTimes(1);
    });

    it("detects wheel activity", async () =>
    {
        const currentSession = createSession(2, 120_000);
        const onContinue = vi.fn().mockImplementation(() => Promise.resolve(createSession(2, 120_000)));
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue,
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(30_000));
        await act(async () =>
        {
            document.dispatchEvent(new WheelEvent("wheel"));
            await Promise.resolve();
        });
        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);
    });

    it("detects non-bubbling scroll activity from nested panels", async () =>
    {
        const currentSession = createSession(2, 120_000);
        const onContinue = vi.fn().mockImplementation(() => Promise.resolve(createSession(2, 120_000)));
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue,
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));
        const scrollPanel = document.createElement("div");
        document.body.appendChild(scrollPanel);

        act(() => vi.advanceTimersByTime(30_000));
        await act(async () =>
        {
            scrollPanel.dispatchEvent(new Event("scroll", {bubbles: false}));
            await Promise.resolve();
        });
        act(() => vi.advanceTimersByTime(59_000));
        expect(result.current.isWarningOpen).toBe(false);

        scrollPanel.remove();
    });

    it("does not extend the authoritative deadline when the activity heartbeat fails", async () =>
    {
        const currentSession = createSession(2, 120_000);
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue: vi.fn().mockRejectedValue(new Error("offline")),
            onExpire: vi.fn().mockResolvedValue(undefined),
        }));

        act(() => vi.advanceTimersByTime(30_000));
        await act(async () =>
        {
            document.dispatchEvent(new KeyboardEvent("keydown"));
            await Promise.resolve();
        });
        act(() => vi.advanceTimersByTime(30_000));

        expect(result.current.isWarningOpen).toBe(true);
    });

    it("warns before the absolute session deadline", async () =>
    {
        const currentSession = createSession(10, 600_000, 120_000);
        const onExpire = vi.fn().mockResolvedValue(undefined);
        const {result} = renderHook(() => useSessionInactivity({
            currentSession,
            onContinue: vi.fn().mockResolvedValue(currentSession),
            onExpire,
        }));

        act(() => vi.advanceTimersByTime(60_000));

        expect(result.current.isWarningOpen).toBe(true);
        expect(result.current.warningReason).toBe("SESSION_EXPIRED");

        await act(async () => vi.advanceTimersByTime(60_000));
        expect(onExpire).toHaveBeenCalledWith("SESSION_EXPIRED");
    });
});
