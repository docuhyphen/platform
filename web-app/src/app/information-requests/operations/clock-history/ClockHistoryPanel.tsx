import {useCallback} from "react";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {getInformationRequestClocks} from "../../../../services/informationRequestOperationsService.ts";
import ClockCard from "../clock-card/ClockCard.tsx";
import {reportingForbidden} from "../operations-detail-content/reportingForbidden.ts";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {clockPolicyText} from "./clockHistoryText.ts";
import {useClockPolicyVersions} from "./useClockPolicyVersions.ts";
import {useClockHistoryPanelStyles} from "./ClockHistoryPanelStyles.tsx";

interface ClockHistoryPanelProps
{
    requestId: string;
    onForbidden?: () => void;
}

const ClockHistoryPanel = ({requestId, onForbidden}: ClockHistoryPanelProps) =>
{
    const styles = useClockHistoryPanelStyles();
    const load = useCallback(() => reportingForbidden(getInformationRequestClocks(requestId), onForbidden), [requestId, onForbidden]);
    const clocks = useLoadedValue(load, "The clocks could not be loaded.");
    const versions = useClockPolicyVersions();

    return (
        <LoadedPanel idPrefix={"information-request-clocks"}
                     loaded={clocks}
                     loadingLabel={"Loading clocks"}
                     emptyText={"No clocks run for this request."}
                     isEmpty={value => value.length === 0}>
            {value => (
                <ul id={"information-request-clocks"}
                    className={styles.list}>
                    {value.map(clock => (
                        <ClockCard key={clock.id}
                                   clock={clock}
                                   policy={clockPolicyText(clock, versions)}/>
                    ))}
                </ul>
            )}
        </LoadedPanel>
    );
};

export default ClockHistoryPanel;
