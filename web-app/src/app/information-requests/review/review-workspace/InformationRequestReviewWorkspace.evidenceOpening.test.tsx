/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as evidence from "../../../../services/informationRequestEvidenceService.ts";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewDto,
    InformationRequestReviewItemDto,
    InformationRequestReviewKind,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewState,
} from "../../../models/models.tsx";
import InformationRequestReviewWorkspace from "./InformationRequestReviewWorkspace.tsx";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
    recordInformationRequestReviewComment: vi.fn(),
    overrideInformationRequestReviewItem: vi.fn(),
    reconsiderInformationRequestReview: vi.fn(),
    assignInformationRequestReviewer: vi.fn(),
    changeInformationRequestReviewAssignment: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestParties: vi.fn()}));
vi.mock("../../../../services/informationRequestEvidenceService.ts", () => ({openInformationRequestSubmittedEvidenceContent: vi.fn()}));

const LABEL = "Attach the supporting record";
const USES: InformationRequestEvidenceContentUse[] = ["preview", "content"];

const reviewOf = (item: Partial<InformationRequestReviewItemDto> = {}): InformationRequestReviewDto => ({
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.IN_REVIEW,
        openedAt: Date.parse("2026-10-14T08:00:00Z"),
        reviewETag: "\"review:2\"",
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [],
    items: [{
        submissionItemId: "item-a",
        requirementId: "requirement-a",
        requirementKey: "supporting-record",
        prompt: LABEL,
        requirementType: InformationRequestRequirementType.DOCUMENT,
        occurrencePath: "root",
        reviewed: true,
        contentVisible: true,
        waiverPermitted: false,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        evidence: [{
            artifactId: "artifact-a",
            evidenceVersionId: "version-a",
            versionNumber: 1,
            contentLength: 2048,
            conformance: "CONFORMING"
        }],
        ...item,
    }],
    assignments: [],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [],
    canManage: false,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: true,
    itemsAwaitingStepUp: 0,
});

const LeaveReview = () => {
    const navigate = useNavigate();
    return (
        <button id={"leave-review"}
                onClick={() => navigate("/elsewhere")}>
            Leave the review
        </button>
    );
};

const renderReviewPage = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/reviews/review-a"]}>
        <LeaveReview/>
        <Routes>
            <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                   element={<InformationRequestReviewWorkspace/>}/>
            <Route path={"/elsewhere"}
                   element={<p>Somewhere else</p>}/>
        </Routes>
    </MemoryRouter>,
);

const settle = () => new Promise<void>(resolve => setTimeout(resolve, 0));

describe("InformationRequestReviewWorkspace when a submitted file answers late", () => {
    let pending: Array<(file: Blob) => void>;
    let downloads: { href: string; download: string }[];

    const startOpening = async (use: InformationRequestEvidenceContentUse) => {
        const action = use === "preview" ? "Preview" : "Download";
        fireEvent.click(await screen.findByRole("button", {name: `${action} version 1 of ${LABEL}`}));
        await waitFor(() => expect(pending).toHaveLength(1));
    };

    const commentThenReread = async (reread: () => Promise<InformationRequestReviewDto>) => {
        vi.mocked(transport.getInformationRequestReview).mockImplementation(reread);
        fireEvent.click(screen.getByRole("button", {name: `Comment on ${LABEL}`}));
        const dialog = await screen.findByRole("dialog", {name: `Comment on ${LABEL}`});
        fireEvent.change(within(dialog).getByLabelText("Comment"), {target: {value: "Checked against the register"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Send comment"}));
        await waitFor(() => expect(transport.getInformationRequestReview).toHaveBeenCalledTimes(2));
        await act(settle);
    };

    const deliver = () => act(async () => {
        pending.splice(0).forEach(answer => answer(new Blob(["submitted record"], {type: "application/pdf"})));
        await settle();
    });

    const expectNothingOpened = () => {
        expect.soft(URL.createObjectURL).not.toHaveBeenCalled();
        expect.soft(window.open).not.toHaveBeenCalled();
        expect.soft(downloads).toEqual([]);
    };

    const expectOpenedOnce = (use: InformationRequestEvidenceContentUse) => {
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
        if (use === "preview") expect(screen.getByTitle("Preview of File version 1").getAttribute("src")).toBe("blob:submitted-record");
        else expect(downloads).toEqual([{href: "blob:submitted-record", download: "evidence-version-1"}]);
    };

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

    beforeEach(() => {
        vi.resetAllMocks();
        pending = [];
        downloads = [];
        Object.assign(URL, {createObjectURL: vi.fn(() => "blob:submitted-record"), revokeObjectURL: vi.fn()});
        vi.spyOn(window, "open").mockImplementation(() => null);
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloads.push({href: this.href, download: this.download});
        });
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(reviewOf());
        vi.mocked(transport.recordInformationRequestReviewComment)
            .mockResolvedValue({outcome: "SAVED", responseETag: "\"review:3\"", data: {} as never});
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            partiesETag: "\"parties-1\"",
            parties: []
        });
        vi.mocked(evidence.openInformationRequestSubmittedEvidenceContent).mockImplementation(() =>
            new Promise<Blob>(answer => pending.push(answer)));
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it.each(USES)("opens the %s of the submitted file on the review on screen", async use => {
        renderReviewPage();
        await startOpening(use);

        await deliver();

        expect(evidence.openInformationRequestSubmittedEvidenceContent)
            .toHaveBeenCalledWith("request-a", "package-a", "version-a", use);
        expectOpenedOnce(use);
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the reviewer left the review", async use => {
        renderReviewPage();
        await startOpening(use);

        fireEvent.click(screen.getByRole("button", {name: "Leave the review"}));
        expect(await screen.findByText("Somewhere else")).toBeTruthy();
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after reading the review again was refused", async use => {
        renderReviewPage();
        await startOpening(use);

        await commentThenReread(() => Promise.reject({
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
            errorMessage: "Reviewing this request is no longer allowed",
        }));
        expect(await screen.findByRole("button", {name: "Try again"})).toBeTruthy();
        expect(screen.queryByRole("heading", {name: LABEL})).toBeNull();
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the review stopped showing the item's content", async use => {
        renderReviewPage();
        await startOpening(use);

        await commentThenReread(() => Promise.resolve(reviewOf({contentVisible: false, evidence: []})));
        expect(await screen.findByText("The submitted content is currently withheld.")).toBeTruthy();
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens a %s whose file arrives after the review was read again with the same file", async use => {
        renderReviewPage();
        await startOpening(use);

        await commentThenReread(() => Promise.resolve(reviewOf()));
        expect(await screen.findByText("The comment was recorded.")).toBeTruthy();
        await deliver();

        expectOpenedOnce(use);
    });
});
