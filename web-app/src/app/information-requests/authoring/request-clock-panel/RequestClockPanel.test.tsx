/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as operations from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestClockDto,
    InformationRequestClockDueEffect,
    InformationRequestClockState,
    InformationRequestClockType,
    InformationRequestClockUrgency,
    InformationRequestOwnerType,
} from "../../../models/models.tsx";
import RequestClockPanel from "./RequestClockPanel.tsx";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestClocks: vi.fn()}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestClockPolicies: vi.fn(),
    startInformationRequestClock: vi.fn(),
    changeInformationRequestClock: vi.fn(),
}));

const running: InformationRequestClockDto = {
    id: "clock-a",
    clockKey: "response",
    policyVersionId: "policy-version-a",
    policyVersionNumber: 2,
    clockType: "CALENDAR",
    urgency: "STANDARD",
    receivedAt: "2026-09-27T08:00:00Z",
    state: InformationRequestClockState.RUNNING,
    dueAt: "2026-09-29T08:00:00Z",
    dueCycle: 0,
    clockETag: "\"clock-a-3\"",
    events: [],
};

const choose = (label: string, option: string) =>
{
    const select = screen.getByLabelText(label) as HTMLSelectElement;
    const value = Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? "";
    fireEvent.change(select, {target: {value}});
};

describe("RequestClockPanel", () =>
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
        vi.mocked(administration.getInformationRequestClockPolicies).mockResolvedValue([{
            id: "policy-a",
            policyKey: "standard",
            displayName: "Standard response time",
            ownerType: InformationRequestOwnerType.ORGANIZATION,
            versions: [1, 2].map(versionNumber => ({
                id: `policy-version-${versionNumber === 2 ? "a" : "old"}`,
                versionNumber,
                clockType: InformationRequestClockType.CALENDAR,
                businessTimezone: "UTC",
                standardDurationMinutes: 2880,
                urgentDurationMinutes: 480,
                dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
                workingPeriods: [],
                holidays: [],
                reminderMinutesBeforeDue: [],
                publishedAt: "2026-09-01T08:00:00Z",
            })),
        }]);
        vi.mocked(administration.startInformationRequestClock).mockResolvedValue(running);
        vi.mocked(administration.changeInformationRequestClock).mockResolvedValue(running);
    });

    afterEach(cleanup);

    it("starts a clock on the latest version of the chosen policy", async () =>
    {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([]);
        render(<RequestClockPanel requestId={"request-a"}
                                  editable={true}/>);

        await screen.findByRole("option", {name: "Standard response time (version 2)"});
        choose("Due policy", "Standard response time (version 2)");
        choose("Urgency", "Urgent");
        fireEvent.click(screen.getByRole("button", {name: "Start clock"}));

        await waitFor(() => expect(administration.startInformationRequestClock).toHaveBeenCalledWith(
            "request-a",
            {clockKey: "response", policyVersionId: "policy-version-a", urgency: InformationRequestClockUrgency.URGENT},
            expect.any(String),
        ));
    });

    it("pauses and extends a running clock with a stated reason under its clock ETag", async () =>
    {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([running]);
        render(<RequestClockPanel requestId={"request-a"}
                                  editable={true}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Extend response clock"}));
        const extend = await screen.findByRole("dialog", {name: "Extend the response clock"});
        fireEvent.change(within(extend).getByLabelText("Reason"), {target: {value: "MORE_TIME"}});
        fireEvent.change(within(extend).getByLabelText("Minutes to add"), {target: {value: "120"}});
        fireEvent.click(within(extend).getByRole("button", {name: "Extend"}));
        await waitFor(() => expect(administration.changeInformationRequestClock).toHaveBeenCalledWith(
            "request-a", "clock-a", "extensions", {reasonCode: "MORE_TIME", extensionMinutes: 120}, "\"clock-a-3\"", expect.any(String),
        ));

        fireEvent.click(screen.getByRole("button", {name: "Pause response clock"}));
        const pause = await screen.findByRole("dialog", {name: "Pause the response clock"});
        fireEvent.change(within(pause).getByLabelText("Reason"), {target: {value: "AWAITING_ANSWER"}});
        fireEvent.click(within(pause).getByRole("button", {name: "Pause"}));
        await waitFor(() => expect(administration.changeInformationRequestClock).toHaveBeenLastCalledWith(
            "request-a", "clock-a", "pauses", {reasonCode: "AWAITING_ANSWER"}, "\"clock-a-3\"", expect.any(String),
        ));
    });

    it("shows clocks without controls when the request no longer changes", async () =>
    {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([running]);
        render(<RequestClockPanel requestId={"request-a"}
                                  editable={false}/>);

        expect(await screen.findByText(/^Due /)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Pause response clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
    });
});
