/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestStandingReason,
    InformationRequestState,
} from "../../../models/models.tsx";
import {useOperationsRequestStanding} from "./useOperationsRequestStanding.ts";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestHeader: vi.fn()}));

const lapsedHeader = (id: string): InformationRequestDto => ({
    id,
    exchangeId: `exchange-${id}`,
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 1,
    createdAt: Date.parse("2026-10-12T08:00:00Z"),
    updatedAt: Date.parse("2026-10-12T08:00:00Z"),
    requestETag: `"aggregate:${id}:1"`,
    executionStanding: {
        kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
        reason: InformationRequestStandingReason.TRIAL_ENDED,
    },
    conditionEvaluations: [],
});

const unanswered = <T, >(): Promise<T> => new Promise<T>(() => undefined);

const renderStanding = (requestId: string) =>
    renderHook(({id}) => useOperationsRequestStanding(id), {initialProps: {id: requestId}});

describe("useOperationsRequestStanding", () => {
    beforeEach(() => vi.resetAllMocks());

    afterEach(cleanup);

    it("withholds one request's standing as soon as another request is shown", async () => {
        vi.mocked(transport.getInformationRequestHeader).mockImplementation(id =>
            id === "request-a" ? Promise.resolve(lapsedHeader(id)) : unanswered());
        const hook = renderStanding("request-a");
        await waitFor(() => expect(hook.result.current?.kind).toBe(InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE));

        hook.rerender({id: "request-b"});

        expect(hook.result.current).toBeNull();
    });

    it("shows no standing when reading the shown request is refused", async () => {
        vi.mocked(transport.getInformationRequestHeader).mockImplementation(async id => {
            if (id === "request-b") throw {errorMessage: "Reading this request is refused"};
            return lapsedHeader(id);
        });
        const hook = renderStanding("request-a");
        await waitFor(() => expect(hook.result.current).not.toBeNull());

        hook.rerender({id: "request-b"});
        await waitFor(() => expect(transport.getInformationRequestHeader).toHaveBeenCalledWith("request-b"));
        await act(() => new Promise<void>(resolve => setTimeout(resolve, 0)));

        expect(hook.result.current).toBeNull();
    });

    it("ignores a late standing for a request that is no longer shown", async () => {
        let answerEarlier: (header: InformationRequestDto) => void = () => undefined;
        vi.mocked(transport.getInformationRequestHeader).mockImplementation(id => id === "request-a"
            ? new Promise(resolve => {
                answerEarlier = resolve;
            })
            : unanswered());
        const hook = renderStanding("request-a");

        hook.rerender({id: "request-b"});
        await act(async () => answerEarlier(lapsedHeader("request-a")));

        expect(hook.result.current).toBeNull();
    });
});
