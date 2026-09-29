/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDto,
    InformationRequestClockType,
    InformationRequestOwnerType,
} from "../../../models/models.tsx";
import ClockPoliciesPanel from "./ClockPoliciesPanel.tsx";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestClockPolicies: vi.fn(),
    defineInformationRequestClockPolicy: vi.fn(),
    publishInformationRequestClockPolicyVersion: vi.fn(),
}));

const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

const policy: InformationRequestClockPolicyDto = {
    id: "policy-a",
    policyKey: "standard-response",
    displayName: "Standard response",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    versions: [
        {
            id: "version-1",
            versionNumber: 1,
            clockType: InformationRequestClockType.CALENDAR,
            businessTimezone: "UTC",
            standardDurationMinutes: 1440,
            urgentDurationMinutes: 240,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods: [],
            holidays: [],
            reminderMinutesBeforeDue: [],
            publishedAt: "2026-08-01T08:00:00Z",
        },
        {
            id: "version-2",
            versionNumber: 2,
            clockType: InformationRequestClockType.BUSINESS,
            businessTimezone: "Africa/Johannesburg",
            standardDurationMinutes: 2880,
            urgentDurationMinutes: 480,
            dueEffect: InformationRequestClockDueEffect.EXPIRE_REQUEST,
            workingPeriods: weekdays.map(dayOfWeek => ({dayOfWeek, startMinute: 540, endMinute: 1020})),
            holidays: ["2026-12-25"],
            reminderMinutesBeforeDue: [1440, 240],
            publishedAt: "2026-09-01T08:00:00Z",
        },
    ],
};

const renderPanel = (canManage = true) => render(<ClockPoliciesPanel canManage={canManage}/>);

describe("ClockPoliciesPanel", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(administration.getInformationRequestClockPolicies).mockResolvedValue([policy]);
        vi.mocked(administration.defineInformationRequestClockPolicy).mockResolvedValue(policy);
        vi.mocked(administration.publishInformationRequestClockPolicyVersion).mockResolvedValue(policy);
    });

    afterEach(cleanup);

    it("lists each policy with its latest version in words", async () =>
    {
        renderPanel();

        const row = (await screen.findByText("Standard response")).closest("li") as HTMLElement;
        expect(within(row).getByText("Version 2 of 2, key standard-response")).toBeTruthy();
        expect(within(row).getByText(
            "Business hours in Africa/Johannesburg. Standard 48 hours, urgent 8 hours. " +
            "Reminders 24 hours and 4 hours before due. When due, the request expires.",
        )).toBeTruthy();
    });

    it("names every control and region of the policies and their form", async () =>
    {
        renderPanel();
        fireEvent.click(await screen.findByRole("button", {name: "Publish a new version of Standard response"}));
        await screen.findByRole("dialog", {name: "New version of Standard response"});

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("creates a calendar policy from the form in minutes", async () =>
    {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = await screen.findByRole("dialog", {name: "New due date policy"});
        fireEvent.change(within(dialog).getByLabelText(/Key/), {target: {value: "Quick Reply"}});
        expect(within(dialog).getByText("Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter or digit.")).toBeTruthy();
        fireEvent.change(within(dialog).getByLabelText(/Key/), {target: {value: "quick-reply"}});
        fireEvent.change(within(dialog).getByLabelText(/Name/), {target: {value: "Quick reply"}});
        fireEvent.change(within(dialog).getByLabelText(/Time zone/), {target: {value: "Europe/London"}});
        fireEvent.change(within(dialog).getByLabelText(/Standard hours/), {target: {value: "48"}});
        fireEvent.change(within(dialog).getByLabelText(/Urgent hours/), {target: {value: "8"}});
        fireEvent.change(within(dialog).getByLabelText(/Reminders/), {target: {value: "24"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Create policy"}));

        await waitFor(() => expect(administration.defineInformationRequestClockPolicy).toHaveBeenCalledWith({
            policyKey: "quick-reply",
            displayName: "Quick reply",
            definition: {
                clockType: InformationRequestClockType.CALENDAR,
                businessTimezone: "Europe/London",
                workingPeriods: [],
                holidays: [],
                standardDurationMinutes: 2880,
                urgentDurationMinutes: 480,
                reminderMinutesBeforeDue: [1440],
                dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            },
        }));
        expect(await screen.findByText("The due date policy was created.")).toBeTruthy();
        expect(administration.getInformationRequestClockPolicies).toHaveBeenCalledTimes(2);
    });

    it("publishes a new version that starts from the latest one", async () =>
    {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Publish a new version of Standard response"}));
        const dialog = await screen.findByRole("dialog", {name: "New version of Standard response"});
        expect((within(dialog).getByLabelText(/Standard hours/) as HTMLInputElement).value).toBe("48");
        fireEvent.change(within(dialog).getByLabelText(/Urgent hours/), {target: {value: "4"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Publish version"}));

        await waitFor(() => expect(administration.publishInformationRequestClockPolicyVersion).toHaveBeenCalledWith("policy-a", {
            clockType: InformationRequestClockType.BUSINESS,
            businessTimezone: "Africa/Johannesburg",
            workingPeriods: weekdays.map(dayOfWeek => ({dayOfWeek, startMinute: 540, endMinute: 1020})),
            holidays: ["2026-12-25"],
            standardDurationMinutes: 2880,
            urgentDurationMinutes: 240,
            reminderMinutesBeforeDue: [1440, 240],
            dueEffect: InformationRequestClockDueEffect.EXPIRE_REQUEST,
        }));
    });

    it("keeps the form open with the refusal when the server refuses it", async () =>
    {
        vi.mocked(administration.defineInformationRequestClockPolicy).mockRejectedValue({
            errorMessage: "A clock policy with this key already exists",
            reasonCode: "INFORMATION_REQUEST_CLOCK_POLICY_KEY_TAKEN",
        });
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = await screen.findByRole("dialog", {name: "New due date policy"});
        fireEvent.change(within(dialog).getByLabelText(/Key/), {target: {value: "standard-response"}});
        fireEvent.change(within(dialog).getByLabelText(/Name/), {target: {value: "Standard response"}});
        fireEvent.change(within(dialog).getByLabelText(/Standard hours/), {target: {value: "48"}});
        fireEvent.change(within(dialog).getByLabelText(/Urgent hours/), {target: {value: "8"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Create policy"}));

        expect((await within(dialog).findByRole("alert")).textContent).toContain("A clock policy with this key already exists");
        expect(screen.getByRole("dialog", {name: "New due date policy"})).toBeTruthy();
    });

    it("shows policies without create or publish controls to a viewer who cannot manage them", async () =>
    {
        renderPanel(false);

        expect(await screen.findByText("Standard response")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "New policy"})).toBeNull();
        expect(screen.queryByRole("button", {name: /Publish a new version/})).toBeNull();
    });
});
