import {Badge, Button, Checkbox, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {InformationRequestOperationsRowDto} from "../../../models/models.tsx";
import {executionStandingBadge, executionStandingNotice} from "../../shared/executionStandingText.ts";
import OperationsRowHoldAction from "../operations-row-hold-action/OperationsRowHoldAction.tsx";
import {assigneeLabel} from "../operationsCsv.ts";
import {requestStateLabels, shortId, slaStatusPresentation} from "../operationsLabels.ts";
import {rowExceptionTexts, rowNoticeTexts, rowProgressText, rowTimingText} from "./operationsRowText.ts";
import {useOperationsQueueRowStyles} from "./OperationsQueueRowStyles.tsx";

interface OperationsQueueRowProps
{
    row: InformationRequestOperationsRowDto;
    selectable: boolean;
    selected: boolean;
    selectionFull: boolean;
    canPlaceHold: boolean;
    onToggle: () => void;
}

const OperationsQueueRow = ({
                                row,
                                selectable,
                                selected,
                                selectionFull,
                                canPlaceHold,
                                onToggle
                            }: OperationsQueueRowProps) =>
{
    const styles = useOperationsQueueRowStyles();
    const navigate = useNavigate();
    const id = `information-request-operations-row-${row.requestId}`;
    const sla = slaStatusPresentation[row.slaStatus];
    const standingBadge = executionStandingBadge(row.executionStanding);
    const notices = rowNoticeTexts(row);
    const exceptions = rowExceptionTexts(row);

    return (
        <li id={id}
            className={styles.row}>
            {selectable && (
                <Checkbox id={`${id}-select`}
                          checked={selected}
                          disabled={selectionFull && !selected}
                          aria-label={`Select ${row.title}`}
                          onChange={onToggle}/>
            )}
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <Text id={`${id}-title`}
                      weight={"semibold"}>
                    {row.title}
                </Text>
                <Text id={`${id}-state`}
                      className={styles.detail}>
                    {`${requestStateLabels[row.state]}, request ${shortId(row.requestId)}`}
                </Text>
                <Text id={`${id}-timing`}
                      className={styles.detail}>
                    {rowTimingText(row)}
                </Text>
                <Text id={`${id}-progress`}
                      className={styles.detail}>
                    {rowProgressText(row)}
                </Text>
                {row.assignees.length > 0 && (
                    <Text id={`${id}-assignees`}
                          className={styles.detail}>
                        {`Assigned to ${row.assignees.map(assigneeLabel).join(", ")}`}
                    </Text>
                )}
                {notices.length > 0 && (
                    <Text id={`${id}-notices`}
                          className={styles.detail}>
                        {`Notices: ${notices.join(", ")}`}
                    </Text>
                )}
                {exceptions.length > 0 && (
                    <Text id={`${id}-exceptions`}
                          className={styles.exception}>
                        {`Needs attention: ${exceptions.join(", ")}`}
                    </Text>
                )}
            </div>
            {standingBadge && (
                <Badge id={`${id}-standing`}
                       appearance={"tint"}
                       color={"warning"}
                       title={executionStandingNotice(row.executionStanding)?.text}>
                    {standingBadge}
                </Badge>
            )}
            <Badge id={`${id}-sla`}
                   appearance={"outline"}
                   color={sla.color}>
                {sla.label}
            </Badge>
            {canPlaceHold && (
                <OperationsRowHoldAction idPrefix={id}
                                         requestId={row.requestId}
                                         title={row.title}/>
            )}
            <Button id={`${id}-open`}
                    appearance={"primary"}
                    shape={"circular"}
                    aria-label={`Open ${row.title}`}
                    onClick={() => navigate(`/information-request-operations/${row.requestId}`)}>
                Open
            </Button>
        </li>
    );
};

export default OperationsQueueRow;
