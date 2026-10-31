/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as evidence from "../../../../services/informationRequestEvidenceService.ts";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewDelegationCandidateDto,
    InformationRequestShareRoleKey
} from "../../../models/models.tsx";
import {
    chooseReviewOption,
    reviewWorkspaceParty,
    reviewWorkspaceReview,
    savedReviewCommand,
} from "../testing/reviewWorkspaceFixtures.ts";
import ReviewWorkspaceContent from "./ReviewWorkspaceContent.tsx";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
    overrideInformationRequestReviewItem: vi.fn(),
    reconsiderInformationRequestReview: vi.fn(),
    recordInformationRequestReviewComment: vi.fn(),
    assignInformationRequestReviewer: vi.fn(),
    changeInformationRequestReviewAssignment: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestParties: vi.fn()}));
vi.mock("../../../../services/informationRequestEvidenceService.ts", () => ({openInformationRequestSubmittedEvidenceContent: vi.fn()}));

const redactedParties = () => [
    reviewWorkspaceParty("party-a", InformationRequestShareRoleKey.REVIEWER, "First reviewer"),
    reviewWorkspaceParty("party-b", InformationRequestShareRoleKey.REVIEWER),
    reviewWorkspaceParty("party-c", InformationRequestShareRoleKey.CONTRIBUTOR),
    reviewWorkspaceParty("party-d", InformationRequestShareRoleKey.REVIEWER),
    reviewWorkspaceParty("party-e", InformationRequestShareRoleKey.REVIEWER),
];

const loadReview = (canManage: boolean, delegationCandidates: InformationRequestReviewDelegationCandidateDto[]) => {
    const loaded = reviewWorkspaceReview();
    vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
        ...loaded,
        canManage,
        assignments: [{...loaded.assignments[0], delegationCandidates}],
    });
};

const openDelegation = async () => {
    render(<ReviewWorkspaceContent requestId={"request-a"}
                                   reviewId={"review-a"}/>);
    await screen.findByText("First reviewer (you)");
    fireEvent.click(screen.getByRole("button", {name: "Delegate Content review"}));
    return screen.findByRole("dialog", {name: "Delegate Content review"});
};

const offered = (dialog: HTMLElement): string[] => {
    const select = within(dialog).getByLabelText("Delegate to") as HTMLSelectElement;
    return Array.from(select.options).filter(option => option.value !== "").map(option => option.textContent ?? "");
};

describe("reviewer delegation", () => {
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
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            partiesETag: "\"parties-1\"",
            parties: redactedParties()
        });
        vi.mocked(evidence.openInformationRequestSubmittedEvidenceContent).mockResolvedValue(new Blob(["record"]));
        vi.mocked(transport.changeInformationRequestReviewAssignment).mockResolvedValue(savedReviewCommand);
    });

    afterEach(cleanup);

    it("lets a reviewer who does not manage the request tell the other reviewers apart and delegate to one", async () => {
        loadReview(false, [{partyId: "party-b", label: "Second reviewer"}, {
            partyId: "party-d",
            label: "Third reviewer"
        }]);

        const dialog = await openDelegation();

        expect(offered(dialog)).toEqual(["Second reviewer", "Third reviewer"]);
        chooseReviewOption(dialog, "Delegate to", "Third reviewer");
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "WORKLOAD"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Delegate"}));
        await waitFor(() => expect(transport.changeInformationRequestReviewAssignment).toHaveBeenCalledWith(
            "request-a", "review-a", "assignment-a", "delegation", {reasonCode: "WORKLOAD", delegatePartyId: "party-d"},
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));
    });

    it("offers only the reviewers the server names as candidates", async () => {
        loadReview(false, [{partyId: "party-e", label: "Fifth reviewer"}]);

        const dialog = await openDelegation();

        expect(offered(dialog)).toEqual(["Fifth reviewer"]);
    });

    it("offers no delegation when no other reviewer can take the assignment", async () => {
        loadReview(false, []);
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        await screen.findByText("First reviewer (you)");

        expect(screen.queryByRole("button", {name: "Delegate Content review"})).toBeNull();
        expect(screen.getByRole("button", {name: "Recuse from Content review"})).toBeTruthy();
    });

    it("offers a manager the candidates the server names for the assignment", async () => {
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            partiesETag: "\"parties-1\"",
            parties: [
                reviewWorkspaceParty("party-a", InformationRequestShareRoleKey.REVIEWER, "First reviewer"),
                reviewWorkspaceParty("party-b", InformationRequestShareRoleKey.REVIEWER, "Second reviewer"),
                reviewWorkspaceParty("party-d", InformationRequestShareRoleKey.REVIEWER, "Third reviewer"),
            ],
        });
        loadReview(true, [{partyId: "party-b", label: "Second reviewer"}]);

        const dialog = await openDelegation();

        expect(offered(dialog)).toEqual(["Second reviewer"]);
    });
});
