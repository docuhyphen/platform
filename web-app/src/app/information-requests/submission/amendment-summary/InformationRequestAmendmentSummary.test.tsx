/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestAmendmentChangeKind,
    InformationRequestNoticeDeliveryState,
    InformationRequestNoticeKind,
} from "../../../models/models.tsx";
import InformationRequestAmendmentSummary from "./InformationRequestAmendmentSummary.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const shownTime = (value: number): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestAmendments: vi.fn(),
}));

describe("InformationRequestAmendmentSummary", () =>
{
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

    it("summarizes what changed and shows each owed notice as pending, never as delivered", async () =>
    {
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValue([{
            id: "amendment-a",
            amendmentNumber: 1,
            fromTemplateVersionId: "version-1",
            toTemplateVersionId: "version-2",
            amendedAt: Date.parse("2026-09-25T08:00:00Z"),
            amendedByCaller: false,
            changes: [
                {requirementKey: "supporting-record", changeKind: InformationRequestAmendmentChangeKind.MEANING_CHANGED, reconfirmationRequired: true},
                {requirementKey: "additional-record", changeKind: InformationRequestAmendmentChangeKind.ADDED, reconfirmationRequired: false},
            ],
            undisclosedChangeCount: 1,
            notices: [{
                id: "notice-a",
                partyId: "party-a",
                noticeKind: InformationRequestNoticeKind.REQUIREMENTS_AMENDED,
                deliveryState: InformationRequestNoticeDeliveryState.PENDING,
                createdAt: Date.parse("2026-09-25T08:00:00Z"),
            }],
        }]);

        render(<InformationRequestAmendmentSummary requestId={"request-a"}
                                                   refreshKey={"\"responses:1\""}
                                                   prompts={{}}/>);

        expect(await screen.findByText("Amendment 1")).toBeTruthy();
        expect(screen.getByRole("region", {name: "What changed in this request"})).toBeTruthy();
        expect(screen.getByText(shownTime(Date.parse("2026-09-25T08:00:00Z")))).toBeTruthy();
        expect(screen.getByText("supporting record: Changed")).toBeTruthy();
        expect(screen.getByText(/confirm your answer again/)).toBeTruthy();
        expect(screen.getByText("additional record: Newly requested")).toBeTruthy();
        expect(screen.getByText("Notice pending")).toBeTruthy();
        expect(screen.queryByText(/delivered/i)).toBeNull();
        expect(screen.getByText("1 other changes concern items handled by other parties.")).toBeTruthy();
    });

    it("names a changed item by the prompt the request states and by its key only once the request no longer states it", async () => {
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValue([{
            id: "amendment-a",
            amendmentNumber: 1,
            fromTemplateVersionId: "version-1",
            toTemplateVersionId: "version-2",
            amendedAt: Date.parse("2026-09-25T08:00:00Z"),
            amendedByCaller: false,
            changes: [
                {
                    requirementKey: "supporting-record",
                    changeKind: InformationRequestAmendmentChangeKind.MEANING_CHANGED,
                    reconfirmationRequired: false
                },
                {
                    requirementKey: "additional-record",
                    changeKind: InformationRequestAmendmentChangeKind.ADDED,
                    reconfirmationRequired: false
                },
            ],
            undisclosedChangeCount: 0,
            notices: [],
        }]);

        render(<InformationRequestAmendmentSummary requestId={"request-a"}
                                                   refreshKey={"\"responses:1\""}
                                                   prompts={{"supporting-record": "Attach the supporting record"}}/>);

        expect(await screen.findByText("Attach the supporting record: Changed")).toBeTruthy();
        expect(screen.getByText("additional record: Newly requested")).toBeTruthy();
    });

    it("states a refused read of what changed with Try again, drops the earlier amendments, and shows them once a retry loads", async () => {
        const amendment = {
            id: "amendment-a",
            amendmentNumber: 1,
            fromTemplateVersionId: "version-1",
            toTemplateVersionId: "version-2",
            amendedAt: Date.parse("2026-09-25T08:00:00Z"),
            amendedByCaller: false,
            changes: [{
                requirementKey: "supporting-record",
                changeKind: InformationRequestAmendmentChangeKind.ADDED,
                reconfirmationRequired: false
            }],
            undisclosedChangeCount: 0,
            notices: [],
        };
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValueOnce([amendment]);
        const view = render(<InformationRequestAmendmentSummary requestId={"request-a"}
                                                                refreshKey={"\"responses:1\""}
                                                                prompts={{}}/>);
        expect(await screen.findByText("Amendment 1")).toBeTruthy();
        vi.mocked(transport.getInformationRequestAmendments).mockRejectedValueOnce({errorMessage: "The amendments could not be read."});

        view.rerender(<InformationRequestAmendmentSummary requestId={"request-a"}
                                                          refreshKey={"\"responses:2\""}
                                                          prompts={{}}/>);

        await waitFor(() => expect(document.getElementById("information-request-amendments-load-error")?.textContent)
            .toContain("The amendments could not be read."));
        expect(screen.queryByText("Amendment 1")).toBeNull();
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValueOnce([amendment]);
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByText("Amendment 1")).toBeTruthy();
        expect(document.getElementById("information-request-amendments-load-error")).toBeNull();
    });
});

