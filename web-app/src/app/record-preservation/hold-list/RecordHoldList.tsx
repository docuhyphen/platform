import {useCallback, useState} from "react";
import {MessageBar, MessageBarBody, Switch, Text} from "@fluentui/react-components";
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

const STALE_HOLD = "This hold changed after you opened it. The latest version is shown; try again.";

const RecordHoldList = ({canManage}: RecordHoldListProps) =>
{
    const styles = useRecordHoldListStyles();
    const [includeReleased, setIncludeReleased] = useState(false);
    const [releasing, setReleasing] = useState<RecordPreservationHoldDto | null>(null);
    const [rescoping, setRescoping] = useState<RecordPreservationHoldDto | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const load = useCallback(
        () => getRecordPreservationHolds(includeReleased ? undefined : RecordPreservationHoldStatus.ACTIVE),
        [includeReleased],
    );
    const holds = useLoadedValue(load, "The preservation holds could not be loaded.");
    const refresh = (stale: boolean) => {
        setRescoping(null);
        setReleasing(null);
        setNotice(stale ? STALE_HOLD : null);
        holds.reload();
    };

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
            {notice && (
                <MessageBar id={"record-holds-notice"}
                            intent={"warning"}>
                    <MessageBarBody id={"record-holds-notice-body"}>{notice}</MessageBarBody>
                </MessageBar>
            )}
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
                                       onStale={() => refresh(true)}
                                       onChanged={() => refresh(false)}/>
            )}
            {releasing && (
                <ReleaseHoldDialog hold={releasing}
                                   onDismiss={() => setReleasing(null)}
                                   onStale={() => refresh(true)}
                                   onReleased={() => refresh(false)}/>
            )}
        </div>
    );
};

export default RecordHoldList;
