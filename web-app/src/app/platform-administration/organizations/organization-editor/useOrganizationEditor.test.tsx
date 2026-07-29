/** @vitest-environment jsdom */
import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {PlatformOrganizationSummary} from "../../../../services/types/platformOrganizations.ts";
import {useOrganizationEditor} from "./useOrganizationEditor.ts";

const apiMocks = vi.hoisted(() => ({
    updateEntitlements: vi.fn(),
    updateStatus: vi.fn(),
    updateSubscriptionPolicy: vi.fn(),
}));

vi.mock("../../../../services/platformOrganizationApi.ts", () => ({
    updatePlatformOrganizationFeatureEntitlements: apiMocks.updateEntitlements,
    updatePlatformOrganizationStatus: apiMocks.updateStatus,
    updatePlatformOrganizationSubscriptionPolicy: apiMocks.updateSubscriptionPolicy,
}));

const organization: PlatformOrganizationSummary = {
    organizationId: "organization-1",
    name: "Acme",
    registrationNumber: "REG-1",
    active: true,
    verificationComplete: false,
    createdDate: "2026-07-28T00:00:00Z",
    tierCode: "PRO",
    maxUsers: 50,
    activeUsers: 12,
    featureEntitlements: [],
};

describe("useOrganizationEditor", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        apiMocks.updateEntitlements.mockResolvedValue({});
        apiMocks.updateStatus.mockResolvedValue({});
        apiMocks.updateSubscriptionPolicy.mockResolvedValue({});
    });

    it("persists changed active and verification values on save", async () =>
    {
        const onSaved = vi.fn();
        const {result} = renderHook(() => useOrganizationEditor(organization, onSaved));
        await waitFor(() => expect(result.current.active).toBe(true));

        act(() =>
        {
            result.current.setActive(false);
            result.current.setVerificationComplete(true);
        });
        await act(async () =>
        {
            await result.current.save();
        });

        expect(apiMocks.updateStatus).toHaveBeenCalledWith("organization-1", {
            active: false,
            verificationComplete: true,
            changeReason: undefined,
        });
        expect(onSaved).toHaveBeenCalledTimes(1);
    });
});
