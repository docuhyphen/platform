import {Button, Text} from "@fluentui/react-components";
import {AddRegular} from "@fluentui/react-icons";
import {
    InformationRequestGroupOccurrenceDto,
    InformationRequestState,
    InformationRequestTemplateGroupDto,
} from "../../../models/models.tsx";
import {occurrencesIn, takesAnotherOccurrence} from "../occurrenceCardinality.ts";
import {OccurrenceCommandResult} from "../StructuredResponseWorkspaceTypes.ts";
import {requestStateLabels} from "../../operations/operationsLabels.ts";
import {groupElementKey} from "../../shared/responseElementKeys.ts";
import {groupLabel} from "../responseAnswerState.ts";
import {useStructuredResponseToolbarStyles} from "./StructuredResponseToolbarStyles.tsx";

interface Props
{
    requestId: string;
    state: InformationRequestState;
    responseETag: string;
    busy: boolean;
    readOnly: boolean;
    groups: InformationRequestTemplateGroupDto[];
    occurrences: InformationRequestGroupOccurrenceDto[];
    onAddOccurrence: (
        requestId: string,
        groupKey: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const StructuredResponseToolbar = ({
                                       requestId,
                                       state,
                                       responseETag,
                                       busy,
                                       readOnly,
                                       groups,
                                       occurrences,
                                       onAddOccurrence,
                                       onResult,
                                       onCommandStart,
                                       onCommandFailure,
                                   }: Props) =>
{
    const styles = useStructuredResponseToolbarStyles();

    return (
        <div id="information-request-response-toolbar"
             className={styles.toolbar}>
            <Text id="information-request-response-state">
                {requestStateLabels[state]}
            </Text>
            <div id="information-request-response-group-controls"
                 className={styles.groupControls}>
                {groups.map(group => (
                    <Button id={`information-request-add-occurrence-${groupElementKey(group.groupKey)}`}
                            key={group.groupKey}
                            shape="circular"
                            icon={<AddRegular/>}
                            disabled={busy || readOnly || !takesAnotherOccurrence(group, occurrencesIn(occurrences, group, null))}
                            onClick={() =>
                            {
                                onCommandStart();
                                onAddOccurrence(requestId, group.groupKey, responseETag)
                                    .then(onResult)
                                    .catch(onCommandFailure);
                            }}>
                        {`Add ${groupLabel(group.groupKey).toLowerCase()}`}
                    </Button>
                ))}
            </div>
        </div>
    );
};

export default StructuredResponseToolbar;
