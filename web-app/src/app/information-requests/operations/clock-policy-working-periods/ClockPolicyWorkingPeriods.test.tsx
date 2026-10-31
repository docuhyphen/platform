/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyDto,
    InformationRequestClockType,
    InformationRequestOwnerType,
} from "../../../models/models.tsx";
import ClockPolicyDialog from "../clock-policy-dialog/ClockPolicyDialog.tsx";
import {ClockPolicyRefusal} from "../clock-policies/useClockPolicies.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/communicationService.ts", () => ({
    listCommunications: vi.fn().mockResolvedValue([]),
    getCommunication: vi.fn(),
}));

const policy: InformationRequestClockPolicyDto = {
    id: "policy-hours",
    policyKey: "working-hours",
    displayName: "Working hours response",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    versions: [{
        id: "version-1",
        versionNumber: 1,
        clockType: InformationRequestClockType.BUSINESS,
        businessTimezone: "Europe/Berlin",
        standardDurationMinutes: 480,
        urgentDurationMinutes: 120,
        dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
        workingPeriods: [
            {dayOfWeek: "MONDAY", startMinute: 540, endMinute: 720},
            {dayOfWeek: "MONDAY", startMinute: 780, endMinute: 1020},
        ],
        holidays: [],
        reminderMinutesBeforeDue: [],
        publishedAt: Date.parse("2026-03-01T08:00:00Z"),
    }],
};

const openDialog = () => {
    const onPublish = vi.fn<(policy: InformationRequestClockPolicyDto, definition: InformationRequestClockPolicyDefinitionRequest) => Promise<ClockPolicyRefusal | null>>()
        .mockResolvedValue(null);
    render(<ClockPolicyDialog policy={policy}
                              ownerType={InformationRequestOwnerType.ORGANIZATION}
                              busy={false}
                              onDefine={vi.fn()}
                              onPublish={onPublish}
                              onDone={vi.fn()}/>);
    return {dialog: within(screen.getByRole("dialog", {name: "New version of Working hours response"})), onPublish};
};

const period = (dialog: ReturnType<typeof within>, position: number) =>
    within(dialog.getByRole("group", {name: `Working period ${position}`}));

describe("ClockPolicyWorkingPeriods", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    afterEach(cleanup);

    it("shows every working period of the latest version with a name for each control", () => {
        const {dialog} = openDialog();

        expect((period(dialog, 1).getByLabelText("Starts") as HTMLInputElement).value).toBe("09:00");
        expect((period(dialog, 1).getByLabelText("Ends") as HTMLInputElement).value).toBe("12:00");
        expect((period(dialog, 2).getByLabelText("Starts") as HTMLInputElement).value).toBe("13:00");
        expect((period(dialog, 2).getByLabelText("Ends") as HTMLInputElement).value).toBe("17:00");
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("adds a period that runs until midnight on another day and removes one, and publishes exactly those periods", async () => {
        const {dialog, onPublish} = openDialog();

        fireEvent.click(dialog.getByRole("button", {name: "Add working period"}));
        const added = period(dialog, 3);
        expect((added.getByLabelText("Day") as HTMLSelectElement).value).toBe("TUESDAY");
        fireEvent.change(added.getByLabelText("Day"), {target: {value: "SATURDAY"}});
        fireEvent.change(added.getByLabelText("Starts"), {target: {value: "18:00"}});
        fireEvent.click(added.getByRole("checkbox", {name: "Until midnight"}));
        expect((added.getByLabelText("Ends") as HTMLInputElement).disabled).toBe(true);
        fireEvent.click(dialog.getByRole("button", {name: "Remove working period 1"}));
        fireEvent.click(dialog.getByRole("button", {name: "Publish version"}));

        await waitFor(() => expect(onPublish).toHaveBeenCalledTimes(1));
        expect(onPublish.mock.calls[0][1].workingPeriods).toEqual([
            {dayOfWeek: "MONDAY", startMinute: 780, endMinute: 1020},
            {dayOfWeek: "SATURDAY", startMinute: 1080, endMinute: 1440},
        ]);
    });

    it("marks an overlapping period and an end at 00:00 and keeps the version from being published", () => {
        const {dialog} = openDialog();

        fireEvent.change(period(dialog, 2).getByLabelText("Starts"), {target: {value: "11:00"}});
        expect(period(dialog, 1).getByText("Overlaps another period on the same day.")).toBeTruthy();
        expect(period(dialog, 2).getByText("Overlaps another period on the same day.")).toBeTruthy();
        fireEvent.change(period(dialog, 2).getByLabelText("Starts"), {target: {value: "13:00"}});
        fireEvent.change(period(dialog, 2).getByLabelText("Ends"), {target: {value: "00:00"}});
        expect(period(dialog, 2).getByText("Ends after it starts. Choose Until midnight to end with the day.")).toBeTruthy();
        expect(dialog.getByText("Correct the marked working periods.")).toBeTruthy();
        expect((dialog.getByRole("button", {name: "Publish version"}) as HTMLButtonElement).disabled).toBe(true);

        fireEvent.click(period(dialog, 2).getByRole("checkbox", {name: "Until midnight"}));
        expect(dialog.queryByText("Correct the marked working periods.")).toBeNull();
        expect((dialog.getByRole("button", {name: "Publish version"}) as HTMLButtonElement).disabled).toBe(false);
    });

    it("asks for a working period once every period is removed", () => {
        const {dialog} = openDialog();

        fireEvent.click(dialog.getByRole("button", {name: "Remove working period 2"}));
        fireEvent.click(dialog.getByRole("button", {name: "Remove working period 1"}));

        expect(dialog.getByText("Add at least one working period.")).toBeTruthy();
        expect((dialog.getByRole("button", {name: "Publish version"}) as HTMLButtonElement).disabled).toBe(true);
    });
});
