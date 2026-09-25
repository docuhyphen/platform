import {Text, Button} from "@fluentui/react-components";
import {AddRegular, ArrowDownRegular, ArrowUpRegular, DeleteRegular} from "@fluentui/react-icons";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import StructuredResponseRequirement from "./StructuredResponseRequirement.tsx";
import {
    isRequirementActive,
    occurrencePresentation,
    requirementOccurrenceAnchorKey,
    responseKey,
} from "./structuredResponseWorkspaceState.ts";
import {StructuredResponseOccurrenceProps} from "./StructuredResponseWorkspaceTypes.ts";
import {useStructuredResponseOccurrenceCommands} from "./useStructuredResponseOccurrenceCommands.ts";
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
    onCommandStart,
    onCommandFailure,
}: StructuredResponseOccurrenceProps) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const {groupKey, isRootOccurrence, childGroups} = occurrencePresentation(occurrence, groups);

    const commands = useStructuredResponseOccurrenceCommands({
        occurrence,
        requestId,
        responseETag,
        groupKey,
        onReorder,
        onResult,
        onCommandStart,
        onCommandFailure,
    });

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
                                onClick={() => commands.reorder([
                                    occurrence.id,
                                    ...siblingOccurrenceIds.filter(id => id !== occurrence.id),
                                ])}/>
                        <Button id={`information-request-occurrence-down-${occurrence.id}`}
                                shape="circular"
                                icon={<ArrowDownRegular/>}
                                disabled={busy || occurrenceIndex === occurrenceCount - 1}
                                aria-label="Move occurrence down"
                                onClick={() => commands.reorder([
                                    ...siblingOccurrenceIds.filter(id => id !== occurrence.id),
                                    occurrence.id,
                                ])}/>
                        <Button id={`information-request-occurrence-remove-${occurrence.id}`}
                                shape="circular"
                                icon={<DeleteRegular/>}
                                disabled={busy}
                                aria-label="Remove occurrence"
                                onClick={() => commands.runCommand(() =>
                                    onRemove(requestId, occurrence.id, responseETag))}/>
                        {childGroups.map(childGroup => (
                            <Button id={`information-request-add-child-occurrence-${occurrence.id}-${toFieldElementId(childGroup.groupKey)}`}
                                    key={childGroup.groupKey}
                                    shape="circular"
                                    icon={<AddRegular/>}
                                    disabled={busy}
                                    onClick={() => commands.runCommand(() =>
                                        onAdd(
                                            requestId,
                                            childGroup.groupKey,
                                            occurrence.id,
                                            responseETag,
                                        ))}>
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
                    .map(requirement => (
                        <StructuredResponseRequirement key={responseKey(requirement.id, occurrence.occurrencePath)}
                                                       requestId={requestId}
                                                       occurrencePath={occurrence.occurrencePath}
                                                       requirement={requirement}
                                                       bindings={bindings}
                                                       responses={responses}
                                                       edits={edits}
                                                       setEdits={setEdits}/>
                    ))}
            </div>
        </div>
    );
};

export default StructuredResponseOccurrence;
