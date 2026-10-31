import {
    FieldScopeKind,
    FieldValueType,
    InformationRequestRequirementType,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
    SchemaDefinitionDto,
    SchemaFieldBindingDto,
} from "../../../../models/models.tsx";

const binding = (
    fieldDefinitionId: string,
    label: string,
    valueType: FieldValueType,
    options: { code: string; label: string }[] = [],
): SchemaFieldBindingDto => ({
    id: `binding-${fieldDefinitionId}`,
    fieldContractId: `contract-${fieldDefinitionId}`,
    fieldDefinitionId,
    namespace: "process",
    fieldKey: fieldDefinitionId,
    label,
    valueType,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: "INTERNAL" as never,
    constraints: {} as never,
    options,
});

const requirement = (
    key: string,
    prompt: string,
    requirementType: InformationRequestRequirementType,
    extra: { collectedFieldDefinitionId?: string; occurrenceAnchorKey?: string } = {},
) => ({
    id: `binding-${key}`,
    templateRequirementId: `requirement-${key}`,
    requirementKey: key,
    requirementType,
    prompt,
    permittedDispositions: [],
    ...extra,
});

export const requestSchema = {
    id: "schema-a",
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: "process",
    schemaKey: "request-answers",
    displayName: "Request answers",
    targetResourceType: "INFORMATION_REQUEST",
    status: "PUBLISHED",
    latestPublishedVersion: {
        id: "schema-version-a",
        schemaDefinitionId: "schema-a",
        versionNumber: 1,
        status: "PUBLISHED",
        bindings: [
            binding("field-amount", "Amount", FieldValueType.DECIMAL),
            binding("field-category", "Category", FieldValueType.SINGLE_SELECT, [
                {code: "standard", label: "Standard"},
                {code: "priority", label: "Priority"},
            ]),
        ],
        createdAt: Date.parse("2026-09-01T08:00:00Z"),
    },
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
} as unknown as SchemaDefinitionDto;

export const templateSummary: InformationRequestTemplateSummaryDto = {
    id: "template-a",
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "process",
    templateKey: "periodic-records",
    displayName: "Periodic records",
    status: InformationRequestTemplateStatus.PUBLISHED,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 2,
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
    updatedAt: Date.parse("2026-09-01T08:00:00Z"),
};

export const template = {
    ...templateSummary,
    latestPublishedVersion: {
        id: "version-a",
        templateDefinitionId: "template-a",
        versionNumber: 2,
        status: InformationRequestTemplateStatus.PUBLISHED,
        schemaVersionId: "schema-version-a",
        sections: [{
            id: "section-a",
            sectionKey: "answers",
            title: "Answers",
            requirements: [
                requirement("amount", "Requested amount", InformationRequestRequirementType.FIELD, {collectedFieldDefinitionId: "field-amount"}),
                requirement("category", "Category", InformationRequestRequirementType.FIELD, {collectedFieldDefinitionId: "field-category"}),
                requirement("evidence", "Supporting file", InformationRequestRequirementType.DOCUMENT),
                requirement("line-total", "Line total", InformationRequestRequirementType.FIELD, {
                    collectedFieldDefinitionId: "field-amount",
                    occurrenceAnchorKey: "items",
                }),
            ],
        }],
        groups: [
            {id: "group-lines", groupKey: "lines", minOccurrences: 1},
            {id: "group-items", groupKey: "items", parentGroupKey: "lines", minOccurrences: 1},
        ],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: Date.parse("2026-09-01T08:00:00Z"),
    },
} as unknown as InformationRequestTemplateDto;
