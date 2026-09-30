/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestDto,
    InformationRequestLineageKind,
    InformationRequestOwnerType,
    InformationRequestRecurrenceUnit,
    InformationRequestState,
} from "../../../models/models.tsx";
import FollowUpPanel from "./FollowUpPanel.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestLineage: vi.fn(),
    defineInformationRequestRecurrence: vi.fn(),
    createNextInformationRequestOccurrence: vi.fn(),
    requestInformationRequestSupplement: vi.fn(),
}));

const request: InformationRequestDto = {
    id: "request-a",
    exchangeId: "exchange-a",
    templateVersionId: "version-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    state: InformationRequestState.CLOSED,
    gatesExchangeClosure: true,
    aggregateRevision: 7,
    createdAt: "2026-09-01T08:00:00Z",
    updatedAt: "2026-09-20T08:00:00Z",
    requestETag: "\"request-7\"",
    conditionEvaluations: [],
};

const renderPanel = (canCreate = true) => render(
    <MemoryRouter>
        <FollowUpPanel request={request}
                       canCreate={canCreate}
                       onChanged={vi.fn()}/>
    </MemoryRouter>,
);

describe("FollowUpPanel", () =>
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
        vi.mocked(authoring.defineInformationRequestRecurrence).mockResolvedValue({} as never);
        vi.mocked(authoring.createNextInformationRequestOccurrence).mockResolvedValue({} as never);
        vi.mocked(authoring.requestInformationRequestSupplement).mockResolvedValue({} as never);
    });

    afterEach(cleanup);

    it("schedules a recurrence under the request ETag", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({informationRequestId: "request-a", successors: []});
        renderPanel();

        fireEvent.change(await screen.findByLabelText("Repeat every"), {target: {value: "3"}});
        fireEvent.change(screen.getByLabelText("Unit"), {target: {value: InformationRequestRecurrenceUnit.MONTH}});
        fireEvent.change(screen.getByLabelText("First due"), {target: {value: "2026-10-01T08:00"}});
        fireEvent.change(screen.getByLabelText("Stop after"), {target: {value: "4"}});
        fireEvent.click(screen.getByRole("button", {name: "Schedule"}));

        await waitFor(() => expect(authoring.defineInformationRequestRecurrence).toHaveBeenCalledWith(
            "request-a",
            {
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 3,
                firstDueAt: new Date("2026-10-01T08:00").toISOString(),
                maximumOccurrences: 4,
            },
            "\"request-7\"",
            expect.any(String),
        ));
    });

    it("creates the next recurring request once it is due and lists follow-up requests", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [{
                id: "lineage-a",
                lineageKind: InformationRequestLineageKind.RECURRENCE,
                sourceRequestId: "request-a",
                successorRequestId: "request-b",
                recurrenceId: "recurrence-a",
                recurrenceSequence: 1,
                createdAt: "2026-09-10T08:00:00Z",
            }],
            recurrence: {
                id: "recurrence-a",
                originRequestId: "request-a",
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 1,
                firstDueAt: "2026-09-10T08:00:00Z",
                createdAt: "2026-09-01T08:00:00Z",
            },
            nextOccurrenceDueAt: "2026-09-26T08:00:00Z",
        });
        renderPanel();

        expect(await screen.findByText("Recurring request 1")).toBeTruthy();
        expect(screen.getByText(/^Repeats every 1 month/)).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Create the next request"}));

        await waitFor(() => expect(authoring.createNextInformationRequestOccurrence)
            .toHaveBeenCalledWith("request-a", "recurrence-a", expect.any(String)));
    });

    it("keeps the follow-up history but offers no new follow-ups while new work is unavailable", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [{
                id: "lineage-a",
                lineageKind: InformationRequestLineageKind.RECURRENCE,
                sourceRequestId: "request-a",
                successorRequestId: "request-b",
                recurrenceId: "recurrence-a",
                recurrenceSequence: 1,
                createdAt: "2026-09-10T08:00:00Z",
            }],
            recurrence: {
                id: "recurrence-a",
                originRequestId: "request-a",
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 1,
                firstDueAt: "2026-09-10T08:00:00Z",
                createdAt: "2026-09-01T08:00:00Z",
            },
            nextOccurrenceDueAt: "2026-09-26T08:00:00Z",
        });
        renderPanel(false);

        expect(await screen.findByText("Recurring request 1")).toBeTruthy();
        expect(screen.getByText(/^Repeats every 1 month/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create the next request"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Request a supplement"})).toBeNull();
    });

    it("offers no schedule while new work is unavailable", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({informationRequestId: "request-a", successors: []});
        renderPanel(false);

        await waitFor(() => expect(authoring.getInformationRequestLineage).toHaveBeenCalled());
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
    });

    it("asks for a supplement with the reason stated", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({informationRequestId: "request-a", successors: []});
        renderPanel();

        fireEvent.change(await screen.findByLabelText("Reason for the supplement"), {target: {value: "MISSING_ITEM"}});
        fireEvent.click(screen.getByRole("button", {name: "Request a supplement"}));

        await waitFor(() => expect(authoring.requestInformationRequestSupplement)
            .toHaveBeenCalledWith("request-a", "MISSING_ITEM", "\"request-7\"", expect.any(String)));
    });
});
