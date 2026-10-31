import {useCallback, useState} from "react";
import {Badge, Button, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {getInformationRequestDisposalStanding} from "../../../../services/informationRequestOperationsService.ts";
import {INFORMATION_REQUEST_RECORD_TYPE} from "../../../../services/recordPreservationService.ts";
import {InformationRequestDisposalStandingDto, RecordRetentionScheduleVersionDto} from "../../../models/models.tsx";
import PlaceHoldDialog from "../../../record-preservation/place-hold-dialog/PlaceHoldDialog.tsx";
import {disposalSummary, recordTypeLabel} from "../../../record-preservation/recordPreservationLabels.ts";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {reportingForbidden} from "../operations-detail-content/reportingForbidden.ts";
import {countedText, formattedTime} from "../operationsLabels.ts";
import RecordExportsPanel from "../record-exports/RecordExportsPanel.tsx";
import {useOperationsAccess} from "../useOperationsAccess.ts";
import {useRecordStandingPanelStyles} from "./RecordStandingPanelStyles.tsx";

interface RecordStandingPanelProps
{
    requestId: string;
    onForbidden?: () => void;
}

const eligibilityOf = (standing: InformationRequestDisposalStandingDto): string =>
{
    if (standing.eligible) return "Eligible for disposal now.";
    const detail = standing.detail ?? "Not eligible for disposal.";
    return standing.eligibleFrom ? `${detail}. Eligible from ${formattedTime(standing.eligibleFrom)}.` : `${detail}.`;
};

const scheduleOf = (schedule: RecordRetentionScheduleVersionDto): string => {
    const kept = `Retention schedule version ${schedule.versionNumber}: kept at least ${countedText(schedule.minimumRetentionDays, "day", "days")}`;
    return schedule.disposalAfterDays === undefined || schedule.disposalAfterDays === null
        ? `${kept} after it finishes, never disposed automatically`
        : `${kept}, disposed ${countedText(schedule.disposalAfterDays, "day", "days")} after it finishes`;
};

const RecordStandingPanel = ({requestId, onForbidden}: RecordStandingPanelProps) =>
{
    const styles = useRecordStandingPanelStyles();
    const access = useOperationsAccess();
    const [placingHold, setPlacingHold] = useState(false);
    const load = useCallback(
        () => reportingForbidden(getInformationRequestDisposalStanding(requestId), onForbidden),
        [requestId, onForbidden],
    );
    const standing = useLoadedValue(load, "The record standing could not be loaded.");

    return (
        <div id={"information-request-record-standing"}
             className={styles.panel}>
            <LoadedPanel idPrefix={"information-request-record-standing"}
                         loaded={standing}
                         loadingLabel={"Loading the record standing"}>
                {value => (
                    <div id={"information-request-disposal-standing"}
                         className={styles.card}>
                        <div id={"information-request-disposal-standing-heading"}
                             className={styles.heading}>
                            <Text id={"information-request-disposal-standing-title"}
                                  weight={"semibold"}>
                                Retention and disposal
                            </Text>
                            {value.disposal && (
                                <Badge id={"information-request-disposal-state"}
                                       appearance={"outline"}
                                       color={"informative"}>
                                    {disposalSummary(value.disposal)}
                                </Badge>
                            )}
                        </div>
                        {!value.disposal &&
                            <Text id={"information-request-disposal-eligibility"}>{eligibilityOf(value)}</Text>}
                        {value.schedule && (
                            <Text id={"information-request-disposal-schedule"}
                                  className={styles.detail}>
                                {scheduleOf(value.schedule)}
                            </Text>
                        )}
                        {value.holds.map(hold => (
                            <Text id={`information-request-disposal-hold-${hold.id}`}
                                  key={hold.id}
                                  className={styles.detail}>
                                {`Held through the ${recordTypeLabel(hold.resourceType)}: ${hold.reason}`}
                            </Text>
                        ))}
                        {value.withheldHolds.map((hold, index) => (
                            <Text id={`information-request-disposal-withheld-hold-${index}`}
                                  key={`withheld-hold-${index}`}
                                  className={styles.detail}>
                                {`Held through the ${recordTypeLabel(hold.resourceType)} since ${formattedTime(hold.effectiveFrom)}. Only people who can read the owner's holds see why.`}
                            </Text>
                        ))}
                        {access.canManageHolds && !value.disposal && (
                            <div id={"information-request-disposal-actions"}
                                 className={styles.actions}>
                                <Button id={"information-request-place-hold-btn"}
                                        appearance={"secondary"}
                                        shape={"circular"}
                                        onClick={() => setPlacingHold(true)}>
                                    Place hold
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </LoadedPanel>
            <RecordExportsPanel requestId={requestId}/>
            {placingHold && (
                <PlaceHoldDialog resourceType={INFORMATION_REQUEST_RECORD_TYPE}
                                 resourceId={requestId}
                                 onDismiss={() => setPlacingHold(false)}
                                 onPlaced={() =>
                                 {
                                     setPlacingHold(false);
                                     standing.reload();
                                 }}/>
            )}
        </div>
    );
};

export default RecordStandingPanel;
