import {beforeEach, describe, expect, it, vi} from "vitest";

const get = vi.fn();
const patch = vi.fn();
const post = vi.fn();
const deleteRequest = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        patch: (...args: unknown[]) => patch(...args),
        post: (...args: unknown[]) => post(...args),
        delete: (...args: unknown[]) => deleteRequest(...args),
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

    it("starts and extends a Personal trial through the trial resources", async () =>
    {
        post.mockResolvedValueOnce({data: {ownerId: "user-1"}});
        patch.mockResolvedValueOnce({data: {ownerId: "user-1"}});
        const {
            extendPlatformUserSubscriptionTrial,
            startPlatformUserSubscriptionTrial,
        } = await import("../platformUserSubscriptionApi.ts");
        const startRequest = {
            planCode: "PERSONAL" as const,
            durationDays: 14,
            reason: "Sales demonstration",
        };
        const extensionRequest = {
            currentPeriodEnd: "2026-09-10T00:00:00Z",
            reason: "Implementation extension",
        };

        await startPlatformUserSubscriptionTrial("user-1", startRequest);
        await extendPlatformUserSubscriptionTrial("user-1", extensionRequest);

        expect(post).toHaveBeenCalledWith(
            "/platform/users/user-1/subscription-trials",
            startRequest,
        );
        expect(patch).toHaveBeenCalledWith(
            "/platform/users/user-1/subscription-trials/current",
            extensionRequest,
        );
    });

    it("ends and converts a Personal trial through dedicated transition resources", async () =>
    {
        deleteRequest.mockResolvedValueOnce({data: {ownerId: "user-1"}});
        post.mockResolvedValueOnce({data: {ownerId: "user-1"}});
        const {
            convertPlatformUserSubscriptionTrial,
            endPlatformUserSubscriptionTrial,
        } = await import("../platformUserSubscriptionApi.ts");
        const endRequest = {reason: "Customer ended evaluation"};
        const conversionRequest = {
            billingFrequency: "MONTHLY" as const,
            currentPeriodEnd: "2026-09-16T00:00:00Z",
            reason: "Customer subscribed",
        };

        await endPlatformUserSubscriptionTrial("user-1", endRequest);
        await convertPlatformUserSubscriptionTrial("user-1", conversionRequest);

        expect(deleteRequest).toHaveBeenCalledWith(
            "/platform/users/user-1/subscription-trials/current",
            {data: endRequest},
        );
        expect(post).toHaveBeenCalledWith(
            "/platform/users/user-1/subscription-trials/current/conversions",
            conversionRequest,
        );
    });
});
