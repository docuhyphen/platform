/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
import UserSubscriptionsTable from "./UserSubscriptionsTable.tsx";

afterEach(cleanup);

const policy = (overrides: Partial<PlatformUserSubscriptionPolicy>): PlatformUserSubscriptionPolicy => ({
    appUserId: "00000000-0000-0000-0000-000000000001",
    email: "subscriber@example.test",
    displayName: "Subscriber",
    planCode: "FREE",
    subscriptionStatus: "ACTIVE",
    billingFrequency: null,
    currentPeriodStart: null,
    currentPeriodEnd: null,
    gracePeriodEnd: null,
    changeReason: null,
    createdDate: "2026-08-16T08:00:00Z",
    updatedDate: "2026-08-16T08:00:00Z",
    ...overrides,
});

const renderTable = (item: PlatformUserSubscriptionPolicy) => render(
    <UserSubscriptionsTable
        items={[item]}
        onEdit={vi.fn()}
        onTrial={vi.fn()}
        onEndTrial={vi.fn()}
        onConvertTrial={vi.fn()}/>,
);

describe("UserSubscriptionsTable", () =>
{
    it("allows a trial start for an eligible Free subscription", () =>
    {
        renderTable(policy({}));

        expect((screen.getByRole("button", {name: "Start trial"}) as HTMLButtonElement).disabled).toBe(false);
    });

    it("does not offer a trial start over a paid Personal lifecycle", () =>
    {
        renderTable(policy({planCode: "PERSONAL", billingFrequency: "MONTHLY"}));

        expect((screen.getByRole("button", {name: "Start trial"}) as HTMLButtonElement).disabled).toBe(true);
    });
});
