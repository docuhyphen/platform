/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {createElement, ReactNode} from "react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as runtime from "../../../services/informationRequestRuntimeService.ts";
import * as transport from "../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestAttestationDecision,
    InformationRequestResponseDisposition,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionStageOrdering,
} from "../../models/models.tsx";
import {CommandKeysContext} from "../shared/useStableIdempotencyKey.ts";
import {SubmissionState, useInformationRequestSubmission} from "./useInformationRequestSubmission.ts";

vi.mock("../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestSubmissionPreview: vi.fn(),
    submitInformationRequestPackage: vi.fn(),
    recordInformationRequestAttestation: vi.fn(),
    withdrawInformationRequestPackage: vi.fn(),
}));
vi.mock("../../../services/informationRequestRuntimeService.ts", () => ({
    patchInformationRequestResponses: vi.fn(),
}));

const preview = (stageKey = "first"): InformationRequestSubmissionPreviewDto => ({
    informationRequestId: "request-a",
    submissionMode: InformationRequestSubmissionMode.STAGED,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    scopeItems: [],
    submissionETag: `"submission:${stageKey}:hash"`,
    stageKey,
    ready: true,
    problems: [],
    undisclosedProblemCount: 0,
    groupProblems: [],
    attestations: [],
    stages: [
        {stageKey: "first", submitted: false, correctionOpen: false},
        {stageKey: "second", submitted: false, correctionOpen: false},
    ],
    canSubmit: true,
    correctionOpen: false,
    packages: [],
});

const refused = {reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "This summary is no longer available."};
const sessionEnded = {
    reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
    errorMessage: "Your verified session has ended.",
};

const interrupted = {isAxiosError: true, message: "Network Error"};
const recorded = {outcome: "SAVED", responseETag: "\"responses:2\"", data: {}};

type Command = "confirm" | "submit" | "withdraw";

const transportOf = (command: Command) => ({
    confirm: transport.recordInformationRequestAttestation,
    submit: transport.submitInformationRequestPackage,
    withdraw: transport.withdrawInformationRequestPackage,
})[command];

const run = (state: SubmissionState, command: Command): Promise<void | boolean> => {
    if (command === "confirm") return state.attest({
        requirementId: "assertion-a",
        decision: InformationRequestAttestationDecision.ASSENTED
    });
    if (command === "submit") return state.submit();
    return state.withdraw("package-a");
};

const keysSent = (command: Command): string[] =>
    vi.mocked(transportOf(command)).mock.calls.map(call => (call[call.length - 1] as {
        idempotencyKey: string
    }).idempotencyKey);

const deferred = <T, >() => {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>(answer => {
        resolve = answer;
    });
    return {promise, resolve};
};

