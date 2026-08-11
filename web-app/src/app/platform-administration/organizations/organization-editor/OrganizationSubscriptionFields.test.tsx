/** @vitest-environment jsdom */
import {render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import OrganizationSubscriptionFields from "./OrganizationSubscriptionFields.tsx";
import {useOrganizationEditor} from "./useOrganizationEditor.ts";

describe("OrganizationSubscriptionFields", () =>
{
    it("presents the organization tier as a Business-only dropdown", () =>
    {
        const editor = {
            tierCode: "BUSINESS",
            maxUsers: "50",
            subscriptionStatus: "ACTIVE",
            billingFrequency: "ANNUAL",
            currentPeriodStart: "2026-01-01T00:00",
            currentPeriodEnd: "2027-01-01T00:00",
            gracePeriodEnd: "",
            saving: false,
            setTierCode: vi.fn(),
            setMaxUsers: vi.fn(),
            setSubscriptionStatus: vi.fn(),
            setBillingFrequency: vi.fn(),
            setCurrentPeriodStart: vi.fn(),
            setCurrentPeriodEnd: vi.fn(),
            setGracePeriodEnd: vi.fn(),
        } as unknown as ReturnType<typeof useOrganizationEditor>;

        render(<OrganizationSubscriptionFields editor={editor}/>);

        const tierSelect = screen.getByRole("combobox", {name: /Tier code/}) as HTMLSelectElement;
        expect(tierSelect.tagName).toBe("SELECT");
        expect(Array.from(tierSelect.options).map((option) => option.value)).toEqual(["BUSINESS"]);
        expect(tierSelect.value).toBe("BUSINESS");
    });
});
