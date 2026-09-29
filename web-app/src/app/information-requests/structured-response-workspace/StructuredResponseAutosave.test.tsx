/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestDto,
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
    id: "request-autosave",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: "2026-09-08T00:00:00Z",
    updatedAt: "2026-09-08T00:00:00Z",
    requestETag: "\"request:3\"",
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
    section: "Records",
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
    helpText: "Count every record kept this period.",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: "field-definition-1",
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
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
    updatedAt: "2026-09-08T00:00:00Z",
});

const workspace = (responses: InformationRequestResponseDto[], responseETag = "\"responses:1\"", requestId = request.id) => (
    <InformationRequestStructuredResponseWorkspace request={{...request, id: requestId}}
                                                   responseETag={responseETag}
                                                   enabled={true}
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

const field = () => document.querySelector("#exchange-field-field-contract-1") as HTMLInputElement;

const type = (value: string) => fireEvent.change(field(), {target: {value}});

const elapse = (milliseconds: number) => act(async () =>
{
    await vi.advanceTimersByTimeAsync(milliseconds);
});

describe("structured response autosave and recovery", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
    });

    afterEach(() =>
    {
        cleanup();
        vi.useRealTimers();
    });

    it("saves two seconds after the last edit and says so in a polite live region", async () =>
    {
        render(workspace([stored(null)]));

        type("1");
        await elapse(1000);
        type("12");
        await elapse(1999);
        expect(onSaveResponses).not.toHaveBeenCalled();
        expect(screen.getByRole("status").textContent).toBe("Unsaved changes. Saving shortly.");

        await elapse(1);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][1].patches[0].fieldValues.values).toEqual([{fieldContractId: "field-contract-1", value: "12"}]);
        expect(screen.getByRole("status").textContent).toBe("All changes saved.");
        expect(screen.getByRole("status").getAttribute("aria-live")).toBe("polite");

        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });

    it("clears saved edits once the latest answers arrive and sends only later changes", async () =>
    {
        const view = render(workspace([stored(null)]));

        type("12");
        await elapse(2000);
        view.rerender(workspace([stored("12", "\"field-set:2\"")], "\"responses:2\""));
        expect(field().value).toBe("12");
        type("13");
        await elapse(2000);

        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][1].patches[0].fieldValues).toEqual({
            etag: "\"field-set:2\"",
            values: [{fieldContractId: "field-contract-1", value: "13"}],
        });
        expect(onSaveResponses.mock.calls[1][2]).toBe("\"responses:2\"");
    });

    it("keeps unsaved edits through a stale save, loads the latest, and waits for the respondent to save again", async () =>
    {
        onSaveResponses.mockResolvedValueOnce({outcome: "STALE"});
        render(workspace([stored(null)]));

        type("12");
        await elapse(2000);

        expect(onRefresh).toHaveBeenCalledTimes(1);
        expect(field().value).toBe("12");
        expect(screen.getByText("These answers changed somewhere else. Your changes are kept: review them against the latest answers, then choose Save responses.")).toBeTruthy();
        await elapse(6000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);

        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        await elapse(0);
        expect(onSaveResponses).toHaveBeenCalledTimes(2);
    });

    it("keeps unsaved edits in the tab's session storage until they are saved", async () =>
    {
        onSaveResponses.mockRejectedValueOnce({errorMessage: "The access session has ended", reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED"});
        const first = render(workspace([stored(null)]));

        type("12");
        await elapse(2000);
        first.unmount();
        render(workspace([stored(null)]));

        expect(field().value).toBe("12");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(window.sessionStorage.getItem("information-request-unsaved:request-autosave")).toContain("12");
    });

    it("keeps drafts and pending autosaves isolated when switching requests", async () =>
    {
        const view = render(workspace([stored(null)]));
        type("12");
        await elapse(1000);

        view.rerender(workspace([stored(null)], "\"responses:1\"", "request-other"));
        expect(field().value).toBe("");
        await elapse(2000);
        expect(onSaveResponses).not.toHaveBeenCalled();
        expect(window.sessionStorage.getItem("information-request-unsaved:request-other")).toBeNull();

        view.rerender(workspace([stored(null)]));
        expect(field().value).toBe("12");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][0]).toBe(request.id);
    });

    it("asks why when an answer other than a value is chosen and sends that answer", async () =>
    {
        render(workspace([stored(null)]));

        expect(screen.getByText("Required")).toBeTruthy();
        expect(screen.getByText("Count every record kept this period.")).toBeTruthy();
        fireEvent.click(screen.getByRole("radio", {name: "Not applicable"}));
        expect(document.querySelector("#exchange-field-field-contract-1")).toBeNull();
        fireEvent.change(screen.getByLabelText("Explain your answer"), {target: {value: "No records this period"}});
        await elapse(2000);

        expect(onSaveResponses.mock.calls[0][1].patches).toEqual([{
            requirementId: "runtime-1",
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "No records this period",
        }]);
    });
});
