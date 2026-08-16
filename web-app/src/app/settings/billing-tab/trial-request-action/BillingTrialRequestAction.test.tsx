/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import BillingTrialRequestAction from "./BillingTrialRequestAction.tsx";

class TestResizeObserver
{
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

globalThis.ResizeObserver = TestResizeObserver;

const api = vi.hoisted(() => ({
    current: vi.fn(),
    create: vi.fn(),
}));

vi.mock("../../../../services/subscriptionTrialRequestApi.ts", () => ({
    fetchCurrentSubscriptionTrialRequest: api.current,
    createSubscriptionTrialRequest: api.create,
}));

describe("BillingTrialRequestAction", () =>
{
    beforeEach(() => vi.clearAllMocks());
    afterEach(cleanup);

    it("submits an eligible Personal trial request and shows pending status", async () =>
    {
        api.current.mockResolvedValue({eligible: true, ineligibilityReason: null, request: null});
        api.create.mockResolvedValue({id: "request-1", status: "PENDING"});
        render(<BillingTrialRequestAction ownerType={"USER"}/>);

        fireEvent.click(await screen.findByText("Request Personal trial"));

        await waitFor(() => expect(api.create).toHaveBeenCalledTimes(1));
        expect(await screen.findByText(/awaiting App Administrator review/)).toBeTruthy();
    });

    it("shows an existing pending Business request without another button", async () =>
    {
        api.current.mockResolvedValue({
            eligible: false,
            ineligibilityReason: "A trial request is awaiting review",
            request: {status: "PENDING"},
        });
        render(<BillingTrialRequestAction ownerType={"ORGANIZATION"}/>);

        expect(await screen.findByText(/Business trial request is awaiting/)).toBeTruthy();
        expect(screen.queryByText("Request Business trial")).toBeNull();
    });
});
