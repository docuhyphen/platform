/** @vitest-environment jsdom */
import {cleanup, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {RequirementEvidenceCommands} from "../requirement-evidence/requirementEvidenceCommands.ts";
import {RequirementEvidenceContext} from "../requirement-evidence/RequirementEvidenceContext.ts";
import StructuredResponseRequirement from "./StructuredResponseRequirement.tsx";

const commands = {list: vi.fn(), upload: vi.fn(), replace: vi.fn(), withdraw: vi.fn(), open: vi.fn()};

const documentRequirement: InformationRequestTemplateRequirementDto = {
    id: "binding-document",
    templateRequirementId: "template-requirement-document",
    requirementKey: "supporting-record",
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt: "Provide the supporting record",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
};

const response = (occurrencePath: string): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${occurrencePath}`,
    sourceTemplateRequirementId: documentRequirement.templateRequirementId,
    sourceTemplateBindingId: documentRequirement.id,
    occurrencePath,
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: "2026-09-25T08:00:00Z",
});

const renderRequirement = (responses: InformationRequestResponseDto[]) =>
    render(
        <RequirementEvidenceContext.Provider value={{uploadAvailable: true, malwareScanning: false, commands: commands as RequirementEvidenceCommands}}>
            <StructuredResponseRequirement requestId="request-a"
                                           occurrencePath="reported-item[1]"
                                           requirement={documentRequirement}
                                           bindings={[]}
                                           responses={responses}
                                           edits={{}}
                                           setEdits={vi.fn()}/>
        </RequirementEvidenceContext.Provider>,
    );

describe("StructuredResponseRequirement", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        commands.list.mockResolvedValue({requirementId: "runtime-reported-item[1]", evidenceETag: "\"e:0\"", artifacts: []});
    });

    afterEach(cleanup);

    it("collects a Document Requirement's evidence under the runtime occurrence the response names", async () =>
    {
        renderRequirement([response("reported-item[0]"), response("reported-item[1]")]);

        expect(await screen.findByText("No files yet.")).toBeTruthy();
        expect(screen.getByText("Provide the supporting record")).toBeTruthy();
        await waitFor(() => expect(commands.list).toHaveBeenCalledWith("request-a", "runtime-reported-item[1]"));
        expect(commands.list).toHaveBeenCalledTimes(1);
    });

    it("shows nothing for a Document Requirement the caller has no response occurrence for", () =>
    {
        const {container} = renderRequirement([response("reported-item[0]")]);

        expect(container.innerHTML).toBe("");
        expect(commands.list).not.toHaveBeenCalled();
    });
});
