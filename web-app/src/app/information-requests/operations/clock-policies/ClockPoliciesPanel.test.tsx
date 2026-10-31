/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as communications from "../../../../services/communicationService.ts";
import {
    CommunicationDto,
    CommunicationScope,
    CommunicationSummaryDto,
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDto,
    InformationRequestClockType,
    InformationRequestOwnerType,
} from "../../../models/models.tsx";
import ClockPoliciesPanel from "./ClockPoliciesPanel.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestClockPolicies: vi.fn(),
    defineInformationRequestClockPolicy: vi.fn(),
    publishInformationRequestClockPolicyVersion: vi.fn(),
}));
vi.mock("../../../../services/communicationService.ts", () => ({
    listCommunications: vi.fn(),
    getCommunication: vi.fn(),
}));

const communication = (id: string, name: string, scope: CommunicationScope): CommunicationSummaryDto => ({
    id,
    name,
    scope,
    subject: `${name} subject`,
    isActive: true,
    isPublished: true,
    isTemplate: false,
    generalTags: [],
    createdAt: "2026-08-01T08:00:00Z",
    updatedAt: "2026-08-01T08:00:00Z",
});

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
            publishedAt: Date.parse("2026-08-01T08:00:00Z"),
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
            reminderCommunicationId: "communication-reminder",
            overdueCommunicationId: "communication-overdue",
            publishedAt: Date.parse("2026-09-01T08:00:00Z"),
        },
    ],
};

const renderPanel = (canManage = true, ownerType = InformationRequestOwnerType.ORGANIZATION) =>
    render(<ClockPoliciesPanel canManage={canManage}
                               ownerType={ownerType}/>);

const communicationPicker = async () =>
    within(await screen.findByRole("dialog", {name: "Select Communication", hidden: true}));

const fillNewCalendarPolicy = (dialog: ReturnType<typeof within>) => {
    fireEvent.change(dialog.getByLabelText(/Key/), {target: {value: "quick-reply"}});
    fireEvent.change(dialog.getByLabelText(/Name/), {target: {value: "Quick reply"}});
    fireEvent.change(dialog.getByLabelText(/Time zone/), {target: {value: "Europe/London"}});
    fireEvent.change(dialog.getByLabelText(/Standard hours/), {target: {value: "48"}});
    fireEvent.change(dialog.getByLabelText(/Urgent hours/), {target: {value: "8"}});
};

const publishWithRefusedReread = async () => {
    renderPanel();
    fireEvent.click(await screen.findByRole("button", {name: "Publish a new version of Standard response"}));
    const dialog = await screen.findByRole("dialog", {name: "New version of Standard response"});
    vi.mocked(administration.getInformationRequestClockPolicies)
        .mockRejectedValue({reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "Policy access was removed"});
    fireEvent.click(within(dialog).getByRole("button", {name: "Publish version"}));
    await screen.findByText("Policy access was removed");
};

