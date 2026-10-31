/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
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

const request: InformationRequestDto = {
    id: "request-refusal",
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

const binding = (index: number, fieldKey: string): SchemaFieldBindingDto => ({
    id: `binding-${index}`,
    fieldContractId: `field-contract-${index}`,
    fieldDefinitionId: `field-definition-${index}`,
    namespace: "process",
    fieldKey,
    label: fieldKey,
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: index,
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
});

const requirement = (index: number, requirementKey: string, prompt: string): InformationRequestTemplateRequirementDto => ({
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
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const stored = (index: number, fieldKey: string): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${index}`,
    sourceTemplateRequirementId: `requirement-${index}`,
    sourceTemplateBindingId: `requirement-${index}`,
    occurrencePath: "root",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetETag: "\"field-set:1\"",
    fieldValues: [{
        fieldContractId: `field-contract-${index}`,
        schemaFieldBindingId: `binding-${index}`,
        namespace: "process",
        fieldKey,
        label: fieldKey,
        valueType: FieldValueType.SHORT_TEXT,
        isEmpty: true,
        value: null,
    }],
    responseRevision: 1,
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
});

const itemRefusal = {
    errorMessage: "Some answers are not valid",
    reasonCode: "INFORMATION_REQUEST_STRUCTURED_RESPONSE_INVALID",
    problems: [{
        code: "VALUE_OUT_OF_RANGE",
        message: "This value is outside the accepted range",
        requirementId: "runtime-2",
        occurrencePath: "root",
    }],
    undisclosedProblemCount: 1,
};

const renderWorkspace = () => render(
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   respondentKey={"respondent-a"}
                                                   responseETag={"\"responses:1\""}
                                                   groups={[]}
                                                   occurrences={[]}
                                                   requirements={[
                                                       requirement(1, "record-count", "How many records were kept?"),
                                                       requirement(2, "record-keeper", "Who kept the records?"),
                                                   ]}
                                                   bindings={[binding(1, "record-count"), binding(2, "record-keeper")]}
                                                   responses={[stored(1, "record-count"), stored(2, "record-keeper")]}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={vi.fn()}
                                                   onRemoveOccurrence={vi.fn()}
                                                   onReorderOccurrences={vi.fn()}
                                                   onRefresh={vi.fn()}/>,
);

const keeperField = () => document.querySelector("#information-request-response-field-root-record-keeper") as HTMLInputElement;

const keeperItem = () => document.getElementById("information-request-response-requirement-root-record-keeper");

describe("a refused save of structured answers", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        Element.prototype.scrollIntoView = vi.fn();
    });

    beforeEach(() => {
        vi.clearAllMocks();
        window.sessionStorage.clear();
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("names each refused answer, links to it, and moves focus to the first one", async () => {
        onSaveResponses.mockRejectedValueOnce(itemRefusal);
        renderWorkspace();

        fireEvent.change(keeperField(), {target: {value: "Records office"}});
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        const go = await screen.findByRole("button", {name: "Go to Who kept the records?"});
        expect(screen.getByRole("alert").textContent).toContain("Some answers are not valid");
        expect(screen.getByRole("alert").textContent).toContain("1 more item could not be shown.");
        expect(screen.getByText("This value is outside the accepted range")).toBeTruthy();
        await waitFor(() => expect(document.activeElement).toBe(keeperItem()));

        screen.getByRole("button", {name: "Save responses"}).focus();
        fireEvent.click(go);
        expect(document.activeElement).toBe(keeperItem());
    });

    it("says that the Exchange is not accepting answers when the parent state refuses a save", async () => {
        onSaveResponses.mockRejectedValueOnce({
            errorMessage: "Information Request parent state does not permit this mutation",
            reasonCode: "INFORMATION_REQUEST_PARENT_STATE_INVALID",
        });
        renderWorkspace();

        fireEvent.change(keeperField(), {target: {value: "Records office"}});
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(screen.getByRole("alert").textContent).toContain(
            "The Exchange holding this request is not accepting answers now, for example because it has not been " +
            "accepted yet or it has ended. Your unsaved changes stay in this tab.",
        ));
        expect(screen.getByRole("alert").textContent).not.toContain("parent state");
    });

    it("moves focus to the clears a refused save asks the respondent to confirm", async () => {
        onSaveResponses.mockRejectedValueOnce({
            errorMessage: "Hidden response data requires explicit clearing confirmation",
            reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
            hiddenResponseClears: [{
                requirementId: "runtime-hidden",
                sourceTemplateBindingId: "requirement-hidden",
                occurrencePath: "root"
            }],
        });
        renderWorkspace();

        fireEvent.change(keeperField(), {target: {value: "Records office"}});
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(document.activeElement).toBe(document.getElementById("information-request-hidden-clear-confirmations")));
        expect(screen.getByRole("group", {name: "Confirm hidden response clearing"})).toBe(document.activeElement);
    });

    it("names the refused answers of an autosave without taking focus from the field being typed in", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        onSaveResponses.mockRejectedValueOnce(itemRefusal);
        renderWorkspace();

        keeperField().focus();
        fireEvent.change(keeperField(), {target: {value: "Records office"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(2000);
        });

        expect(screen.getByRole("button", {name: "Go to Who kept the records?"})).toBeTruthy();
        expect(document.activeElement).toBe(keeperField());
    });
});
