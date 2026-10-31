/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactOfferDto,
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestGroupOccurrenceDto,
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
import {ReusableAnswerContext} from "../reusable-answer/ReusableAnswerContext.ts";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";
import {STALE_MESSAGE} from "./responseSaveStatus.ts";

const onSaveResponses = vi.fn();
const onAddOccurrence = vi.fn();
const onRemoveOccurrence = vi.fn();
const onReorderOccurrences = vi.fn();
const onRefresh = vi.fn();
const recertify = vi.fn();

const request: InformationRequestDto = {
    id: "request-stale",
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
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
});

const entry = (id: string, index: number): InformationRequestGroupOccurrenceDto => ({
    id,
    informationRequestId: "request-stale",
    sourceTemplateGroupId: "group-entry",
    occurrenceIndex: index,
    occurrencePath: `entry[${index}]`,
    createdAt: Date.parse("2026-09-08T00:00:00Z"),
});

const offer: InformationRequestAcceptedFactOfferDto = {
    requirementId: "runtime-1",
    requirementKey: "record-count",
    reconfirmationRequired: true,
    fact: {
        id: "fact-a",
        purposeKey: "profile.reuse",
        policyBasisKey: "policy.reuse",
        valueType: FieldValueType.SHORT_TEXT,
        value: "Earlier answer",
        confidence: InformationRequestAcceptedFactConfidence.DECLARED,
        validFrom: Date.parse("2026-09-01T08:00:00Z"),
        freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    },
};

const workspace = (responses: InformationRequestResponseDto[], responseETag = "\"responses:1\"", offers: InformationRequestAcceptedFactOfferDto[] = []) => (
    <ReusableAnswerContext.Provider value={{offers, error: null, reload: () => Promise.resolve(), recertify}}>
        <InformationRequestStructuredResponseWorkspace request={request}
                                                       respondentKey={"respondent-a"}
                                                       responseETag={responseETag}
                                                       groups={[{
                                                           id: "group-entry",
                                                           groupKey: "entry",
                                                           minOccurrences: 0,
                                                           maxOccurrences: 3
                                                       }]}
                                                       occurrences={[entry("entry-1", 0), entry("entry-2", 1)]}
                                                       requirements={[requirement]}
                                                       bindings={[binding]}
                                                       responses={responses}
                                                       onSaveResponses={onSaveResponses}
                                                       onAddOccurrence={onAddOccurrence}
                                                       onRemoveOccurrence={onRemoveOccurrence}
                                                       onReorderOccurrences={onReorderOccurrences}
                                                       onRefresh={onRefresh}/>
    </ReusableAnswerContext.Provider>
);

const field = () => document.querySelector("#information-request-response-field-root-record-count") as HTMLInputElement;

const type = (value: string) => fireEvent.change(field(), {target: {value}});

const status = () => screen.getByRole("status").textContent;

const click = (id: string) => fireEvent.click(document.getElementById(id) as HTMLElement);

const elapse = (milliseconds: number) => act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
});

const entryCommands: [string, () => void][] = [
    ["adding an entry", () => click("information-request-add-occurrence-entry")],
    ["moving an entry", () => click("information-request-occurrence-down-entry-1")],
    ["removing an entry", () => {
        click("information-request-occurrence-remove-entry-1");
        click("information-request-occurrence-remove-dialog-entry-1-confirm");
    }],
];

describe("structured response recovery after a command finds the answers changed elsewhere", () => {
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
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:3\"", responses: []});
        onAddOccurrence.mockResolvedValue({outcome: "STALE"});
        onReorderOccurrences.mockResolvedValue({outcome: "STALE"});
        onRemoveOccurrence.mockResolvedValue({outcome: "STALE"});
        recertify.mockResolvedValue({outcome: "STALE"});
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it.each(entryCommands)("keeps autosaving answers after %s finds the answers changed elsewhere", async (_, command) => {
        const view = render(workspace([stored(null)]));

        command();
        await elapse(0);
        expect(onRefresh).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("alert").textContent).toContain("so that change was not made");
        view.rerender(workspace([stored(null)], "\"responses:2\""));
        type("12");

        expect(status()).toBe("Unsaved changes. Saving shortly.");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(onSaveResponses.mock.calls[0][2]).toBe("\"responses:2\"");
    });

    it("keeps autosaving answers after Use this answer finds the answers changed elsewhere", async () => {
        const view = render(workspace([stored(null)], "\"responses:1\"", [offer]));

        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));
        fireEvent.click(screen.getByRole("button", {name: "Use this answer"}));
        await elapse(0);
        expect(onRefresh).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("alert").textContent).toContain("so that change was not made");
        view.rerender(workspace([stored(null)], "\"responses:2\"", [offer]));
        type("12");

        expect(status()).toBe("Unsaved changes. Saving shortly.");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });

    it("stops asking to review a stale save once nothing is left to save", async () => {
        onSaveResponses.mockResolvedValueOnce({outcome: "STALE"});
        const view = render(workspace([stored(null)]));

        type("12");
        await elapse(2000);
        expect(status()).toBe(STALE_MESSAGE);

        view.rerender(workspace([stored("12", "\"field-set:2\"", 2)], "\"responses:2\""));

        expect(status()).not.toBe(STALE_MESSAGE);
        type("13");
        expect(status()).toBe("Unsaved changes. Saving shortly.");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(2);
    });

    it("stops asking to review a stale save once a later command succeeds", async () => {
        onSaveResponses.mockResolvedValueOnce({outcome: "STALE"});
        onAddOccurrence.mockResolvedValueOnce({outcome: "SAVED", responseETag: "\"responses:2\"", occurrences: []});
        render(workspace([stored(null)]));

        type("12");
        await elapse(2000);
        expect(status()).toBe(STALE_MESSAGE);

        click("information-request-add-occurrence-entry");
        await elapse(0);

        expect(status()).toBe("Unsaved changes. Saving shortly.");
        await elapse(2000);
        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][2]).toBe("\"responses:2\"");
    });
});
