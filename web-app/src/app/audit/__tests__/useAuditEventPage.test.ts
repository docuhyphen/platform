/** @vitest-environment jsdom */
import {describe, expect, it, vi} from "vitest";
import {renderHook, waitFor, act} from "@testing-library/react";
import {useAuditEventPage} from "../components/use-audit-event-page/useAuditEventPage.ts";
import {AuditEventDto, AuditEventPageDto} from "../../models/models.tsx";

const makeEvent = (eventId: string): AuditEventDto => ({
    eventId,
    category: "SECURITY",
    eventTypeKey: "user.login",
    outcome: "SUCCESS",
    occurredAt: "2024-01-01T00:00:00Z",
    recordedAt: "2024-01-01T00:00:01Z",
    ledgerTime: "2024-01-01T00:00:02Z",
    streamId: "stream-1",
    streamSequence: 1,
    actorKind: "USER",
    actorId: "user-1",
    targetType: "USER",
    targetId: "user-1",
    payload: {},
    eventHash: "hash-1",
});

describe("useAuditEventPage", () =>
{
    it("fetches the first page on mount", async () =>
    {
        const page: AuditEventPageDto = {items: [makeEvent("e1")], nextCursor: null};
        const fetchFn = vi.fn().mockResolvedValue(page);

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(fetchFn).toHaveBeenCalledTimes(1);
        expect(fetchFn).toHaveBeenCalledWith(undefined);
        expect(result.current.items).toEqual([makeEvent("e1")]);
        expect(result.current.cursor).toBeNull();
    });

    it("appends items and advances the cursor on loadMore", async () =>
    {
        const firstPage: AuditEventPageDto = {
            items: [makeEvent("e1")],
            nextCursor: {occurredAt: "2024-01-01T00:00:00Z", eventId: "e1"},
        };
        const secondPage: AuditEventPageDto = {items: [makeEvent("e2")], nextCursor: null};
        const fetchFn = vi.fn()
            .mockResolvedValueOnce(firstPage)
            .mockResolvedValueOnce(secondPage);

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() =>
        {
            result.current.loadMore();
        });

        await waitFor(() => expect(result.current.items).toHaveLength(2));

        expect(fetchFn).toHaveBeenLastCalledWith({cursorOccurredAt: "2024-01-01T00:00:00Z", cursorEventId: "e1"});
        expect(result.current.items.map((event) => event.eventId)).toEqual(["e1", "e2"]);
        expect(result.current.cursor).toBeNull();
    });

    it("clears items and refetches the first page on reset", async () =>
    {
        const firstPage: AuditEventPageDto = {items: [makeEvent("e1")], nextCursor: null};
        const resetPage: AuditEventPageDto = {items: [makeEvent("e9")], nextCursor: null};
        const fetchFn = vi.fn()
            .mockResolvedValueOnce(firstPage)
            .mockResolvedValueOnce(resetPage);

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() =>
        {
            result.current.reset();
        });

        await waitFor(() => expect(result.current.items).toEqual([makeEvent("e9")]));

        expect(fetchFn).toHaveBeenCalledTimes(2);
        expect(fetchFn).toHaveBeenLastCalledWith(undefined);
    });

    it("reports loading true while the fetch is in flight", async () =>
    {
        let resolvePage: (page: AuditEventPageDto) => void = () => {};
        const pending = new Promise<AuditEventPageDto>((resolve) =>
        {
            resolvePage = resolve;
        });
        const fetchFn = vi.fn().mockReturnValue(pending);

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        expect(result.current.loading).toBe(true);

        act(() =>
        {
            resolvePage({items: [], nextCursor: null});
        });

        await waitFor(() => expect(result.current.loading).toBe(false));
    });

    it("sets an error message when the fetch rejects", async () =>
    {
        const fetchFn = vi.fn().mockRejectedValue(new Error("boom"));

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.error).toBe("boom");
        expect(result.current.items).toEqual([]);
    });

    it("extracts the backend errorMessage when the fetch rejects with a ResponseError-shaped body, not an Error instance", async () =>
    {
        const fetchFn = vi.fn().mockRejectedValue({errorMessage: "Insufficient privileges", reasonCode: "FORBIDDEN"});

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.error).toBe("Insufficient privileges");
    });

    it("does not let a stale in-flight request overwrite a newer reset's results", async () =>
    {
        let resolveStale: (page: AuditEventPageDto) => void = () => {};
        const stalePending = new Promise<AuditEventPageDto>((resolve) =>
        {
            resolveStale = resolve;
        });
        const freshPage: AuditEventPageDto = {items: [makeEvent("fresh")], nextCursor: null};
        const fetchFn = vi.fn()
            .mockReturnValueOnce(stalePending)
            .mockResolvedValueOnce(freshPage);

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        act(() =>
        {
            result.current.reset();
        });

        await waitFor(() => expect(result.current.items).toEqual([makeEvent("fresh")]));

        await act(async () =>
        {
            resolveStale({items: [makeEvent("stale")], nextCursor: null});
            await Promise.resolve();
        });

        expect(result.current.items).toEqual([makeEvent("fresh")]);
        expect(result.current.loading).toBe(false);
    });

    it("does not let a stale in-flight request overwrite a newer loadMore's results", async () =>
    {
        const firstPage: AuditEventPageDto = {
            items: [makeEvent("e1")],
            nextCursor: {occurredAt: "2024-01-01T00:00:00Z", eventId: "e1"},
        };
        let resolveStaleMore: (page: AuditEventPageDto) => void = () => {};
        const staleMorePending = new Promise<AuditEventPageDto>((resolve) =>
        {
            resolveStaleMore = resolve;
        });
        const freshResetPage: AuditEventPageDto = {items: [makeEvent("reset-1")], nextCursor: null};
        const fetchFn = vi.fn()
            .mockResolvedValueOnce(firstPage)
            .mockReturnValueOnce(staleMorePending)
            .mockResolvedValueOnce(freshResetPage);

        const {result} = renderHook(() => useAuditEventPage(fetchFn));

        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() =>
        {
            result.current.loadMore();
        });

        act(() =>
        {
            result.current.reset();
        });

        await waitFor(() => expect(result.current.items).toEqual([makeEvent("reset-1")]));

        await act(async () =>
        {
            resolveStaleMore({items: [makeEvent("e1"), makeEvent("stale-more")], nextCursor: null});
            await Promise.resolve();
        });

        expect(result.current.items).toEqual([makeEvent("reset-1")]);
    });
});
