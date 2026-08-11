import {beforeEach, describe, expect, it, vi} from "vitest";

const get = vi.fn();
const patch = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        patch: (...args: unknown[]) => patch(...args),
    },
}));

describe("platformUserSubscriptionApi", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists and updates user subscription resources", async () =>
    {
        get.mockResolvedValueOnce({data: {total: 0, limit: 25, offset: 0, items: []}});
        patch.mockResolvedValueOnce({data: {}});
        const {fetchPlatformUserSubscriptions, updatePlatformUserSubscription} =
            await import("../platformUserSubscriptionApi.ts");

        await fetchPlatformUserSubscriptions("alex", 25, 0);
        const request = {
            planCode: "PERSONAL" as const,
            subscriptionStatus: "ACTIVE" as const,
            billingFrequency: "MONTHLY" as const,
            currentPeriodStart: "2026-08-01T00:00:00Z",
            currentPeriodEnd: "2026-09-01T00:00:00Z",
            gracePeriodEnd: null,
            changeReason: "Support correction",
        };
        await updatePlatformUserSubscription("user-1", request);

        expect(get).toHaveBeenCalledWith(
            "/platform/users/subscription-policies",
            {params: {query: "alex", limit: 25, offset: 0}},
        );
        expect(patch).toHaveBeenCalledWith(
            "/platform/users/user-1/subscription-policy",
            request,
        );
    });
});
