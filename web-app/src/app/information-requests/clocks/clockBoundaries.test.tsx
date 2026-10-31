/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import * as operations from "../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestClockDto,
    InformationRequestClockEventKind,
    InformationRequestClockState
} from "../../models/models.tsx";
import ClockChangeDialog from "./clock-change-dialog/ClockChangeDialog.tsx";
import ClockStartForm from "./clock-start-form/ClockStartForm.tsx";
import ClockHistoryPanel from "../operations/clock-history/ClockHistoryPanel.tsx";

vi.mock("../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestClocks: vi.fn()}));
const clock: InformationRequestClockDto = {
    id: "clock-a", clockKey: "response", policyVersionId: "version-a", policyVersionNumber: 1,
    clockType: "CALENDAR", urgency: "STANDARD", state: InformationRequestClockState.RUNNING,
    receivedAt: 0, dueAt: 3600000, dueCycle: 0, clockETag: "clock:1", events: [],
};

describe("clock boundaries", () => {
    beforeAll(() => vi.stubGlobal("ResizeObserver", class {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    }));
    afterEach(cleanup);
    it("requires a machine reason code before a clock change", () => {
        render(<ClockChangeDialog clock={clock}
                                  path={"pauses"}
                                  busy={false}
                                  onConfirm={vi.fn()}
                                  onDismiss={vi.fn()}/>);
        fireEvent.change(screen.getByLabelText("Reason"), {target: {value: "Wait for the responding party"}});
        expect((screen.getByRole("button", {name: "Pause"}) as HTMLButtonElement).disabled).toBe(true);
    });
    it("states that a new clock starts counting when the command is sent", () => {
        render(<ClockStartForm choices={[]}
                               busy={false}
                               onStart={vi.fn()}/>);
        expect(screen.getByText("Time starts when you select Start clock. The saved clock shows its due time.")).toBeTruthy();
    });
    it("labels an issue rebase without claiming the clock had been paused", async () => {
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([{
            ...clock,
            events: [{
                eventNumber: 1,
                eventKind: InformationRequestClockEventKind.RESUMED,
                dueCycle: 0,
                reasonCode: "REQUEST_ISSUED",
                occurredAt: 0
            }],
        }]);
        render(<ClockHistoryPanel requestId={"request-a"}/>);
        expect(await screen.findByText(/^Counting reset .*Request issued/)).toBeTruthy();
    });
});