/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {useLoadedValue} from "./useLoadedValue.ts";

interface Pending<T> {
    load: () => Promise<T>;
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
    return {load: () => promise, answer, refuse};
};

const renderLoaded = (load: () => Promise<string>) =>
    renderHook(({current}) => useLoadedValue(current, "The value could not be loaded."), {initialProps: {current: load}});

describe("useLoadedValue", () => {
    afterEach(cleanup);

    it("withholds a value loaded by an earlier load until the current load answers", async () => {
        const next = pending<string>();
        const hook = renderLoaded(() => Promise.resolve("earlier"));
        await waitFor(() => expect(hook.result.current.value).toBe("earlier"));

        hook.rerender({current: next.load});

        expect(hook.result.current.value).toBeNull();
        await act(async () => next.answer("current"));
        expect(hook.result.current.value).toBe("current");
    });

    it("withholds an earlier load's refusal from the current load", async () => {
        const next = pending<string>();
        const hook = renderLoaded(() => Promise.reject({errorMessage: "The earlier read was refused."}));
        await waitFor(() => expect(hook.result.current.error).toBe("The earlier read was refused."));

        hook.rerender({current: next.load});

        expect(hook.result.current.error).toBeNull();
        expect(hook.result.current.value).toBeNull();
    });

    it("ignores a late answer from an earlier load", async () => {
        const earlier = pending<string>();
        const next = pending<string>();
        const hook = renderLoaded(earlier.load);

        hook.rerender({current: next.load});
        await act(async () => next.answer("current"));
        await act(async () => earlier.answer("earlier"));

        expect(hook.result.current.value).toBe("current");
    });

    it("keeps a value while another load of the same scope answers", async () => {
        const next = pending<string>();
        const hook = renderHook(({current}) => useLoadedValue(current, "The value could not be loaded.", "scope-a"),
            {initialProps: {current: () => Promise.resolve("first page")}});
        await waitFor(() => expect(hook.result.current.value).toBe("first page"));

        hook.rerender({current: next.load});

        expect(hook.result.current.value).toBe("first page");
        await act(async () => next.answer("second page"));
        expect(hook.result.current.value).toBe("second page");
    });

    it("withholds a value once its scope changes and loads for the new scope", async () => {
        let reads = 0;
        const next = pending<string>();
        const load = () => {
            reads += 1;
            return reads === 1 ? Promise.resolve("scope-a value") : next.load();
        };
        const hook = renderHook(({scope}) => useLoadedValue(load, "The value could not be loaded.", scope),
            {initialProps: {scope: "scope-a"}});
        await waitFor(() => expect(hook.result.current.value).toBe("scope-a value"));

        hook.rerender({scope: "scope-b"});

        expect(hook.result.current.value).toBeNull();
        expect(reads).toBe(2);
        await act(async () => next.answer("scope-b value"));
        expect(hook.result.current.value).toBe("scope-b value");
    });

    it("withholds the value once a reload of the same load is refused and while the next reload is pending", async () => {
        let reads = 0;
        const refused = pending<string>();
        const retried = pending<string>();
        const load = () => {
            reads += 1;
            if (reads === 1) return Promise.resolve("first");
            return reads === 2 ? refused.load() : retried.load();
        };
        const hook = renderLoaded(load);
        await waitFor(() => expect(hook.result.current.value).toBe("first"));

        act(() => hook.result.current.reload());
        await act(async () => refused.refuse({errorMessage: "The read was refused."}));

        expect(hook.result.current.value).toBeNull();
        expect(hook.result.current.error).toBe("The read was refused.");
        act(() => hook.result.current.reload());
        expect(hook.result.current.value).toBeNull();
        expect(hook.result.current.error).toBeNull();
        await act(async () => retried.answer("current"));
        expect(hook.result.current.value).toBe("current");
    });

    it("withholds the value of a scope once another load of that scope is refused", async () => {
        const next = pending<string>();
        const hook = renderHook(({current}) => useLoadedValue(current, "The value could not be loaded.", "scope-a"),
            {initialProps: {current: () => Promise.resolve("first page")}});
        await waitFor(() => expect(hook.result.current.value).toBe("first page"));

        hook.rerender({current: next.load});
        await act(async () => next.refuse({errorMessage: "The page read was refused."}));

        expect(hook.result.current.value).toBeNull();
        expect(hook.result.current.error).toBe("The page read was refused.");
    });

    it("keeps showing the value while the same load reloads, then shows the new answer", async () => {
        let reads = 0;
        const reloading = pending<string>();
        const load = () => {
            reads += 1;
            return reads === 1 ? Promise.resolve("first") : reloading.load();
        };
        const hook = renderLoaded(load);
        await waitFor(() => expect(hook.result.current.value).toBe("first"));

        act(() => hook.result.current.reload());

        expect(hook.result.current.value).toBe("first");
        await act(async () => reloading.answer("second"));
        expect(hook.result.current.value).toBe("second");
    });
});
