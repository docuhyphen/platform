import {describe, expect, it} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestGroupOccurrenceDto,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {buildResponsePatches, responseKey} from "./structuredResponseWorkspaceState.ts";

interface RuntimeRequirementCorrelation
{
    sourceTemplateRequirementId: string;
    sourceTemplateBindingId: string;
}

const occurrence: InformationRequestGroupOccurrenceDto = {
    id: "occurrence-1",
    informationRequestId: "request-1",
    sourceTemplateGroupId: "group-1",
    occurrenceIndex: 0,
    occurrencePath: "items[0]",
    createdAt: "2026-09-11T00:00:00Z",
};

const group: InformationRequestTemplateGroupDto = {
    id: "group-1",
    groupKey: "items",
    minOccurrences: 0,
    maxOccurrences: 3,
};

const binding = (
    id: string,
    fieldContractId: string,
    fieldDefinitionId: string,
    fieldKey: string,
): SchemaFieldBindingDto => ({
    id,
    fieldContractId,
    fieldDefinitionId,
    namespace: "process",
    fieldKey,
    label: fieldKey,
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    section: "Data",
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.PUBLIC,
    constraints: {},
    options: [],
});

const requirement = (
    sourceTemplateBindingId: string,
    sourceTemplateRequirementId: string,
    fieldDefinitionId: string,
): InformationRequestTemplateRequirementDto => ({
    id: sourceTemplateBindingId,
    templateRequirementId: sourceTemplateRequirementId,
    requirementKey: sourceTemplateBindingId,
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: sourceTemplateBindingId,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    occurrenceAnchorKey: "items",
    collectedFieldDefinitionId: fieldDefinitionId,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const response = (
    runtimeRequirementId: string,
    sourceTemplateBindingId: string,
    sourceTemplateRequirementId: string,
): InformationRequestResponseDto & RuntimeRequirementCorrelation => ({
    informationRequestRequirementId: runtimeRequirementId,
    sourceTemplateBindingId,
    sourceTemplateRequirementId,
    occurrencePath: "items[0]",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetId: "value-set-1",
    fieldValueSetETag: "\"field-set:1\"",
    fieldValues: [
        {
            fieldContractId: "field-contract-a",
            schemaFieldBindingId: "binding-a",
            namespace: "process",
            fieldKey: "first",
            label: "First",
            valueType: FieldValueType.SHORT_TEXT,
            isEmpty: false,
            value: "stored first",
        },
        {
            fieldContractId: "field-contract-b",
            schemaFieldBindingId: "binding-b",
            namespace: "process",
            fieldKey: "second",
            label: "Second",
            valueType: FieldValueType.SHORT_TEXT,
            isEmpty: false,
            value: "stored second",
        },
    ],
    responseRevision: 1,
    updatedAt: "2026-09-11T00:00:00Z",
});

describe("structuredResponseWorkspaceState", () =>
{
    it("uses runtime to Template correlation when saving the second Field in a shared occurrence", () =>
    {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const secondBinding = binding("binding-b", "field-contract-b", "field-definition-b", "second");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const secondRequirement = requirement("template-binding-b", "template-requirement-b", "field-definition-b");

        const patches = buildResponsePatches(
            [occurrence],
            [group],
            [firstRequirement, secondRequirement],
            [firstBinding, secondBinding],
            [
                response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
                response("runtime-requirement-b", "template-binding-b", "template-requirement-b"),
            ],
            new Map(),
            {
                [responseKey(secondRequirement.id, occurrence.occurrencePath)]: {
                    [secondBinding.fieldContractId]: "updated second",
                },
            },
        );

        expect(patches).toEqual([
            {
                requirementId: "runtime-requirement-b",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: {
                    etag: "\"field-set:1\"",
                    values: [
                        {
                            fieldContractId: "field-contract-b",
                            value: "updated second",
                        },
                    ],
                },
            },
        ]);
    });

    it("names distinct runtime Requirements when saving both Fields in a shared occurrence", () =>
    {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const secondBinding = binding("binding-b", "field-contract-b", "field-definition-b", "second");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const secondRequirement = requirement("template-binding-b", "template-requirement-b", "field-definition-b");

        const patches = buildResponsePatches(
            [occurrence],
            [group],
            [firstRequirement, secondRequirement],
            [firstBinding, secondBinding],
            [
                response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
                response("runtime-requirement-b", "template-binding-b", "template-requirement-b"),
            ],
            new Map(),
            {
                [responseKey(firstRequirement.id, occurrence.occurrencePath)]: {
                    [firstBinding.fieldContractId]: "updated first",
                },
                [responseKey(secondRequirement.id, occurrence.occurrencePath)]: {
                    [secondBinding.fieldContractId]: "updated second",
                },
            },
        );

        expect(patches.map(patch => patch?.requirementId)).toEqual([
            "runtime-requirement-a",
            "runtime-requirement-b",
        ]);
    });
});
