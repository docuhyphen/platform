import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Text,
    Textarea,
} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {TrustRelationshipAction} from "../useTrustedOrganizations.ts";
import {useTrustActionDialogStyles} from "./TrustActionDialogStyles.tsx";

interface TrustActionDialogProps
{
    action: TrustRelationshipAction | null;
    partnerName?: string;
    busy: boolean;
    onDismiss: () => void;
    onConfirm: (reason?: string) => Promise<void>;
}

const labels: Record<TrustRelationshipAction, {title: string; confirm: string; description: string}> = {
    ACCEPT: {title: "Accept trust request", confirm: "Accept", description: "Trust will become active with this organization."},
    REJECT: {title: "Reject trust request", confirm: "Reject", description: "This trust request will be closed."},
    WITHDRAW: {title: "Withdraw trust request", confirm: "Withdraw", description: "The pending request will be closed."},
    SUSPEND: {title: "Suspend trust", confirm: "Suspend", description: "New trusted operations will be blocked until you resume."},
    RESUME: {title: "Resume trust", confirm: "Resume", description: "Your organization suspension will be cleared."},
    END: {title: "End trust", confirm: "End trust", description: "Ending trust is terminal and cannot be resumed."},
};

const TrustActionDialog = ({action, partnerName, busy, onDismiss, onConfirm}: TrustActionDialogProps) =>
{
    const styles = useTrustActionDialogStyles();
    const [reason, setReason] = useState("");
    useEffect(() => setReason(""), [action]);
    if (!action) return null;
    const label = labels[action];
    const reasonRequired = action === "SUSPEND";

    return (
        <Dialog
            open={true}
            onOpenChange={(_, data) => !data.open && onDismiss()}
        >
            <DialogSurface id={"trusted-organization-action-dialog"}>
                <DialogBody id={"trusted-organization-action-dialog-body"}>
                    <DialogTitle id={"trusted-organization-action-dialog-title"}>{label.title}</DialogTitle>
                    <DialogContent
                        id={"trusted-organization-action-dialog-content"}
                        className={styles.content}
                    >
                        <Text id={"trusted-organization-action-description"}>{label.description} {partnerName}</Text>
                        {action !== "ACCEPT" && action !== "RESUME" && (
                            <Field
                                id={"trusted-organization-action-reason-field"}
                                label={reasonRequired ? "Reason" : "Optional reason"}
                                required={reasonRequired}
                            >
                                <Textarea
                                    id={"trusted-organization-action-reason"}
                                    value={reason}
                                    resize={"vertical"}
                                    onChange={(_, data) => setReason(data.value)}
                                />
                            </Field>
                        )}
                    </DialogContent>
                    <DialogActions id={"trusted-organization-action-dialog-actions"}>
                        <Button
                            id={"trusted-organization-action-confirm"}
                            shape={"circular"}
                            appearance={"primary"}
                            disabled={busy || (reasonRequired && !reason.trim())}
                            onClick={() => void onConfirm(reason.trim() || undefined)}
                        >
                            {label.confirm}
                        </Button>
                        <Button
                            id={"trusted-organization-action-cancel"}
                            shape={"circular"}
                            appearance={"secondary"}
                            disabled={busy}
                            onClick={onDismiss}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default TrustActionDialog;
