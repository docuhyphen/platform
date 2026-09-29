/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactOfferDto,
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
} from "../../../models/models.tsx";
import InformationRequestStructuredResponseWorkspace from "../../structured-response-workspace/InformationRequestStructuredResponseWorkspace.tsx";
import {ReusableAnswerContext} from "../ReusableAnswerContext.ts";

const onRefresh = vi.fn();
const recertify = vi.fn();

const request: InformationRequestDto = {
    id: "request-reuse",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
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
    fieldKey: "recorded-note",
    label: "Recorded note",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.PUBLIC,
    constraints: {},
    options: [],
};

const requirement: InformationRequestTemplateRequirementDto = {
    id: "requirement-1",
    templateRequirementId: "requirement-1",
    requirementKey: "recorded-note",
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: "Record the note",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: "field-definition-1",
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
};

const stored = (value: string | null): InformationRequestResponseDto => ({
    informationRequestRequirementId: "runtime-1",
    sourceTemplateRequirementId: "requirement-1",
    sourceTemplateBindingId: "requirement-1",
    occurrencePath: "root",
    disposition: value ? InformationRequestResponseDisposition.PROVIDED : InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetETag: "\"field-set:1\"",
    fieldValues: [{
        fieldContractId: "field-contract-1",
        schemaFieldBindingId: "binding-1",
        namespace: "process",
        fieldKey: "recorded-note",
        label: "Recorded note",
        valueType: FieldValueType.SHORT_TEXT,
        isEmpty: value === null,
        value,
    }],
    responseRevision: 1,
    updatedAt: "2026-09-08T00:00:00Z",
});

const offer = (requirementId: string): InformationRequestAcceptedFactOfferDto => ({
    requirementId,
    requirementKey: "recorded-note",
    reconfirmationRequired: true,
    fact: {
        id: "fact-a",
        purposeKey: "profile.reuse",
        policyBasisKey: "policy.reuse",
        valueType: FieldValueType.SHORT_TEXT,
        value: "Earlier answer",
        confidence: InformationRequestAcceptedFactConfidence.DECLARED,
        validFrom: "2026-09-01T08:00:00Z",
        freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    },
});

const renderWorkspace = (responses: InformationRequestResponseDto[], offers: InformationRequestAcceptedFactOfferDto[]) =>
    render(
        <ReusableAnswerContext.Provider value={{offers, recertify}}>
            <InformationRequestStructuredResponseWorkspace request={request}
                                                           responseETag={"\"responses:1\""}
                                                           enabled={true}
                                                           groups={[]}
                                                           occurrences={[]}
                                                           requirements={[requirement]}
                                                           bindings={[binding]}
                                                           responses={responses}
                                                           onSaveResponses={vi.fn()}
                                                           onAddOccurrence={vi.fn()}
                                                           onRemoveOccurrence={vi.fn()}
                                                           onReorderOccurrences={vi.fn()}
                                                           onRefresh={onRefresh}/>
        </ReusableAnswerContext.Provider>,
    );

describe("reusable answers in the structured response workspace", () =>
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
    });

    afterEach(cleanup);

    it("offers an earlier answer beside the matching runtime Requirement and recertifies it under the current ETag", async () =>
    {
        recertify.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\""});
        renderWorkspace([stored(null)], [offer("runtime-1")]);

        expect(screen.getByText("Earlier answer")).toBeTruthy();
        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));
        fireEvent.click(screen.getByRole("button", {name: "Use this answer"}));

        await waitFor(() => expect(onRefresh).toHaveBeenCalled());
        expect(recertify).toHaveBeenCalledWith("request-reuse", "fact-a", "runtime-1", "\"responses:1\"");
    });

    it("offers nothing for another Requirement or when the current answer already matches", () =>
    {
        renderWorkspace([stored(null)], [offer("runtime-other")]);
        expect(screen.queryByText("Earlier answer")).toBeNull();
        cleanup();

        renderWorkspace([stored("Earlier answer")], [offer("runtime-1")]);
        expect(screen.queryByRole("button", {name: "Use this answer"})).toBeNull();
    });
});
