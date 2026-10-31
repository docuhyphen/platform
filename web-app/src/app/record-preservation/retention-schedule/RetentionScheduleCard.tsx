import {useCallback} from "react";
import {Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../hooks/useLoadedValue.ts";
import {
    getRecordRetentionSchedule,
    INFORMATION_REQUEST_RECORD_TYPE
} from "../../../services/recordPreservationService.ts";
import LoadedPanel from "../../information-requests/operations/loaded-panel/LoadedPanel.tsx";
import RetentionScheduleForm from "../retention-schedule-form/RetentionScheduleForm.tsx";
import {retentionVersionSentence} from "./retentionSchedule.ts";
import {useRetentionScheduleCardStyles} from "./RetentionScheduleCardStyles.tsx";

interface RetentionScheduleCardProps
{
    canManage: boolean;
}

const RetentionScheduleCard = ({canManage}: RetentionScheduleCardProps) =>
{
    const styles = useRetentionScheduleCardStyles();
    const load = useCallback(() => getRecordRetentionSchedule(INFORMATION_REQUEST_RECORD_TYPE), []);
    const schedule = useLoadedValue(load, "The retention schedule could not be loaded.");

    return (
        <div id={"record-retention-schedule"}
             className={styles.card}>
            <Text id={"record-retention-schedule-title"}
                  weight={"semibold"}>
                Information Request retention
            </Text>
            <LoadedPanel idPrefix={"record-retention-schedule"}
                         loaded={schedule}
                         loadingLabel={"Loading the retention schedule"}>
                {value => (
                    <Text id={"record-retention-schedule-current"}>
                        {value.current
                            ? retentionVersionSentence(value.current)
                            : "No retention schedule is published. Finished requests are kept until one is."}
                    </Text>
                )}
            </LoadedPanel>
            {canManage && <RetentionScheduleForm schedule={schedule}/>}
        </div>
    );
};

export default RetentionScheduleCard;
