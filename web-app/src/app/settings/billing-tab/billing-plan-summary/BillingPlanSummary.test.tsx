/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    EffectiveSubscriptionDto,
    PlanCode,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../../../models/models.tsx";
import BillingPlanSummary from "./BillingPlanSummary.tsx";
import {trialDaysRemaining} from "./BillingTrialDetails.tsx";

const subscriptionMock = vi.hoisted(() => ({value: null as EffectiveSubscriptionDto | null}));

vi.mock("../../../../hooks/subscription/useCurrentSubscription.ts", () => ({
    useCurrentSubscription: () => subscriptionMock.value,
}));

vi.mock("../trial-request-action/BillingTrialRequestAction.tsx", () => ({
    default: () => <div id={"test-billing-trial-request-action"}/>,
}));

const subscription = (
    planCode: PlanCode,
    ownerType: SubscriptionOwnerType,
    status: SubscriptionStatus,
    currentPeriodEnd: string | null,
): EffectiveSubscriptionDto => ({
    planCode,
    ownerType,
    ownerId: "owner-1",
    status,
    features: [],
    limits: {seatsArePurchased: ownerType === SubscriptionOwnerType.ORGANIZATION, seatCapacity: 5},
    usage: {activeSeats: 2},
    allowsMutations: status !== SubscriptionStatus.SUSPENDED,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
    currentPeriodEnd,
});

describe("BillingPlanSummary", () =>
{
    beforeEach(() =>
    {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-08-16T12:00:00Z"));
    });

    afterEach(() =>
    {
        cleanup();
        vi.useRealTimers();
    });

    it.each([
        [PlanCode.PERSONAL, SubscriptionOwnerType.USER, "Personal trial"],
        [PlanCode.BUSINESS, SubscriptionOwnerType.ORGANIZATION, "Business trial"],
    ])("labels an active %s subscription as a trial", (planCode, ownerType, label) =>
    {
        subscriptionMock.value = subscription(
            planCode,
            ownerType,
            SubscriptionStatus.TRIALING,
            "2026-08-18T12:00:00Z",
        );

        render(<BillingPlanSummary/>);

        expect(screen.getByText(label)).toBeTruthy();
        expect(screen.getByText(/Trial ends/)).toBeTruthy();
        expect(screen.getByText("2 days remaining")).toBeTruthy();
        expect(document.querySelector("#test-billing-trial-request-action")).toBeNull();
    });

    it("shows zero remaining days for an expired trial", () =>
    {
        subscriptionMock.value = subscription(
            PlanCode.PERSONAL,
            SubscriptionOwnerType.USER,
            SubscriptionStatus.TRIALING,
            "2026-08-15T12:00:00Z",
        );

        render(<BillingPlanSummary/>);

        expect(screen.getByText("0 days remaining")).toBeTruthy();
    });

    it("keeps ordinary Active subscriptions labeled as plans", () =>
    {
        subscriptionMock.value = subscription(
            PlanCode.PERSONAL,
            SubscriptionOwnerType.USER,
            SubscriptionStatus.ACTIVE,
            "2026-09-16T12:00:00Z",
        );

        render(<BillingPlanSummary/>);

        expect(screen.getByText("Personal plan")).toBeTruthy();
        expect(screen.queryByText(/days remaining/)).toBeNull();
        expect(document.querySelector("#test-billing-trial-request-action")).toBeTruthy();
    });

    it("clamps missing and invalid trial ends to zero remaining days", () =>
    {
        expect(trialDaysRemaining(null)).toBe(0);
        expect(trialDaysRemaining("not-a-date")).toBe(0);
    });
    it("states the plan's Information Request allowances", () =>
    {
        subscriptionMock.value = {
            ...subscription(PlanCode.PERSONAL, SubscriptionOwnerType.USER, SubscriptionStatus.ACTIVE, "2026-09-16T12:00:00Z"),
            limits: {
                seatsArePurchased: false,
                maxOpenInformationRequests: 25,
                maxActingPartiesPerInformationRequest: 10,
                maxEvidenceFilesPerInformationRequest: 100,
                maxEvidenceBytesPerInformationRequest: 250 * 1024 * 1024,
                maxCommittedEvidenceBytes: 5 * 1024 * 1024 * 1024,
            },
        };

        render(<BillingPlanSummary/>);

        const allowances = document.getElementById("settings-billing-information-request-allowances")?.textContent;
        expect(allowances).toContain("Up to 25 open Information Requests at a time");
        expect(allowances).toContain("10 acting parties, 100 evidence files, and 250 MiB per request");
        expect(allowances).toContain("5 GiB of evidence across requests");
    });

    it("states no Information Request allowances for a plan without them", () =>
    {
        subscriptionMock.value = {
            ...subscription(PlanCode.FREE, SubscriptionOwnerType.USER, SubscriptionStatus.ACTIVE, "2026-09-16T12:00:00Z"),
            limits: {
                seatsArePurchased: false,
                maxOpenInformationRequests: 0,
                maxActingPartiesPerInformationRequest: 0,
                maxEvidenceFilesPerInformationRequest: 0,
                maxEvidenceBytesPerInformationRequest: 0,
                maxCommittedEvidenceBytes: 0,
            },
        };

        render(<BillingPlanSummary/>);

        expect(document.getElementById("settings-billing-information-request-allowances")).toBeNull();
    });
});
