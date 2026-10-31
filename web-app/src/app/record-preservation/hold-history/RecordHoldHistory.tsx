import {useCallback} from "react";
import {useLoadedValue} from "../../../hooks/useLoadedValue.ts";
import {getRecordPreservationHold} from "../../../services/recordPreservationService.ts";
import LoadedPanel from "../../information-requests/operations/loaded-panel/LoadedPanel.tsx";
import {holdEventSentence} from "../recordPreservationLabels.ts";
import {useRecordHoldHistoryStyles} from "./RecordHoldHistoryStyles.tsx";

interface RecordHoldHistoryProps {
    id: string;
    holdId: string;
}

const RecordHoldHistory = ({id, holdId}: RecordHoldHistoryProps) => {
    const styles = useRecordHoldHistoryStyles();
    const load = useCallback(() => getRecordPreservationHold(holdId), [holdId]);
    const hold = useLoadedValue(load, "The hold's history could not be loaded.");

    return (
        <div id={id}
             className={styles.history}>
            <LoadedPanel idPrefix={`${id}-read`}
                         loaded={hold}
                         loadingLabel={"Loading the hold's history"}
                         emptyText={"No history is recorded for this hold."}
                         isEmpty={value => value.events.length === 0}>
                {value => (
                    <ol id={`${id}-events`}
                        aria-label={"Hold history"}
                        className={styles.events}>
                        {value.events.map((event, index) => (
                            <li id={`${id}-event-${event.eventNumber}`}
                                key={event.eventNumber}
                                className={styles.event}>
                                {holdEventSentence(event, value.events[index - 1])}
                            </li>
                        ))}
                    </ol>
                )}
            </LoadedPanel>
        </div>
    );
};

export default RecordHoldHistory;
