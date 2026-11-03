/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestContributorRole,
    InformationRequestPartyDto,
    InformationRequestRecipientPreviewDto,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestShareRoleKey,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import RecipientPreviewDialog from "./RecipientPreviewDialog.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestRecipientPreview: vi.fn()}));

const party = (id: string, roleKey: InformationRequestShareRoleKey, label: string, active = true): InformationRequestPartyDto => ({
    id,
    informationRequestId: "request-a",
    roleKey,
    active,
    assignedAt: Date.parse("2026-09-27T08:00:00Z"),
    partyRevision: 1,
    partyETag: `"${id}-1"`,
    label,
});

const preview = (partyId: string, prompts: string[]): InformationRequestRecipientPreviewDto => ({
    partyId,
    roleKey: InformationRequestShareRoleKey.ATTESTOR,
    templateVersion: {
        id: "version-a",
        templateDefinitionId: "definition-a",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        sections: [{
            id: "section-a",
            sectionKey: "confirmations",
            title: "Confirmations",
            requirements: prompts.map((prompt, index): InformationRequestTemplateRequirementDto => ({
                id: `binding-${index}`,
                templateRequirementId: `requirement-${index}`,
                requirementKey: `confirmation-${index}`,
                requirementType: InformationRequestRequirementType.RESPONSE_ATTESTATION,
                prompt,
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.REQUIRED,
                contributorRole: InformationRequestContributorRole.ATTESTOR,
                reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                permittedDispositions: [],
                substituteRequirementKeys: [],
                supportingEvidenceRequirementKeys: [],
            })),
        }],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: Date.parse("2026-09-20T08:00:00Z"),
    },
});

const attestor = party("party-a", InformationRequestShareRoleKey.ATTESTOR, "attestor@process.test");
const removedContributor = party("party-b", InformationRequestShareRoleKey.CONTRIBUTOR, "former@process.test", false);
const reviewer = party("party-c", InformationRequestShareRoleKey.REVIEWER, "reviewer@process.test");

describe("RecipientPreviewDialog", () => {
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

    beforeEach(() => vi.clearAllMocks());

    afterEach(cleanup);

    it("offers only active answering parties and shows the chosen party's questions once they load", async () => {
        let answer: (value: InformationRequestRecipientPreviewDto) => void = () => undefined;
        vi.mocked(authoring.getInformationRequestRecipientPreview).mockReturnValue(new Promise(resolve => {
            answer = resolve;
        }));
        render(<RecipientPreviewDialog requestId={"request-a"}
                                       parties={[removedContributor, reviewer, attestor]}
                                       onDismiss={vi.fn()}/>);
        const dialog = await screen.findByRole("dialog", {name: "Preview as recipient"});
        const choice = within(dialog).getByLabelText("Preview for") as HTMLSelectElement;

        expect(Array.from(choice.options).map(option => option.textContent)).toEqual(["Attestor: attestor@process.test"]);
        expect(within(dialog).getByText("Loading preview")).toBeTruthy();
        await act(async () => answer(preview("party-a", ["Confirm the response is complete"])));

        expect(within(dialog).getByText("Confirm the response is complete")).toBeTruthy();
        expect(within(dialog).getByText("Answered by confirming the response")).toBeTruthy();
        expect(authoring.getInformationRequestRecipientPreview).toHaveBeenCalledWith("request-a", "party-a");
    });

    it("says so when the chosen party is not asked anything", async () => {
        vi.mocked(authoring.getInformationRequestRecipientPreview).mockResolvedValue(preview("party-a", []));
        render(<RecipientPreviewDialog requestId={"request-a"}
                                       parties={[attestor]}
                                       onDismiss={vi.fn()}/>);
        const dialog = await screen.findByRole("dialog", {name: "Preview as recipient"});

        expect(await within(dialog).findByText("This party is not asked anything on this request.")).toBeTruthy();
        expect(within(dialog).queryByText("Confirmations")).toBeNull();
    });

    it("shows the server's refusal instead of any questions", async () => {
        vi.mocked(authoring.getInformationRequestRecipientPreview).mockRejectedValue({errorMessage: "Access denied to this Information Request"});
        render(<RecipientPreviewDialog requestId={"request-a"}
                                       parties={[attestor]}
                                       onDismiss={vi.fn()}/>);
        const dialog = await screen.findByRole("dialog", {name: "Preview as recipient"});

        expect(await within(dialog).findByText("Access denied to this Information Request")).toBeTruthy();
        expect(within(dialog).queryByText("Confirmations")).toBeNull();
    });

    it("never shows an earlier party's questions under a later party", async () => {
        const preparer = party("party-d", InformationRequestShareRoleKey.PREPARER, "preparer@process.test");
        let answerPreparer: (value: InformationRequestRecipientPreviewDto) => void = () => undefined;
        vi.mocked(authoring.getInformationRequestRecipientPreview).mockImplementation((_, partyId) => partyId === "party-a"
            ? Promise.resolve(preview("party-a", ["Confirm the response is complete"]))
            : new Promise(resolve => {
                answerPreparer = resolve;
            }));
        render(<RecipientPreviewDialog requestId={"request-a"}
                                       parties={[attestor, preparer]}
                                       onDismiss={vi.fn()}/>);
        const dialog = await screen.findByRole("dialog", {name: "Preview as recipient"});
        expect(await within(dialog).findByText("Confirm the response is complete")).toBeTruthy();

        fireEvent.change(within(dialog).getByLabelText("Preview for"), {target: {value: "party-d"}});

        expect(within(dialog).queryByText("Confirm the response is complete")).toBeNull();
        expect(within(dialog).getByText("Loading preview")).toBeTruthy();
        await act(async () => answerPreparer(preview("party-d", ["Prepare the record summary"])));
        expect(within(dialog).getByText("Prepare the record summary")).toBeTruthy();
    });
});
