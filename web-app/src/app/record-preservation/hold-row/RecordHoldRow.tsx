import {useState} from "react";
import {Badge, Button, Text} from "@fluentui/react-components";
import {formattedTime, shortId} from "../../information-requests/operations/operationsLabels.ts";
import {RecordPreservationHoldDto, RecordPreservationHoldStatus} from "../../models/models.tsx";
import RecordHoldHistory from "../hold-history/RecordHoldHistory.tsx";
import {holdScopeLabels, holdStatusPresentation, recordTypeLabel} from "../recordPreservationLabels.ts";
import {useRecordHoldRowStyles} from "./RecordHoldRowStyles.tsx";

interface RecordHoldRowProps
{
    hold: RecordPreservationHoldDto;
    canManage: boolean;
    onChangeScope: () => void;
    onRelease: () => void;
}

const RecordHoldRow = ({hold, canManage, onChangeScope, onRelease}: RecordHoldRowProps) =>
{
    const styles = useRecordHoldRowStyles();
    const [showingHistory, setShowingHistory] = useState(false);
    const id = `record-hold-${hold.id}`;
    const status = holdStatusPresentation[hold.status];

    return (
        <li id={id}
            className={styles.row}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <Text id={`${id}-title`}
                      weight={"semibold"}>
                    {`${recordTypeLabel(hold.resourceType)} ${shortId(hold.resourceId)}`}
                </Text>
                <Text id={`${id}-scope`}
                      className={styles.detail}>
                    {`${holdScopeLabels[hold.scope]}, placed ${formattedTime(hold.placedAt)}`}
                </Text>
                <Text id={`${id}-reason`}>
                    {hold.caseReference ? `${hold.reason} (case ${hold.caseReference})` : hold.reason}
                </Text>
                {hold.releasedAt && (
                    <Text id={`${id}-released`}
                          className={styles.detail}>
                        {`Released ${formattedTime(hold.releasedAt)}: ${hold.releaseReason ?? ""}`}
                    </Text>
                )}
            </div>
            <Badge id={`${id}-status`}
                   appearance={"outline"}
                   color={status.color}>
                {status.label}
            </Badge>
            <div id={`${id}-actions`}
                 className={styles.actions}>
                <Button id={`${id}-history-btn`}
                        appearance={"secondary"}
                        shape={"circular"}
                        aria-expanded={showingHistory}
                        aria-controls={`${id}-history`}
                        onClick={() => setShowingHistory(current => !current)}>
                    History
                </Button>
                {canManage && hold.status === RecordPreservationHoldStatus.ACTIVE && (
                    <>
                        <Button id={`${id}-change-scope`}
                                appearance={"secondary"}
                                shape={"circular"}
                                onClick={onChangeScope}>
                            Change scope
                        </Button>
                        <Button id={`${id}-release`}
                                appearance={"secondary"}
                                shape={"circular"}
                                onClick={onRelease}>
                            Release
                        </Button>
                    </>
                )}
            </div>
            {showingHistory && (
                <RecordHoldHistory id={`${id}-history`}
                                   holdId={hold.id}/>
            )}
        </li>
    );
};

export default RecordHoldRow;
