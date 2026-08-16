import {
    Badge,
    Button,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {SubscriptionTrialRequest} from "../../../../services/types/subscriptionTrialRequests.ts";
import {useTrialRequestsTableStyles} from "./TrialRequestsTableStyles.tsx";

interface Props
{
    requests: SubscriptionTrialRequest[];
    onDecision: (request: SubscriptionTrialRequest, decision: "APPROVED" | "REJECTED") => void;
}

const formatDate = (value: string) => new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
}).format(new Date(value));

const TrialRequestsTable = ({requests, onDecision}: Props) =>
{
    const styles = useTrialRequestsTableStyles();
    return (
        <div
            id={"platform-trial-requests-table-scroll"}
            className={styles.scroll}>
            <Table
                id={"platform-trial-requests-table"}
                size={"small"}
                className={styles.table}>
                <TableHeader id={"platform-trial-requests-table-header"}>
                    <TableRow id={"platform-trial-requests-header-row"}>
                        <TableHeaderCell id={"platform-trial-requests-requester-header"}>Requester</TableHeaderCell>
                        <TableHeaderCell id={"platform-trial-requests-owner-header"}>Owner</TableHeaderCell>
                        <TableHeaderCell id={"platform-trial-requests-plan-header"}>Trial</TableHeaderCell>
                        <TableHeaderCell id={"platform-trial-requests-date-header"}>Requested</TableHeaderCell>
                        <TableHeaderCell id={"platform-trial-requests-status-header"}>Status</TableHeaderCell>
                        <TableHeaderCell
                            id={"platform-trial-requests-actions-header"}
                            className={styles.actions}>Actions</TableHeaderCell>
                    </TableRow>
                </TableHeader>
                <TableBody id={"platform-trial-requests-table-body"}>
                    {requests.map(request => (
                        <TableRow
                            id={`platform-trial-request-row-${request.id}`}
                            key={request.id}>
                            <TableCell id={`platform-trial-request-requester-${request.id}`}>
                                <Text
                                    id={`platform-trial-request-requester-name-${request.id}`}
                                    weight={"semibold"}>{request.requesterName}</Text>
                                <br id={`platform-trial-request-requester-break-${request.id}`}/>
                                <Text
                                    id={`platform-trial-request-requester-email-${request.id}`}
                                    size={200}>{request.requesterEmail}</Text>
                            </TableCell>
                            <TableCell id={`platform-trial-request-owner-${request.id}`}>
                                {request.ownerName} ({request.ownerType})
                            </TableCell>
                            <TableCell id={`platform-trial-request-plan-${request.id}`}>{request.planCode}</TableCell>
                            <TableCell id={`platform-trial-request-date-${request.id}`}>
                                {formatDate(request.requestedAt)}
                            </TableCell>
                            <TableCell id={`platform-trial-request-status-${request.id}`}>
                                <Badge
                                    id={`platform-trial-request-status-badge-${request.id}`}
                                    appearance={"tint"}>{request.status}</Badge>
                            </TableCell>
                            <TableCell
                                id={`platform-trial-request-actions-cell-${request.id}`}
                                className={styles.actions}>
                                {request.status === "PENDING" && (
                                    <div
                                        id={`platform-trial-request-actions-${request.id}`}
                                        className={styles.actionButtons}>
                                        <Button
                                            id={`platform-trial-request-approve-${request.id}`}
                                            appearance={"primary"}
                                            shape={"circular"}
                                            onClick={() => onDecision(request, "APPROVED")}>
                                            Approve
                                        </Button>
                                        <Button
                                            id={`platform-trial-request-reject-${request.id}`}
                                            appearance={"secondary"}
                                            shape={"circular"}
                                            onClick={() => onDecision(request, "REJECTED")}>
                                            Reject
                                        </Button>
                                    </div>
                                )}
                            </TableCell>
                        </TableRow>
                    ))}
                    {requests.length === 0 && (
                        <TableRow id={"platform-trial-requests-empty-row"}>
                            <TableCell
                                id={"platform-trial-requests-empty-cell"}
                                colSpan={6}>No trial requests match this status.</TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </div>
    );
};

export default TrialRequestsTable;
