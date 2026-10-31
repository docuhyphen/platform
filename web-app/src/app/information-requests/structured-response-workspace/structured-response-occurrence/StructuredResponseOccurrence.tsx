import {Text} from "@fluentui/react-components";
import {occurrenceElementKey} from "../../shared/responseElementKeys.ts";
import StructuredResponseInactiveConditionNotices
    from "../structured-response-inactive-condition-notices/StructuredResponseInactiveConditionNotices.tsx";
import StructuredResponseOccurrenceControls
    from "../structured-response-occurrence-controls/StructuredResponseOccurrenceControls.tsx";
import StructuredResponseRequirement from "../structured-response-requirement/StructuredResponseRequirement.tsx";
import {
    isRequirementActive,
    requirementOccurrenceAnchorKey,
    responseKey,
} from "../structuredResponseWorkspaceState.ts";
import {StructuredResponseOccurrenceProps} from "../StructuredResponseWorkspaceTypes.ts";
import {useStructuredResponseOccurrenceCommands} from "../useStructuredResponseOccurrenceCommands.ts";
import {movedOccurrenceOrder} from "./occurrenceOrder.ts";
import {useStructuredResponseOccurrenceStyles} from "./StructuredResponseOccurrenceStyles.tsx";

const StructuredResponseOccurrence = ({
                                          occurrence,
                                          title,
                                          presentation,
                                          occurrenceIndex,
                                          occurrenceCount,
                                          siblingOccurrenceIds,
                                          removable,
                                          fullChildGroupKeys,
                                          nested,
                                          requestId,
                                          responseETag,
                                          busy,
                                          readOnly,
                                          requirements,
                                          responses,
                                          conditionByScope,
                                          onAdd,
                                          onRemove,
                                          onReorder,
                                          onResult,
                                          onCommandStart,
                                          onCommandFailure,
                                          ...answering
                                      }: StructuredResponseOccurrenceProps) => {
    const styles = useStructuredResponseOccurrenceStyles();
    const {groupKey, isRootOccurrence, childGroups} = presentation;
    const anchored = requirements.filter(requirement => requirementOccurrenceAnchorKey(requirement) === groupKey);

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
        <div id={`information-request-occurrence-${occurrenceElementKey(occurrence.occurrencePath)}`}
             className={styles.occurrenceBlock}>
            <div id={`information-request-occurrence-header-${occurrence.id}`}
                 className={styles.occurrenceHeader}>
                <Text id={`information-request-occurrence-title-${occurrence.id}`}
                      className={styles.occurrenceTitle}>
                    {title}
                </Text>
                {!isRootOccurrence && (
                    <StructuredResponseOccurrenceControls occurrenceId={occurrence.id}
                                                          title={title}
                                                          first={occurrenceIndex === 0}
                                                          last={occurrenceIndex === occurrenceCount - 1}
                                                          removable={removable}
                                                          childGroups={childGroups}
                                                          fullChildGroupKeys={fullChildGroupKeys}
                                                          disabled={busy || readOnly}
                                                          onMove={offset => commands.reorder(movedOccurrenceOrder(siblingOccurrenceIds, occurrence.id, offset))}
                                                          onRemove={() => commands.runCommand(() => onRemove(requestId, occurrence.id, responseETag))}
                                                          onAddChild={childGroupKey => commands.runCommand(() =>
                                                              onAdd(requestId, childGroupKey, occurrence.id, responseETag))}/>
                )}
            </div>
            <div id={`information-request-occurrence-requirements-${occurrence.id}`}
                 className={styles.requirementGrid}>
                {anchored
                    .filter(requirement => isRequirementActive(requirement, occurrence.occurrencePath, conditionByScope, responses))
                    .map(requirement => (
                        <StructuredResponseRequirement key={responseKey(requirement.id, occurrence.occurrencePath)}
                                                       requestId={requestId}
                                                       occurrencePath={occurrence.occurrencePath}
                                                       requirement={requirement}
                                                       responses={responses}
                                                       responseETag={responseETag}
                                                       busy={busy}
                                                       readOnly={readOnly}
                                                       onResult={onResult}
                                                       onCommandStart={onCommandStart}
                                                       onCommandFailure={onCommandFailure}
                                                       {...answering}/>
                    ))}
            </div>
            <StructuredResponseInactiveConditionNotices occurrencePath={occurrence.occurrencePath}
                                                        requirements={anchored}
                                                        conditionByScope={conditionByScope}
                                                        responses={responses}/>
            {nested && (
                <div id={`information-request-occurrence-children-${occurrence.id}`}
                     className={styles.nested}>
                    {nested}
                </div>
            )}
        </div>
    );
};

export default StructuredResponseOccurrence;
