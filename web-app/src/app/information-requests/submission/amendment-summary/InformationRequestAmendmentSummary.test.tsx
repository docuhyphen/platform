/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestAmendmentChangeKind,
    InformationRequestNoticeDeliveryState,
    InformationRequestNoticeKind,
} from "../../../models/models.tsx";
import InformationRequestAmendmentSummary from "./InformationRequestAmendmentSummary.tsx";

vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestAmendments: vi.fn(),
}));

describe("InformationRequestAmendmentSummary", () =>
{
    afterEach(cleanup);

    it("summarizes what changed and shows each owed notice as pending, never as delivered", async () =>
    {
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValue([{
            id: "amendment-a",
            amendmentNumber: 1,
            fromTemplateVersionId: "version-1",
            toTemplateVersionId: "version-2",
            amendedAt: "2026-09-25T08:00:00Z",
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
                createdAt: "2026-09-25T08:00:00Z",
            }],
        }]);

        render(<InformationRequestAmendmentSummary requestId={"request-a"}
                                                   refreshKey={"\"responses:1\""}/>);

        expect(await screen.findByText("Amendment 1")).toBeTruthy();
        expect(screen.getByText("supporting record: Changed")).toBeTruthy();
        expect(screen.getByText(/confirm your answer again/)).toBeTruthy();
        expect(screen.getByText("additional record: Newly requested")).toBeTruthy();
        expect(screen.getByText("Notice pending")).toBeTruthy();
        expect(screen.queryByText(/delivered/i)).toBeNull();
        expect(screen.getByText("1 other changes concern items handled by other parties.")).toBeTruthy();
    });
});
