import {Text, Button} from "@fluentui/react-components";
import {AddRegular, ArrowDownRegular, ArrowUpRegular, DeleteRegular} from "@fluentui/react-icons";
import {
    InformationRequestConditionEvaluationDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    InformationRequestResponseDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import FieldValueEditor from "../../exchanges/components/exchange-fields-tab/FieldValueEditor.tsx";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import {
    isRequirementActive,
    occurrenceGroupKeyFromTemplate,
    requirementOccurrenceAnchorKey,
    ROOT_OCCURRENCE_PATH,
    responseForFieldRequirement,
    ResponseEdits,
    responseKey,
    shownFieldValues,
} from "./structuredResponseWorkspaceState.ts";
import {OccurrenceCommandResult} from "./StructuredResponseWorkspaceTypes.ts";

interface Props
{
    occurrence: InformationRequestGroupOccurrenceDto;
    occurrenceIndex: number;
    occurrenceCount: number;
    siblingOccurrenceIds: string[];
    requestId: string;
    responseETag: string;
    busy: boolean;
    groups: InformationRequestTemplateGroupDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    onAdd: (
        requestId: string,
        groupKey: string,
        parentOccurrenceId: string | undefined,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onRemove: (requestId: string, occurrenceId: string, responseETag: string) => Promise<OccurrenceCommandResult>;
    onReorder: (
        requestId: string,
        groupKey: string,
        parentOccurrenceId: string | undefined,
        occurrenceIds: string[],
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
}

const StructuredResponseOccurrence = ({
    occurrence,
    occurrenceIndex,
    occurrenceCount,
    siblingOccurrenceIds,
    requestId,
    responseETag,
    busy,
    groups,
    requirements,
    bindings,
    responses,
    conditionByScope,
    edits,
    setEdits,
    onAdd,
    onRemove,
    onReorder,
    onResult,
}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const groupKey = occurrenceGroupKeyFromTemplate(occurrence, groups);
    const isRootOccurrence = occurrence.occurrencePath === ROOT_OCCURRENCE_PATH;
    const childGroups = isRootOccurrence
        ? []
        : groups.filter(group => group.parentGroupKey === groupKey);

    const handleReorder = (orderedIds: string[]) =>
        onReorder(requestId, groupKey, occurrence.parentOccurrenceId, orderedIds, responseETag).then(onResult);

    return (
        <div id={`information-request-occurrence-${toFieldElementId(occurrence.occurrencePath)}`}
             className={styles.occurrenceBlock}>
            <div id={`information-request-occurrence-header-${occurrence.id}`}
                 className={styles.occurrenceHeader}>
                <Text id={`information-request-occurrence-title-${occurrence.id}`}
                      className={styles.occurrenceTitle}>
                    {occurrence.occurrencePath}
                </Text>
                {!isRootOccurrence && (
                    <div id={`information-request-occurrence-actions-${occurrence.id}`}
                         className={styles.groupControls}>
                        <Button id={`information-request-occurrence-up-${occurrence.id}`}
                                shape="circular"
                                icon={<ArrowUpRegular/>}
                                disabled={busy || occurrenceIndex === 0}
                                aria-label="Move occurrence up"
                                onClick={() => handleReorder([
                                    occurrence.id,
                                    ...siblingOccurrenceIds.filter(id => id !== occurrence.id),
                                ])}/>
                        <Button id={`information-request-occurrence-down-${occurrence.id}`}
                                shape="circular"
                                icon={<ArrowDownRegular/>}
                                disabled={busy || occurrenceIndex === occurrenceCount - 1}
                                aria-label="Move occurrence down"
                                onClick={() => handleReorder([
                                    ...siblingOccurrenceIds.filter(id => id !== occurrence.id),
                                    occurrence.id,
                                ])}/>
                        <Button id={`information-request-occurrence-remove-${occurrence.id}`}
                                shape="circular"
                                icon={<DeleteRegular/>}
                                disabled={busy}
                                aria-label="Remove occurrence"
                                onClick={() => onRemove(requestId, occurrence.id, responseETag).then(onResult)}/>
                        {childGroups.map(childGroup => (
                            <Button id={`information-request-add-child-occurrence-${occurrence.id}-${toFieldElementId(childGroup.groupKey)}`}
                                    key={childGroup.groupKey}
                                    shape="circular"
                                    icon={<AddRegular/>}
                                    disabled={busy}
                                    onClick={() =>
                                        onAdd(
                                            requestId,
                                            childGroup.groupKey,
                                            occurrence.id,
                                            responseETag,
                                        ).then(onResult)}>
                                Add {childGroup.groupKey}
                            </Button>
                        ))}
                    </div>
                )}
            </div>
            <div id={`information-request-occurrence-requirements-${occurrence.id}`}
                 className={styles.requirementGrid}>
                {requirements
                    .filter(requirement => isRequirementActive(requirement, occurrence.occurrencePath, conditionByScope))
                    .filter(requirement => requirementOccurrenceAnchorKey(requirement) === groupKey)
                    .map(requirement =>
                    {
                        const binding = bindings.find(candidate =>
                            candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
                        if (!binding) return null;
                        const response = responseForFieldRequirement(
                            responses,
                            requirement,
                            occurrence.occurrencePath,
                        );
                        const key = responseKey(requirement.id, occurrence.occurrencePath);
                        const shown = shownFieldValues(binding, occurrence.occurrencePath, requirement.id, response, edits);
                        const elementId = toFieldElementId(`${occurrence.occurrencePath}-${requirement.requirementKey}`);
                        return (
                            <div id={`information-request-response-requirement-${elementId}`}
                                 key={key}
                                 className={styles.requirement}>
                                <Text id={`information-request-response-prompt-${elementId}`}
                                      className={styles.prompt}>
                                    {requirement.prompt}
                                </Text>
                                <FieldValueEditor binding={binding}
                                                  value={shown[binding.fieldContractId]}
                                                  onChange={value => setEdits(previous => ({
                                                      ...previous,
                                                      [key]: {...previous[key], [binding.fieldContractId]: value},
                                                  }))}
                                                  showLabel={false}/>
                            </div>
                        );
                    })}
            </div>
        </div>
    );
};

export default StructuredResponseOccurrence;
