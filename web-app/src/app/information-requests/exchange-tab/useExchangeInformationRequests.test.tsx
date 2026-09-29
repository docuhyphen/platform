/** @vitest-environment jsdom */
import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestNextAction,
    InformationRequestState,
} from "../../models/models.tsx";
import {useExchangeInformationRequests} from "./useExchangeInformationRequests.ts";

vi.mock("../../../services/informationRequestAuthoringService.ts", () => ({getExchangeInformationRequests: vi.fn()}));

const retained = {
    id: "request-a",
    exchangeId: "exchange-a",
    title: "Periodic records request",
    state: InformationRequestState.CLOSED,
    completedCount: 5,
    requiredCount: 5,
    callerRoles: [],
    permissions: {canManage: false, canRespond: false, canReview: false},
    nextAction: InformationRequestNextAction.VIEW,
};

describe("useExchangeInformationRequests", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("shows the tab when the server lists a request or offers creation, whatever the viewer's plan", async () =>
    {
        vi.mocked(authoring.getExchangeInformationRequests)
            .mockResolvedValueOnce({requests: [retained], canCreate: false})
            .mockResolvedValueOnce({requests: [], canCreate: true})
            .mockResolvedValueOnce({requests: [], canCreate: false});

        const {result} = renderHook(() => useExchangeInformationRequests("exchange-a"));
        await waitFor(() => expect(result.current.listing?.requests).toHaveLength(1));
        expect(result.current.visible).toBe(true);

        await act(() => result.current.reload());
        expect(result.current.visible).toBe(true);
        await act(() => result.current.reload());
        expect(result.current.visible).toBe(false);
        expect(authoring.getExchangeInformationRequests).toHaveBeenCalledWith("exchange-a");
    });

    it("hides the tab when the listing is refused and loads nothing without an Exchange", async () =>
    {
        vi.mocked(authoring.getExchangeInformationRequests).mockRejectedValueOnce({errorMessage: "Access denied to list Information Requests"});

        const {result, rerender} = renderHook(({exchangeId}: {exchangeId?: string}) => useExchangeInformationRequests(exchangeId), {
            initialProps: {exchangeId: "exchange-a" as string | undefined},
        });
        await waitFor(() => expect(result.current.error).toBe("Access denied to list Information Requests"));
        expect(result.current.visible).toBe(false);

        rerender({exchangeId: undefined});
        await waitFor(() => expect(result.current.listing).toBeNull());
        expect(authoring.getExchangeInformationRequests).toHaveBeenCalledTimes(1);
    });
});
