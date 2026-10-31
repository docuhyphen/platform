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
import {SaveResponsesResult} from "./StructuredResponseWorkspaceTypes.ts";

const onSaveResponses = vi.fn();
const onRefresh = vi.fn();

const request: InformationRequestDto = {
    id: "request-owned",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: Date.parse("2026-10-13T00:00:00Z"),
    updatedAt: Date.parse("2026-10-13T00:00:00Z"),
    requestETag: "\"request:3\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
};

const binding: SchemaFieldBindingDto = {
    id: "binding-1",
    fieldContractId: "field-contract-1",
    fieldDefinitionId: "field-definition-1",
    namespace: "process",
    fieldKey: "record-count",
    label: "Record count",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
};

const requirement: InformationRequestTemplateRequirementDto = {
    id: "requirement-1",
    templateRequirementId: "requirement-1",
    requirementKey: "record-count",
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: "How many records were kept?",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: "field-definition-1",
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
};

const stored = (value: string | null, etag = "\"field-set:1\""): InformationRequestResponseDto => ({
    informationRequestRequirementId: "runtime-1",
    sourceTemplateRequirementId: "requirement-1",
    sourceTemplateBindingId: "requirement-1",
    occurrencePath: "root",
    disposition: value ? InformationRequestResponseDisposition.PROVIDED : InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetETag: etag,
    fieldValues: [{
        fieldContractId: "field-contract-1",
        schemaFieldBindingId: "binding-1",
        namespace: "process",
        fieldKey: "record-count",
        label: "Record count",
        valueType: FieldValueType.SHORT_TEXT,
        isEmpty: value === null,
        value,
    }],
    responseRevision: 1,
    updatedAt: Date.parse("2026-10-13T00:00:00Z"),
});

const workspace = (respondentKey: string, responses = [stored(null)], responseETag = "\"responses:1\"") => (
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   respondentKey={respondentKey}
                                                   responseETag={responseETag}
                                                   groups={[]}
                                                   occurrences={[]}
                                                   requirements={[requirement]}
                                                   bindings={[binding]}
                                                   responses={responses}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={vi.fn()}
                                                   onRemoveOccurrence={vi.fn()}
                                                   onReorderOccurrences={vi.fn()}
                                                   onRefresh={onRefresh}/>
);

const field = () => document.querySelector("#information-request-response-field-root-record-count") as HTMLInputElement;

const type = (value: string) => fireEvent.change(field(), {target: {value}});

const elapse = (milliseconds: number) => act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
});

const sentValues = (call: number) => onSaveResponses.mock.calls[call][1].patches[0].fieldValues.values;

describe("structured response drafts belong to the respondent who entered them", () => {
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
        window.sessionStorage.clear();
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("shows a respondent's unsent answers again when the same respondent opens the workspace again", async () => {
        const first = render(workspace("respondent-a"));
        type("12");
        await elapse(1000);
        first.unmount();

        render(workspace("respondent-a"));

        expect(field().value).toBe("12");
    });

    it("neither shows nor saves another respondent's unsent answers in the same tab", async () => {
        const first = render(workspace("respondent-a"));
        type("12");
        await elapse(1000);
        first.unmount();

        const other = render(workspace("respondent-b"));
        expect(field().value).toBe("");
        await elapse(5000);
        expect(onSaveResponses).not.toHaveBeenCalled();
        other.unmount();

        render(workspace("respondent-a"));
        expect(field().value).toBe("12");
    });

    it("switches an open workspace to the new respondent's own answers and keeps the earlier ones for their owner", async () => {
        const view = render(workspace("respondent-a"));
        type("12");
        await elapse(1000);

        view.rerender(workspace("respondent-b"));
        expect(field().value).toBe("");
        await elapse(5000);
        expect(onSaveResponses).not.toHaveBeenCalled();

        view.rerender(workspace("respondent-a"));
        expect(field().value).toBe("12");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(sentValues(0)).toEqual([{fieldContractId: "field-contract-1", value: "12"}]);
    });

    it("names the respondent the answers belong to on every save", async () => {
        render(workspace("respondent-a"));
        type("12");
        await elapse(2000);

        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][1].respondentKey).toBe("respondent-a");
    });

    it("leaves the new respondent's answers alone when the earlier respondent's save is answered late", async () => {
        let answerFirst: (result: SaveResponsesResult) => void = () => undefined;
        onSaveResponses.mockImplementationOnce(() => new Promise<SaveResponsesResult>(resolve => {
            answerFirst = resolve;
        }));
        const view = render(workspace("respondent-a"));
        type("12");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);

        view.rerender(workspace("respondent-b"));
        expect(field().value).toBe("");
        type("34");
        await act(async () => answerFirst({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []}));
        view.rerender(workspace("respondent-b", [stored("12", "\"field-set:2\"")], "\"responses:2\""));

        expect(field().value).toBe("34");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][1].respondentKey).toBe("respondent-b");
        expect(sentValues(1)).toEqual([{fieldContractId: "field-contract-1", value: "34"}]);
    });

    it("keeps the answers and reloads when the server says they were entered by another respondent", async () => {
        onSaveResponses.mockRejectedValueOnce({
            errorMessage: "These answers were entered by another respondent and were not saved",
            reasonCode: "INFORMATION_REQUEST_RESPONDENT_CHANGED",
        });
        render(workspace("respondent-a"));
        type("12");
        await elapse(2000);

        expect(onRefresh).toHaveBeenCalledTimes(1);
        expect(screen.getByText("You are no longer responding as the person who entered these answers, so they were not saved. They stay in this browser tab for that person.")).toBeTruthy();
        expect(field().value).toBe("12");
        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });
});