describe("useInformationRequestSubmission", () => {
    beforeEach(() => vi.resetAllMocks());
    afterEach(cleanup);

    it("withholds an earlier preview and its commands when a reread is refused", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockResolvedValueOnce(preview())
            .mockRejectedValueOnce(refused);
        const view = renderHook(({etag}) => useInformationRequestSubmission("request-a", etag, undefined, vi.fn()), {
            initialProps: {etag: "\"responses:1\""},
        });
        await waitFor(() => expect(view.result.current.preview).not.toBeNull());

        view.rerender({etag: "\"responses:2\""});

        await waitFor(() => expect(view.result.current.error).toBe(refused.errorMessage));
        expect(view.result.current.preview).toBeNull();
        await act(() => view.result.current.submit());
        expect(transport.submitInformationRequestPackage).not.toHaveBeenCalled();
    });

    it("loads the preview again on Try again and clears the refusal once it loads", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockRejectedValueOnce(refused)
            .mockResolvedValueOnce(preview("second"));
        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, vi.fn()));
        await waitFor(() => expect(view.result.current.error).toBe(refused.errorMessage));

        await act(() => view.result.current.retry());

        expect(view.result.current.preview?.stageKey).toBe("second");
        expect(view.result.current.error).toBeNull();
    });

    it("asks the page to check access again when a preview read finds the access session ended", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockRejectedValueOnce(sessionEnded);
        const onChanged = vi.fn();

        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", "link-a", onChanged));

        await waitFor(() => expect(view.result.current.error).toBe(sessionEnded.errorMessage));
        expect(onChanged).toHaveBeenCalledTimes(1);
    });

    it("does not ask the page to check access again for any other refused preview read", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockRejectedValueOnce(refused);
        const onChanged = vi.fn();

        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, onChanged));

        await waitFor(() => expect(view.result.current.error).toBe(refused.errorMessage));
        expect(onChanged).not.toHaveBeenCalled();
    });

    it("keeps the most recently chosen part when an earlier part's read answers later", async () => {
        const first = deferred<InformationRequestSubmissionPreviewDto>();
        const second = deferred<InformationRequestSubmissionPreviewDto>();
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockResolvedValueOnce(preview())
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise);
        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, vi.fn()));
        await waitFor(() => expect(view.result.current.preview?.stageKey).toBe("first"));
        let choosingFirst!: Promise<void>;
        let choosingSecond!: Promise<void>;

        act(() => {
            choosingFirst = view.result.current.selectStage("first");
            choosingSecond = view.result.current.selectStage("second");
        });
        await act(async () => {
            second.resolve(preview("second"));
            await choosingSecond;
        });
        await act(async () => {
            first.resolve(preview("first"));
            await choosingFirst;
        });

        expect(view.result.current.preview?.stageKey).toBe("second");
        expect(view.result.current.selectedStageKey).toBe("second");
        expect(view.result.current.loading).toBe(false);
    });

    it("keeps waiting for the most recently chosen part when an earlier part's read answers first", async () => {
        const first = deferred<InformationRequestSubmissionPreviewDto>();
        const second = deferred<InformationRequestSubmissionPreviewDto>();
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockResolvedValueOnce(preview())
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise);
        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, vi.fn()));
        await waitFor(() => expect(view.result.current.preview?.stageKey).toBe("first"));
        let choosingFirst!: Promise<void>;
        let choosingSecond!: Promise<void>;

        act(() => {
            choosingFirst = view.result.current.selectStage("first");
            choosingSecond = view.result.current.selectStage("second");
        });
        await act(async () => {
            first.resolve(preview("first"));
            await choosingFirst;
        });

        expect(view.result.current.loading).toBe(true);
        expect(view.result.current.selectedStageKey).toBe("second");
        await act(() => view.result.current.submit());
        expect(transport.submitInformationRequestPackage).not.toHaveBeenCalled();
        await act(async () => {
            second.resolve(preview("second"));
            await choosingSecond;
        });
        expect(view.result.current.preview?.stageKey).toBe("second");
        expect(view.result.current.loading).toBe(false);
    });

    it("keeps the chosen part when the preview reloads after another response save", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockImplementation(async (_requestId, stageKey) => preview(stageKey));
        const view = renderHook(({etag}) => useInformationRequestSubmission("request-a", etag, undefined, vi.fn()), {
            initialProps: {etag: "\"responses:1\""},
        });
        await waitFor(() => expect(view.result.current.preview?.stageKey).toBe("first"));
        await act(() => view.result.current.selectStage("second"));

        view.rerender({etag: "\"responses:2\""});

        await waitFor(() => expect(transport.getInformationRequestSubmissionPreview).toHaveBeenCalledTimes(3));
        expect(transport.getInformationRequestSubmissionPreview).toHaveBeenLastCalledWith("request-a", "second", undefined);
        await waitFor(() => expect(view.result.current.loading).toBe(false));
        expect(view.result.current.preview?.stageKey).toBe("second");
    });

    it("holds submission while the chosen part is still loading and submits it once it has loaded", async () => {
        const second = deferred<InformationRequestSubmissionPreviewDto>();
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockResolvedValueOnce(preview())
            .mockReturnValueOnce(second.promise)
            .mockImplementation(async (_requestId, stageKey) => preview(stageKey));
        vi.mocked(transport.submitInformationRequestPackage).mockResolvedValue({outcome: "STALE"});
        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, vi.fn()));
        await waitFor(() => expect(view.result.current.preview?.stageKey).toBe("first"));
        let choosing!: Promise<void>;

        act(() => {
            choosing = view.result.current.selectStage("second");
        });
        expect(view.result.current.loading).toBe(true);
        expect(view.result.current.selectedStageKey).toBe("second");
        await act(() => view.result.current.submit());
        expect(transport.submitInformationRequestPackage).not.toHaveBeenCalled();

        await act(async () => {
            second.resolve(preview("second"));
            await choosing;
        });
        await act(() => view.result.current.submit());

        expect(transport.submitInformationRequestPackage).toHaveBeenCalledWith(
            "request-a",
            {stageKey: "second"},
            expect.objectContaining({expectedETag: "\"submission:second:hash\""}),
        );
    });

    it.each(["confirm", "submit", "withdraw"] as Command[])(
        "resends an interrupted %s under the key its first attempt used, then keys the next one afresh",
        async command => {
            vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
            vi.mocked(transportOf(command))
                .mockRejectedValueOnce(interrupted)
                .mockResolvedValueOnce(recorded as never)
                .mockResolvedValueOnce(recorded as never);
            const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", "link-a", vi.fn()));
            await waitFor(() => expect(view.result.current.preview).not.toBeNull());

            await act(() => run(view.result.current, command));
            expect(view.result.current.error).toBeTruthy();
            await act(() => run(view.result.current, command));
            expect(view.result.current.error).toBeNull();
            await act(() => run(view.result.current, command));

            const [first, retried, next] = keysSent(command);
            expect(retried).toBe(first);
            expect(next).not.toBe(first);
        },
    );

    it("resends an interrupted confirmation under its key after the summary is opened again with the same command keys", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        vi.mocked(transport.recordInformationRequestAttestation)
            .mockRejectedValueOnce(interrupted)
            .mockResolvedValueOnce(recorded as never);
        const keys = new Map<string, string>();
        const wrapper = ({children}: {
            children: ReactNode
        }) => createElement(CommandKeysContext.Provider, {value: keys}, children);
        const open = async () => {
            const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", "link-a", vi.fn()), {wrapper});
            await waitFor(() => expect(view.result.current.preview).not.toBeNull());
            return view;
        };

        const first = await open();
        await act(() => run(first.result.current, "confirm"));
        first.unmount();
        const reopened = await open();
        await act(() => run(reopened.result.current, "confirm"));

        const [sent, resent] = keysSent("confirm");
        expect(resent).toBe(sent);
        expect(reopened.result.current.error).toBeNull();
    });

    it("answers true only for a recorded withdrawal, so a stale or refused one keeps its dialog open", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        vi.mocked(transport.withdrawInformationRequestPackage)
            .mockResolvedValueOnce(recorded as never)
            .mockResolvedValueOnce({outcome: "STALE"})
            .mockRejectedValueOnce(refused);
        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, vi.fn()));
        await waitFor(() => expect(view.result.current.preview).not.toBeNull());
        const answers: boolean[] = [];

        for (const packageId of ["package-a", "package-b", "package-c"]) {
            await act(async () => {
                answers.push(await view.result.current.withdraw(packageId));
            });
        }

        expect(answers).toEqual([true, false, false]);
        expect(view.result.current.error).toBe(refused.errorMessage);
    });

    it("shows an ordinary staged preview with no message", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview("second"));

        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, vi.fn()));

        await waitFor(() => expect(view.result.current.preview?.stageKey).toBe("second"));
        expect(view.result.current.error).toBeNull();
        expect(view.result.current.loading).toBe(false);
    });
});

describe("useInformationRequestSubmission exceptions", () => {
    beforeEach(() => vi.resetAllMocks());
    afterEach(cleanup);

    it("records a confirmation exception as a response patch under the response ETag and tells the page", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        vi.mocked(runtime.patchInformationRequestResponses).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            data: [],
        });
        const onChanged = vi.fn();
        const view = renderHook(() => useInformationRequestSubmission("request-a", "\"responses:1\"", undefined, onChanged));
        await waitFor(() => expect(view.result.current.preview).not.toBeNull());

        let saved = false;
        await act(async () => {
            saved = await view.result.current.recordException({
                requirementId: "assertion-a",
                disposition: InformationRequestResponseDisposition.UNAVAILABLE,
                reason: "The register was lost in the move",
            });
        });

        expect(saved).toBe(true);
        expect(runtime.patchInformationRequestResponses).toHaveBeenCalledWith(
            "request-a",
            {
                patches: [{
                    requirementId: "assertion-a",
                    disposition: InformationRequestResponseDisposition.UNAVAILABLE,
                    narrative: "The register was lost in the move",
                }],
            },
            expect.objectContaining({expectedETag: "\"responses:1\""}),
        );
        expect(onChanged).toHaveBeenCalled();
    });
});
