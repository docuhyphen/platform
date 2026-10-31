import {
    InformationRequestRequirementType,
    InformationRequestTemplateRequirementRequest,
    SchemaFieldBindingDto,
} from "../../../models/models.tsx";
import {requirementLabel} from "../templateDraftRules.ts";
import {PredicateSource} from "../condition-predicate-row/ConditionPredicateRow.tsx";

export const conditionPredicateSources = (
    requirements: InformationRequestTemplateRequirementRequest[],
    fieldBindings: SchemaFieldBindingDto[],
): PredicateSource[] => [
    ...requirements
        .filter(requirement => requirement.requirementType !== InformationRequestRequirementType.RESPONSE_ATTESTATION)
        .map(requirement => ({
            value: `requirement:${requirement.requirementKey}`,
            label: `Answer to "${requirementLabel(requirement)}"`,
        })),
    ...requirements
        .filter(requirement => requirement.requirementType === InformationRequestRequirementType.FIELD)
        .flatMap(requirement =>
        {
            const binding = fieldBindings.find(candidate => candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
            return binding
                ? [{
                    value: `field:${binding.fieldDefinitionId}`,
                    label: `Value of ${binding.label}`,
                    valueType: binding.valueType,
                    options: binding.options,
                }]
                : [];
        }),
];
