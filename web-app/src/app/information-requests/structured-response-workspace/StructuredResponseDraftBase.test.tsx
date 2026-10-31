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

const STORAGE_KEY = "information-request-unsaved:request-draft-base:respondent-a";

const STALE_TEXT = "These answers changed somewhere else. Your changes are kept: review them against the latest answers, then choose Save responses.";

const request: InformationRequestDto = {
    id: "request-draft-base",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: Date.parse("2026-10-26T00:00:00Z"),
    updatedAt: Date.parse("2026-10-26T00:00:00Z"),
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
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
};

const stored = (value: string | null, etag = "\"field-set:1\"", revision = 1): InformationRequestResponseDto => ({
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
    responseRevision: revision,
    updatedAt: Date.parse("2026-10-26T00:00:00Z"),
});

const notApplicable = (narrative: string, revision: number): InformationRequestResponseDto => ({
    ...stored(null, "\"field-set:1\"", revision),
    disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
    narrative,
});

const workspace = (responses: InformationRequestResponseDto[], responseETag = "\"responses:1\"") => (
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   respondentKey={"respondent-a"}
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

const draftThenReopen = async (draft: string, latest: InformationRequestResponseDto, responseETag: string) => {
    const first = render(workspace([stored(null)]));
    type(draft);
    await elapse(1000);
    first.unmount();
    return render(workspace([latest], responseETag));
};

describe("structured response drafts remember the answer they were based on", () => {
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

    it("shows the latest saved answer instead of sending a reopened draft whose answer changed elsewhere", async () => {
        await draftThenReopen("12", stored("15", "\"field-set:2\"", 2), "\"responses:2\"");

        expect(field().value).toBe("12");
        expect(screen.getByText("Answers saved elsewhere")).toBeTruthy();
        expect(screen.getByText("Latest saved answer: 15")).toBeTruthy();
        expect(screen.getByRole("status").textContent).toBe(STALE_TEXT);
        await elapse(5000);
        expect(onSaveResponses).not.toHaveBeenCalled();
    });

    it("replaces the latest saved answer when the respondent saves after reviewing it", async () => {
        await draftThenReopen("12", stored("15", "\"field-set:2\"", 2), "\"responses:2\"");

        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        await elapse(0);

        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][1].patches[0].fieldValues).toEqual({
            etag: "\"field-set:2\"",
            values: [{fieldContractId: "field-contract-1", value: "12"}],
        });
        expect(onSaveResponses.mock.calls[0][2]).toBe("\"responses:2\"");
    });

    it("keeps the latest saved answer and drops the draft when the respondent chooses it", async () => {
        await draftThenReopen("12", stored("15", "\"field-set:2\"", 2), "\"responses:2\"");

        fireEvent.click(screen.getByRole("button", {name: "Keep saved answer: How many records were kept?"}));
        await elapse(0);

        expect(field().value).toBe("15");
        expect(screen.queryByText("Latest saved answer: 15")).toBeNull();
        expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull();
        await elapse(5000);
        expect(onSaveResponses).not.toHaveBeenCalled();
    });

    it("drops a reopened draft that already equals the latest saved answer", async () => {
        await draftThenReopen("15", stored("15", "\"field-set:2\"", 2), "\"responses:2\"");
        await elapse(0);

        expect(field().value).toBe("15");
        expect(screen.queryByText("Answers saved elsewhere")).toBeNull();
        expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull();
        await elapse(5000);
        expect(onSaveResponses).not.toHaveBeenCalled();
    });

    it("does not bring back a saved answer whose latest read never arrived", async () => {
        onSaveResponses.mockResolvedValueOnce({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            responses: [stored("12", "\"field-set:2\"", 2)],
        });
        const first = render(workspace([stored(null)]));
        type("12");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        first.unmount();
        expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull();

        render(workspace([stored("15", "\"field-set:3\"", 3)], "\"responses:3\""));

        expect(field().value).toBe("15");
        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });

    it("shows the other device's answer while the respondent reviews after a stale save", async () => {
        onSaveResponses.mockResolvedValueOnce({outcome: "STALE"});
        const view = render(workspace([stored(null)]));
        type("12");
        await elapse(2000);
        expect(onRefresh).toHaveBeenCalledTimes(1);

        view.rerender(workspace([stored("15", "\"field-set:2\"", 2)], "\"responses:2\""));

        expect(field().value).toBe("12");
        expect(screen.getByText("Latest saved answer: 15")).toBeTruthy();
        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });

    it("holds a reopened explanation for review when the explanation changed elsewhere", async () => {
        const first = render(workspace([stored(null)]));
        fireEvent.click(screen.getByRole("radio", {name: "Not applicable"}));
        fireEvent.change(screen.getByLabelText(/^Explain your answer/), {target: {value: "No records this period"}});
        await elapse(1000);
        first.unmount();

        render(workspace([notApplicable("Kept elsewhere", 2)], "\"responses:2\""));

        expect(screen.getByText("Latest saved answer: Not applicable: Kept elsewhere")).toBeTruthy();
        await elapse(5000);
        expect(onSaveResponses).not.toHaveBeenCalled();
    });

    it("keeps saving a reopened draft whose answer did not change elsewhere", async () => {
        await draftThenReopen("12", stored(null), "\"responses:1\"");

        expect(screen.queryByText("Answers saved elsewhere")).toBeNull();
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][1].patches[0].fieldValues.values).toEqual([{
            fieldContractId: "field-contract-1",
            value: "12",
        }]);
    });
});
