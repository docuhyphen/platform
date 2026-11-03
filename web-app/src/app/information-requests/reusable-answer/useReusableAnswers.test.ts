/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../services/informationRequestReuseService.ts";
import {useReusableAnswers} from "./useReusableAnswers.ts";

vi.mock("../../../services/informationRequestReuseService.ts", () => ({
    getInformationRequestAcceptedFactOffers: vi.fn(),
    recertifyInformationRequestAcceptedFact: vi.fn(),
}));

const sentKeys = (): (string | undefined)[] =>
    vi.mocked(transport.recertifyInformationRequestAcceptedFact).mock.calls.map(call => call[3].idempotencyKey);

const renderAnswers = async () => {
    const hook = renderHook(() => useReusableAnswers("request-a", "\"responses:1\"", "bootstrap"));
    await waitFor(() => expect(transport.getInformationRequestAcceptedFactOffers).toHaveBeenCalled());
    return hook;
};

describe("useReusableAnswers", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        vi.mocked(transport.getInformationRequestAcceptedFactOffers).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("sends explicit hidden answer clear confirmations with a new retry command", async () => {
        vi.mocked(transport.recertifyInformationRequestAcceptedFact).mockResolvedValue({outcome: "STALE"});
        const hook = await renderAnswers();
        await act(async () => void await hook.result.current.recertify("request-a", "fact-a", "requirement-a", "\"responses:1\"", ["hidden-a"]));
        expect(vi.mocked(transport.recertifyInformationRequestAcceptedFact).mock.calls[0][2]).toEqual({
            requirementId: "requirement-a", assented: true, confirmedHiddenResponseClearRequirementIds: ["hidden-a"],
        });
    });

    it("recovers an answer saved by an attempt whose reply was lost by sending the same command again", async () => {
        let committedKey = "";
        vi.mocked(transport.recertifyInformationRequestAcceptedFact).mockImplementation(async (_request, _fact, _body, options) => {
            if (!committedKey) {
                committedKey = options.idempotencyKey ?? "";
                throw {errorMessage: "The connection was lost"};
            }
            return options.idempotencyKey === committedKey
                ? {outcome: "SAVED", responseETag: "\"responses:2\"", data: {} as never}
                : {outcome: "STALE"};
        });
        const hook = await renderAnswers();

        await act(async () => {
            await expect(hook.result.current.recertify("request-a", "fact-a", "requirement-a", "\"responses:1\""))
                .rejects.toEqual({errorMessage: "The connection was lost"});
        });
        const retried = await act(async () =>
            hook.result.current.recertify("request-a", "fact-a", "requirement-a", "\"responses:1\""));

        const [first, second] = vi.mocked(transport.recertifyInformationRequestAcceptedFact).mock.calls;
        expect(second.slice(0, 3)).toEqual(first.slice(0, 3));
        expect(second[2]).toEqual({requirementId: "requirement-a", assented: true});
        expect(second[3]).toEqual({
            expectedETag: "\"responses:1\"",
            idempotencyKey: committedKey,
            accessLinkToken: "bootstrap"
        });
        expect(retried).toEqual({outcome: "SAVED", responseETag: "\"responses:2\""});
    });

    it("keys another fact, Requirement, target request, or response revision as a separate confirmation", async () => {
        vi.mocked(transport.recertifyInformationRequestAcceptedFact).mockRejectedValue({errorMessage: "The connection was lost"});
        const hook = await renderAnswers();
        const attempt = (requestId: string, factId: string, requirementId: string, responseETag: string) =>
            act(async () => {
                await hook.result.current.recertify(requestId, factId, requirementId, responseETag).catch((): undefined => undefined);
            });

        await attempt("request-a", "fact-a", "requirement-a", "\"responses:1\"");
        await attempt("request-a", "fact-b", "requirement-a", "\"responses:1\"");
        await attempt("request-a", "fact-a", "requirement-b", "\"responses:1\"");
        await attempt("request-b", "fact-a", "requirement-a", "\"responses:1\"");
        await attempt("request-a", "fact-a", "requirement-a", "\"responses:2\"");
        await attempt("request-a", "fact-a", "requirement-a", "\"responses:1\"");

        const keys = sentKeys();
        expect(new Set(keys.slice(0, 5)).size).toBe(5);
        expect(keys[5]).toBe(keys[0]);
    });

    it("keys the same confirmation afresh once an attempt was answered", async () => {
        vi.mocked(transport.recertifyInformationRequestAcceptedFact).mockResolvedValue({outcome: "STALE"});
        const hook = await renderAnswers();

        await act(async () => void await hook.result.current.recertify("request-a", "fact-a", "requirement-a", "\"responses:1\""));
        await act(async () => void await hook.result.current.recertify("request-a", "fact-a", "requirement-a", "\"responses:1\""));

        const [first, next] = sentKeys();
        expect(next).not.toBe(first);
    });

    it("drops the offers shown earlier when a reread is refused, states the refusal, and loads them again on reload", async () => {
        const offer = {requirementId: "requirement-a"} as never;
        vi.mocked(transport.getInformationRequestAcceptedFactOffers).mockResolvedValueOnce([offer]);
        const hook = renderHook(({responseETag}) => useReusableAnswers("request-a", responseETag, "bootstrap"),
            {initialProps: {responseETag: "\"responses:1\""}});
        await waitFor(() => expect(hook.result.current.offers).toEqual([offer]));
        vi.mocked(transport.getInformationRequestAcceptedFactOffers).mockRejectedValueOnce({errorMessage: "The offers could not be read."});

        hook.rerender({responseETag: "\"responses:2\""});

        await waitFor(() => expect(hook.result.current.error).toBe("The offers could not be read."));
        expect(hook.result.current.offers).toEqual([]);
        vi.mocked(transport.getInformationRequestAcceptedFactOffers).mockResolvedValueOnce([offer]);
        await act(async () => hook.result.current.reload());
        expect(hook.result.current.offers).toEqual([offer]);
        expect(hook.result.current.error).toBeNull();
    });
});

