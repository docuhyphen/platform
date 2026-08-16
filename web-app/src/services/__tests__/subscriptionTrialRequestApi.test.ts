import {beforeEach, describe, expect, it, vi} from "vitest";

const get = vi.fn();
const post = vi.fn();
const patch = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        post: (...args: unknown[]) => post(...args),
        patch: (...args: unknown[]) => patch(...args),
    },
}));

describe("subscriptionTrialRequestApi", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("loads and creates the current Billing trial request", async () =>
    {
        get.mockResolvedValueOnce({data: {eligible: true, request: null}});
        post.mockResolvedValueOnce({data: {id: "request-1"}});
        const {
            createSubscriptionTrialRequest,
            fetchCurrentSubscriptionTrialRequest,
        } = await import("../subscriptionTrialRequestApi.ts");

        await fetchCurrentSubscriptionTrialRequest();
        await createSubscriptionTrialRequest();

        expect(get).toHaveBeenCalledWith("/subscription-trial-requests/current");
        expect(post).toHaveBeenCalledWith("/subscription-trial-requests", {});
    });

    it("lists and decides platform trial request resources", async () =>
    {
        get.mockResolvedValueOnce({data: {total: 0, limit: 25, offset: 0, items: []}});
        patch.mockResolvedValueOnce({data: {id: "request-1", status: "APPROVED"}});
        const {
            decidePlatformSubscriptionTrialRequest,
            fetchPlatformSubscriptionTrialRequests,
        } = await import("../subscriptionTrialRequestApi.ts");
        const decision = {status: "APPROVED" as const, durationDays: 14, reason: "Approved pilot"};

        await fetchPlatformSubscriptionTrialRequests("PENDING", 25, 0);
        await decidePlatformSubscriptionTrialRequest("request-1", decision);

        expect(get).toHaveBeenCalledWith(
            "/platform/subscription-trial-requests",
            {params: {status: "PENDING", limit: 25, offset: 0}},
        );
        expect(patch).toHaveBeenCalledWith(
            "/platform/subscription-trial-requests/request-1/status",
            decision,
        );
    });
});
