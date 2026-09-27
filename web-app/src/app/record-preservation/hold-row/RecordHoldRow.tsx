import {Badge, Button, Text} from "@fluentui/react-components";
import {formattedTime, shortId} from "../../information-requests/operations/operationsLabels.ts";
import {RecordPreservationHoldDto, RecordPreservationHoldStatus} from "../../models/models.tsx";
import {holdScopeLabels, holdStatusPresentation, recordTypeLabel} from "../recordPreservationLabels.ts";
import {useRecordHoldRowStyles} from "./RecordHoldRowStyles.tsx";

interface RecordHoldRowProps
{
    hold: RecordPreservationHoldDto;
    canManage: boolean;
    onRelease: () => void;
}

const RecordHoldRow = ({hold, canManage, onRelease}: RecordHoldRowProps) =>
{
    const styles = useRecordHoldRowStyles();
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
            {canManage && hold.status === RecordPreservationHoldStatus.ACTIVE && (
                <Button id={`${id}-release`}
                        appearance={"secondary"}
                        shape={"circular"}
                        onClick={onRelease}>
                    Release
                </Button>
            )}
        </li>
    );
};

export default RecordHoldRow;
