import {useCallback, useState} from "react";
import {Switch, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../hooks/useLoadedValue.ts";
import {getRecordPreservationHolds} from "../../../services/recordPreservationService.ts";
import LoadedPanel from "../../information-requests/operations/loaded-panel/LoadedPanel.tsx";
import {RecordPreservationHoldDto, RecordPreservationHoldStatus} from "../../models/models.tsx";
import ChangeHoldScopeDialog from "../change-hold-scope-dialog/ChangeHoldScopeDialog.tsx";
import RecordHoldRow from "../hold-row/RecordHoldRow.tsx";
import ReleaseHoldDialog from "../release-hold-dialog/ReleaseHoldDialog.tsx";
import {useRecordHoldListStyles} from "./RecordHoldListStyles.tsx";

interface RecordHoldListProps
{
    canManage: boolean;
}

const RecordHoldList = ({canManage}: RecordHoldListProps) =>
{
    const styles = useRecordHoldListStyles();
    const [includeReleased, setIncludeReleased] = useState(false);
    const [releasing, setReleasing] = useState<RecordPreservationHoldDto | null>(null);
    const [rescoping, setRescoping] = useState<RecordPreservationHoldDto | null>(null);
    const load = useCallback(
        () => getRecordPreservationHolds(includeReleased ? undefined : RecordPreservationHoldStatus.ACTIVE),
        [includeReleased],
    );
    const holds = useLoadedValue(load, "The preservation holds could not be loaded.");

    return (
        <div id={"record-holds"}
             className={styles.card}>
            <div id={"record-holds-heading"}
                 className={styles.heading}>
                <Text id={"record-holds-title"}
                      weight={"semibold"}>
                    Preservation holds
                </Text>
                <Switch id={"record-holds-released-switch"}
                        label={"Include released holds"}
                        checked={includeReleased}
                        onChange={(_, data) => setIncludeReleased(data.checked)}/>
            </div>
            <LoadedPanel idPrefix={"record-holds"}
                         loaded={holds}
                         loadingLabel={"Loading preservation holds"}
                         emptyText={includeReleased ? "No preservation holds have been placed." : "No preservation holds are active."}
                         isEmpty={value => value.length === 0}>
                {value => (
                    <ul id={"record-holds-list"}
                        className={styles.list}>
                        {value.map(hold => (
                            <RecordHoldRow key={hold.id}
                                           hold={hold}
                                           canManage={canManage}
                                           onChangeScope={() => setRescoping(hold)}
                                           onRelease={() => setReleasing(hold)}/>
                        ))}
                    </ul>
                )}
            </LoadedPanel>
            {rescoping && (
                <ChangeHoldScopeDialog hold={rescoping}
                                       onDismiss={() => setRescoping(null)}
                                       onChanged={() =>
                                       {
                                           setRescoping(null);
                                           holds.reload();
                                       }}/>
            )}
            {releasing && (
                <ReleaseHoldDialog hold={releasing}
                                   onDismiss={() => setReleasing(null)}
                                   onReleased={() =>
                                   {
                                       setReleasing(null);
                                       holds.reload();
                                   }}/>
            )}
        </div>
    );
};

export default RecordHoldList;
