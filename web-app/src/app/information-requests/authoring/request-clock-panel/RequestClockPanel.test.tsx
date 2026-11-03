/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as operations from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestClockDto,
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockState,
    InformationRequestClockType,
    InformationRequestClockUrgency,
    InformationRequestOwnerType,
} from "../../../models/models.tsx";
import RequestClockPanel from "./RequestClockPanel.tsx";

const session = vi.hoisted(() => ({current: null as { userId: string; activeOrganizationId: string | null } | null}));

vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => ({currentSession: session.current})}));
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
    receivedAt: Date.parse("2026-09-27T08:00:00Z"),
    state: InformationRequestClockState.RUNNING,
    dueAt: Date.parse("2026-09-29T08:00:00Z"),
    dueCycle: 0,
    clockETag: "\"clock-a-3\"",
    events: [],
};

const CLOCK_REFUSAL = {reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "Clock access was removed"};

const OWNER = {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"};

const OTHER_WORKSPACE = "Due date policies belong to the workspace that owns this request. Switch to it to start a clock.";

const EVERY_CLOCK_ACTION = {startOrResume: true, pauseOrExtend: true};

const NO_CLOCK_ACTION = {startOrResume: false, pauseOrExtend: false};

const RECOVERY_ONLY = {startOrResume: false, pauseOrExtend: true};

const pause = async (reason: string) => {
    fireEvent.click(await screen.findByRole("button", {name: "Pause response clock"}));
    const dialog = await screen.findByRole("dialog", {name: "Pause the response clock"});
    fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: reason}});
    fireEvent.click(within(dialog).getByRole("button", {name: "Pause"}));
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
        vi.resetAllMocks();
        session.current = {userId: "user-a", activeOrganizationId: "organization-a"};
        vi.mocked(administration.getInformationRequestClockPolicies).mockResolvedValue([{
            id: "policy-a",
            policyKey: "standard",
            displayName: "Standard response time",
            ownerType: InformationRequestOwnerType.ORGANIZATION,
            versions: [1, 2].map((versionNumber): InformationRequestClockPolicyVersionDto => ({
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
                publishedAt: Date.parse("2026-09-01T08:00:00Z"),
            })),
        }]);
        vi.mocked(administration.startInformationRequestClock).mockResolvedValue(running);
        vi.mocked(administration.changeInformationRequestClock).mockResolvedValue(running);
    });

    afterEach(cleanup);

    it.each(["-response", ".urgent"])("refuses a clock key %s before sending it", async key => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);
        await screen.findByLabelText("Due policy");
        choose("Due policy", "Standard response time (version 2)");
        fireEvent.change(screen.getByLabelText("Clock key"), {target: {value: key}});
        expect((screen.getByRole("button", {name: "Start clock"}) as HTMLButtonElement).disabled).toBe(true);
        expect(screen.getByText(/starting with a letter or digit/)).toBeTruthy();
    });

    it("announces a refused clock change", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([running]);
        vi.mocked(administration.changeInformationRequestClock).mockRejectedValueOnce({errorMessage: "Clock change refused"});
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);
        await pause("awaiting.answer");
        await screen.findByText("Clock change refused");
        expect(document.getElementById("information-request-clock-panel-error")?.getAttribute("role")).toBe("alert");
    });

    it("states that a stopped clock has no active deadline", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([{
            ...running,
            state: InformationRequestClockState.STOPPED
        }]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={NO_CLOCK_ACTION}/>);
        expect(await screen.findByText("Stopped; no active deadline")).toBeTruthy();
    });

    it("starts a clock on the latest version of the chosen policy", async () =>
    {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);

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
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Extend response clock"}));
        const extend = await screen.findByRole("dialog", {name: "Extend the response clock"});
        fireEvent.change(within(extend).getByLabelText("Reason"), {target: {value: "more.time"}});
        fireEvent.change(within(extend).getByLabelText("Minutes to add"), {target: {value: "120"}});
        fireEvent.click(within(extend).getByRole("button", {name: "Extend"}));
        await waitFor(() => expect(administration.changeInformationRequestClock).toHaveBeenCalledWith(
            "request-a", "clock-a", "extensions", {
                reasonCode: "more.time",
                extensionMinutes: 120
            }, "\"clock-a-3\"", expect.any(String),
        ));

        fireEvent.click(screen.getByRole("button", {name: "Pause response clock"}));
        const pause = await screen.findByRole("dialog", {name: "Pause the response clock"});
        fireEvent.change(within(pause).getByLabelText("Reason"), {target: {value: "awaiting.answer"}});
        fireEvent.click(within(pause).getByRole("button", {name: "Pause"}));
        await waitFor(() => expect(administration.changeInformationRequestClock).toHaveBeenLastCalledWith(
            "request-a", "clock-a", "pauses", {reasonCode: "awaiting.answer"}, "\"clock-a-3\"", expect.any(String),
        ));
    });

    it("shows clocks without controls when the request no longer changes", async () =>
    {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([running]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={NO_CLOCK_ACTION}/>);

        expect(await screen.findByText(/^Due /)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Pause response clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
    });

    it("offers pausing and extending but neither starting nor resuming when only owner recovery remains", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([
            running,
            {
                ...running,
                id: "clock-b",
                clockKey: "review",
                state: InformationRequestClockState.PAUSED,
                remainingSeconds: 3600
            },
        ]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={RECOVERY_ONLY}/>);

        expect(await screen.findByRole("button", {name: "Pause response clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Extend response clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Extend review clock"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Resume review clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
        expect(screen.queryByText(OTHER_WORKSPACE)).toBeNull();

        await pause("awaiting.answer");
        await waitFor(() => expect(administration.changeInformationRequestClock).toHaveBeenCalledWith(
            "request-a", "clock-a", "pauses", {reasonCode: "awaiting.answer"}, "\"clock-a-3\"", expect.any(String),
        ));
    });

    it("shows a paused clock's time left instead of a due time that no longer holds", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([{
            ...running,
            state: InformationRequestClockState.PAUSED,
            remainingSeconds: 10 * 3600,
        }]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={NO_CLOCK_ACTION}/>);

        expect(await screen.findByText("Time left when paused: 10h 0m")).toBeTruthy();
        expect(document.getElementById("information-request-clock-panel-clock-a-due")?.textContent)
            .toBe("Time left when paused: 10h 0m");
    });

    it("withholds the earlier clocks and every clock control once the reread after a change is refused", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValueOnce([running]).mockRejectedValue(CLOCK_REFUSAL);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);

        await pause("awaiting.answer");

        expect(await screen.findByText("Clock access was removed")).toBeTruthy();
        expect(document.getElementById("information-request-clock-panel-clock-a-due")).toBeNull();
        expect(screen.queryByRole("button", {name: "Pause response clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
    });

    it("reads the clocks again on Try again and shows no earlier clock until they answer", async () => {
        let answer: (clocks: InformationRequestClockDto[]) => void = () => undefined;
        vi.mocked(operations.getInformationRequestClocks)
            .mockResolvedValueOnce([running])
            .mockRejectedValueOnce(CLOCK_REFUSAL)
            .mockImplementation(() => new Promise(resolve => {
                answer = resolve;
            }));
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);
        await pause("awaiting.answer");
        await screen.findByText("Clock access was removed");

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(screen.queryByText("Clock access was removed")).toBeNull();
        expect(document.getElementById("information-request-clock-panel-clock-a-due")).toBeNull();
        expect(document.getElementById("information-request-clock-panel-loading")).not.toBeNull();
        await act(async () => answer([{...running, dueAt: Date.parse("2026-09-30T08:00:00Z")}]));
        expect(await screen.findByRole("button", {name: "Pause response clock"})).toBeTruthy();
    });

    it("offers no due date policy of another workspace and keeps the request's own clock controls", async () => {
        session.current = {userId: "user-a", activeOrganizationId: "organization-b"};
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([running]);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);

        expect(await screen.findByRole("button", {name: "Pause response clock"})).toBeTruthy();
        expect(screen.getByText(OTHER_WORKSPACE)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
        expect(administration.getInformationRequestClockPolicies).not.toHaveBeenCalled();
    });

    it("drops the owner's policies as soon as the session leaves its workspace and reads them again on return", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([]);
        const view = render(<RequestClockPanel requestId={"request-a"}
                                               owner={OWNER}
                                               permissions={EVERY_CLOCK_ACTION}/>);
        await screen.findByRole("option", {name: "Standard response time (version 2)"});

        session.current = {userId: "user-a", activeOrganizationId: "organization-b"};
        view.rerender(<RequestClockPanel requestId={"request-a"}
                                         owner={{...OWNER}}
                                         permissions={EVERY_CLOCK_ACTION}/>);

        expect(screen.queryByRole("option", {name: "Standard response time (version 2)"})).toBeNull();
        expect(screen.getByText(OTHER_WORKSPACE)).toBeTruthy();

        session.current = {userId: "user-a", activeOrganizationId: "organization-a"};
        view.rerender(<RequestClockPanel requestId={"request-a"}
                                         owner={{...OWNER}}
                                         permissions={EVERY_CLOCK_ACTION}/>);

        expect(await screen.findByRole("option", {name: "Standard response time (version 2)"})).toBeTruthy();
        expect(screen.queryByText(OTHER_WORKSPACE)).toBeNull();
        expect(administration.getInformationRequestClockPolicies).toHaveBeenCalledTimes(2);
    });

    it("retries a clock change that failed in transit under the same Idempotency-Key once the clocks are read again", async () => {
        vi.mocked(operations.getInformationRequestClocks)
            .mockResolvedValueOnce([running])
            .mockRejectedValueOnce(CLOCK_REFUSAL)
            .mockResolvedValue([running]);
        vi.mocked(administration.changeInformationRequestClock)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValue(running);
        render(<RequestClockPanel requestId={"request-a"}
                                  owner={OWNER}
                                  permissions={EVERY_CLOCK_ACTION}/>);
        await pause("awaiting.answer");
        await screen.findByText("Clock access was removed");

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        await pause("awaiting.answer");

        await waitFor(() => expect(administration.changeInformationRequestClock).toHaveBeenCalledTimes(2));
        const [first, retried] = vi.mocked(administration.changeInformationRequestClock).mock.calls;
        expect(retried[5]).toBe(first[5]);
    });
});
