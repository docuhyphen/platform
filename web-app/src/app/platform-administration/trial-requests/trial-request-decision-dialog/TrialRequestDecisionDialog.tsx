import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Spinner,
} from "@fluentui/react-components";
import {SubscriptionTrialRequest} from "../../../../services/types/subscriptionTrialRequests.ts";
import {useTrialRequestDecisionDialogStyles} from "./TrialRequestDecisionDialogStyles.tsx";
import TrialRequestDecisionFields from "./TrialRequestDecisionFields.tsx";
import {useTrialRequestDecisionDialog} from "./useTrialRequestDecisionDialog.ts";

interface Props
{
    request: SubscriptionTrialRequest | null;
    decision: "APPROVED" | "REJECTED" | null;
    onDismiss: () => void;
    onSaved: () => void;
}

const TrialRequestDecisionDialog = ({request, decision, onDismiss, onSaved}: Props) =>
{
    const styles = useTrialRequestDecisionDialogStyles();
    const dialog = useTrialRequestDecisionDialog({request, decision, onSaved});

    return (
        <Dialog
            open={dialog.open}
            onOpenChange={(_, data) => !data.open && !dialog.saving && onDismiss()}>
            <DialogSurface id={"platform-trial-request-decision-surface"}>
                <DialogBody
                    id={"platform-trial-request-decision-body"}
                    className={styles.body}>
                    <DialogTitle id={"platform-trial-request-decision-title"}>
                        {dialog.approving ? "Approve" : "Reject"} {request?.planCode} trial request
                    </DialogTitle>
                    <DialogContent
                        id={"platform-trial-request-decision-content"}
                        className={styles.content}>
                        {dialog.error && (
                            <MessageBar
                                id={"platform-trial-request-decision-error"}
                                intent={"error"}>
                                <MessageBarBody id={"platform-trial-request-decision-error-body"}>{dialog.error}</MessageBarBody>
                            </MessageBar>
                        )}
                        <TrialRequestDecisionFields
                            approving={dialog.approving}
                            request={request}
                            durationDays={dialog.durationDays}
                            seatCapacity={dialog.seatCapacity}
                            reason={dialog.reason}
                            fieldsClassName={styles.fields}
                            setDurationDays={dialog.setDurationDays}
                            setSeatCapacity={dialog.setSeatCapacity}
                            setReason={dialog.setReason}/>
                    </DialogContent>
                    <DialogActions
                        id={"platform-trial-request-decision-actions"}
                        className={styles.footer}>
                        <Button
                            id={"platform-trial-request-decision-confirm"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={dialog.saving}
                            onClick={() => void dialog.save()}>
                            {dialog.saving
                                ? <Spinner id={"platform-trial-request-decision-saving"} size={"tiny"}/>
                                : dialog.approving ? "Approve" : "Reject"}
                        </Button>
                        <Button
                            id={"platform-trial-request-decision-cancel"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={dialog.saving}
                            onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default TrialRequestDecisionDialog;
