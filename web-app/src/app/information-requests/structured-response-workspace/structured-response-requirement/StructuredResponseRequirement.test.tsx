/** @vitest-environment jsdom */
import {cleanup, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceSourceKind,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseEditLock,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
} from "../../../models/models.tsx";
import {RequirementEvidenceCommands} from "../../requirement-evidence/requirementEvidenceCommands.ts";
import {RequirementEvidenceContext} from "../../requirement-evidence/RequirementEvidenceContext.ts";
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
    updatedAt: Date.parse("2026-09-25T08:00:00Z"),
});

const renderRequirement = (responses: InformationRequestResponseDto[]) =>
    render(
        <RequirementEvidenceContext.Provider
            value={{uploadAvailable: true, malwareScanning: false, commands: commands as RequirementEvidenceCommands}}>
            <StructuredResponseRequirement requestId="request-a"
                                           occurrencePath="reported-item[1]"
                                           requirement={documentRequirement}
                                           bindings={[]}
                                           responses={responses}
                                           edits={{}}
                                           setEdits={vi.fn()}
                                           answers={{}}
                                           setAnswers={vi.fn()}
                                           responseETag={"\"responses:1\""}
                                           busy={false}
                                           readOnly={false}
                                           onResult={vi.fn()}
                                           onCommandStart={vi.fn()}
                                           onCommandFailure={vi.fn()}/>
        </RequirementEvidenceContext.Provider>,
    );

describe("StructuredResponseRequirement", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        commands.list.mockResolvedValue({
            requirementId: "runtime-reported-item[1]",
            evidenceETag: "\"e:0\"",
            artifacts: []
        });
    });

    afterEach(cleanup);

    it("collects a Document Requirement's evidence under the runtime occurrence the response names", async () => {
        renderRequirement([response("reported-item[0]"), response("reported-item[1]")]);

        expect(await screen.findByText("No files yet.")).toBeTruthy();
        expect(screen.getByText("Provide the supporting record")).toBeTruthy();
        await waitFor(() => expect(commands.list).toHaveBeenCalledWith("request-a", "runtime-reported-item[1]"));
        expect(commands.list).toHaveBeenCalledTimes(1);
    });

    it("offers no file changes for a Document Requirement whose part was submitted", async () => {
        commands.list.mockResolvedValue({
            requirementId: "runtime-reported-item[1]",
            evidenceETag: "\"e:1\"",
            artifacts: [{
                id: "artifact-a",
                requirementId: "runtime-reported-item[1]",
                artifactKey: "evidence-1",
                collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
                artifactRevision: 1,
                etag: "\"artifact-a:1\"",
                createdByCaller: true,
                changeableByCaller: true,
                createdAt: Date.parse("2026-09-25T08:00:00Z"),
                updatedAt: Date.parse("2026-09-25T08:00:00Z"),
                versions: [{
                    id: "version-a",
                    versionNumber: 1,
                    sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
                    declaredFileName: "record.pdf",
                    declaredMediaType: "application/pdf",
                    contentLength: 2048,
                    createdByCaller: true,
                    createdAt: Date.parse("2026-09-25T08:00:00Z"),
                    findings: [],
                }],
            }],
        });
        renderRequirement([{...response("reported-item[1]"), editLock: InformationRequestResponseEditLock.SUBMITTED}]);

        expect(await screen.findByText("record.pdf")).toBeTruthy();
        const panel = "information-request-evidence-reported-item_1-supporting-record-PANEL";
        expect(document.getElementById(`${panel}-upload-button`)).toBeNull();
        expect(document.getElementById(`${panel}-artifact-evidence-1-replace`)).toBeNull();
        expect(document.getElementById(`${panel}-artifact-evidence-1-withdraw`)).toBeNull();
        expect(document.getElementById(`${panel}-artifact-evidence-1-download`)).toBeTruthy();
    });

    it("shows nothing for a Document Requirement the caller has no response occurrence for", () => {
        const {container} = renderRequirement([response("reported-item[0]")]);

        expect(container.innerHTML).toBe("");
        expect(commands.list).not.toHaveBeenCalled();
    });

    it("offers no answer for a Field Requirement the caller has no runtime Requirement for, since nothing could save it", () => {
        const fieldRequirement: InformationRequestTemplateRequirementDto = {
            ...documentRequirement,
            id: "binding-field",
            templateRequirementId: "template-requirement-field",
            requirementKey: "recorded-summary",
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: "Provide the recorded summary",
            collectedFieldDefinitionId: "field-definition-a",
        };
        const {container} = render(
            <StructuredResponseRequirement requestId="request-a"
                                           occurrencePath="reported-item[1]"
                                           requirement={fieldRequirement}
                                           bindings={[{
                                               id: "schema-binding-a",
                                               fieldContractId: "field-contract-a",
                                               fieldDefinitionId: "field-definition-a",
                                               namespace: "process",
                                               fieldKey: "recorded-summary",
                                               label: "Recorded summary",
                                               valueType: FieldValueType.SHORT_TEXT,
                                               displayOrder: 0,
                                               section: "Data",
                                               isRequired: true,
                                               isReadOnly: false,
                                               visibility: FieldDataClassification.PUBLIC,
                                               constraints: {},
                                               options: [],
                                           }]}
                                           responses={[{
                                               ...response("reported-item[0]"),
                                               sourceTemplateBindingId: "binding-field",
                                               sourceTemplateRequirementId: "template-requirement-field"
                                           }]}
                                           edits={{}}
                                           setEdits={vi.fn()}
                                           answers={{}}
                                           setAnswers={vi.fn()}
                                           responseETag={"\"responses:1\""}
                                           busy={false}
                                           readOnly={false}
                                           onResult={vi.fn()}
                                           onCommandStart={vi.fn()}
                                           onCommandFailure={vi.fn()}/>,
        );

        expect(container.innerHTML).toBe("");
    });
});
