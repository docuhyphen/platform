/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import OrganizationTrialActions from "./OrganizationTrialActions.tsx";

afterEach(cleanup);

const renderActions = (billingFrequency: string) => render(
    <OrganizationTrialActions
        subscriptionStatus={"ACTIVE"}
        billingFrequency={billingFrequency}
        currentPeriodEnd={""}
        disabled={false}
        onManageTrial={vi.fn()}
        onEndTrial={vi.fn()}
        onConvertTrial={vi.fn()}/>,
);

describe("OrganizationTrialActions", () =>
{
    it("allows a trial start for an unbilled active organization", () =>
    {
        renderActions("");

        expect((screen.getByRole("button", {name: "Start Business trial"}) as HTMLButtonElement).disabled)
            .toBe(false);
    });

    it("does not offer a trial start over a paid organization lifecycle", () =>
    {
        renderActions("ANNUAL");

        expect((screen.getByRole("button", {name: "Start Business trial"}) as HTMLButtonElement).disabled)
            .toBe(true);
    });
});
