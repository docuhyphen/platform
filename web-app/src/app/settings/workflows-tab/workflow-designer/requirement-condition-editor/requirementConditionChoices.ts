import {
    InformationRequestRequirementType,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateVersionDto,
    SchemaDefinitionDto,
    SchemaFieldBindingDto,
    SchemaVersionDto,
    WorkflowRequirementConditionDraft,
} from "../../../../models/models.tsx";
import {operatorsForType} from "../applicability-editor/applicabilityOperators.ts";

export const ROOT_OCCURRENCE_PATH = "root";

export interface RequirementChoice {
    templateRequirementId: string;
    requirementKey: string;
    prompt: string;
    occurrencePath: string;
    repeated: boolean;
    binding: SchemaFieldBindingDto;
}

type BindingFinder = (fieldDefinitionId: string) => SchemaFieldBindingDto | undefined;

const bindingFinderFor = (schemas: SchemaDefinitionDto[], schemaVersionId?: string | null): BindingFinder => {
    const versions = schemas.flatMap(schema => [schema.latestPublishedVersion, schema.draftVersion])
        .filter((version): version is SchemaVersionDto => version !== undefined && version !== null);
    const pinned = versions.find(version => version.id === schemaVersionId)?.bindings ?? [];
    const known = [...pinned, ...versions.flatMap(version => version.bindings)];
    return fieldDefinitionId => known.find(binding => binding.fieldDefinitionId === fieldDefinitionId);
};

const firstOccurrencePath = (groupKey: string, groups: InformationRequestTemplateGroupDto[]): string => {
    const parents = new Map(groups.map(group => [group.groupKey, group.parentGroupKey ?? undefined]));
    const path: string[] = [];
    const seen = new Set<string>();
    for (let key: string | undefined = groupKey; key !== undefined && !seen.has(key); key = parents.get(key)) {
        seen.add(key);
        path.unshift(`${key}[0]`);
    }
    return path.join("/");
};

const choiceOf = (
    requirement: InformationRequestTemplateRequirementDto,
    findBinding: BindingFinder,
    groups: InformationRequestTemplateGroupDto[],
): RequirementChoice[] => {
    if (requirement.requirementType !== InformationRequestRequirementType.FIELD || !requirement.collectedFieldDefinitionId) return [];
    const binding = findBinding(requirement.collectedFieldDefinitionId);
    if (!binding) return [];
    const anchor = requirement.occurrenceAnchorKey;
    return [{
        templateRequirementId: requirement.templateRequirementId,
        requirementKey: requirement.requirementKey,
        prompt: requirement.prompt,
        occurrencePath: anchor ? firstOccurrencePath(anchor, groups) : ROOT_OCCURRENCE_PATH,
        repeated: Boolean(anchor),
        binding,
    }];
};

export const requirementChoicesOf = (
    version: InformationRequestTemplateVersionDto | null | undefined,
    schemas: SchemaDefinitionDto[],
): RequirementChoice[] => {
    if (!version) return [];
    const findBinding = bindingFinderFor(schemas, version.schemaVersionId);
    return version.sections
        .flatMap(section => section.requirements)
        .flatMap(requirement => choiceOf(requirement, findBinding, version.groups ?? []));
};

export const defaultRequirementCondition = (choice: RequirementChoice): WorkflowRequirementConditionDraft => ({
    templateRequirementId: choice.templateRequirementId,
    occurrencePath: choice.occurrencePath,
    requirementKey: choice.requirementKey,
    valueType: choice.binding.valueType,
    operator: operatorsForType(choice.binding.valueType)[0],
    value: undefined,
});
