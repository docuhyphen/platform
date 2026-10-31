/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";

const onSaveResponses = vi.fn();
const onRefresh = vi.fn();

const request: InformationRequestDto = {
    id: "request-explanation",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: Date.parse("2026-09-08T00:00:00Z"),
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
    requestETag: "\"request:3\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
};

const binding = (index: number, fieldKey: string, label: string): SchemaFieldBindingDto => ({
    id: `binding-${index}`,
    fieldContractId: `field-contract-${index}`,
    fieldDefinitionId: `field-definition-${index}`,
    namespace: "process",
    fieldKey,
    label,
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: index,
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
});

const requirement = (
    index: number,
    requirementKey: string,
    prompt: string,
    permittedDispositions: InformationRequestResponseDisposition[],
): InformationRequestTemplateRequirementDto => ({
    id: `requirement-${index}`,
    templateRequirementId: `requirement-${index}`,
    requirementKey,
    requirementType: InformationRequestRequirementType.FIELD,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: `field-definition-${index}`,
    permittedDispositions,
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const stored = (index: number, fieldKey: string, value: string | null, etag = "\"field-set:1\""): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${index}`,
    sourceTemplateRequirementId: `requirement-${index}`,
    sourceTemplateBindingId: `requirement-${index}`,
    occurrencePath: "root",
    disposition: value ? InformationRequestResponseDisposition.PROVIDED : InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetETag: etag,
    fieldValues: [{
        fieldContractId: `field-contract-${index}`,
        schemaFieldBindingId: `binding-${index}`,
        namespace: "process",
        fieldKey,
        label: fieldKey,
        valueType: FieldValueType.SHORT_TEXT,
        isEmpty: value === null,
        value,
    }],
    responseRevision: value ? 2 : 1,
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
});

const countRequirement = requirement(1, "record-count", "How many records were kept?", [
    InformationRequestResponseDisposition.PROVIDED,
    InformationRequestResponseDisposition.NOT_APPLICABLE,
]);

const keeperRequirement = requirement(2, "record-keeper", "Who kept the records?", [InformationRequestResponseDisposition.PROVIDED]);

const workspace = (keeper: string | null, responseETag = "\"responses:1\"") => (
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   respondentKey={"respondent-a"}
                                                   responseETag={responseETag}
                                                   groups={[]}
                                                   occurrences={[]}
                                                   requirements={[countRequirement, keeperRequirement]}
                                                   bindings={[binding(1, "record-count", "Record count"), binding(2, "record-keeper", "Record keeper")]}
                                                   responses={[
                                                       stored(1, "record-count", null),
                                                       stored(2, "record-keeper", keeper, keeper ? "\"field-set:2\"" : "\"field-set:1\""),
                                                   ]}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={vi.fn()}
                                                   onRemoveOccurrence={vi.fn()}
                                                   onReorderOccurrences={vi.fn()}
                                                   onRefresh={onRefresh}/>
);

const status = () => screen.getByRole("status").textContent;

const elapse = (milliseconds: number) => act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
});

describe("an answer that needs an explanation", () => {
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
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("is held until its explanation is typed while other answers still save", async () => {
        const view = render(workspace(null));

        fireEvent.click(screen.getByRole("radio", {name: "Not applicable"}));
        const explanation = screen.getByLabelText(/^Explain your answer/) as HTMLTextAreaElement;
        expect(explanation.required).toBe(true);
        fireEvent.change(document.querySelector("#information-request-response-field-root-record-keeper") as HTMLInputElement, {
            target: {value: "Records office"},
        });
        await elapse(2500);

        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][1].patches).toEqual([{
            requirementId: "runtime-2",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValues: {
                etag: "\"field-set:1\"",
                values: [{fieldContractId: "field-contract-2", value: "Records office"}]
            },
        }]);
        view.rerender(workspace("Records office", "\"responses:2\""));
        expect(status()).toBe("Unsaved changes. Explain your answer so it can be saved.");
        expect((screen.getByRole("radio", {name: "Not applicable"}) as HTMLInputElement).checked).toBe(true);

        fireEvent.change(explanation, {target: {value: "No records this period"}});
        expect(status()).toBe("Unsaved changes. Saving shortly.");
        await elapse(2000);

        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][1].patches).toEqual([{
            requirementId: "runtime-1",
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "No records this period",
        }]);
    });

    it("never promises an autosave that a refused save holds back", async () => {
        onSaveResponses.mockRejectedValueOnce({errorMessage: "Access denied to save responses"});
        render(workspace(null));

        fireEvent.change(document.querySelector("#information-request-response-field-root-record-keeper") as HTMLInputElement, {
            target: {value: "Records office"},
        });
        await elapse(2000);

        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(status()).toBe("Unsaved changes. Choose Save responses to try again.");
        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });
});
