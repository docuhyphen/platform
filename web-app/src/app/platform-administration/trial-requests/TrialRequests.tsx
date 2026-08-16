import {Button, Field, MessageBar, MessageBarBody, Select, Spinner, Text} from "@fluentui/react-components";
import TrialRequestDecisionDialog from "./trial-request-decision-dialog/TrialRequestDecisionDialog.tsx";
import TrialRequestsTable from "./trial-requests-table/TrialRequestsTable.tsx";
import {useTrialRequestsStyles} from "./TrialRequestsStyles.tsx";
import {useTrialRequests} from "./useTrialRequests.ts";

const TrialRequests = () =>
{
    const styles = useTrialRequestsStyles();
    const state = useTrialRequests();
    const first = state.total === 0 ? 0 : state.offset + 1;
    const last = Math.min(state.offset + state.pageSize, state.total);
    return (
        <div
            id={"platform-trial-requests-container"}
            className={styles.container}>
            <div
                id={"platform-trial-requests-toolbar"}
                className={styles.toolbar}>
                <Text id={"platform-trial-requests-description"}>
                    Review Personal and Business trial requests submitted from Billing.
                </Text>
                <Field
                    id={"platform-trial-requests-status-field"}
                    className={styles.filter}
                    label={"Request status"}>
                    <Select
                        id={"platform-trial-requests-status-filter"}
                        value={state.status}
                        onChange={event => state.chooseStatus(event.target.value)}>
                        <option id={"platform-trial-requests-filter-pending"} value={"PENDING"}>Pending</option>
                        <option id={"platform-trial-requests-filter-approved"} value={"APPROVED"}>Approved</option>
                        <option id={"platform-trial-requests-filter-rejected"} value={"REJECTED"}>Rejected</option>
                        <option id={"platform-trial-requests-filter-all"} value={""}>All</option>
                    </Select>
                </Field>
            </div>
            {state.error && (
                <MessageBar
                    id={"platform-trial-requests-error"}
                    intent={"error"}>
                    <MessageBarBody id={"platform-trial-requests-error-body"}>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            {state.loading ? (
                <div
                    id={"platform-trial-requests-loading"}
                    className={styles.loading}>
                    <Spinner
                        id={"platform-trial-requests-loading-spinner"}
                        label={"Loading trial requests"}/>
                </div>
            ) : (
                <TrialRequestsTable
                    requests={state.items}
                    onDecision={state.openDecision}/>
            )}
            <div
                id={"platform-trial-requests-pagination"}
                className={styles.pagination}>
                <Text id={"platform-trial-requests-page-summary"}>{first}-{last} of {state.total}</Text>
                <Button
                    id={"platform-trial-requests-previous"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={state.offset === 0}
                    onClick={() => state.setOffset(Math.max(0, state.offset - state.pageSize))}>
                    Previous
                </Button>
                <Button
                    id={"platform-trial-requests-next"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={state.offset + state.pageSize >= state.total}
                    onClick={() => state.setOffset(state.offset + state.pageSize)}>
                    Next
                </Button>
            </div>
            <TrialRequestDecisionDialog
                request={state.selected}
                decision={state.decision}
                onDismiss={state.closeDecision}
                onSaved={() =>
                {
                    state.closeDecision();
                    void state.reload();
                }}/>
        </div>
    );
};

export default TrialRequests;
