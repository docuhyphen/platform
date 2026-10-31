/** @vitest-environment jsdom */
import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import * as api from "../../../services/platformUserSubscriptionApi.ts";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
import {useUserSubscriptions} from "./useUserSubscriptions.ts";

vi.mock("../../../services/platformUserSubscriptionApi.ts", () => ({
    fetchPlatformUserSubscriptions: vi.fn(),
    fetchPlatformUserFeatureEntitlements: vi.fn(),
    updatePlatformUserFeatureEntitlements: vi.fn(),
    updatePlatformUserSubscription: vi.fn(),
    startPlatformUserSubscriptionTrial: vi.fn(),
    extendPlatformUserSubscriptionTrial: vi.fn(),
    endPlatformUserSubscriptionTrial: vi.fn(),
    convertPlatformUserSubscriptionTrial: vi.fn(),
}));

const user: PlatformUserSubscriptionPolicy = {
    appUserId: "user-1",
    email: "tier-user@example.com",
    displayName: null,
    planCode: "PERSONAL",
    subscriptionStatus: "ACTIVE",
    billingFrequency: "MONTHLY",
    currentPeriodStart: null,
    currentPeriodEnd: null,
    gracePeriodEnd: null,
    changeReason: null,
    createdDate: "2026-08-11T00:00:00Z",
    updatedDate: "2026-08-11T00:00:00Z",
};

describe("useUserSubscriptions", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.fetchPlatformUserSubscriptions).mockResolvedValue({
            total: 1,
            limit: 25,
            offset: 0,
            items: [user]
        });
    });

    it("reads an account's feature overrides and replaces them with the audited reason", async () => {
        vi.mocked(api.fetchPlatformUserFeatureEntitlements).mockResolvedValue({
            appUserId: "user-1",
            email: "tier-user@example.com",
            displayName: null,
            entitlements: [{featureCode: "INFORMATION_REQUESTS", enabled: true}],
        });
        vi.mocked(api.updatePlatformUserFeatureEntitlements).mockResolvedValue({
            appUserId: "user-1",
            email: "tier-user@example.com",
            displayName: null,
            entitlements: [],
        });
        const {result} = renderHook(() => useUserSubscriptions());
        await waitFor(() => expect(result.current.items).toEqual([user]));

        expect(await result.current.loadFeatureOverrides(user)).toEqual([{
            featureCode: "INFORMATION_REQUESTS",
            enabled: true
        }]);
        await act(async () => {
            await result.current.saveFeatureOverrides(user, [], "Back to the plan");
        });

        expect(api.fetchPlatformUserFeatureEntitlements).toHaveBeenCalledWith("user-1");
        expect(api.updatePlatformUserFeatureEntitlements).toHaveBeenCalledWith("user-1", {
            entitlements: [],
            changeReason: "Back to the plan",
        });
    });
});
