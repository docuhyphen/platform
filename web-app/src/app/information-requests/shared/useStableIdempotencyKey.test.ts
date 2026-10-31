/** @vitest-environment jsdom */
import {renderHook} from "@testing-library/react";
import {createElement, ReactNode} from "react";
import {describe, expect, it, vi} from "vitest";
import {CommandKeysContext, useStableIdempotencyKey} from "./useStableIdempotencyKey.ts";

const keysOf = (command: ReturnType<typeof vi.fn>): string[] => command.mock.calls.map(call => call[0] as string);

describe("useStableIdempotencyKey", () => {
    it("sends a retry of the same command under the key its failed attempt used, across renders", async () => {
        const {result, rerender} = renderHook(() => useStableIdempotencyKey());
        const command = vi.fn()
            .mockRejectedValueOnce(new Error("The connection was lost"))
            .mockResolvedValueOnce("recorded");

        await expect(result.current("review-a", {body: "Check the register"}, command)).rejects.toThrow("The connection was lost");
        rerender();
        await expect(result.current("review-a", {body: "Check the register"}, command)).resolves.toBe("recorded");

        const [first, retried] = keysOf(command);
        expect(first).toMatch(/\S/);
        expect(retried).toBe(first);
    });

    it("keys the same command afresh once an attempt was answered", async () => {
        const {result} = renderHook(() => useStableIdempotencyKey());
        const command = vi.fn().mockResolvedValue("recorded");

        await result.current("review-a", {body: "Check the register"}, command);
        await result.current("review-a", {body: "Check the register"}, command);

        const [first, next] = keysOf(command);
        expect(next).not.toBe(first);
    });

    it("keeps an unanswered command's key for a caller mounted again under the same command keys", async () => {
        const keys = new Map<string, string>();
        const wrapper = ({children}: {
            children: ReactNode
        }) => createElement(CommandKeysContext.Provider, {value: keys}, children);
        const command = vi.fn()
            .mockRejectedValueOnce(new Error("The connection was lost"))
            .mockResolvedValueOnce("recorded");
        const first = renderHook(() => useStableIdempotencyKey(), {wrapper});

        await expect(first.result.current("confirm-a", {decision: "ASSENTED"}, command)).rejects.toThrow("The connection was lost");
        first.unmount();
        const remounted = renderHook(() => useStableIdempotencyKey(), {wrapper});
        await expect(remounted.result.current("confirm-a", {decision: "ASSENTED"}, command)).resolves.toBe("recorded");

        const [sent, resent] = keysOf(command);
        expect(resent).toBe(sent);
        expect(keys.size).toBe(0);
    });

    it("keys the same command afresh for a caller under other command keys", async () => {
        const command = vi.fn().mockRejectedValue(new Error("The connection was lost"));
        const under = (keys: Map<string, string>) => renderHook(() => useStableIdempotencyKey(), {
            wrapper: ({children}: {
                children: ReactNode
            }) => createElement(CommandKeysContext.Provider, {value: keys}, children),
        });

        await under(new Map()).result.current("confirm-a", {decision: "ASSENTED"}, command).catch(() => undefined);
        await under(new Map()).result.current("confirm-a", {decision: "ASSENTED"}, command).catch(() => undefined);

        const [first, other] = keysOf(command);
        expect(other).not.toBe(first);
    });

    it("keys a changed body or another review afresh and keeps each unanswered command's own key", async () => {
        const {result} = renderHook(() => useStableIdempotencyKey());
        const command = vi.fn().mockRejectedValue(new Error("The connection was lost"));
        const attempt = (identity: string, body: string) =>
            result.current(identity, {body}, command).catch(() => undefined);

        await attempt("review-a", "Check the register");
        await attempt("review-a", "Check the second page");
        await attempt("review-b", "Check the register");
        await attempt("review-a", "Check the register");

        const [first, changedBody, otherReview, repeated] = keysOf(command);
        expect(new Set([first, changedBody, otherReview]).size).toBe(3);
        expect(repeated).toBe(first);
    });
});
