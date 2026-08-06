/** @vitest-environment jsdom */
import {act, renderHook} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {useDocumentCommentUnreadCount} from "../useDocumentCommentUnreadCount.ts";

const mocks = vi.hoisted(() => ({
    handler: undefined as undefined | ((message: {exchangeId?: string; documentId?: string; userId?: string}) => void),
}));

vi.mock("../../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({appUser: {id: "current-user"}}),
}));

vi.mock("../../../../../services/NotificationService.tsx", () => ({
    realtimeService: {
        on: vi.fn((_type: string, handler: typeof mocks.handler) =>
        {
            mocks.handler = handler;
            return vi.fn();
        }),
    },
}));

beforeEach(() =>
{
    mocks.handler = undefined;
});

describe("useDocumentCommentUnreadCount", () =>
{
    it("counts matching events while closed and clears when opened", () =>
    {
        const hook = renderHook(
            ({open}) => useDocumentCommentUnreadCount("exchange-1", "document-1", open),
            {initialProps: {open: false}}
        );

        act(() => mocks.handler?.({exchangeId: "exchange-1", documentId: "document-1"}));
        act(() => mocks.handler?.({exchangeId: "exchange-2", documentId: "document-1"}));
        expect(hook.result.current).toBe(1);

        hook.rerender({open: true});
        expect(hook.result.current).toBe(0);
    });

    it("ignores events authored by the current user", () =>
    {
        const hook = renderHook(
            ({open}) => useDocumentCommentUnreadCount("exchange-1", "document-1", open),
            {initialProps: {open: false}}
        );

        act(() => mocks.handler?.({exchangeId: "exchange-1", documentId: "document-1", userId: "current-user"}));
        expect(hook.result.current).toBe(0);

        act(() => mocks.handler?.({exchangeId: "exchange-1", documentId: "document-1", userId: "other-user"}));
        expect(hook.result.current).toBe(1);
    });
});
