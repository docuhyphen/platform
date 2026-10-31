/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {getInformationRequestNotices} from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestNoticeDeliveryState,
    InformationRequestNoticeHistoryDto,
    InformationRequestNoticeKind,
} from "../../../models/models.tsx";
import NoticeHistoryPanel from "./NoticeHistoryPanel.tsx";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({
    getInformationRequestNotices: vi.fn(),
}));

const notice = (overrides: Partial<InformationRequestNoticeHistoryDto>): InformationRequestNoticeHistoryDto => ({
    noticeIntentId: "intent-group",
    noticeKind: InformationRequestNoticeKind.RESPONSE_OVERDUE,
    partyId: "party-group",
    deliveryState: InformationRequestNoticeDeliveryState.DELIVERED,
    owedAt: 1_790_000_000_000,
    endpointState: "RESOLVED",
    attempts: [],
    sequenceAllocations: [],
    ...overrides,
});

describe("NoticeHistoryPanel", () => {
    afterEach(cleanup);

    it("lists each group member's notice on its own and never shows a member's address", async () => {
        vi.mocked(getInformationRequestNotices).mockResolvedValue([
            notice({noticeId: "notice-member-a", recipientWithheld: true}),
            notice({noticeId: "notice-member-b", recipientWithheld: true}),
            notice({
                noticeIntentId: "intent-person",
                partyId: "party-person",
                noticeId: "notice-person",
                maskedEndpoint: "p***@process.test"
            }),
        ]);

        render(<NoticeHistoryPanel requestId={"request-1"}/>);

        expect(await screen.findAllByText(/To a group member/)).toHaveLength(2);
        expect(screen.getByText(/To p\*\*\*@process\.test/)).toBeTruthy();
        expect(document.getElementById("information-request-notice-notice-member-a")).not.toBeNull();
        expect(document.getElementById("information-request-notice-notice-member-b")).not.toBeNull();
    });
});
