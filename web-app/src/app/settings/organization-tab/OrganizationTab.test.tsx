/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import OrganizationTab from "./OrganizationTab.tsx";

vi.mock("../../../context/AuthContext", () => ({
    useAuth: () => ({
        appUser: {id: "user-1", person: null, organizationRoles: []},
        token: null,
        appUserPersonOrganization: null,
    }),
}));

vi.mock("../../onboarding/organization-onboarding/OrganizationOnboardingForm.tsx", () => ({
    default: () => <form id={"organization-registration-form-stub"}
                         aria-label={"Organization registration"}/>,
}));

describe("OrganizationTab", () =>
{
    afterEach(() => cleanup());

    it("shows the registration form in place for a person without an organization", () =>
    {
        render(<OrganizationTab/>);

        const action = document.getElementById("organization-empty-state-action");
        expect(action?.querySelector("#organization-registration-form-stub")).not.toBeNull();
        expect(screen.queryByRole("button", {name: "Register your organization"})).toBeNull();
        expect(screen.queryByRole("dialog")).toBeNull();
    });
});
