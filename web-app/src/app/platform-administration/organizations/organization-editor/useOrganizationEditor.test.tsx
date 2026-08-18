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
    tierCode: "BUSINESS",
    maxUsers: 50,
    subscriptionStatus: "ACTIVE",
    billingFrequency: "ANNUAL",
    currentPeriodStart: "2026-01-01T00:00:00Z",
    currentPeriodEnd: "2027-01-01T00:00:00Z",
    gracePeriodEnd: null,
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
        const refreshCurrentSession = vi.fn().mockResolvedValue(null);
        const {result} = renderHook(() => useOrganizationEditor(
            organization,
            onSaved,
            refreshCurrentSession,
        ));
        await waitFor(() => expect(result.current.active).toBe(true));

        act(() =>
        {
            result.current.setActive(false);
            result.current.setVerificationComplete(true);
            result.current.setChangeReason("Annual account review");
        });
        await act(async () =>
        {
            await result.current.save();
        });

        expect(apiMocks.updateStatus).toHaveBeenCalledWith("organization-1", {
            active: false,
            verificationComplete: true,
            changeReason: "Annual account review",
        });
        expect(refreshCurrentSession).toHaveBeenCalledTimes(1);
        expect(onSaved).toHaveBeenCalledTimes(1);
    });

    it("blocks audited organization subscription saves without a change reason", async () =>
    {
        const {result} = renderHook(() => useOrganizationEditor(
            organization,
            vi.fn(),
            vi.fn().mockResolvedValue(null),
        ));
        await waitFor(() => expect(result.current.tierCode).toBe("BUSINESS"));

        await act(async () =>
        {
            await result.current.save();
        });

        expect(result.current.error).toBe("Change reason is required for audited subscription updates.");
        expect(apiMocks.updateSubscriptionPolicy).not.toHaveBeenCalled();
    });

    it("validates seat and lifecycle requirements before calling the admin API", async () =>
    {
        const {result} = renderHook(() => useOrganizationEditor(
            organization,
            vi.fn(),
            vi.fn().mockResolvedValue(null),
        ));
        await waitFor(() => expect(result.current.tierCode).toBe("BUSINESS"));

        act(() =>
        {
            result.current.setChangeReason("Billing correction");
            result.current.setMaxUsers("0");
        });
        await act(async () =>
        {
            await result.current.save();
        });

        expect(result.current.error).toBe("Purchased seats must be a whole number greater than zero.");
        expect(apiMocks.updateSubscriptionPolicy).not.toHaveBeenCalled();

        act(() =>
        {
            result.current.setMaxUsers("25");
            result.current.setSubscriptionStatus("PAST_DUE");
            result.current.setGracePeriodEnd("");
        });
        await act(async () =>
        {
            await result.current.save();
        });

        expect(result.current.error).toBe("Past-due subscriptions require a grace period end.");
        expect(apiMocks.updateSubscriptionPolicy).not.toHaveBeenCalled();
    });

    it("persists organization subscription policy and refreshes the session after save", async () =>
    {
        const onSaved = vi.fn();
        const refreshCurrentSession = vi.fn().mockResolvedValue(null);
        const {result} = renderHook(() => useOrganizationEditor(
            organization,
            onSaved,
            refreshCurrentSession,
        ));
        await waitFor(() => expect(result.current.tierCode).toBe("BUSINESS"));

        act(() =>
        {
            result.current.setMaxUsers("75");
            result.current.setBillingFrequency("MONTHLY");
            result.current.setChangeReason("Contract updated");
        });
        await act(async () =>
        {
            await result.current.save();
        });

        expect(apiMocks.updateSubscriptionPolicy).toHaveBeenCalledWith("organization-1", {
            tierCode: "BUSINESS",
            maxUsers: 75,
            subscriptionStatus: "ACTIVE",
            billingFrequency: "MONTHLY",
            currentPeriodStart: new Date("2026-01-01T00:00").toISOString(),
            currentPeriodEnd: new Date("2027-01-01T00:00").toISOString(),
            gracePeriodEnd: null,
            changeReason: "Contract updated",
        });
        expect(apiMocks.updateEntitlements).toHaveBeenCalledWith("organization-1", {
            entitlements: [],
            changeReason: "Contract updated",
        });
        expect(refreshCurrentSession).toHaveBeenCalledTimes(1);
        expect(onSaved).toHaveBeenCalledTimes(1);
    });

    it("adds selected feature overrides and preserves their enabled state", async () =>
    {
        const organizationWithFeature = {
            ...organization,
            featureEntitlements: [{featureCode: "WORKFLOW_AUTOMATION", enabled: false}],
        };
        const {result} = renderHook(() => useOrganizationEditor(
            organizationWithFeature,
            vi.fn(),
            vi.fn().mockResolvedValue(null),
        ));
        await waitFor(() => expect(result.current.entitlements).toHaveLength(1));

        act(() =>
        {
            result.current.setEntitlementFeatureCodes([
                "WORKFLOW_AUTOMATION",
                "AUDIT_GOVERNANCE",
            ]);
        });

        expect(result.current.entitlements).toEqual([
            {featureCode: "WORKFLOW_AUTOMATION", enabled: false},
            {featureCode: "AUDIT_GOVERNANCE", enabled: true},
        ]);
    });
});
