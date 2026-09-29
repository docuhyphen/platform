import {Button, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {InformationRequestLineageViewDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {informationRequestManagePath} from "../../shared/informationRequestWorkspacePaths.ts";
import {lineageLabel} from "../follow-up-panel/followUpLabels.ts";
import {useFollowUpPanelStyles} from "../follow-up-panel/FollowUpPanelStyles.tsx";

interface Props
{
    lineage: InformationRequestLineageViewDto;
}

const LineageList = ({lineage}: Props) =>
{
    const styles = useFollowUpPanelStyles();
    const navigate = useNavigate();
    const source = lineage.source;

    return (
        <>
            {source && (
                <div id={"information-request-lineage-source"}
                     className={styles.row}>
                    <Text>{`This request is a ${lineageLabel(source).toLowerCase()} of an earlier request.`}</Text>
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
                                <Text weight={"semibold"}>{lineageLabel(successor)}</Text>
                                <Text size={200}
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