describe("ClockPoliciesPanel", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(administration.getInformationRequestClockPolicies).mockResolvedValue([policy]);
        vi.mocked(administration.defineInformationRequestClockPolicy).mockResolvedValue(policy);
        vi.mocked(administration.publishInformationRequestClockPolicyVersion).mockResolvedValue(policy);
        vi.mocked(communications.getCommunication).mockImplementation(async id => ({
            ...communication(id, id === "communication-reminder" ? "Gentle reminder" : "Overdue warning", "ORG"),
            body: "Body",
        }) as CommunicationDto);
        vi.mocked(communications.listCommunications).mockImplementation(async params =>
            params?.scope === "PLATFORM"
                ? [communication("communication-platform", "Platform reminder", "PLATFORM")]
                : [communication("communication-org", "Organization reminder", "ORG")]);
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

        expect(duplicateIds(document.body)).toEqual([]);
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
        }, expect.any(String)));
        expect(await screen.findByText("The due date policy was created.")).toBeTruthy();
        expect(administration.getInformationRequestClockPolicies).toHaveBeenCalledTimes(2);
    });

    it("publishes a new version that starts from the latest one, keeping its reminder and overdue Communications", async () =>
    {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Publish a new version of Standard response"}));
        const dialog = await screen.findByRole("dialog", {name: "New version of Standard response"});
        expect((within(dialog).getByLabelText(/Standard hours/) as HTMLInputElement).value).toBe("48");
        expect(await within(dialog).findByText("Gentle reminder")).toBeTruthy();
        expect(within(dialog).getByText("Overdue warning")).toBeTruthy();
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
            reminderCommunicationId: "communication-reminder",
            overdueCommunicationId: "communication-overdue",
        }, expect.any(String)));
    });

    it("sends the platform default wording again when a chosen Communication is removed", async () => {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Publish a new version of Standard response"}));
        const dialog = await screen.findByRole("dialog", {name: "New version of Standard response"});
        fireEvent.click(await within(dialog).findByRole("button", {name: "Use the platform wording for the overdue notice"}));
        expect(within(dialog).getByText("Platform wording")).toBeTruthy();
        fireEvent.click(within(dialog).getByRole("button", {name: "Publish version"}));

        await waitFor(() => expect(administration.publishInformationRequestClockPolicyVersion).toHaveBeenCalledTimes(1));
        const definition = vi.mocked(administration.publishInformationRequestClockPolicyVersion).mock.calls[0][1];
        expect(definition.reminderCommunicationId).toBe("communication-reminder");
        expect(definition).not.toHaveProperty("overdueCommunicationId");
    });

    it("chooses a reminder Communication from the organization's and the platform's Communications", async () => {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = within(await screen.findByRole("dialog", {name: "New due date policy"}));
        fillNewCalendarPolicy(dialog);
        fireEvent.click(dialog.getByRole("button", {name: "Choose the reminder notice Communication"}));
        const picker = await communicationPicker();
        expect(picker.getByRole("tab", {name: "Organization", hidden: true})).toBeTruthy();
        expect(picker.getByRole("tab", {name: "Platform", hidden: true})).toBeTruthy();
        expect(picker.queryByRole("tab", {name: "My Communications", hidden: true})).toBeNull();
        fireEvent.click(picker.getByRole("tab", {name: "Platform", hidden: true}));
        fireEvent.click(await picker.findByText("Platform reminder"));
        fireEvent.click(picker.getByRole("button", {name: "Select Communication", hidden: true}));

        expect(await dialog.findByText("Platform reminder")).toBeTruthy();
        fireEvent.click(dialog.getByRole("button", {name: "Create policy"}));

        await waitFor(() => expect(administration.defineInformationRequestClockPolicy).toHaveBeenCalledWith(expect.objectContaining({
            definition: expect.objectContaining({reminderCommunicationId: "communication-platform"}),
        }), expect.any(String)));
        expect(communications.listCommunications).toHaveBeenCalledWith({scope: "ORG"});
        expect(communications.listCommunications).not.toHaveBeenCalledWith({scope: "PERSONAL"});
    });

    it("offers a personal owner their own and the platform's Communications", async () => {
        renderPanel(true, InformationRequestOwnerType.USER);

        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = within(await screen.findByRole("dialog", {name: "New due date policy"}));
        fireEvent.click(dialog.getByRole("button", {name: "Choose the overdue notice Communication"}));
        const picker = await communicationPicker();

        expect(picker.getByRole("tab", {name: "My Communications", hidden: true})).toBeTruthy();
        expect(picker.getByRole("tab", {name: "Platform", hidden: true})).toBeTruthy();
        expect(picker.queryByRole("tab", {name: "Organization", hidden: true})).toBeNull();
        await waitFor(() => expect(communications.listCommunications).toHaveBeenCalledWith({scope: "PERSONAL"}));
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

    it("retries a create that failed in transit under the same Idempotency-Key", async () => {
        vi.mocked(administration.defineInformationRequestClockPolicy)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce(policy);
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = await screen.findByRole("dialog", {name: "New due date policy"});
        fireEvent.change(within(dialog).getByLabelText(/Key/), {target: {value: "quick-reply"}});
        fireEvent.change(within(dialog).getByLabelText(/Name/), {target: {value: "Quick reply"}});
        fireEvent.change(within(dialog).getByLabelText(/Standard hours/), {target: {value: "48"}});
        fireEvent.change(within(dialog).getByLabelText(/Urgent hours/), {target: {value: "8"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Create policy"}));
        await within(dialog).findByRole("alert");
        fireEvent.click(within(dialog).getByRole("button", {name: "Create policy"}));

        await waitFor(() => expect(administration.defineInformationRequestClockPolicy).toHaveBeenCalledTimes(2));
        const [first, second] = vi.mocked(administration.defineInformationRequestClockPolicy).mock.calls;
        expect(first[1]).toEqual(expect.any(String));
        expect(second[1]).toBe(first[1]);
    });

    it("says what a new policy still needs while Create policy is unavailable", async () => {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = within(await screen.findByRole("dialog", {name: "New due date policy"}));
        expect(dialog.getByText("Create policy needs Key, Name, Standard hours, and Urgent hours.")).toBeTruthy();
        fireEvent.change(dialog.getByLabelText(/Key/), {target: {value: "quick-reply"}});
        fireEvent.change(dialog.getByLabelText(/Name/), {target: {value: "Quick reply"}});
        expect(dialog.getByText("Create policy needs Standard hours and Urgent hours.")).toBeTruthy();
        fireEvent.change(dialog.getByLabelText(/Standard hours/), {target: {value: "48"}});
        fireEvent.change(dialog.getByLabelText(/Urgent hours/), {target: {value: "8"}});

        expect(dialog.queryByText(/Create policy needs/)).toBeNull();
    });

    it("moves focus to the Key field and marks it when another policy already uses the key", async () => {
        vi.mocked(administration.defineInformationRequestClockPolicy).mockRejectedValueOnce({
            errorMessage: "A clock policy with this key already exists",
            reasonCode: "INFORMATION_REQUEST_CLOCK_POLICY_KEY_TAKEN",
        });
        renderPanel();
        fireEvent.click(await screen.findByRole("button", {name: "New policy"}));
        const dialog = within(await screen.findByRole("dialog", {name: "New due date policy"}));
        fillNewCalendarPolicy(dialog);

        fireEvent.click(dialog.getByRole("button", {name: "Create policy"}));

        await waitFor(() => expect(document.activeElement?.id).toBe("information-request-clock-policy-key"));
        expect(dialog.getByLabelText(/Key/).getAttribute("aria-invalid")).toBe("true");
        expect(dialog.getAllByText("A clock policy with this key already exists")).toHaveLength(1);
        fireEvent.change(dialog.getByLabelText(/Key/), {target: {value: "quick-reply-2"}});
        expect(dialog.queryByText("A clock policy with this key already exists")).toBeNull();
    });

    it("lists every version of a policy with the hours, holidays, and notice wording it counts with", async () => {
        renderPanel(false);

        fireEvent.click(await screen.findByRole("button", {name: "Show the versions of Standard response"}));

        const versions = await screen.findByRole("list", {name: "Versions of Standard response"});
        const [latest, first] = within(versions).getAllByRole("listitem");
        const weekdayHours = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"].map(day => `${day} 09:00 to 17:00`).join(", ");
        await waitFor(() => expect(latest.textContent).toBe(
            `Version 2, published ${formatInformationRequestTime(Date.parse("2026-09-01T08:00:00Z"))}: `
            + "Business hours in Africa/Johannesburg. Standard 48 hours, urgent 8 hours. Reminders 24 hours and 4 hours before due. "
            + `When due, the request expires. Counts ${weekdayHours}. Skips 2026-12-25. `
            + "Reminder notice Gentle reminder, overdue notice Overdue warning.",
        ));
        expect(first.textContent).toBe(
            `Version 1, published ${formatInformationRequestTime(Date.parse("2026-08-01T08:00:00Z"))}: `
            + "Calendar time in UTC. Standard 24 hours, urgent 4 hours. When due, the request is marked overdue. "
            + "Reminder and overdue notices use the platform wording.",
        );
        expect(screen.getByRole("button", {name: "Show the versions of Standard response"}).getAttribute("aria-expanded")).toBe("true");
    });

    it("states the fallback instead of an HTML error page when the policies cannot be read", async () => {
        vi.mocked(administration.getInformationRequestClockPolicies)
            .mockRejectedValue("<html><head><title>504 Gateway Time-out</title></head><body>504</body></html>");
        renderPanel();

        expect((await screen.findByRole("alert")).textContent).toContain("The due date policies could not be loaded.");
        expect(screen.queryByText(/Gateway Time-out/)).toBeNull();
    });

    it("shows policies without create or publish controls to a viewer who cannot manage them", async () =>
    {
        renderPanel(false);

        expect(await screen.findByText("Standard response")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "New policy"})).toBeNull();
        expect(screen.queryByRole("button", {name: /Publish a new version/})).toBeNull();
    });

    it("withholds the earlier policies and their actions once the reread after publication is refused", async () => {
        await publishWithRefusedReread();

        expect(screen.queryByText("Standard response")).toBeNull();
        expect(screen.queryByRole("button", {name: "Publish a new version of Standard response"})).toBeNull();
        expect(screen.queryByRole("button", {name: "New policy"})).toBeNull();
    });

    it("reads the policies again on Try again and shows no earlier policy until they answer", async () => {
        await publishWithRefusedReread();
        let answer: (policies: InformationRequestClockPolicyDto[]) => void = () => undefined;
        vi.mocked(administration.getInformationRequestClockPolicies).mockImplementation(() => new Promise(resolve => {
            answer = resolve;
        }));

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(screen.queryByText("Policy access was removed")).toBeNull();
        expect(screen.queryByText("Standard response")).toBeNull();
        expect(document.getElementById("information-request-clock-policies-loading")).not.toBeNull();
        await act(async () => answer([policy]));
        expect(await screen.findByRole("button", {name: "Publish a new version of Standard response"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "New policy"})).toBeTruthy();
    });
});
