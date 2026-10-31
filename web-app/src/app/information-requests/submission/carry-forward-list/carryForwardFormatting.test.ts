import {describe, expect, it} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestCarryForwardDecision,
    InformationRequestCarryForwardDto,
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../../models/models.tsx";
import {priorAnswerText, requirementValueFormats} from "./carryForwardFormatting.ts";

const requirement = (id: string, collectedFieldDefinitionId: string | null): InformationRequestTemplateRequirementDto => ({
    id,
    templateRequirementId: `template-${id}`,
    requirementKey: id,
    requirementType: collectedFieldDefinitionId ? InformationRequestRequirementType.FIELD : InformationRequestRequirementType.DOCUMENT,
    prompt: id,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const response = (runtimeId: string, bindingId: string): InformationRequestResponseDto => ({
    informationRequestRequirementId: runtimeId,
    sourceTemplateRequirementId: `template-${bindingId}`,
    sourceTemplateBindingId: bindingId,
    occurrencePath: "root",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: 0,
});

const binding: SchemaFieldBindingDto = {
    id: "binding-location",
    fieldContractId: "contract-location",
    fieldDefinitionId: "field-location",
    namespace: "process",
    fieldKey: "location",
    label: "Location",
    valueType: FieldValueType.SINGLE_SELECT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [{code: "on-site", label: "On site"}],
};

const carried = (changes: Partial<InformationRequestCarryForwardDto>): InformationRequestCarryForwardDto => ({
    requirementId: "runtime-location",
    decision: InformationRequestCarryForwardDecision.OFFERED,
    sourcePackageId: "package-a",
    ...changes,
});

describe("carryForwardFormatting", () => {
    it("maps each answered item to the value type and options of the Field it collects", () => {
        const formats = requirementValueFormats(
            [response("runtime-location", "binding-location-requirement"), response("runtime-file", "binding-file-requirement")],
            [requirement("binding-location-requirement", "field-location"), requirement("binding-file-requirement", null)],
            [binding],
        );

        expect(formats).toEqual({
            "runtime-location": {valueType: FieldValueType.SINGLE_SELECT, options: [{code: "on-site", label: "On site"}]},
        });
    });

    it("states an earlier answer by its label, its answer kind, or its reason", () => {
        const format = {valueType: FieldValueType.SINGLE_SELECT, options: binding.options};

        expect(priorAnswerText(carried({priorDisposition: InformationRequestResponseDisposition.PROVIDED, priorFieldValue: "on-site"}), format))
            .toBe("On site");
        expect(priorAnswerText(carried({priorDisposition: InformationRequestResponseDisposition.PARTIALLY_PROVIDED, priorFieldValue: "on-site"}), format))
            .toBe("Partly provided: On site");
        expect(priorAnswerText(carried({priorDisposition: InformationRequestResponseDisposition.UNAVAILABLE, priorNarrative: "Lost in the move"})))
            .toBe("Unavailable: Lost in the move");
        expect(priorAnswerText(carried({priorDisposition: InformationRequestResponseDisposition.WAIVED})))
            .toBe("Waived");
        expect(priorAnswerText(carried({priorDisposition: InformationRequestResponseDisposition.PROVIDED, priorFieldValue: ["one", "two"]})))
            .toBe("one, two");
    });
});
