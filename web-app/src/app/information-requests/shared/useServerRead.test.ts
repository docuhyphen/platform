/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {useServerRead} from "./useServerRead.ts";

interface Pending<T> {
    promise: Promise<T>;
    answer: (value: T) => void;
    refuse: (reason: unknown) => void;
}

const pending = <T, >(): Pending<T> => {
    let answer: (value: T) => void = () => undefined;
    let refuse: (reason: unknown) => void = () => undefined;
    const promise = new Promise<T>((resolve, reject) => {
        answer = resolve;
        refuse = reject;
    });
    return {promise, answer, refuse};
};

const reads = <T, >(...queued: Pending<T>[]) => () => queued.shift()?.promise ?? new Promise<T>(() => undefined);

const message = (refusal: unknown) => (refusal as { errorMessage?: string }).errorMessage ?? "The read was refused.";

const renderRead = (read: () => Promise<string>, scope = "scope-a") =>
    renderHook(({current}) => useServerRead(current, read, message), {initialProps: {current: scope}});

describe("useServerRead", () => {
    afterEach(cleanup);

    it("shows nothing while the first read is pending and then its answer", async () => {
        const first = pending<string>();
        const hook = renderRead(reads(first));

        expect(hook.result.current).toMatchObject({value: null, error: null, loading: true});
        await act(async () => first.answer("first answer"));

        expect(hook.result.current).toMatchObject({value: "first answer", error: null, loading: false});
    });

    it("withholds the value once a reread is refused and states the refusal", async () => {
        const first = pending<string>();
        const refused = pending<string>();
        const hook = renderRead(reads(first, refused));
        await act(async () => first.answer("first answer"));

        const reread = hook.result.current.reload();
        await act(async () => refused.refuse({errorMessage: "Access was removed"}));
        await act(async () => reread);

        expect(hook.result.current).toMatchObject({value: null, error: "Access was removed", loading: false});
    });

    it("shows no earlier value while it reads again after a refusal and then the new answer", async () => {
        const first = pending<string>();
        const refused = pending<string>();
        const retried = pending<string>();
        const hook = renderRead(reads(first, refused, retried));
        await act(async () => first.answer("first answer"));
        await act(async () => {
            const reread = hook.result.current.reload();
            refused.refuse({errorMessage: "Access was removed"});
            await reread;
        });

        act(() => {
            void hook.result.current.reload();
        });

        expect(hook.result.current).toMatchObject({value: null, error: null, loading: true});
        await act(async () => retried.answer("retried answer"));
        expect(hook.result.current).toMatchObject({value: "retried answer", error: null, loading: false});
    });

    it("keeps the value while a reread of the same scope is pending", async () => {
        const first = pending<string>();
        const reread = pending<string>();
        const hook = renderRead(reads(first, reread));
        await act(async () => first.answer("first answer"));

        act(() => {
            void hook.result.current.reload();
        });

        expect(hook.result.current).toMatchObject({value: "first answer", loading: true});
        await act(async () => reread.answer("second answer"));
        expect(hook.result.current).toMatchObject({value: "second answer", loading: false});
    });

    it("withholds a value read for another scope and ignores that scope's late answer", async () => {
        const earlier = pending<string>();
        const next = pending<string>();
        const hook = renderRead(reads(earlier, next));

        hook.rerender({current: "scope-b"});
        await act(async () => earlier.answer("scope-a answer"));

        expect(hook.result.current).toMatchObject({value: null, loading: true});
        await act(async () => next.answer("scope-b answer"));
        expect(hook.result.current).toMatchObject({value: "scope-b answer", loading: false});
    });

    it("lets only the latest of overlapping reads of one scope settle the value", async () => {
        const first = pending<string>();
        const slow = pending<string>();
        const fast = pending<string>();
        const hook = renderRead(reads(first, slow, fast));
        await act(async () => first.answer("first answer"));

        act(() => {
            void hook.result.current.reload();
            void hook.result.current.reload();
        });
        await act(async () => fast.answer("latest answer"));
        await act(async () => slow.refuse({errorMessage: "An earlier read was refused"}));

        expect(hook.result.current).toMatchObject({value: "latest answer", error: null, loading: false});
    });

    it("ignores an earlier read of one scope that answers after the latest read", async () => {
        const first = pending<string>();
        const slow = pending<string>();
        const fast = pending<string>();
        const hook = renderRead(reads(first, slow, fast));
        await act(async () => first.answer("first answer"));

        act(() => {
            void hook.result.current.reload();
            void hook.result.current.reload();
        });
        await act(async () => fast.answer("latest answer"));
        await act(async () => slow.answer("earlier answer"));

        expect(hook.result.current).toMatchObject({value: "latest answer", error: null, loading: false});
    });

    it("settles a reload only after its read has answered", async () => {
        const first = pending<string>();
        const reread = pending<string>();
        const hook = renderRead(reads(first, reread));
        await waitFor(() => expect(hook.result.current.loading).toBe(true));
        await act(async () => first.answer("first answer"));
        let settled = false;

        act(() => {
            void hook.result.current.reload().then(() => {
                settled = true;
            });
        });
        await act(async () => Promise.resolve());
        expect(settled).toBe(false);
        await act(async () => reread.answer("second answer"));

        expect(settled).toBe(true);
    });
});
