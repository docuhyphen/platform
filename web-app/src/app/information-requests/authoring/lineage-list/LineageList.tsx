import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {InformationRequestLineageViewDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {informationRequestManagePath} from "../../shared/informationRequestWorkspacePaths.ts";
import {lineageLabel} from "../follow-up-panel/followUpLabels.ts";
import {useLineageListStyles} from "./LineageListStyles.tsx";

interface Props
{
    lineage: InformationRequestLineageViewDto;
}

const LineageList = ({lineage}: Props) =>
{
    const styles = useLineageListStyles();
    const navigate = useNavigate();
    const source = lineage.source;

    return (
        <>
            {!source && lineage.successors.length === 0 && !lineage.recurrence && !lineage.refreshRules?.length && (
                <Text id={"information-request-lineage-empty"}>No earlier or follow-up requests are recorded.</Text>
            )}
            {source && (
                <div id={"information-request-lineage-source"}
                     className={styles.row}>
                    <Text id={"information-request-lineage-source-text"}>
                        {`This request is a ${lineageLabel(source).toLowerCase()} of an earlier request.`}
                    </Text>
                    <Button id={"information-request-lineage-source-open"}
                            size={"small"}
                            shape={"circular"}
                            onClick={() => navigate(informationRequestManagePath(source.sourceRequestId))}>
                        Open earlier request
                    </Button>
                </div>
            )}
            {lineage.successors.length > 0 && (
                <ul id={"information-request-lineage-successors"}
                    aria-label={"Follow-up requests"}
                    className={styles.list}>
                    {lineage.successors.map(successor => (
                        <li key={successor.id}
                            id={`information-request-lineage-${successor.id}`}
                            className={styles.row}>
                            <div id={`information-request-lineage-${successor.id}-text`}
                                 className={styles.text}>
                                <Text id={`information-request-lineage-${successor.id}-label`}
                                      weight={"semibold"}>
                                    {lineageLabel(successor)}
                                </Text>
                                <Text id={`information-request-lineage-${successor.id}-created`}
                                      size={200}
                                      className={styles.muted}>
                                    {`Created ${formatInformationRequestTime(successor.createdAt)}`}
                                </Text>
                            </div>
                            <Button id={`information-request-lineage-${successor.id}-open`}
                                    size={"small"}
                                    shape={"circular"}
                                    aria-label={`Open ${lineageLabel(successor)}`}
                                    onClick={() => navigate(informationRequestManagePath(successor.successorRequestId))}>
                                Open
                            </Button>
                        </li>
                    ))}
                </ul>
            )}
        </>
    );
};

export default LineageList;
