import {useCallback, useState} from "react";
import {Badge, Button, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {getInformationRequestDisposalStanding} from "../../../../services/informationRequestOperationsService.ts";
import {INFORMATION_REQUEST_RECORD_TYPE} from "../../../../services/recordPreservationService.ts";
import {InformationRequestDisposalStandingDto} from "../../../models/models.tsx";
import PlaceHoldDialog from "../../../record-preservation/place-hold-dialog/PlaceHoldDialog.tsx";
import {disposalSummary, recordTypeLabel} from "../../../record-preservation/recordPreservationLabels.ts";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {formattedTime} from "../operationsLabels.ts";
import RecordExportsPanel from "../record-exports/RecordExportsPanel.tsx";
import {useOperationsAccess} from "../useOperationsAccess.ts";
import {useRecordStandingPanelStyles} from "./RecordStandingPanelStyles.tsx";

interface RecordStandingPanelProps
{
    requestId: string;
}

const eligibilityOf = (standing: InformationRequestDisposalStandingDto): string =>
{
    if (standing.eligible) return "Eligible for disposal now.";
    const detail = standing.detail ?? "Not eligible for disposal.";
    return standing.eligibleFrom ? `${detail}. Eligible from ${formattedTime(standing.eligibleFrom)}.` : `${detail}.`;
};

const RecordStandingPanel = ({requestId}: RecordStandingPanelProps) =>
{
    const styles = useRecordStandingPanelStyles();
    const access = useOperationsAccess();
    const [placingHold, setPlacingHold] = useState(false);
    const load = useCallback(() => getInformationRequestDisposalStanding(requestId), [requestId]);
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
                        {!value.disposal && <Text id={"information-request-disposal-eligibility"}>{eligibilityOf(value)}</Text>}
                        {value.schedule && (
                            <Text id={"information-request-disposal-schedule"}
                                  className={styles.detail}>
                                {value.schedule.disposalAfterDays === undefined
                                    ? `Retention schedule version ${value.schedule.versionNumber}: kept at least ${value.schedule.minimumRetentionDays} days after it finishes, never disposed automatically`
                                    : `Retention schedule version ${value.schedule.versionNumber}: kept at least ${value.schedule.minimumRetentionDays} days, disposed ${value.schedule.disposalAfterDays} days after it finishes`}
                            </Text>
                        )}
                        {value.holds.map(hold => (
                            <Text id={`information-request-disposal-hold-${hold.id}`}
                                  key={hold.id}
                                  className={styles.detail}>
                                {`Held through the ${recordTypeLabel(hold.resourceType)}: ${hold.reason}`}
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
