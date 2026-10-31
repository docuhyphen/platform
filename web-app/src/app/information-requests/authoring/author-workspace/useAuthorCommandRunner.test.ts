// @vitest-environment jsdom
import {act, cleanup, renderHook} from "@testing-library/react";
import {afterEach, expect, it, vi} from "vitest";
import {useAuthorCommandRunner} from "./useAuthorCommandRunner.ts";

afterEach(cleanup);

it("forgets a conflicting fingerprint and rereads before another attempt", async () => {
    const reload = vi.fn().mockResolvedValue(undefined);
    const command = vi.fn()
        .mockRejectedValueOnce({reasonCode: "COMMAND_RECEIPT_FINGERPRINT_CONFLICT", errorMessage: "Different details"})
        .mockResolvedValueOnce("recorded");
    const hook = renderHook(() => useAuthorCommandRunner("request-a", reload));

    await act(async () => {
        await hook.result.current.run("same-details", command);
    });
    expect(reload).toHaveBeenCalledOnce();
    await act(async () => {
        await hook.result.current.run("same-details", command);
    });

    expect(command.mock.calls[1][0]).not.toBe(command.mock.calls[0][0]);
});

it("keeps the same command key after a lost answer", async () => {
    const command = vi.fn().mockRejectedValueOnce("Connection interrupted").mockResolvedValueOnce("recorded");
    const hook = renderHook(() => useAuthorCommandRunner("request-a", vi.fn().mockResolvedValue(undefined)));

    await act(async () => {
        await hook.result.current.run("same-details", command);
    });
    await act(async () => {
        await hook.result.current.run("same-details", command);
    });

    expect(command.mock.calls[1][0]).toBe(command.mock.calls[0][0]);
});
