import {Button, Text} from "@fluentui/react-components";
import {AddRegular} from "@fluentui/react-icons";
import {InformationRequestState, InformationRequestTemplateGroupDto} from "../../models/models.tsx";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import {OccurrenceCommandResult} from "./StructuredResponseWorkspaceTypes.ts";

interface Props
{
    requestId: string;
    state: InformationRequestState;
    responseETag: string;
    busy: boolean;
    groups: InformationRequestTemplateGroupDto[];
    onAddOccurrence: (
        requestId: string,
        groupKey: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
    onCommandStart: () => void;
}

const StructuredResponseToolbar = ({
    requestId,
    state,
    responseETag,
    busy,
    groups,
    onAddOccurrence,
    onResult,
    onCommandStart,
}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();

    return (
        <div id="information-request-response-toolbar"
             className={styles.toolbar}>
            <Text id="information-request-response-state">
                {state}
            </Text>
            <div id="information-request-response-group-controls"
                 className={styles.groupControls}>
                {groups.map(group => (
                    <Button id={`information-request-add-occurrence-${toFieldElementId(group.groupKey)}`}
                            key={group.groupKey}
                            shape="circular"
                            icon={<AddRegular/>}
                            disabled={busy}
                            onClick={() =>
                            {
                                onCommandStart();
                                onAddOccurrence(requestId, group.groupKey, responseETag).then(onResult);
                            }}>
                        Add {group.groupKey}
                    </Button>
                ))}
            </div>
        </div>
    );
};

export default StructuredResponseToolbar;
