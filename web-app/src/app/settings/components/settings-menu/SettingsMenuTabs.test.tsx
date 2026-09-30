/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import SettingsMenuTabs from "./SettingsMenuTabs.tsx";
import type {SettingsMenuProps} from "./SettingsMenu.tsx";
import {tabIds} from "../../settingsTabs.ts";

const props = (overrides: Partial<SettingsMenuProps> = {}): SettingsMenuProps => ({
    selectedValue: tabIds.profile,
    tabIds,
    hasOrg: false,
    canManageOrganization: false,
    canSeeBillingTab: true,
    canSeeOrganizationAdminTab: true,
    canSeeAuditTab: false,
    canUseDocumentLibrary: false,
    canUseBlueprints: false,
    canUseBusinessFields: false,
    canUseWorkflows: false,
    canUseVariables: false,
    canUseInformationRequests: false,
    registersOrganization: false,
    onTabSelect: vi.fn(),
    ...overrides,
});

describe("SettingsMenuTabs", () =>
{
    afterEach(() => cleanup());

    it("labels the organization entry Register your org for a person without an organization", () =>
    {
        render(<SettingsMenuTabs {...props({registersOrganization: true})}/>);

        expect(screen.getByRole("tab", {name: "Register your org"})).toBeTruthy();
        expect(screen.queryByRole("tab", {name: "Administration"})).toBeNull();
    });

    it("keeps the Administration label when there is nothing to register", () =>
    {
        render(<SettingsMenuTabs {...props()}/>);

        expect(screen.getByRole("tab", {name: "Administration"})).toBeTruthy();
    });

    it("lists Information Requests only when the person can use them", () =>
    {
        const {rerender} = render(<SettingsMenuTabs {...props()}/>);
        expect(screen.queryByRole("tab", {name: "Information Requests"})).toBeNull();

        rerender(<SettingsMenuTabs {...props({canUseInformationRequests: true})}/>);
        expect(screen.getByRole("tab", {name: "Information Requests"})).toBeTruthy();
    });
});
