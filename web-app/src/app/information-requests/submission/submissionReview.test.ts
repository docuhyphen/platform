import {describe, expect, it} from "vitest";
import {
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {submissionReviewItems} from "./submissionReview.ts";

const requirement = (id: string, prompt: string, requirementType: InformationRequestRequirementType): InformationRequestTemplateRequirementDto => ({
    id,
    templateRequirementId: id,
    requirementKey: id,
    requirementType,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const response = (bindingId: string, disposition: InformationRequestResponseDisposition, overrides: Partial<InformationRequestResponseDto> = {}): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${bindingId}`,
    sourceTemplateRequirementId: bindingId,
    sourceTemplateBindingId: bindingId,
    occurrencePath: "root",
    disposition,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: "2026-09-11T00:00:00Z",
    ...overrides,
});

describe("submissionReviewItems", () =>
{
    it("states each answer in words and leaves confirmations to their own step", () =>
    {
        const items = submissionReviewItems(
            [
                response("count", InformationRequestResponseDisposition.PROVIDED, {
                    fieldValues: [{
                        fieldContractId: "contract-a",
                        schemaFieldBindingId: "binding-a",
                        namespace: "process",
                        fieldKey: "count",
                        label: "Count",
                        valueType: FieldValueType.SHORT_TEXT,
                        isEmpty: false,
                        value: "12",
                    }],
                }),
                response("register", InformationRequestResponseDisposition.NOT_APPLICABLE, {narrative: "No register this period"}),
                response("files", InformationRequestResponseDisposition.PROVIDED),
                response("pending", InformationRequestResponseDisposition.NOT_ANSWERED),
                response("assent", InformationRequestResponseDisposition.PROVIDED),
            ],
            [
                requirement("count", "How many records were kept?", InformationRequestRequirementType.FIELD),
                requirement("register", "Attach the record register", InformationRequestRequirementType.DOCUMENT),
                requirement("files", "Attach the supporting files", InformationRequestRequirementType.DOCUMENT),
                requirement("pending", "Name the keeper", InformationRequestRequirementType.FIELD),
                requirement("assent", "Confirm the response", InformationRequestRequirementType.RESPONSE_ATTESTATION),
            ],
        );

        expect(items).toEqual([
            {requirementId: "runtime-count", label: "How many records were kept?", answer: "12"},
            {requirementId: "runtime-register", label: "Attach the record register", answer: "Not applicable: No register this period"},
            {requirementId: "runtime-files", label: "Attach the supporting files", answer: "Files attached"},
            {requirementId: "runtime-pending", label: "Name the keeper", answer: "Not answered yet"},
        ]);
    });
});
