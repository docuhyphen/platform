import {Badge, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../hooks/useLoadedValue.ts";
import {getRecordDisposals} from "../../../services/recordPreservationService.ts";
import LoadedPanel from "../../information-requests/operations/loaded-panel/LoadedPanel.tsx";
import {formattedTime, shortId} from "../../information-requests/operations/operationsLabels.ts";
import {RecordDisposalDto} from "../../models/models.tsx";
import {disposalStatePresentation, disposalSummary, recordTypeLabel} from "../recordPreservationLabels.ts";
import {useRecordDisposalListStyles} from "./RecordDisposalListStyles.tsx";

const objectsOf = (disposal: RecordDisposalDto): string =>
{
    if (disposal.tombstone)
    {
        return `${disposal.tombstone.deletedObjectCount} stored files deleted, ${disposal.tombstone.retainedObjectCount} kept because other records share them`;
    }
    const retained = disposal.objects.filter(entry => entry.retained).length;
    return `${disposal.objects.length - retained} stored files to delete, ${retained} kept because other records share them`;
};

const RecordDisposalList = () =>
{
    const styles = useRecordDisposalListStyles();
    const disposals = useLoadedValue(getRecordDisposals, "The disposals could not be loaded.");

    return (
        <div id={"record-disposals"}
             className={styles.card}>
            <Text id={"record-disposals-title"}
                  weight={"semibold"}>
                Disposals
            </Text>
            <LoadedPanel idPrefix={"record-disposals"}
                         loaded={disposals}
                         loadingLabel={"Loading disposals"}
                         emptyText={"No records have been claimed for disposal."}
                         isEmpty={value => value.length === 0}>
                {value => (
                    <ul id={"record-disposals-list"}
                        className={styles.list}>
                        {value.map(disposal =>
                        {
                            const id = `record-disposal-${disposal.claimId}`;
                            const state = disposalStatePresentation[disposal.state];
                            return (
                                <li id={id}
                                    key={disposal.claimId}
                                    className={styles.row}>
                                    <div id={`${id}-summary`}
                                         className={styles.summary}>
                                        <Text id={`${id}-title`}
                                              weight={"semibold"}>
                                            {`${recordTypeLabel(disposal.resourceType)} ${shortId(disposal.resourceId)}`}
                                        </Text>
                                        <Text id={`${id}-progress`}
                                              className={styles.detail}>
                                            {`${disposalSummary(disposal)}, claimed ${formattedTime(disposal.claimedAt)}`}
                                        </Text>
                                        <Text id={`${id}-objects`}
                                              className={styles.detail}>
                                            {objectsOf(disposal)}
                                        </Text>
                                    </div>
                                    <Badge id={`${id}-state`}
                                           appearance={"outline"}
                                           color={state.color}>
                                        {state.label}
                                    </Badge>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </LoadedPanel>
        </div>
    );
};

export default RecordDisposalList;
